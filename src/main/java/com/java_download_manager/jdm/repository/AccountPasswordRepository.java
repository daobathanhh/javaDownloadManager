package com.java_download_manager.jdm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java_download_manager.jdm.entities.AccountPassword;

public interface AccountPasswordRepository extends JpaRepository<AccountPassword, Long> {
}
