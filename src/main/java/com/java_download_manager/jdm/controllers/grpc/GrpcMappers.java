package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.entities.Account;
import com.google.protobuf.Timestamp;
import jdm.v1.AccountStatus;

import java.time.ZoneOffset;

/** Shared mapping from entities to gRPC proto messages. */
public final class GrpcMappers {

    private GrpcMappers() {}

    public static AccountStatus toProtoAccountStatus(Account.AccountStatusEnum entityStatus) {
        return switch (entityStatus) {
            case DISABLED -> AccountStatus.ACCOUNT_STATUS_DISABLED;
            case ACTIVE -> AccountStatus.ACCOUNT_STATUS_ACTIVE;
            case LOCKED -> AccountStatus.ACCOUNT_STATUS_LOCKED;
        };
    }

    public static jdm.v1.Account toProtoAccount(Account entity) {
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
