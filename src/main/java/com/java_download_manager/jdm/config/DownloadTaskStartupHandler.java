package com.java_download_manager.jdm.config;

import java.time.LocalDateTime;
import java.util.List;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadStatusEnum;
import com.java_download_manager.jdm.repository.DownloadTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DownloadTaskStartupHandler implements ApplicationRunner {

    private final DownloadTaskRepository downloadTaskRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DownloadTask> stuck = downloadTaskRepository.findByDownloadStatusOrderByIdAsc(DownloadStatusEnum.DOWNLOADING);
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Marking {} tasks left in DOWNLOADING as FAILED (server was restarted)", stuck.size());
        for (DownloadTask t : stuck) {
            t.setDownloadStatus(DownloadStatusEnum.FAILED);
            t.setCompletedAt(LocalDateTime.now());
            downloadTaskRepository.save(t);
        }
    }
}
