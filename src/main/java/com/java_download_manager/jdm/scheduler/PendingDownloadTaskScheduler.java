package com.java_download_manager.jdm.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.repository.DownloadTaskRepository;
import com.java_download_manager.jdm.service.DownloadTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingDownloadTaskScheduler {

    private final DownloadTaskRepository downloadTaskRepository;
    private final DownloadTaskService downloadTaskService;

    private static final int BATCH_SIZE = 8;

    @Scheduled(cron = "${jdm.cron.execute-pending-download-task:0 * * * * ?}")
    public void executePendingDownloadTasks() {
        List<DownloadTask> pending = downloadTaskRepository.findByDownloadStatusOrderByIdAsc(DownloadTask.DownloadStatusEnum.PENDING);
        if (pending.isEmpty()) return;

        int limit = Math.min(pending.size(), BATCH_SIZE);
        for (int i = 0; i < limit; i++) {
            long taskId = pending.get(i).getId();
            try {
                downloadTaskService.executeDownloadTask(taskId);
            } catch (Exception e) {
                log.warn("Cron: failed to execute pending task taskId={}", taskId, e);
            }
        }
    }
}
