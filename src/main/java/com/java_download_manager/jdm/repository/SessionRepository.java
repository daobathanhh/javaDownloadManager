package com.java_download_manager.jdm.repository;

import com.java_download_manager.jdm.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByAccessTokenJtiAndRevokedAtIsNull(String accessTokenJti);

    Optional<Session> findByRefreshTokenJtiAndRevokedAtIsNull(String refreshTokenJti);

    List<Session> findByOfAccountIdAndRevokedAtIsNull(Long ofAccountId);
}
