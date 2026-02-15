package com.java_download_manager.jdm.controllers.grpc;

import com.google.protobuf.Timestamp;
import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.exceptions.DuplicateAccountException;
import com.java_download_manager.jdm.exceptions.AccountNotAllowedForPasswordResetException;
import com.java_download_manager.jdm.exceptions.AccountNotFoundException;
import com.java_download_manager.jdm.exceptions.AccountPasswordNotFoundException;
import com.java_download_manager.jdm.exceptions.InvalidNewPasswordException;
import com.java_download_manager.jdm.exceptions.InvalidPasswordException;
import com.java_download_manager.jdm.exceptions.InvalidResetTokenException;
import com.java_download_manager.jdm.service.AccountService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jdm.v1.AccountServiceGrpc;
import jdm.v1.AccountStatus;
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

    /** Set by auth interceptor from JWT/session. Used for ChangePassword. */
    public static final Context.Key<Long> ACCOUNT_ID_CTX = Context.key("account_id");

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
        } catch (DuplicateAccountException e) {
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
        Long accountId = ACCOUNT_ID_CTX.get();
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
        Long accountId = ACCOUNT_ID_CTX.get();
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

    /**
     * Maps entity account status to proto enum explicitly so we do not depend on
     * Java enum ordinal order (which would break if someone reorders or adds values).
     */
    private static AccountStatus toProtoAccountStatus(Account.AccountStatusEnum entityStatus) {
        return switch (entityStatus) {
            case DISABLED -> AccountStatus.ACCOUNT_STATUS_DISABLED;
            case ACTIVE -> AccountStatus.ACCOUNT_STATUS_ACTIVE;
            case LOCKED -> AccountStatus.ACCOUNT_STATUS_LOCKED;
        };
    }

    private static jdm.v1.Account toProtoAccount(Account entity) {
        var builder = jdm.v1.Account.newBuilder()
                .setId(entity.getId())
                .setAccountName(entity.getAccountName())
                .setEmail(entity.getEmail() != null ? entity.getEmail() : "")
                .setFailedLoginAttempts(entity.getFailedLoginAttempts())
                .setAccountStatus(toProtoAccountStatus(entity.getAccountStatus()));

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
