package com.java_download_manager.jdm.controllers.grpc;

import io.grpc.Context;

public final class GrpcAuthContext {

    private GrpcAuthContext() {}
    public static final Context.Key<Long> ACCOUNT_ID = Context.key("account_id");
    public static final Context.Key<String> ACCESS_TOKEN_JTI = Context.key("access_token_jti");
    
}
