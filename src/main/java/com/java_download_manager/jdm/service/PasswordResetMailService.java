package com.java_download_manager.jdm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends the password reset email to the user. Runs async so the request returns quickly.
 * Subject and body are read from config (see jdm.mail.password-reset.*).
 */
@Service
public class PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);
    private static final String PLACEHOLDER_RESET_LINK = "{resetLink}";
    private static final String PLACEHOLDER_EXPIRY = "{expiryDescription}";

    @Value("${jdm.password-reset.base-url:http://localhost:3000/reset}")
    private String baseUrl;

    @Value("${jdm.mail.password-reset.subject:Reset your password}")
    private String subject;

    @Value("${jdm.mail.from:noreply@jdm.local}")
    private String fromEmail;

    @Value("${jdm.mail.password-reset.expiry-description:24 hours}")
    private String expiryDescription;

    @Value("${jdm.mail.password-reset.body:Click the link below to reset your password:\\n\\n{resetLink}\\n\\nThis link expires in {expiryDescription}.}")
    private String bodyTemplate;

    private final JavaMailSender mailSender;

    public PasswordResetMailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send the password reset email asynchronously. Returns immediately; sending happens in the background.
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String encodedToken = URLEncoder.encode(resetToken, StandardCharsets.UTF_8);
        String resetLink = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "token=" + encodedToken;
        String body = bodyTemplate
                .replace(PLACEHOLDER_RESET_LINK, resetLink)
                .replace(PLACEHOLDER_EXPIRY, expiryDescription)
                .replace("\\n", "\n");

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
                log.debug("Password reset send failure", e);
                log.info("Password reset link for {} (fallback): {}", toEmail, resetLink);
            }
        } else {
            log.info("Password reset requested for {} - reset link: {}", toEmail, resetLink);
        }
    }
}
