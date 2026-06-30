package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AdminAlertService {
    private static final Logger log = LoggerFactory.getLogger(AdminAlertService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String adminEmail;
    private final boolean mailEnabled;

    public AdminAlertService(ObjectProvider<JavaMailSender> mailSenderProvider,
                             @Value("${app.security.alerts.admin-email}") String adminEmail,
                             @Value("${app.security.alerts.mail-enabled:false}") boolean mailEnabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.adminEmail = adminEmail;
        this.mailEnabled = mailEnabled;
    }

    public void securityIncident(String username, String reason, String sanitizedMessage) {
        log.warn("AI security incident. username={}, reason={}, sanitizedMessage=\"{}\"",
                username, reason, preview(sanitizedMessage));

        if (!mailEnabled) {
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Admin alert mail is enabled but JavaMailSender is not configured.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("AI Chatbot Security Alert");
            message.setText("""
                    A risky chatbot request was blocked.

                    Username: %s
                    Reason: %s
                    Sanitized message: %s
                    """.formatted(username, reason, sanitizedMessage));
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.warn("Admin alert mail could not be sent.", exception);
        }
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
