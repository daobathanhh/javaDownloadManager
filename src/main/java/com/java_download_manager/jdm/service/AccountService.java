package com.java_download_manager.jdm.service;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.entities.AccountPassword;
import com.java_download_manager.jdm.repository.AccountPasswordRepository;
import com.java_download_manager.jdm.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountPasswordRepository accountPasswordRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates an account and stores the hashed password.
     *
     * @throws DuplicateAccountException if accountName already exists
     */
    @Transactional
    public Account createAccount(String accountName, String plainPassword, String email) {
        if (accountRepository.existsByAccountName(accountName)) {
            throw new DuplicateAccountException("Account name already exists: " + accountName);
        }
        Account account = Account.builder()
                .accountName(accountName)
                .email(email != null && !email.isBlank() ? email : null)
                .build();
        account = accountRepository.save(account);

        String hash = passwordEncoder.encode(plainPassword);
        LocalDateTime now = LocalDateTime.now();
        AccountPassword accountPassword = AccountPassword.builder()
                .account(account)
                .ofAccountId(account.getId())
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

    public static class DuplicateAccountException extends RuntimeException {
        public DuplicateAccountException(String message) {
            super(message);
        }
    }
}
