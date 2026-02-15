package com.java_download_manager.jdm.exceptions;

/**
 * Thrown when a password reset token is invalid, already used, or expired.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
