    package com.java_download_manager.jdm.download;

    import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;

    public record ProgressInfo(
            long bytesDownloaded,
            long totalSize,
            DynamicSegmentPool.SegmentStats segmentStats) {

        public static ProgressInfo of(long bytes, long total) {
            return new ProgressInfo(bytes, total, null);
        }

        public static ProgressInfo of(long bytes, long total, DynamicSegmentPool.SegmentStats stats) {
            return new ProgressInfo(bytes, total, stats);
        }
    }
