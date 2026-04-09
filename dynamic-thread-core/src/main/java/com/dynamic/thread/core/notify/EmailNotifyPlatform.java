package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.enums.NotifyPlatformEnum;
import com.dynamic.thread.core.model.ThreadPoolState;
import lombok.extern.slf4j.Slf4j;

import javax.mail.*;
import javax.mail.internet.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Email notification platform implementation.
 * Sends notifications via SMTP email.
 */
@Slf4j
public class EmailNotifyPlatform implements NotifyPlatform {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String toAddresses;
    private final boolean ssl;

    public EmailNotifyPlatform(String smtpHost, int smtpPort, String username,
                               String password, String fromAddress, String toAddresses, boolean ssl) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.toAddresses = toAddresses;
        this.ssl = ssl;
    }

    @Override
    public String getType() {
        return NotifyPlatformEnum.EMAIL.getCode();
    }

    @Override
    public Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("platform", getType());
        config.put("smtpHost", smtpHost);
        config.put("smtpPort", String.valueOf(smtpPort));
        config.put("fromAddress", fromAddress);
        config.put("toAddresses", toAddresses);
        config.put("ssl", String.valueOf(ssl));
        config.put("username", username);
        config.put("hasPassword", password != null && !password.trim().isEmpty() ? "true" : "false");
        return config;
    }

    @Override
    public boolean sendAlarm(ThreadPoolState state, String message) {
        String subject = "[Thread Pool Alarm] " + state.getThreadPoolId() + " - " + message;
        String body = buildAlarmHtml(state, message);
        return sendEmail(subject, body);
    }

    @Override
    public boolean sendConfigChange(String threadPoolId, String oldConfig, String newConfig) {
        String subject = "[Thread Pool Config Changed] " + threadPoolId;
        String body = buildConfigChangeHtml(threadPoolId, oldConfig, newConfig);
        return sendEmail(subject, body);
    }

    /**
     * Send an email with the given subject and HTML body.
     */
    private boolean sendEmail(String subject, String htmlBody) {
        if (smtpHost == null || smtpHost.trim().isEmpty()) {
            log.warn("[Email] SMTP host is not configured");
            return false;
        }
        if (toAddresses == null || toAddresses.trim().isEmpty()) {
            log.warn("[Email] No recipient addresses configured");
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.connectiontimeout", "10000");

            if (ssl) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", smtpHost);
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "false");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddresses));
            msg.setSubject(subject, "UTF-8");
            msg.setContent(htmlBody, "text/html; charset=UTF-8");

            Transport.send(msg);
            log.info("[Email] Notification sent successfully to {}", toAddresses);
            return true;
        } catch (Exception e) {
            log.error("[Email] Failed to send notification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Build HTML content for alarm notification.
     */
    private String buildAlarmHtml(ThreadPoolState state, String message) {
        return String.format(
            "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
            + "<h2 style=\"color: #e74c3c; border-bottom: 2px solid #e74c3c; padding-bottom: 8px;\">"
            + "Dynamic Thread Pool Alarm</h2>"
            + "<table style=\"width: 100%%; border-collapse: collapse; margin: 16px 0;\">"
            + "<tr><td style=\"padding: 8px; font-weight: bold; color: #555;\">Thread Pool</td>"
            + "<td style=\"padding: 8px; color: #e74c3c; font-weight: bold;\">%s</td></tr>"
            + "<tr style=\"background: #f9f9f9;\">"
            + "<td style=\"padding: 8px; font-weight: bold; color: #555;\">Alarm Message</td>"
            + "<td style=\"padding: 8px;\">%s</td></tr></table>"
            + "<h3 style=\"color: #333; margin-top: 20px;\">Thread Pool State</h3>"
            + "<table style=\"width: 100%%; border-collapse: collapse; border: 1px solid #ddd;\">"
            + "<tr style=\"background: #f5f5f5;\">"
            + "<th style=\"padding: 8px; text-align: left; border: 1px solid #ddd;\">Metric</th>"
            + "<th style=\"padding: 8px; text-align: left; border: 1px solid #ddd;\">Value</th></tr>"
            + "<tr><td style=\"padding: 8px; border: 1px solid #ddd;\">Core Pool Size</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">%d</td></tr>"
            + "<tr style=\"background: #f9f9f9;\">"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">Max Pool Size</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">%d</td></tr>"
            + "<tr><td style=\"padding: 8px; border: 1px solid #ddd;\">Active Count</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">%d</td></tr>"
            + "<tr style=\"background: #f9f9f9;\">"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">Queue Size</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">%d / %d</td></tr>"
            + "<tr><td style=\"padding: 8px; border: 1px solid #ddd;\">Queue Usage</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd; color: #e74c3c; font-weight: bold;\">%.1f%%</td></tr>"
            + "<tr style=\"background: #f9f9f9;\">"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">Thread Usage</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd; color: #e74c3c; font-weight: bold;\">%.1f%%</td></tr>"
            + "<tr><td style=\"padding: 8px; border: 1px solid #ddd;\">Rejected Count</td>"
            + "<td style=\"padding: 8px; border: 1px solid #ddd;\">%d</td></tr></table>"
            + "<p style=\"color: #999; font-size: 12px; margin-top: 16px;\">Alarm Time: %s</p></div>",
            state.getThreadPoolId(),
            message,
            state.getCorePoolSize(),
            state.getMaximumPoolSize(),
            state.getActiveCount(),
            state.getQueueSize(), state.getQueueCapacity(),
            state.getQueueUsagePercent(),
            state.getActivePercent(),
            state.getRejectedCount(),
            state.getTimestamp().format(FORMATTER)
        );
    }

    /**
     * Build HTML content for config change notification.
     */
    private String buildConfigChangeHtml(String threadPoolId, String oldConfig, String newConfig) {
        return String.format(
            "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
            + "<h2 style=\"color: #3498db; border-bottom: 2px solid #3498db; padding-bottom: 8px;\">"
            + "Thread Pool Config Changed</h2>"
            + "<p><strong>Thread Pool:</strong> <span style=\"color: #3498db;\">%s</span></p>"
            + "<h3 style=\"color: #555;\">Before</h3>"
            + "<pre style=\"background: #f5f5f5; padding: 12px; border-radius: 4px; overflow-x: auto;\">%s</pre>"
            + "<h3 style=\"color: #555;\">After</h3>"
            + "<pre style=\"background: #f0fff0; padding: 12px; border-radius: 4px; overflow-x: auto;\">%s</pre>"
            + "<p style=\"color: #999; font-size: 12px; margin-top: 16px;\">Change Time: %s</p></div>",
            threadPoolId,
            oldConfig,
            newConfig,
            LocalDateTime.now().format(FORMATTER)
        );
    }
}
