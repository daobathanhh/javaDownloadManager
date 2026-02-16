package com.java_download_manager.jdm.controllers.grpc;

import io.grpc.Context;

/**
 * Context keys set by the auth interceptor when a valid JWT is present.
 * Used by gRPC controllers for authenticated calls (e.g. ChangePassword, LogoutSession).
 */
public final class GrpcAuthContext {

    private GrpcAuthContext() {}

    /** Account ID from the JWT subject. Set by auth interceptor. */
    public static final Context.Key<Long> ACCOUNT_ID = Context.key("account_id");

    /** Access token JTI (session identifier). Set by auth interceptor for LogoutSession. */
    public static final Context.Key<String> ACCESS_TOKEN_JTI = Context.key("access_token_jti");
}
