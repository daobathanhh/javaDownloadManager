package com.java_download_manager.jdm.controllers.grpc;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jdm.v1.AccountServiceGrpc;
import jdm.v1.CreateAccountRequest;
import jdm.v1.GetAccountRequest;
import jdm.v1.SessionServiceGrpc;
import jdm.v1.CreateSessionRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountGrpcControllerIntegrationTest {

    @GrpcClient("test")
    private AccountServiceGrpc.AccountServiceBlockingStub accountStub;

    @GrpcClient("test")
    private SessionServiceGrpc.SessionServiceBlockingStub sessionStub;

    private AccountServiceGrpc.AccountServiceBlockingStub withAuth(String accessToken) {
        Metadata headers = new Metadata();
        Metadata.Key<String> AUTHORIZATION =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(AUTHORIZATION, "Bearer " + accessToken);
        return MetadataUtils.attachHeaders(accountStub, headers);
    }

    @Test
    @DisplayName("CreateAccount then GetAccount (authenticated) returns same account")
    void createAndGetAccount() {
        String name = "testuser-" + System.currentTimeMillis();
        String email = name + "@example.com";
        String password = "secret123";

        var createResp = accountStub.createAccount(CreateAccountRequest.newBuilder()
                .setAccountName(name)
                .setPassword(password)
                .setEmail(email)
                .build());

        assertThat(createResp.hasAccount()).isTrue();
        assertThat(createResp.getAccount().getAccountName()).isEqualTo(name);
        assertThat(createResp.getAccount().getEmail()).isEqualTo(email);
        long id = createResp.getAccount().getId();

        // Log in to obtain access token
        var sessionResp = sessionStub.createSession(CreateSessionRequest.newBuilder()
                .setAccountName(name)
                .setPassword(password)
                .build());

        String accessToken = sessionResp.getAccessToken();
        var authedAccountStub = withAuth(accessToken);

        var getResp = authedAccountStub.getAccount(GetAccountRequest.newBuilder().setAccountId(id).build());
        assertThat(getResp.getAccount().getId()).isEqualTo(id);
        assertThat(getResp.getAccount().getAccountName()).isEqualTo(name);
    }

    @Test
    @DisplayName("GetAccount without authentication returns UNAUTHENTICATED")
    void getAccountUnauthenticated() {
        assertThatThrownBy(() ->
                accountStub.getAccount(GetAccountRequest.newBuilder().setAccountId(999999L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }
}
