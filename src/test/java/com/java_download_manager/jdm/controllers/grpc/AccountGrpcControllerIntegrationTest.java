package com.java_download_manager.jdm.controllers.grpc;

import io.grpc.StatusRuntimeException;
import jdm.v1.AccountServiceGrpc;
import jdm.v1.CreateAccountRequest;
import jdm.v1.GetAccountRequest;
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

    @Test
    @DisplayName("CreateAccount then GetAccount returns same account")
    void createAndGetAccount() {
        String name = "testuser-" + System.currentTimeMillis();
        String email = name + "@example.com";

        var createResp = accountStub.createAccount(CreateAccountRequest.newBuilder()
                .setAccountName(name)
                .setPassword("secret123")
                .setEmail(email)
                .build());

        assertThat(createResp.hasAccount()).isTrue();
        assertThat(createResp.getAccount().getAccountName()).isEqualTo(name);
        assertThat(createResp.getAccount().getEmail()).isEqualTo(email);
        long id = createResp.getAccount().getId();

        var getResp = accountStub.getAccount(GetAccountRequest.newBuilder().setAccountId(id).build());
        assertThat(getResp.getAccount().getId()).isEqualTo(id);
        assertThat(getResp.getAccount().getAccountName()).isEqualTo(name);
    }

    @Test
    @DisplayName("GetAccount with unknown id returns NOT_FOUND")
    void getAccountNotFound() {
        assertThatThrownBy(() ->
                accountStub.getAccount(GetAccountRequest.newBuilder().setAccountId(999999L).build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("NOT_FOUND");
    }
}
