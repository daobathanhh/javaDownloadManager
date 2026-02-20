package com.java_download_manager.jdm.controllers.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.java_download_manager.jdm.controllers.http")
public class HttpExceptionHandler {

    @ExceptionHandler(DownloadTaskRestController.UnauthorizedException.class)
    public ResponseEntity<Void> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(AuthRestController.ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflict(AuthRestController.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
    }

    public record ErrorBody(String message) {}
}
