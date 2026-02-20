package com.java_download_manager.jdm.download;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;

import org.springframework.stereotype.Component;

@Component
public class ActiveDownloadRegistry {

    private final Map<Long, DynamicSegmentPool> pools = new ConcurrentHashMap<>();

    public void register(long taskId, DynamicSegmentPool pool) {
        pools.put(taskId, pool);
    }

    public void unregister(long taskId) {
        pools.remove(taskId);
    }

    public DynamicSegmentPool getPool(long taskId) {
        return pools.get(taskId);
    }

    public Set<Long> getActiveTaskIds() {
        return Set.copyOf(pools.keySet());
    }
}
