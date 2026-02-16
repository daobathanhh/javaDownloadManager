package com.java_download_manager.jdm.service;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.entities.AccountPassword;
import com.java_download_manager.jdm.entities.PasswordResetToken;
import com.java_download_manager.jdm.exceptions.*;
import com.java_download_manager.jdm.repository.AccountPasswordRepository;
import com.java_download_manager.jdm.repository.AccountRepository;
import com.java_download_manager.jdm.repository.PasswordHistoryRepository;
import com.java_download_manager.jdm.repository.PasswordResetTokenRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountPasswordRepository accountPasswordRepository;
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetMailService passwordResetMailService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private EntityManagerFactory entityManagerFactory;
    @Mock
    private PersistenceUnitUtil persistenceUnitUtil;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountService, "resetTokenExpiryMinutes", 1440);
    }

    @Test
    void createAccount_success() {
        when(accountRepository.existsByAccountName("alice")).thenReturn(false);
        when(entityManager.getEntityManagerFactory()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.getPersistenceUnitUtil()).thenReturn(persistenceUnitUtil);
        when(persistenceUnitUtil.getIdentifier(any(Account.class))).thenReturn(1L);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        Account saved = accountService.createAccount("alice", "secret", "alice@example.com");

        assertThat(saved.getAccountName()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        verify(entityManager).persist(any(Account.class));
        verify(entityManager).flush();
        verify(entityManager).persist(argThat(ap ->
                ap instanceof AccountPassword
                        && "hashed".equals(((AccountPassword) ap).getHash())
                        && Long.valueOf(1L).equals(((AccountPassword) ap).getOfAccountId())));
    }

    @Test
    void createAccount_throwsWhenDuplicateName() {
        when(accountRepository.existsByAccountName("alice")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount("alice", "secret", "a@b.com"))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("alice");

        verify(entityManager, never()).persist(any());
    }

    @Test
    void getAccountById_found() {
        Account account = Account.builder().id(1L).accountName("alice").build();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        Optional<Account> result = accountService.getAccountById(1L);

        assertThat(result).hasValue(account);
    }

    @Test
    void getAccountById_notFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Account> result = accountService.getAccountById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void changePassword_success() {
        Account account = Account.builder().id(1L).build();
        AccountPassword ap = new AccountPassword();
        ap.setOfAccountId(1L);
        ap.setHash("oldHash");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountPasswordRepository.findById(1L)).thenReturn(Optional.of(ap));
        when(passwordEncoder.matches("current", "oldHash")).thenReturn(true);
        when(passwordEncoder.matches("newPass", "oldHash")).thenReturn(false);
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");

        accountService.changePassword(1L, "current", "newPass");

        verify(passwordHistoryRepository).save(any());
        assertThat(ap.getHash()).isEqualTo("newHash");
        verify(accountPasswordRepository).save(ap);
    }

    @Test
    void changePassword_throwsWhenAccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changePassword(1L, "cur", "new"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void changePassword_throwsWhenWrongCurrentPassword() {
        Account account = Account.builder().id(1L).build();
        AccountPassword ap = new AccountPassword();
        ap.setHash("oldHash");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountPasswordRepository.findById(1L)).thenReturn(Optional.of(ap));
        when(passwordEncoder.matches("wrong", "oldHash")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(1L, "wrong", "new"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void changePassword_throwsWhenNewPasswordSameAsCurrent() {
        Account account = Account.builder().id(1L).build();
        AccountPassword ap = new AccountPassword();
        ap.setHash("oldHash");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountPasswordRepository.findById(1L)).thenReturn(Optional.of(ap));
        when(passwordEncoder.matches("same", "oldHash")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword(1L, "same", "same"))
                .isInstanceOf(InvalidNewPasswordException.class);
    }

    @Test
    void requestPasswordReset_doesNothingForBlankEmail() {
        accountService.requestPasswordReset("   ");
        accountService.requestPasswordReset(null);

        verify(accountRepository, never()).findByEmail(any());
    }

    @Test
    void requestPasswordReset_doesNothingWhenNoAccount() {
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        accountService.requestPasswordReset("nobody@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetMailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestPasswordReset_sendsEmailForActiveAccount() {
        Account account = Account.builder().id(1L).email("alice@example.com")
                .accountStatus(Account.AccountStatusEnum.ACTIVE).build();
        when(accountRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(account));
        when(passwordResetTokenRepository.findByOfAccountIdAndUsedAtIsNull(1L)).thenReturn(List.of());

        accountService.requestPasswordReset("alice@example.com");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetMailService).sendPasswordResetEmail(eq("alice@example.com"), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isNotBlank();
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_throwsWhenAccountDisabled() {
        Account account = Account.builder().id(1L).email("a@b.com")
                .accountStatus(Account.AccountStatusEnum.DISABLED).build();
        when(accountRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.requestPasswordReset("a@b.com"))
                .isInstanceOf(AccountNotAllowedForPasswordResetException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void requestPasswordReset_throwsWhenAccountLocked() {
        Account account = Account.builder().id(1L).email("a@b.com")
                .accountStatus(Account.AccountStatusEnum.LOCKED).build();
        when(accountRepository.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.requestPasswordReset("a@b.com"))
                .isInstanceOf(AccountNotAllowedForPasswordResetException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void resetPassword_throwsWhenTokenInvalid() {
        when(passwordResetTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.resetPassword("bad-token", "newPass"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("Invalid");
    }
}
