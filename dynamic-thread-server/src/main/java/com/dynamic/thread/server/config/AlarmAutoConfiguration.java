package com.dynamic.thread.server.config;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.notify.DingTalkNotifyPlatform;
import com.dynamic.thread.core.notify.EmailNotifyPlatform;
import com.dynamic.thread.core.notify.NotifyPlatform;
import com.dynamic.thread.core.notify.WeChatWorkNotifyPlatform;
import com.dynamic.thread.core.notify.WebhookNotifyPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;

/**
 * Alarm auto-configuration for registering notification platforms
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlarmAutoConfiguration {

    private final ServerProperties properties;

    /**
     * Register notification platforms on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerNotifyPlatforms() {
        List<Map<String, String>> notifyPlatforms = properties.getNotifyPlatforms();
        
        if (notifyPlatforms == null || notifyPlatforms.isEmpty()) {
            log.info("No notification platforms configured");
            return;
        }

        AlarmManager alarmManager = AlarmManager.getInstance();

        for (Map<String, String> platformConfig : notifyPlatforms) {
            String platformType = platformConfig.get("platform");
            String url = platformConfig.get("url");
            String secret = platformConfig.get("secret");

            if (platformType == null) {
                continue;
            }

            NotifyPlatform platform;
            if ("EMAIL".equalsIgnoreCase(platformType)) {
                // Email uses SMTP configuration, not webhook URL
                platform = createEmailPlatform(platformConfig);
            } else {
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                platform = createPlatform(platformType.toUpperCase(), url, secret);
            }

            if (platform != null) {
                alarmManager.registerPlatform(platform);
                alarmManager.enablePlatform(platform.getType());
                log.info("Registered and enabled notification platform: {}", platformType);
            }
        }
    }

    /**
     * Create notification platform instance
     */
    private NotifyPlatform createPlatform(String type, String url, String secret) {
        if ("DING".equalsIgnoreCase(type) || "DINGTALK".equalsIgnoreCase(type)) {
            if (secret != null && !secret.trim().isEmpty()) {
                log.info("DingTalk platform configured with HMAC-SHA256 signing");
                return new DingTalkNotifyPlatform(url, secret);
            }
            return new DingTalkNotifyPlatform(url);
        } else if ("WECHAT".equalsIgnoreCase(type) || "WECHATWORK".equalsIgnoreCase(type)) {
            log.info("WeChatWork platform configured");
            return new WeChatWorkNotifyPlatform(url);
        } else if ("WEBHOOK".equalsIgnoreCase(type)) {
            if (secret != null && !secret.trim().isEmpty()) {
                log.info("Webhook platform configured with HMAC-SHA256 signing");
                return new WebhookNotifyPlatform(url, secret);
            }
            return new WebhookNotifyPlatform(url);
        }
        
        log.warn("Unknown notification platform type: {}", type);
        return null;
    }

    /**
     * Create email notification platform from config map.
     * Email config uses different keys: smtpHost, smtpPort, username, password, fromAddress, toAddresses, ssl
     */
    private NotifyPlatform createEmailPlatform(Map<String, String> config) {
        String smtpHost = config.get("smtpHost");
        String smtpPortStr = config.get("smtpPort");
        String username = config.get("username");
        String password = config.get("password");
        String fromAddress = config.get("fromAddress");
        String toAddresses = config.get("toAddresses");
        boolean ssl = "true".equalsIgnoreCase(config.get("ssl"));

        if (smtpHost == null || smtpHost.trim().isEmpty()) {
            log.warn("Email platform missing smtpHost configuration");
            return null;
        }

        int smtpPort = ssl ? 465 : 587;
        try {
            if (smtpPortStr != null && !smtpPortStr.trim().isEmpty()) {
                smtpPort = Integer.parseInt(smtpPortStr);
            }
        } catch (NumberFormatException e) {
            // use default
        }

        log.info("Email platform configured: {}:{} -> {}", smtpHost, smtpPort, toAddresses);
        return new EmailNotifyPlatform(smtpHost, smtpPort, username, password, fromAddress, toAddresses, ssl);
    }
}
