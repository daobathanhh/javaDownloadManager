package com.java_download_manager.jdm.ws;

import com.java_download_manager.jdm.download.DownloadProgress;
import com.java_download_manager.jdm.download.ProgressManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProgressPushScheduler {

    private final ProgressManager progressManager;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 500)
    public void pushProgress() {
        for (Long taskId : progressManager.getActiveTaskIds()) {
            DownloadProgress p = progressManager.get(taskId);
            if (p == null) continue;
            long downloaded = p.getDownloadedBytes().get();
            long total = p.getTotalBytes();
            double percent = total > 0 ? (downloaded * 100.0 / total) : 0;
            long speed = p.getCurrentSpeedBytesPerSec();
            long remaining = (speed > 0 && total > downloaded) ? (total - downloaded) / speed : 0;
            ProgressPushPayload payload = new ProgressPushPayload(taskId, downloaded, total, percent,
                    speed, remaining, p.getStatus(), p.isPaused());
            messagingTemplate.convertAndSend("/topic/progress/" + taskId, payload);
        }
    }

    public record ProgressPushPayload(long taskId, long downloadedBytes, long totalBytes, double percent,
                                     long speedBytesPerSec, long remainingSeconds, String status, boolean paused) {}
}
