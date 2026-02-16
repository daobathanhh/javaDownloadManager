package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.exceptions.DuplicateAccountException;
import com.java_download_manager.jdm.exceptions.AccountNotAllowedForPasswordResetException;
import com.java_download_manager.jdm.exceptions.AccountNotFoundException;
import com.java_download_manager.jdm.exceptions.AccountPasswordNotFoundException;
import com.java_download_manager.jdm.exceptions.InvalidNewPasswordException;
import com.java_download_manager.jdm.exceptions.InvalidPasswordException;
import com.java_download_manager.jdm.exceptions.InvalidResetTokenException;
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
            jdm.v1.Account protoAccount = GrpcMappers.toProtoAccount(account);
            responseObserver.onNext(CreateAccountResponse.newBuilder().setAccount(protoAccount).build());
            responseObserver.onCompleted();
        } catch (DuplicateAccountException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getAccount(GetAccountRequest request, StreamObserver<GetAccountResponse> responseObserver) {
        Long currentAccountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (currentAccountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        if (request.getAccountId() != currentAccountId) {
            responseObserver.onError(Status.PERMISSION_DENIED
                    .withDescription("Can only get your own account")
                    .asRuntimeException());
            return;
        }
        try {
            var accountOpt = accountService.getAccountById(request.getAccountId());
            if (accountOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Account not found: " + request.getAccountId())
                        .asRuntimeException());
                return;
            }
            jdm.v1.Account protoAccount = GrpcMappers.toProtoAccount(accountOpt.get());
            responseObserver.onNext(GetAccountResponse.newBuilder().setAccount(protoAccount).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Internal error").withCause(e).asRuntimeException());
        }
    }

    @Override
    public void changePassword(jdm.v1.ChangePasswordRequest request,
                              StreamObserver<jdm.v1.ChangePasswordResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            accountService.changePassword(accountId, request.getCurrentPassword(), request.getNewPassword());
            responseObserver.onNext(jdm.v1.ChangePasswordResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (AccountNotFoundException | AccountPasswordNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidPasswordException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidNewPasswordException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void requestPasswordReset(jdm.v1.RequestPasswordResetRequest request,
                                    StreamObserver<jdm.v1.RequestPasswordResetResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId != null) {
            var accountOpt = accountService.getAccountById(accountId);
            if (accountOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Account not found")
                        .asRuntimeException());
                return;
            }
            Account account = accountOpt.get();
            String requestEmail = request.getEmail() != null ? request.getEmail().trim() : "";
            String accountEmail = account.getEmail() != null ? account.getEmail().trim() : "";
            if (!requestEmail.equalsIgnoreCase(accountEmail)) {
                responseObserver.onError(Status.PERMISSION_DENIED
                        .withDescription("You can only request a password reset for your own account email")
                        .asRuntimeException());
                return;
            }
        }
        try {
            accountService.requestPasswordReset(request.getEmail());
            responseObserver.onNext(jdm.v1.RequestPasswordResetResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (AccountNotAllowedForPasswordResetException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void resetPassword(jdm.v1.ResetPasswordRequest request,
                              StreamObserver<jdm.v1.ResetPasswordResponse> responseObserver) {
        try {
            accountService.resetPassword(request.getResetToken(), request.getNewPassword());
            responseObserver.onNext(jdm.v1.ResetPasswordResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (InvalidResetTokenException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

}
