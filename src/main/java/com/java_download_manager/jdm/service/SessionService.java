package com.java_download_manager.jdm.service;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.entities.Session;
import com.java_download_manager.jdm.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final AccountService accountService;
    private final JwtTokenService jwtTokenService;
    private final TokenService tokenService;
    private final SessionRepository sessionRepository;

    public record CreateSessionResult(
            Account account,
            String accessToken,
            String refreshToken,
            long accessTokenExpiresInSeconds,
            long refreshTokenExpiresInSeconds
    ) {}

    public record RefreshSessionResult(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresInSeconds,
            long refreshTokenExpiresInSeconds
    ) {}

    @Transactional
    public Optional<CreateSessionResult> createSession(String accountName, String password, String ipAddress, String userAgent) {
        Optional<Account> accountOpt = accountService.validateCredentials(accountName, password);
        if (accountOpt.isEmpty()) return Optional.empty();

        Account account = accountOpt.get();
        long accountId = account.getId();

        String accessJti = UUID.randomUUID().toString().replace("-", "");
        String refreshJti = UUID.randomUUID().toString().replace("-", "");

        String accessToken = jwtTokenService.generateAccessToken(accountId, accessJti);
        String refreshToken = jwtTokenService.generateRefreshToken(accountId, refreshJti);

        long accessExpiresSec = jwtTokenService.getAccessTokenExpirySeconds();
        long refreshExpiresSec = jwtTokenService.getRefreshTokenExpirySeconds();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExpiresAt = now.plusSeconds(accessExpiresSec);
        LocalDateTime refreshExpiresAt = now.plusSeconds(refreshExpiresSec);

        long keyId = Long.parseLong(tokenService.getCurrentKeyId());

        Session session = Session.builder()
                .ofAccountId(accountId)
                .accessTokenJti(accessJti)
                .refreshTokenJti(refreshJti)
                .tokenPublicKeyId(keyId)
                .ipAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : null)
                .userAgent(userAgent != null && !userAgent.isBlank() ? userAgent : null)
                .accessTokenExpiresAt(accessExpiresAt)
                .refreshTokenExpiresAt(refreshExpiresAt)
                .build();
        sessionRepository.save(session);

        return Optional.of(new CreateSessionResult(
                account,
                accessToken,
                refreshToken,
                accessExpiresSec,
                refreshExpiresSec
        ));
    }

    @Transactional
    public Optional<RefreshSessionResult> refreshSession(String refreshToken) {
        var claimsOpt = jwtTokenService.parseAndVerify(refreshToken);
        if (claimsOpt.isEmpty()) return Optional.empty();
        if (!"refresh".equals(claimsOpt.get().type())) return Optional.empty();

        String refreshJti = claimsOpt.get().jti();
        long accountId = claimsOpt.get().accountId();

        Optional<Session> sessionOpt = sessionRepository.findByRefreshTokenJtiAndRevokedAtIsNull(refreshJti);
        if (sessionOpt.isEmpty()) return Optional.empty();
        Session session = sessionOpt.get();
        if (session.getOfAccountId() != accountId) return Optional.empty();

        String newRefreshJti = UUID.randomUUID().toString().replace("-", "");
        String newRefreshToken = jwtTokenService.generateRefreshToken(accountId, newRefreshJti);
        long refreshExpiresSec = jwtTokenService.getRefreshTokenExpirySeconds();
        String newAccessJti = UUID.randomUUID().toString().replace("-", "");
        String newAccessToken = jwtTokenService.generateAccessToken(accountId, newAccessJti);
        long accessExpiresSec = jwtTokenService.getAccessTokenExpirySeconds();

        LocalDateTime now = LocalDateTime.now();
        session.setAccessTokenJti(newAccessJti);
        session.setAccessTokenExpiresAt(now.plusSeconds(accessExpiresSec));
        session.setRefreshTokenJti(newRefreshJti);
        session.setRefreshTokenExpiresAt(now.plusSeconds(refreshExpiresSec));
        sessionRepository.save(session);

        return Optional.of(new RefreshSessionResult(
                newAccessToken,
                newRefreshToken,
                accessExpiresSec,
                refreshExpiresSec
        ));
    }

    @Transactional
    public boolean revokeSessionByAccessTokenJti(String accessTokenJti) {
        Optional<Session> sessionOpt = sessionRepository.findByAccessTokenJtiAndRevokedAtIsNull(accessTokenJti);
        if (sessionOpt.isEmpty()) return false;
        Session session = sessionOpt.get();
        session.setRevokedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return true;
    }

    @Transactional
    public int revokeAllSessionsForAccount(long accountId) {
        var sessions = sessionRepository.findByOfAccountIdAndRevokedAtIsNull(accountId);
        LocalDateTime now = LocalDateTime.now();
        for (Session s : sessions) {
            s.setRevokedAt(now);
            sessionRepository.save(s);
        }
        return sessions.size();
    }
}
