package com.java_download_manager.jdm.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity for the {@code download_tasks} table.
 * download_type in DB: 0=unspecified, 1=http (SMALLINT).
 * download_status in DB: 0=unspecified, 1=pending, 2=downloading, 3=failed, 4=success (SMALLINT).
 */
@Entity
@Table(name = "download_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "of_account_id", nullable = false)
    private Long ofAccountId;

    @Column(name = "download_type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    @Builder.Default
    private DownloadTypeEnum downloadType = DownloadTypeEnum.UNSPECIFIED;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "download_status", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    @Builder.Default
    private DownloadStatusEnum downloadStatus = DownloadStatusEnum.PENDING;

    @Column(name = "metadata", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** DB ordinal: 0=unspecified, 1=http. Matches proto DownloadType. */
    public enum DownloadTypeEnum {
        UNSPECIFIED,  // 0
        HTTP          // 1
    }

    /** DB ordinal: 0=unspecified, 1=pending, 2=downloading, 3=failed, 4=success. Matches proto DownloadStatus. */
    public enum DownloadStatusEnum {
        UNSPECIFIED,   // 0
        PENDING,       // 1
        DOWNLOADING,   // 2
        FAILED,        // 3
        SUCCESS        // 4
    }
}
