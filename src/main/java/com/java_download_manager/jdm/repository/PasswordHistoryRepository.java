package com.java_download_manager.jdm.repository;

import com.java_download_manager.jdm.entities.PasswordHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByOfAccountIdOrderByCreatedAtDesc(Long ofAccountId, Pageable pageable);
}
