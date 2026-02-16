package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.service.SessionService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jdm.v1.*;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

@GrpcService
@Component
@RequiredArgsConstructor
public class SessionGrpcController extends SessionServiceGrpc.SessionServiceImplBase {

    private final SessionService sessionService;

    @Override
    public void createSession(CreateSessionRequest request, StreamObserver<CreateSessionResponse> responseObserver) {
        var resultOpt = sessionService.createSession(
                request.getAccountName(),
                request.getPassword(),
                null,
                null
        );
        if (resultOpt.isEmpty()) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Invalid account name or password")
                    .asRuntimeException());
            return;
        }
        SessionService.CreateSessionResult result = resultOpt.get();
        CreateSessionResponse response = CreateSessionResponse.newBuilder()
                .setAccount(GrpcMappers.toProtoAccount(result.account()))
                .setAccessToken(result.accessToken())
                .setRefreshToken(result.refreshToken())
                .setAccessTokenExpiresIn(result.accessTokenExpiresInSeconds())
                .setRefreshTokenExpiresIn(result.refreshTokenExpiresInSeconds())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void refreshSession(RefreshSessionRequest request, StreamObserver<RefreshSessionResponse> responseObserver) {
        var resultOpt = sessionService.refreshSession(request.getRefreshToken());
        if (resultOpt.isEmpty()) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Invalid or expired refresh token")
                    .asRuntimeException());
            return;
        }
        SessionService.RefreshSessionResult result = resultOpt.get();
        RefreshSessionResponse response = RefreshSessionResponse.newBuilder()
                .setAccessToken(result.accessToken())
                .setRefreshToken(result.refreshToken())
                .setAccessTokenExpiresIn(result.accessTokenExpiresInSeconds())
                .setRefreshTokenExpiresIn(result.refreshTokenExpiresInSeconds())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void logoutSession(LogoutSessionRequest request, StreamObserver<LogoutSessionResponse> responseObserver) {
        String accessTokenJti = GrpcAuthContext.ACCESS_TOKEN_JTI.get();
        if (accessTokenJti == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required (access token from Authorization header)")
                    .asRuntimeException());
            return;
        }
        sessionService.revokeSessionByAccessTokenJti(accessTokenJti);
        responseObserver.onNext(LogoutSessionResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void logoutAllSessions(LogoutAllSessionsRequest request, StreamObserver<LogoutAllSessionsResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        int revoked = sessionService.revokeAllSessionsForAccount(accountId);
        responseObserver.onNext(LogoutAllSessionsResponse.newBuilder().setSessionsRevoked(revoked).build());
        responseObserver.onCompleted();
    }
}
