package com.java_download_manager.jdm.repository;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadStatusEnum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DownloadTaskRepository extends JpaRepository<DownloadTask, Long> {

    List<DownloadTask> findByDownloadStatusOrderByIdAsc(DownloadStatusEnum downloadStatus);

    @Modifying
    @Query("UPDATE DownloadTask t SET t.downloadStatus = :toStatus WHERE t.id = :taskId AND t.downloadStatus = :fromStatus")
    int updateStatusIf(@Param("taskId") long taskId, @Param("fromStatus") DownloadStatusEnum fromStatus, @Param("toStatus") DownloadStatusEnum toStatus);

    List<DownloadTask> findByOfAccountIdOrderByCreatedAtDesc(Long ofAccountId, Pageable pageable);

    long countByOfAccountId(Long ofAccountId);
    
    boolean existsByUrl(String url);

    boolean existsByOfAccountIdAndId(Long ofAccountId, Long id);

    DownloadTask findByOfAccountIdAndId(Long ofAccountId, Long id);
}
