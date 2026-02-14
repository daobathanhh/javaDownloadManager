package com.java_download_manager.jdm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.java_download_manager.jdm.entities.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountName(String accountName);

    boolean existsByAccountName(String accountName);
}
