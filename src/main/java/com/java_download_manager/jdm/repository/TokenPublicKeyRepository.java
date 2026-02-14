package com.java_download_manager.jdm.repository;

import com.java_download_manager.jdm.entities.TokenPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenPublicKeyRepository extends JpaRepository<TokenPublicKey, Long> {

    List<TokenPublicKey> findByIsActiveTrueOrderByCreatedAtDesc();
}
