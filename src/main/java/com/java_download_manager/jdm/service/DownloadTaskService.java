package com.java_download_manager.jdm.service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadStatusEnum;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadTypeEnum;
import com.java_download_manager.jdm.mq.DownloadTaskCreatedEvent;
import com.java_download_manager.jdm.mq.DownloadTaskCreatedProducer;
import com.java_download_manager.jdm.download.ActiveDownloadRegistry;
import com.java_download_manager.jdm.download.DownloadExecutor;
import com.java_download_manager.jdm.download.DownloadProgress;
import com.java_download_manager.jdm.download.ProgressManager;
import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;
import com.java_download_manager.jdm.repository.DownloadTaskRepository;
import com.java_download_manager.jdm.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadTaskService {

    private final DownloadTaskRepository downloadTaskRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private DownloadTaskCreatedProducer downloadTaskCreatedProducer;

    @Autowired(required = false)
    private FileStorageService fileStorageService;

    @Autowired(required = false)
    private ActiveDownloadRegistry activeDownloadRegistry;

    @Autowired(required = false)
    private ProgressManager progressManager;

    private final DownloadExecutor downloadExecutor;

    /**
     * Create a download task (status PENDING) and publish a RabbitMQ event so a consumer
     * can run the download. If RabbitMQ is not configured, the event is skipped.
     */
    @Transactional
    public DownloadTask createDownloadTask(long accountId, URI uri, DownloadTypeEnum downloadType, String metadataJson) {
        String url = uri.toString();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (downloadType == null) {
            throw new IllegalArgumentException("Download type is required");
        }
        if (metadataJson == null || metadataJson.isBlank()) {
            throw new IllegalArgumentException("Metadata is required");
        }
        try {
            DownloadTask downloadTask = new DownloadTask();
            downloadTask.setOfAccountId(accountId);
            downloadTask.setUrl(url);
            downloadTask.setDownloadType(downloadType);
            downloadTask.setMetadata(metadataJson);
            downloadTask = downloadTaskRepository.save(downloadTask);
            final long savedTaskId = downloadTask.getId();

            if (downloadTaskCreatedProducer != null) {
                try {
                    downloadTaskCreatedProducer.send(new DownloadTaskCreatedEvent(savedTaskId));
                } catch (Exception ex) {
                    log.warn("Failed to send download-task-created event for taskId={}", savedTaskId, ex);
                }
            }

            return downloadTask;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create download task", e);
        }
    }

    public Optional<DownloadTask> getDownloadTask(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            return Optional.empty();
        }
        return Optional.of(downloadTaskRepository.findByOfAccountIdAndId(accountId, taskId));
    }

    public List<DownloadTask> listDownloadTasks(long accountId, int page, int pageSize) {
        return downloadTaskRepository.findByOfAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(page, pageSize));
    }

    public long countDownloadTasks(long accountId) {
        return downloadTaskRepository.countByOfAccountId(accountId);
    }

    /**
     * Get runtime segment/worker status for a task currently downloading.
     * Returns empty when task is not found, not owned by account, or not using parallel chunk download.
     */
    public Optional<List<DynamicSegmentPool.SegmentDetail>> getSegmentDetails(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            return Optional.empty();
        }
        if (activeDownloadRegistry == null) {
            return Optional.empty();
        }
        DynamicSegmentPool pool = activeDownloadRegistry.getPool(taskId);
        if (pool == null) {
            return Optional.empty();
        }
        return Optional.of(pool.getAllChunkDetails());
    }

    public Optional<DownloadTask> updateDownloadTask(long accountId, long taskId, String url) {
        if (downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            DownloadTask downloadTask = downloadTaskRepository.findByOfAccountIdAndId(accountId, taskId);
            downloadTask.setUrl(url);
            return Optional.of(downloadTaskRepository.save(downloadTask));
        }
        return Optional.empty();
    }

    public boolean deleteDownloadTask(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            return false;
        }
        downloadTaskRepository.deleteById(taskId);
        return true;
    }

    /**
     * Get file input stream for a completed download task. Caller must close the stream.
     */
    public Optional<InputStream> getDownloadTaskFile(long accountId, long taskId) {
        Optional<DownloadTask> opt = getDownloadTask(accountId, taskId);
        if (opt.isEmpty() || fileStorageService == null) {
            return Optional.empty();
        }
        DownloadTask task = opt.get();
        if (task.getDownloadStatus() != DownloadStatusEnum.SUCCESS) {
            return Optional.empty();
        }
        String storedKey = extractStoredKey(task.getMetadata());
        if (storedKey == null || storedKey.isBlank()) {
            return Optional.empty();
        }
        return fileStorageService.download(storedKey);
    }

    private String extractStoredKey(String metadata) {
        try {
            String json = metadata != null ? metadata : "{}";
            var node = objectMapper.readTree(json);
            return node.has("storedKey") ? node.get("storedKey").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Atomic: set status to DOWNLOADING only if PENDING. Returns true if updated. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean setStatusDownloadingIfPending(long taskId) {
        int updated = downloadTaskRepository.updateStatusIf(taskId, DownloadStatusEnum.PENDING, DownloadStatusEnum.DOWNLOADING);
        return updated > 0;
    }

    /** Pause a downloading task. No-op if not found, not owned, or not in ProgressManager. */
    public boolean pauseTask(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId) || progressManager == null) {
            return false;
        }
        DownloadProgress p = progressManager.get(taskId);
        if (p == null) return false;
        p.setPaused(true);
        return true;
    }

    /** Resume a paused download. */
    public boolean resumeTask(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId) || progressManager == null) {
            return false;
        }
        DownloadProgress p = progressManager.get(taskId);
        if (p == null) return false;
        p.setPaused(false);
        return true;
    }

    /**
     * Get in-memory progress for a downloading task. Returns empty when not found or not actively downloading.
     */
    public Optional<DownloadProgress> getProgress(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            return Optional.empty();
        }
        if (progressManager == null) {
            return Optional.empty();
        }
        DownloadProgress p = progressManager.get(taskId);
        return Optional.ofNullable(p);
    }

    /**
     * Get an "effective" status string for a task, preferring live in-memory state over
     * the persisted DB status. Returns empty when the task does not belong to the account
     * or there is no more accurate runtime status available.
     */
    public Optional<String> getEffectiveStatus(long accountId, long taskId) {
        if (!downloadTaskRepository.existsByOfAccountIdAndId(accountId, taskId)) {
            return Optional.empty();
        }

        if (progressManager != null) {
            DownloadProgress p = progressManager.get(taskId);
            if (p != null) {
                if (p.isPaused()) {
                    return Optional.of("PAUSED");
                }
                String status = p.getStatus();
                if (status != null && !status.isBlank()) {
                    return Optional.of(status);
                }
                return Optional.of(DownloadStatusEnum.DOWNLOADING.name());
            }
        }

        if (activeDownloadRegistry != null && activeDownloadRegistry.getPool(taskId) != null) {
            return Optional.of(DownloadStatusEnum.DOWNLOADING.name());
        }

        return Optional.empty();
    }

    /**
     * Execute a download task by id. Called by the RabbitMQ consumer (or a cron). Idempotent:
     * only runs when status is PENDING. Sets DOWNLOADING, fetches URL, uploads to S3/MinIO if
     * configured, then sets SUCCESS or FAILED. Progress is written to metadata during DOWNLOADING.
     */
    @Transactional
    public void executeDownloadTask(long taskId) {
        Optional<DownloadTask> opt = downloadTaskRepository.findById(taskId);
        if (opt.isEmpty()) {
            log.debug("Execute download task: task not found, taskId={}", taskId);
            return;
        }
        DownloadTask task = opt.get();
        if (!setStatusDownloadingIfPending(taskId)) {
            log.debug("Execute download task: not PENDING (or already taken), skipping taskId={}", taskId);
            return;
        }

        try {
            if (task.getDownloadType() != DownloadTypeEnum.HTTP) {
                throw new UnsupportedOperationException("Only HTTP download type is supported");
            }
            if (fileStorageService == null) {
                throw new IllegalStateException("File storage (S3/MinIO) not configured; add jdm.storage.s3.bucket");
            }
            String storedKey = runDownloadTask(task.getUrl(), task.getOfAccountId(), taskId);
            task.setDownloadStatus(DownloadStatusEnum.SUCCESS);
            task.setCompletedAt(LocalDateTime.now());
            task.setMetadata(mergeMetadataWithStoredPath(task.getMetadata(), storedKey));
            downloadTaskRepository.save(task);
            log.info("Execute download task: success taskId={} storedKey={}", taskId, storedKey);
        } catch (Exception e) {
            log.error("Execute download task: failed taskId={}", taskId, e);
            task.setDownloadStatus(DownloadStatusEnum.FAILED);
            task.setCompletedAt(LocalDateTime.now());
            task.setMetadata(mergeMetadataWithError(task.getMetadata(), e.getMessage()));
            downloadTaskRepository.save(task);
        }
    }

    /**
     * Download via DownloadExecutor, then stream to storage. No DB progress writes.
     */
    private String runDownloadTask(String url, long accountId, long taskId) throws Exception {
        Path tempFile = Files.createTempFile("jdm-", ".tmp");
        try {
            String filename = downloadExecutor.execute(url, tempFile, taskId);
            String key = "downloads/" + accountId + "/" + taskId + "/" + filename;
            long size = Files.size(tempFile);
            try (InputStream in = Files.newInputStream(tempFile)) {
                return fileStorageService.upload(key, in, size);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String mergeMetadataWithStoredPath(String existingMetadata, String storedKey) {
        try {
            String json = existingMetadata != null ? existingMetadata : "{}";
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            if (storedKey != null) {
                node.put("storedKey", storedKey);
            }
            return node.toString();
        } catch (Exception e) {
            return existingMetadata != null ? existingMetadata : "{}";
        }
    }

    private String mergeMetadataWithError(String existingMetadata, String errorMessage) {
        try {
            String json = existingMetadata != null ? existingMetadata : "{}";
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            node.put("error", errorMessage);
            return node.toString();
        } catch (Exception e) {
            return existingMetadata != null ? existingMetadata : "{}";
        }
    }
}
