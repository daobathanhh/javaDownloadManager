package com.java_download_manager.jdm.download;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.java_download_manager.jdm.download.core.Download;
import com.java_download_manager.jdm.download.network.FileRequester;
import com.java_download_manager.jdm.download.network.StreamDownloader;
import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;
import com.java_download_manager.jdm.download.worker.ChunkWorker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadExecutor {

    private final FileRequester fileRequester;
    private final StreamDownloader streamDownloader;

    @Qualifier("downloadHttpClient")
    private final HttpClient httpClient;

    @Qualifier("downloadWorkerPool")
    private final ExecutorService executor;

    @Autowired(required = false)
    private ActiveDownloadRegistry activeDownloadRegistry;

    @Autowired(required = false)
    private ProgressManager progressManager;

    @Value("${jdm.download.chunk-count:4}")
    private int chunkCount;

    @Value("${jdm.download.min-segment-size-bytes:262144}")
    private long minSegmentSizeBytes;

    public String execute(String url, Path outputPath) {
        return execute(url, outputPath, null);
    }

    public String execute(String url, Path outputPath, Long taskId) {
        Download download = new Download(url, outputPath, 0, null);
        boolean metadataOk = fileRequester.requestMetadata(download);

        String filename = download.getSuggestedFilename();
        if (filename == null || filename.isBlank()) {
            filename = filenameFromUrl(url);
        }

        if (metadataOk && download.getTotalSize() > 0) {
            log.debug("HEAD ok, using parallel chunk download size={}", download.getTotalSize());
            runChunkedDownload(download, taskId);
        } else {
            log.info("HEAD failed or no Content-Length, falling back to GET stream");
            DownloadProgress streamProgress = null;
            if (progressManager != null && taskId != null) {
                streamProgress = progressManager.register(taskId, -1L);
            }
            try {
                streamDownloader.download(download, null, streamProgress);
            } finally {
                if (progressManager != null && taskId != null) {
                    progressManager.unregister(taskId);
                }
            }
            if (download.getSuggestedFilename() != null && !download.getSuggestedFilename().isBlank()) {
                filename = download.getSuggestedFilename();
            }
        }

        return filename;
    }

    private void runChunkedDownload(Download download, Long taskId) {
        long totalSize = download.getTotalSize();
        DynamicSegmentPool pool = new DynamicSegmentPool(totalSize, minSegmentSizeBytes, chunkCount);

        DownloadProgress progress = null;
        if (progressManager != null && taskId != null) {
            progress = progressManager.register(taskId, totalSize);
        }
        if (activeDownloadRegistry != null && taskId != null) {
            activeDownloadRegistry.register(taskId, pool);
        }
        try {
            runChunkedDownloadInner(download, pool, progress);
        } finally {
            if (progressManager != null && taskId != null) {
                progressManager.unregister(taskId);
            }
            if (activeDownloadRegistry != null && taskId != null) {
                activeDownloadRegistry.unregister(taskId);
            }
        }
    }

    private void runChunkedDownloadInner(Download download, DynamicSegmentPool pool, DownloadProgress progress) {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            futures.add(executor.submit(new ChunkWorker(download, pool, httpClient, progress)));
        }
        RuntimeException firstError = null;
        try {
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (ExecutionException e) {
            firstError = e.getCause() instanceof RuntimeException re ? re : new RuntimeException("Download failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            firstError = new RuntimeException("Download interrupted", e);
        } finally {
            if (firstError != null) {
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
            }
        }
        if (firstError != null) {
            throw firstError;
        }
        if (!pool.isComplete()) {
            throw new RuntimeException("Download incomplete");
        }
    }

    private String filenameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.contains("/")) {
                String last = path.substring(path.lastIndexOf('/') + 1);
                if (!last.isBlank()) {
                    return last;
                }
            }
        } catch (Exception ignored) {
        }
        return "download";
    }
}
