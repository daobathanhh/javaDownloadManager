package com.java_download_manager.jdm.download.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.java_download_manager.jdm.download.core.Download;
import com.java_download_manager.jdm.download.util.ContentDispositionParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileRequester {

    @Qualifier("requestMetadataExecutor")
    private final Executor requestMetadataExecutor;

    private final HttpClient httpClient;

    private static final String UA = MetadataRequestHeaders.UA_CHROME;

    public CompletableFuture<Boolean> requestMetadataAsync(Download download) {
        CompletableFuture<Boolean> f1 = CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return tryHead(download);
            }
        }, requestMetadataExecutor);
        CompletableFuture<Boolean> f2 = CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return tryRange(download, 0, 0);
            }
        }, requestMetadataExecutor);
        CompletableFuture<Boolean> f3 = CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return tryRange(download, 0, 1);
            }
        }, requestMetadataExecutor);
        CompletableFuture<Boolean> f4 = CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return tryRange(download, 0, 499);
            }
        }, requestMetadataExecutor);

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Consumer<Boolean> onComplete = new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (Boolean.TRUE.equals(success)) {
                    result.complete(true);
                }
            }
        };
        f1.thenAccept(onComplete);
        f2.thenAccept(onComplete);
        f3.thenAccept(onComplete);
        f4.thenAccept(onComplete);

        CompletableFuture.allOf(f1, f2, f3, f4).thenRun(new Runnable() {
            @Override
            public void run() {
                if (!result.isDone()) {
                    result.complete(tryFullGetAbort(download));
                }
            }
        });
        return result;
    }

    public boolean requestMetadata(Download download) {
        if (tryHead(download)) return true;
        if (tryRange(download, 0, 0)) return true;
        if (tryRange(download, 0, 1)) return true;
        if (tryRange(download, 0, 499)) return true;
        if (tryFullGetAbort(download)) return true;
        return false;
    }

    private HttpRequest.Builder newMetadataRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Referer", "https://www.google.com/");
    }

    private boolean tryHead(Download download) {
        HttpRequest request = newMetadataRequest(download.getUrl())
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            return false;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }

        long size = extractSizeFromResponse(response);
        if (size <= 0) return false;

        download.setTotalSize(size);
        response.headers().firstValue("Content-Disposition").ifPresent(disposition -> {
            String filename = ContentDispositionParser.parseFilename(disposition);
            if (filename != null && !filename.isBlank()) {
                download.setSuggestedFilename(filename);
            }
        });
        log.debug("Got total size {} from HEAD for {}", size, download.getUrl());
        return true;
    }

    private boolean tryRange(Download download, long start, long end) {
        HttpRequest request = newMetadataRequest(download.getUrl())
                .header("Range", "bytes=" + start + "-" + end)
                .GET()
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.trace("Range {}-{} failed for {}", start, end, download.getUrl(), e);
            return false;
        }

        if (response.statusCode() != 200 && response.statusCode() != 206) {
            return false;
        }

        String contentRange = response.headers().firstValue("Content-Range").orElse(null);
        if (contentRange != null && !contentRange.isBlank()) {
            long total = parseTotalFromContentRange(contentRange);
            if (total > 0) {
                download.setTotalSize(total);
                response.headers().firstValue("Content-Disposition").ifPresent(disposition -> {
                    String filename = ContentDispositionParser.parseFilename(disposition);
                    if (filename != null && !filename.isBlank()) {
                        download.setSuggestedFilename(filename);
                    }
                });
                log.debug("Got total size {} from Content-Range (bytes {}-{}) for {}", total, start, end, download.getUrl());
                return true;
            }
        }

        long size = extractSizeFromResponse(response);
        if (size > 0) {
            download.setTotalSize(size);
            return true;
        }
        return false;
    }

    private boolean tryFullGetAbort(Download download) {
        HttpRequest request = newMetadataRequest(download.getUrl())
                .header("Connection", "close")
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            log.debug("Full GET (abort) failed for {}", download.getUrl(), e);
            return false;
        }

        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }

            long size = extractSizeFromResponse(response);
            if (size <= 0) return false;

            download.setTotalSize(size);
            response.headers().firstValue("Content-Disposition").ifPresent(disposition -> {
                String filename = ContentDispositionParser.parseFilename(disposition);
                if (filename != null && !filename.isBlank()) {
                    download.setSuggestedFilename(filename);
                }
            });
            log.debug("Got total size {} from full GET (abort) for {}", size, download.getUrl());
            return true;
        } finally {
            try {
                response.body().close();
            } catch (Exception ignored) {
            }
        }
    }

    private long extractSizeFromResponse(HttpResponse<?> response) {
        java.net.http.HttpHeaders h = response.headers();

        long fromContentLength = parseLongHeader(h.firstValue("Content-Length").orElse(null));
        if (fromContentLength > 0) return fromContentLength;

        String contentRange = h.firstValue("Content-Range").orElse(null);
        if (contentRange != null && !contentRange.isBlank()) {
            long total = parseTotalFromContentRange(contentRange);
            if (total > 0) return total;
        }

        for (String name : MetadataRequestHeaders.SIZE_HEADER_NAMES) {
            if ("Content-Length".equals(name)) continue;
            long v = parseLongHeader(h.firstValue(name).orElse(null));
            if (v > 0) return v;
        }
        return -1;
    }

    private static long parseLongHeader(String value) {
        if (value == null || value.isBlank()) return -1;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseTotalFromContentRange(String contentRange) {
        int slash = contentRange.indexOf('/');
        if (slash < 0) return -1;
        String part = contentRange.substring(slash + 1).trim();
        if ("*".equals(part)) return -1;
        return parseLongHeader(part);
    }
}
