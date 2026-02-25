package com.java_download_manager.jdm.download.worker;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import com.java_download_manager.jdm.download.DownloadProgress;
import com.java_download_manager.jdm.download.core.Chunk;
import com.java_download_manager.jdm.download.core.ChunkState;
import com.java_download_manager.jdm.download.core.Download;
import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChunkWorker implements Runnable {

    private final Download download;
    private final DynamicSegmentPool pool;
    private final HttpClient httpClient;
    private final DownloadProgress sharedProgress;

    public ChunkWorker(Download download, DynamicSegmentPool pool, HttpClient httpClient) {
        this(download, pool, httpClient, null);
    }

    public ChunkWorker(Download download, DynamicSegmentPool pool, HttpClient httpClient, DownloadProgress sharedProgress) {
        this.download = download;
        this.pool = pool;
        this.httpClient = httpClient;
        this.sharedProgress = sharedProgress;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Optional<Chunk> opt = pool.takeSegment();
            if (opt.isEmpty()) {
                break;
            }
            Chunk chunk = opt.get();
            downloadChunk(chunk);
        }
    }

    private void downloadChunk(Chunk chunk) {
        chunk.setState(ChunkState.DOWNLOADING);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(download.getUrl()))
                    .header("Range", "bytes=" + chunk.getStartOffset() + "-" + chunk.getEndOffset())
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200 && status != 206) {
                throw new RuntimeException("Unexpected HTTP " + status);
            }

            try (InputStream in = response.body();
                RandomAccessFile raf = new RandomAccessFile(download.getOutputPath().toFile(), "rw")) {
                raf.seek(chunk.getStartOffset());
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Worker interrupted");
                    }
                    if (sharedProgress != null) {
                        while (sharedProgress.isPaused()) {
                            Thread.sleep(200);
                        }
                    }
                    raf.write(buffer, 0, read);
                    chunk.addDownloaded(read);
                    if (sharedProgress != null) {
                        sharedProgress.addDownloaded(read);
                    }
                }
            }

            chunk.setState(ChunkState.COMPLETED);
        } catch (Exception e) {
            chunk.setState(ChunkState.FAILED);
            throw new RuntimeException("Chunk " + chunk.getId() + " failed", e);
        }
    }
}
