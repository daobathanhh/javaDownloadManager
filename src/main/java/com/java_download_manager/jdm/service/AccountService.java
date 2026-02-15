package com.java_download_manager.jdm.service;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.entities.AccountPassword;
import com.java_download_manager.jdm.entities.Account.AccountStatusEnum;
import com.java_download_manager.jdm.entities.PasswordHistory;
import com.java_download_manager.jdm.entities.PasswordResetToken;
import com.java_download_manager.jdm.exceptions.AccountNotFoundException;
import com.java_download_manager.jdm.exceptions.AccountPasswordNotFoundException;
import com.java_download_manager.jdm.exceptions.DuplicateAccountException;
import com.java_download_manager.jdm.exceptions.InvalidNewPasswordException;
import com.java_download_manager.jdm.exceptions.InvalidPasswordException;
import com.java_download_manager.jdm.exceptions.AccountNotAllowedForPasswordResetException;
import com.java_download_manager.jdm.exceptions.InvalidResetTokenException;
import com.java_download_manager.jdm.repository.AccountPasswordRepository;
import com.java_download_manager.jdm.repository.AccountRepository;
import com.java_download_manager.jdm.repository.PasswordHistoryRepository;
import com.java_download_manager.jdm.repository.PasswordResetTokenRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    @Value("${jdm.password-reset.expiry-minutes:1440}")
    private int resetTokenExpiryMinutes;

    private final AccountRepository accountRepository;
    private final AccountPasswordRepository accountPasswordRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailService passwordResetMailService;
    private final EntityManager entityManager;


    @Transactional
    public Account createAccount(String accountName, String plainPassword, String email) {
        if (accountRepository.existsByAccountName(accountName)) {
            throw new DuplicateAccountException("Account name already exists: " + accountName);
        }
        Account account = Account.builder()
                .accountName(accountName)
                .email(email != null && !email.isBlank() ? email : null)
                .build();
        entityManager.persist(account);
        entityManager.flush();
        Long accountId = (Long) entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(account);
        if (accountId == null) {
            account = accountRepository.findByAccountName(accountName).orElseThrow();
            accountId = account.getId();
        } else {
            account.setId(accountId);
        }

        String hash = passwordEncoder.encode(plainPassword);
        LocalDateTime now = LocalDateTime.now();
        AccountPassword accountPassword = AccountPassword.builder()
                .account(account)
                .ofAccountId(accountId)
                .hash(hash)
                .createdAt(now)
                .updatedAt(now)
                .build();
        accountPasswordRepository.save(accountPassword);

        return account;
    }

    public Optional<Account> getAccountById(long accountId) {
        return accountRepository.findById(accountId);
    }

    public Optional<Account> getAccountByAccountName(String accountName) {
        return accountRepository.findByAccountName(accountName);
    }

    @Transactional
    public void changePassword(long accountId, String currentPassword, String newPassword) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        
        AccountPassword accountPassword = accountPasswordRepository.findById(accountId)
                .orElseThrow(() -> new AccountPasswordNotFoundException("Account not found: " + accountId));

        if (!passwordEncoder.matches(currentPassword, accountPassword.getHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, accountPassword.getHash())) {
            throw new InvalidNewPasswordException("New password must be different from the current password");
        }

        passwordHistoryRepository.save(PasswordHistory.builder()
                .ofAccountId(accountId)
                .hash(accountPassword.getHash())
                .build());

        accountPassword.setHash(passwordEncoder.encode(newPassword));
        accountPasswordRepository.save(accountPassword);
    }

    /**
     * Request a password reset for the given email. Always returns without error (to prevent
     * email enumeration). If an account exists, a reset token is created and should be sent
     * to the user (e.g. by email); caller or a separate sender can use the returned token
     * for the reset link, or implement an email sender and not return the token.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<Account> accountOpt = accountRepository.findByEmail(email.trim());
        if (accountOpt.isEmpty()) {
            return; // Don't reveal that the account doesn't exist
        }
        Account account = accountOpt.get();
        if (account.getAccountStatus() == AccountStatusEnum.DISABLED) {
            throw new AccountNotAllowedForPasswordResetException("Account is disabled");
        }
        if (account.getAccountStatus() == AccountStatusEnum.LOCKED) {
            throw new AccountNotAllowedForPasswordResetException("Account is locked");
        }
        // Invalidate any existing unused reset tokens so only the latest link works
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.findByOfAccountIdAndUsedAtIsNull(account.getId())
                .forEach(token -> {
                    token.setUsedAt(now);
                    passwordResetTokenRepository.save(token);
                });
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = now.plusMinutes(resetTokenExpiryMinutes);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .ofAccountId(account.getId())
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build());

        if (account.getEmail() != null && !account.getEmail().isBlank()) {
            passwordResetMailService.sendPasswordResetEmail(account.getEmail(), rawToken);
        }
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        String tokenHash = hashToken(resetToken);
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, now)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid, expired, or already used reset token"));

        long accountId = token.getOfAccountId();
        AccountPassword accountPassword = accountPasswordRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        passwordHistoryRepository.save(PasswordHistory.builder()
                .ofAccountId(accountId)
                .hash(accountPassword.getHash())
                .build());

        accountPassword.setHash(passwordEncoder.encode(newPassword));
        accountPasswordRepository.save(accountPassword);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
