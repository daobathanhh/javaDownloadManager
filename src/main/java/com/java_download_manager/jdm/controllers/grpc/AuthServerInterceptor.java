package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.repository.SessionRepository;
import com.java_download_manager.jdm.service.JwtTokenService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Parses the Bearer token from gRPC metadata and sets {@link GrpcAuthContext#ACCOUNT_ID}
 * and {@link GrpcAuthContext#ACCESS_TOKEN_JTI} when the token is a valid, non-revoked access token.
 * Protected RPCs can then use these context values; unauthenticated calls get null.
 */
@Component
@GrpcGlobalServerInterceptor
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class AuthServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTHORIZATION_CAPITAL =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SessionRepository sessionRepository;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        Long accountId = null;
        String accessTokenJti = null;

        String value = headers.get(AUTHORIZATION);
        if (value == null) value = headers.get(AUTHORIZATION_CAPITAL);
        if (value != null && value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = value.substring(BEARER_PREFIX.length()).trim();
            var claimsOpt = jwtTokenService.parseAndVerify(token);
            if (claimsOpt.isPresent() && "access".equals(claimsOpt.get().type())) {
                String jti = claimsOpt.get().jti();
                // Only set context if session exists and is not revoked
                if (sessionRepository.findByAccessTokenJtiAndRevokedAtIsNull(jti).isPresent()) {
                    accountId = claimsOpt.get().accountId();
                    accessTokenJti = jti;
                } else {
                    log.debug("Auth failed: no active session for jti={}", jti);
                }
            } else {
                log.debug("Auth failed: JWT invalid or not access type");
            }
        } else if (value != null) {
            log.debug("Auth failed: missing or invalid Bearer prefix");
        }

        Context context = Context.current()
                .withValue(GrpcAuthContext.ACCOUNT_ID, accountId)
                .withValue(GrpcAuthContext.ACCESS_TOKEN_JTI, accessTokenJti);

        return Contexts.interceptCall(context, call, headers, next);
    }
}
