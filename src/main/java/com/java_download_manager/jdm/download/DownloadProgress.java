package com.java_download_manager.jdm.download;

import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;

@Getter
public class DownloadProgress {

    private final long totalBytes;
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private final long startTime = System.currentTimeMillis();
    private volatile long lastUpdatedTime = System.currentTimeMillis();
    private volatile long currentSpeedBytesPerSec;
    private volatile long previousDownloadedForSpeed;
    private volatile String status = "DOWNLOADING";
    private volatile boolean paused;

    public DownloadProgress(long totalBytes) {
        this.totalBytes = totalBytes;
        this.previousDownloadedForSpeed = 0;
    }

    public void addDownloaded(long n) {
        downloadedBytes.addAndGet(n);
        lastUpdatedTime = System.currentTimeMillis();
    }

    public void setDownloaded(long bytes) {
        downloadedBytes.set(bytes);
        lastUpdatedTime = System.currentTimeMillis();
    }

    public long updateSpeed() {
        long current = downloadedBytes.get();
        long delta = current - previousDownloadedForSpeed;
        previousDownloadedForSpeed = current;
        currentSpeedBytesPerSec = delta;
        return currentSpeedBytesPerSec;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isComplete() {
        return totalBytes > 0 && downloadedBytes.get() >= totalBytes;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
