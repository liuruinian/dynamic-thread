package com.dynamic.thread.core.model;

import com.dynamic.thread.core.i18n.I18nUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Alarm record model for storing alarm history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRecord {

    /**
     * Record unique identifier
     */
    private String id;

    /**
     * Associated rule ID
     */
    private String ruleId;

    /**
     * Rule name at the time of alarm
     */
    private String ruleName;

    /**
     * Thread pool ID
     */
    private String threadPoolId;

    /**
     * Metric that triggered the alarm
     */
    private String metric;

    /**
     * Current metric value when alarm was triggered
     */
    private Double value;

    /**
     * Threshold that was exceeded
     */
    private Double threshold;

    /**
     * Comparison operator
     */
    private String operator;

    /**
     * Alarm level (INFO, WARNING, CRITICAL)
     */
    private String level;

    /**
     * Notification sent status
     */
    @Builder.Default
    private Boolean notified = false;

    /**
     * Notification platforms that received the alarm
     */
    private String notifyPlatforms;

    /**
     * Alarm timestamp
     */
    @Builder.Default
    private LocalDateTime time = LocalDateTime.now();

    /**
     * Thread pool state snapshot at alarm time
     */
    private ThreadPoolState state;

    /**
     * Whether the alarm has been resolved
     */
    @Builder.Default
    private Boolean resolved = false;

    /**
     * Resolved timestamp
     */
    private LocalDateTime resolvedTime;

    /**
     * Get formatted alarm message
     */
    public String getAlarmMessage() {
        String unit = (metric != null && (metric.contains("Usage") || metric.contains("Percent"))) ? "%" : "";
        return I18nUtil.get("alarm.message.threshold-exceeded",
                threadPoolId, getMetricLabel(), String.format("%.2f", value), unit, String.format("%.2f", threshold), unit);
    }

    /**
     * Get human-readable metric label
     */
    public String getMetricLabel() {
        if (metric == null) return "";
        if ("threadUsage".equals(metric)) {
            return I18nUtil.get("alarm.metric.thread-usage");
        } else if ("queueUsage".equals(metric)) {
            return I18nUtil.get("alarm.metric.queue-usage");
        } else if ("activeCount".equals(metric)) {
            return I18nUtil.get("alarm.metric.active-count");
        } else if ("queueSize".equals(metric)) {
            return I18nUtil.get("alarm.metric.queue-size");
        } else if ("rejectedCount".equals(metric)) {
            return I18nUtil.get("alarm.metric.rejected-count");
        }
        return metric;
    }
}
