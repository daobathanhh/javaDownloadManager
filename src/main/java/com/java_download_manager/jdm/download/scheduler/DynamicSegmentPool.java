package com.java_download_manager.jdm.download.scheduler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.java_download_manager.jdm.download.core.Chunk;
import com.java_download_manager.jdm.download.core.ChunkState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DynamicSegmentPool {

    private final long totalSize;
    private final long minSegmentSize;
    private final AtomicInteger nextId = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Chunk> pending = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Chunk> allChunks = new ConcurrentLinkedQueue<>();

    public DynamicSegmentPool(long totalSize, long minSegmentSize) {
        if (totalSize <= 0) throw new IllegalArgumentException("totalSize must be positive");
        if (minSegmentSize <= 0) throw new IllegalArgumentException("minSegmentSize must be positive");
        this.totalSize = totalSize;
        this.minSegmentSize = minSegmentSize;
        Chunk initial = new Chunk(nextId.getAndIncrement(), 0, totalSize - 1, 0);
        pending.offer(initial);
        allChunks.offer(initial);
    }

    public synchronized Optional<Chunk> takeSegment() {
        Chunk largest = findLargestPending();
        if (largest == null) {
            return Optional.empty();
        }
        pending.remove(largest);

        long size = largest.getSize();
        if (size >= 2 * minSegmentSize) {
            long mid = largest.getStartOffset() + size / 2 - 1;
            Chunk first = new Chunk(nextId.getAndIncrement(), largest.getStartOffset(), mid, largest.getStartOffset());
            Chunk second = new Chunk(nextId.getAndIncrement(), mid + 1, largest.getEndOffset(), mid + 1);
            pending.offer(second);
            allChunks.offer(first);
            allChunks.offer(second);
            log.debug("Split segment [{}-{}] into [{}-{}] and [{}-{}]",
                    largest.getStartOffset(), largest.getEndOffset(),
                    first.getStartOffset(), first.getEndOffset(),
                    second.getStartOffset(), second.getEndOffset());
            return Optional.of(first);
        }
        return Optional.of(largest);
    }

    private Chunk findLargestPending() {
        return pending.stream()
                .filter(c -> c.getState() == ChunkState.PENDING)
                .max(Comparator.comparingLong(Chunk::getSize))
                .orElse(null);
    }

    public long getTotalDownloaded() {
        return allChunks.stream()
                .mapToLong(Chunk::getDownloaded)
                .sum();
    }

    public boolean isComplete() {
        return getTotalDownloaded() >= totalSize;
    }

    public SegmentStats getSegmentStats() {
        int pending = 0;
        int downloading = 0;
        int completed = 0;
        int failed = 0;
        for (Chunk c : allChunks) {
            switch (c.getState()) {
                case PENDING -> pending++;
                case DOWNLOADING -> downloading++;
                case COMPLETED -> completed++;
                case FAILED -> failed++;
            }
        }
        return new SegmentStats(pending, downloading, completed, failed);
    }

    public List<SegmentDetail> getAllChunkDetails() {
        return allChunks.stream()
                .map(c -> new SegmentDetail(
                        c.getId(),
                        c.getStartOffset(),
                        c.getEndOffset(),
                        c.getSize(),
                        c.getDownloaded(),
                        c.getState().name()))
                .toList();
    }

    public record SegmentStats(int pending, int downloading, int completed, int failed) {}

    public record SegmentDetail(int id, long startOffset, long endOffset, long size,
                                long downloaded, String state) {}
}
