package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.entities.DownloadTask;
import com.google.protobuf.Timestamp;
import jdm.v1.AccountStatus;
import jdm.v1.DownloadStatus;
import jdm.v1.DownloadType;

import java.time.ZoneOffset;

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

    public static DownloadType toProtoDownloadType(DownloadTask.DownloadTypeEnum entityType) {
        return switch (entityType) {
            case UNSPECIFIED -> DownloadType.DOWNLOAD_TYPE_UNSPECIFIED;
            case HTTP -> DownloadType.DOWNLOAD_TYPE_HTTP;
        };
    }

    public static DownloadStatus toProtoDownloadStatus(DownloadTask.DownloadStatusEnum entityStatus) {
        return switch (entityStatus) {
            case UNSPECIFIED -> DownloadStatus.DOWNLOAD_STATUS_UNSPECIFIED;
            case PENDING -> DownloadStatus.DOWNLOAD_STATUS_PENDING;
            case DOWNLOADING -> DownloadStatus.DOWNLOAD_STATUS_DOWNLOADING;
            case FAILED -> DownloadStatus.DOWNLOAD_STATUS_FAILED;
            case SUCCESS -> DownloadStatus.DOWNLOAD_STATUS_SUCCESS;
        };
    }

    public static jdm.v1.DownloadTask toProtoDownloadTask(DownloadTask entity) {
        var builder = jdm.v1.DownloadTask.newBuilder()
                .setId(entity.getId())
                .setAccountId(entity.getOfAccountId())
                .setUrl(entity.getUrl() != null ? entity.getUrl() : "")
                .setDownloadType(toProtoDownloadType(entity.getDownloadType()))
                .setDownloadStatus(toProtoDownloadStatus(entity.getDownloadStatus()))
                .setMetadata(entity.getMetadata() != null ? entity.getMetadata() : "{}");

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
        if (entity.getCompletedAt() != null) {
            var instant = entity.getCompletedAt().atZone(ZoneOffset.UTC).toInstant();
            builder.setCompletedAt(Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build());
        }
        return builder.build();
    }
}
