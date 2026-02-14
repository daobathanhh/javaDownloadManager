package com.java_download_manager.jdm.controllers.grpc;

import com.google.protobuf.Timestamp;
import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.service.AccountService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jdm.v1.AccountServiceGrpc;
import jdm.v1.CreateAccountRequest;
import jdm.v1.CreateAccountResponse;
import jdm.v1.GetAccountRequest;
import jdm.v1.GetAccountResponse;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@GrpcService
@Component
@RequiredArgsConstructor
public class AccountGrpcController extends AccountServiceGrpc.AccountServiceImplBase {

    private final AccountService accountService;

    @Override
    public void createAccount(CreateAccountRequest request, StreamObserver<CreateAccountResponse> responseObserver) {
        try {
            Account account = accountService.createAccount(
                    request.getAccountName(),
                    request.getPassword(),
                    request.getEmail()
            );
            jdm.v1.Account protoAccount = toProtoAccount(account);
            responseObserver.onNext(CreateAccountResponse.newBuilder().setAccount(protoAccount).build());
            responseObserver.onCompleted();
        } catch (AccountService.DuplicateAccountException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getAccount(GetAccountRequest request, StreamObserver<GetAccountResponse> responseObserver) {
        var accountOpt = accountService.getAccountById(request.getAccountId());
        if (accountOpt.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Account not found: " + request.getAccountId())
                    .asRuntimeException());
            return;
        }
        jdm.v1.Account protoAccount = toProtoAccount(accountOpt.get());
        responseObserver.onNext(GetAccountResponse.newBuilder().setAccount(protoAccount).build());
        responseObserver.onCompleted();
    }

    @Override
    public void changePassword(jdm.v1.ChangePasswordRequest request,
                              StreamObserver<jdm.v1.ChangePasswordResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Not implemented yet").asRuntimeException());
    }

    @Override
    public void requestPasswordReset(jdm.v1.RequestPasswordResetRequest request,
                                    StreamObserver<jdm.v1.RequestPasswordResetResponse> responseObserver) {
        responseObserver.onNext(jdm.v1.RequestPasswordResetResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void resetPassword(jdm.v1.ResetPasswordRequest request,
                              StreamObserver<jdm.v1.ResetPasswordResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("Not implemented yet").asRuntimeException());
    }

    private static jdm.v1.Account toProtoAccount(Account entity) {
        var builder = jdm.v1.Account.newBuilder()
                .setId(entity.getId())
                .setAccountName(entity.getAccountName())
                .setEmail(entity.getEmail() != null ? entity.getEmail() : "")
                .setFailedLoginAttempts(entity.getFailedLoginAttempts())
                .setAccountStatusValue(entity.getAccountStatus().ordinal() + 1); // proto: 1=DISABLED, 2=ACTIVE, 3=LOCKED

        if (entity.getCreatedAt() != null) {
            var instant = entity.getCreatedAt().atZone(ZoneOffset.UTC).toInstant();
            builder.setCreatedAt(Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build());
        }
        if (entity.getUpdatedAt() != null) {
            var instant = entity.getUpdatedAt().atZone(ZoneOffset.UTC).toInstant();
            builder.setUpdatedAt(Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build());
        }
        return builder.build();
    }
}
