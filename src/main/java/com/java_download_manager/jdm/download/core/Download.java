package com.java_download_manager.jdm.download.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Download {

    private final String url;
    private Path outputPath;
    private long totalSize;
    private List<Chunk> chunks;
    private String suggestedFilename;

    public Download(String url, Path outputPath, long totalSize, List<Chunk> chunks) {
        this.url = Objects.requireNonNull(url);
        this.outputPath = Objects.requireNonNull(outputPath);
        this.totalSize = totalSize;
        this.chunks = chunks != null ? List.copyOf(chunks) : null;
    }

    public long getTotalDownloaded() {
        if (chunks == null) return 0;
        return chunks.stream().mapToLong(Chunk::getDownloaded).sum();
    }

    public boolean isComplete() {
        return chunks != null && getTotalDownloaded() >= totalSize;
    }
}
