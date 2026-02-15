package com.java_download_manager.jdm.exceptions;

/**
 * Thrown when a password reset cannot be sent because the account is disabled or locked.
 */
public class AccountNotAllowedForPasswordResetException extends RuntimeException {

    public AccountNotAllowedForPasswordResetException(String message) {
        super(message);
    }
}
