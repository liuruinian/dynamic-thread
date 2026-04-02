package com.dynamic.thread.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alarm rule model for defining alerting conditions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    /**
     * Rule unique identifier
     */
    private String id;

    /**
     * Rule name
     */
    private String name;

    /**
     * Target thread pool ID (* for all pools)
     */
    @Builder.Default
    private String threadPoolId = "*";

    /**
     * Metric to monitor (threadUsage, queueUsage, activeCount, queueSize, rejectedCount)
     */
    private String metric;

    /**
     * Comparison operator (>, >=, <, <=, =)
     */
    @Builder.Default
    private String operator = ">";

    /**
     * Threshold value
     */
    private Double threshold;

    /**
     * Alarm level (INFO, WARNING, CRITICAL)
     */
    @Builder.Default
    private String level = "WARNING";

    /**
     * Whether the rule is enabled
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Alarm interval in seconds to prevent frequent alarms
     */
    @Builder.Default
    private Integer interval = 60;

    /**
     * Last alarm timestamp for this rule
     */
    private Long lastAlarmTime;

    /**
     * Check if the value triggers this rule
     *
     * @param value the current metric value
     * @return true if the rule is triggered
     */
    public boolean evaluate(double value) {
        if (!Boolean.TRUE.equals(enabled) || threshold == null) {
            return false;
        }

        if (">".equals(operator)) {
            return value > threshold;
        } else if (">=".equals(operator)) {
            return value >= threshold;
        } else if ("<".equals(operator)) {
            return value < threshold;
        } else if ("<=".equals(operator)) {
            return value <= threshold;
        } else if ("=".equals(operator)) {
            return Math.abs(value - threshold) < 0.001;
        }
        return false;
    }

    /**
     * Check if alarm can be triggered based on interval
     *
     * @return true if interval has passed since last alarm
     */
    public boolean canAlarm() {
        if (lastAlarmTime == null) {
            return true;
        }
        long intervalMs = (interval != null ? interval : 60) * 1000L;
        return System.currentTimeMillis() - lastAlarmTime >= intervalMs;
    }

    /**
     * Update last alarm time
     */
    public void updateLastAlarmTime() {
        this.lastAlarmTime = System.currentTimeMillis();
    }
}
