package com.dynamic.thread.core.notify;

import com.dynamic.thread.core.model.ThreadPoolState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification platform interface for sending alerts
 */
public interface NotifyPlatform {

    /**
     * Get platform type
     *
     * @return the platform type name
     */
    String getType();

    /**
     * Send alarm notification
     *
     * @param state   the thread pool state
     * @param message the alarm message
     * @return true if sent successfully
     */
    boolean sendAlarm(ThreadPoolState state, String message);

    /**
     * Send config change notification
     *
     * @param threadPoolId the thread pool id
     * @param oldConfig    the old configuration description
     * @param newConfig    the new configuration description
     * @return true if sent successfully
     */
    boolean sendConfigChange(String threadPoolId, String oldConfig, String newConfig);

    /**
     * Get platform configuration as a map for API exposure.
     * Returns config details like webhookUrl, secret (masked), etc.
     *
     * @return map of configuration key-value pairs
     */
    default Map<String, String> getConfig() {
        return new HashMap<>();
    }

    /**
     * Send a test notification to verify platform configuration.
     * Default implementation sends a test alarm with a mock thread pool state.
     *
     * @return true if sent successfully
     */
    default boolean sendTest() {
        ThreadPoolState mockState = ThreadPoolState.builder()
                .threadPoolId("test-pool")
                .corePoolSize(10)
                .maximumPoolSize(20)
                .activeCount(5)
                .poolSize(10)
                .queueSize(10)
                .queueCapacity(100)
                .completedTaskCount(1000L)
                .rejectedCount(0L)
                .timestamp(LocalDateTime.now())
                .build();
        mockState.calculateMetrics();
        String message = "Test notification from Dynamic Thread Pool - " + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return sendAlarm(mockState, message);
    }
}
