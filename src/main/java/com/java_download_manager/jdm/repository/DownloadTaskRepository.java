package com.java_download_manager.jdm.repository;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadTaskRepository extends JpaRepository<DownloadTask, Long> {

    List<DownloadTask> findByOfAccountIdOrderByCreatedAtDesc(Long ofAccountId, Pageable pageable);

    long countByOfAccountId(Long ofAccountId);
}
