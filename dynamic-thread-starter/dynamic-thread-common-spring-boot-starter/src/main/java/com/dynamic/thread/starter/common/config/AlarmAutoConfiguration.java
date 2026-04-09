package com.dynamic.thread.starter.common.config;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.notify.DingTalkNotifyPlatform;
import com.dynamic.thread.core.notify.NotifyPlatform;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Alarm auto-configuration for registering notification platforms
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlarmAutoConfiguration {

    private final DynamicThreadPoolProperties properties;

    /**
     * Register notification platforms on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerNotifyPlatforms() {
        if (properties.getNotifyPlatforms() == null || properties.getNotifyPlatforms().isEmpty()) {
            log.info("No notification platforms configured");
            return;
        }

        AlarmManager alarmManager = AlarmManager.getInstance();

        for (DynamicThreadPoolProperties.NotifyPlatformProperties platformConfig : properties.getNotifyPlatforms()) {
            String platformType = platformConfig.getPlatform();
            String url = platformConfig.getUrl();

            if (platformType == null || url == null || url.trim().isEmpty()) {
                continue;
            }

            NotifyPlatform platform = createPlatform(platformType.toUpperCase(), url, platformConfig.getSecret());
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
        }
        // Add more platform types as needed
        // else if ("WECHAT".equalsIgnoreCase(type)) { ... }
        // else if ("EMAIL".equalsIgnoreCase(type)) { ... }
        
        log.warn("Unknown notification platform type: {}", type);
        return null;
    }
}
