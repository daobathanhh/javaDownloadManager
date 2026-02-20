package com.java_download_manager.jdm.download.network;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.java_download_manager.jdm.download.DownloadProgress;
import com.java_download_manager.jdm.download.ProgressInfo;
import com.java_download_manager.jdm.download.core.Download;
import com.java_download_manager.jdm.download.util.ContentDispositionParser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StreamDownloader {

    private final HttpClient httpClient;

    public void download(Download download) {
        download(download, null);
    }

    public void download(Download download, Consumer<ProgressInfo> progressCallback) {
        download(download, progressCallback, null);
    }

    public void download(Download download, Consumer<ProgressInfo> progressCallback, DownloadProgress sharedProgress) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(download.getUrl()))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new RuntimeException("GET failed", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("GET failed, HTTP " + response.statusCode());
        }

        response.headers().firstValue("Content-Disposition").ifPresent(disposition -> {
            String filename = ContentDispositionParser.parseFilename(disposition);
            if (filename != null && !filename.isBlank()) {
                download.setSuggestedFilename(filename);
            }
        });

        long totalSize = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path outputPath = download.getOutputPath();
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(outputPath)) {
            byte[] buffer = new byte[65536];
            long bytesDownloaded = 0;
            long lastCallbackAt = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesDownloaded += read;
                if (sharedProgress != null) {
                    sharedProgress.setDownloaded(bytesDownloaded);
                }
                if (progressCallback != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastCallbackAt >= 500) {
                        lastCallbackAt = now;
                        progressCallback.accept(ProgressInfo.of(bytesDownloaded, totalSize > 0 ? totalSize : -1));
                    }
                }
            }
            if (progressCallback != null) {
                progressCallback.accept(ProgressInfo.of(bytesDownloaded, totalSize > 0 ? totalSize : -1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream download", e);
        }
    }
}
