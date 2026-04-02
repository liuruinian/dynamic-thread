package com.dynamic.thread.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alarm configuration for thread pool.
 * 
 * Includes rate limiting configuration to prevent alarm storms:
 * - rateLimitEnabled: toggle rate limiting on/off
 * - rateLimitInterval: global default interval for rate limiting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmConfig {

    /**
     * Whether alarm is enabled
     */
    @Builder.Default
    private Boolean enable = true;

    /**
     * Queue capacity threshold percentage (0-100)
     * Triggers alarm when queue usage exceeds this threshold
     */
    @Builder.Default
    private Integer queueThreshold = 80;

    /**
     * Active thread threshold percentage (0-100)
     * Triggers alarm when active thread ratio exceeds this threshold
     */
    @Builder.Default
    private Integer activeThreshold = 80;

    /**
     * Alarm interval in seconds
     * Prevents frequent alarms by limiting alarm frequency
     */
    @Builder.Default
    private Integer interval = 60;

    // ==================== Rate Limiting Configuration ====================

    /**
     * Whether rate limiting is enabled to prevent alarm storms
     */
    @Builder.Default
    private Boolean rateLimitEnabled = true;

    /**
     * Default rate limit interval in seconds.
     * This is used when a rule doesn't specify its own interval.
     * Each threadPoolId + metric combination has its own rate limit window.
     */
    @Builder.Default
    private Integer rateLimitInterval = 60;
}
