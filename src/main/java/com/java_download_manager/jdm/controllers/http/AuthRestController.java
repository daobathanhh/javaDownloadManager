package com.java_download_manager.jdm.controllers.http;

import com.java_download_manager.jdm.entities.Account;
import com.java_download_manager.jdm.exceptions.DuplicateAccountException;
import com.java_download_manager.jdm.service.AccountService;
import com.java_download_manager.jdm.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthRestController {

    private final AccountService accountService;
    private final SessionService sessionService;

    @PostMapping("/login")
    @Operation(summary = "Login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        var result = sessionService.createSession(body.accountName(), body.password(), ip, userAgent);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var r = result.get();
        return ResponseEntity.ok(new LoginResponse(
                toAccountDto(r.account()),
                r.accessToken(),
                r.refreshToken(),
                r.accessTokenExpiresInSeconds(),
                r.refreshTokenExpiresInSeconds()));
    }

    @PostMapping("/signup")
    @Operation(summary = "Sign up")
    public ResponseEntity<AccountDto> signup(@RequestBody SignupRequest body) {
        try {
            Account account = accountService.createAccount(body.accountName(), body.password(), body.email());
            return ResponseEntity.status(HttpStatus.CREATED).body(toAccountDto(account));
        } catch (DuplicateAccountException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest body) {
        var result = sessionService.refreshSession(body.refreshToken());
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var r = result.get();
        return ResponseEntity.ok(new RefreshResponse(
                r.accessToken(),
                r.refreshToken(),
                r.accessTokenExpiresInSeconds(),
                r.refreshTokenExpiresInSeconds()));
    }

    private static AccountDto toAccountDto(Account a) {
        return new AccountDto(
                a.getId(),
                a.getAccountName(),
                a.getEmail() != null ? a.getEmail() : "",
                a.getAccountStatus().name(),
                a.getFailedLoginAttempts());
    }

    public record LoginRequest(String accountName, String password) {}

    public record LoginResponse(AccountDto account, String accessToken, String refreshToken,
                                long accessTokenExpiresIn, long refreshTokenExpiresIn) {}

    public record SignupRequest(String accountName, String password, String email) {}

    public record RefreshRequest(String refreshToken) {}

    public record RefreshResponse(String accessToken, String refreshToken,
                                  long accessTokenExpiresIn, long refreshTokenExpiresIn) {}

    public record AccountDto(long id, String accountName, String email, String status, int failedLoginAttempts) {}

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
