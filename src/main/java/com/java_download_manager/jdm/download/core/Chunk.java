package com.java_download_manager.jdm.download.core;

import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;

@Getter
public class Chunk {

    private final int id;
    private final long startOffset;
    private final long endOffset;
    private final long currentOffset;
    private final AtomicLong downloaded = new AtomicLong(0);
    private volatile ChunkState state = ChunkState.PENDING;

    public Chunk(int id, long startOffset, long endOffset, long currentOffset) {
        if (startOffset < 0 || endOffset < startOffset || currentOffset < startOffset || currentOffset > endOffset) {
            throw new IllegalArgumentException("Invalid chunk range");
        }
        this.id = id;
        this.startOffset = startOffset;
        this.currentOffset = currentOffset;
        this.endOffset = endOffset;
    }

    public long getSize() {
        return endOffset - startOffset + 1;
    }

    public long getDownloaded() {
        return downloaded.get();
    }

    public void addDownloaded(long bytes) {
        downloaded.addAndGet(bytes);
    }

    public boolean isComplete() {
        return downloaded.get() >= getSize();
    }

    public void setState(ChunkState state) {
        this.state = state;
    }
}
