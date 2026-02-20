package com.java_download_manager.jdm.download;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProgressManager {

    private final ConcurrentHashMap<Long, DownloadProgress> progressMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService speedScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jdm-progress-speed");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> speedTask;

    public DownloadProgress register(long taskId, long totalBytes) {
        DownloadProgress progress = new DownloadProgress(totalBytes);
        progressMap.put(taskId, progress);
        ensureSpeedTaskRunning();
        return progress;
    }

    public void unregister(long taskId) {
        progressMap.remove(taskId);
    }

    public DownloadProgress get(long taskId) {
        return progressMap.get(taskId);
    }

    public Set<Long> getActiveTaskIds() {
        return Set.copyOf(progressMap.keySet());
    }

    private synchronized void ensureSpeedTaskRunning() {
        if (speedTask == null || speedTask.isCancelled()) {
            speedTask = speedScheduler.scheduleAtFixedRate(this::updateAllSpeeds, 1, 1, TimeUnit.SECONDS);
        }
    }

    private void updateAllSpeeds() {
        for (DownloadProgress p : progressMap.values()) {
            p.updateSpeed();
        }
    }

    @PreDestroy
    public void shutdown() {
        speedScheduler.shutdown();
        try {
            if (!speedScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                speedScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            speedScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
