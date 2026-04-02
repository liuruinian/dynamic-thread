package com.dynamic.thread.core.alarm;

import com.dynamic.thread.core.model.AlarmRecord;
import com.dynamic.thread.core.model.AlarmRule;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.notify.NotifyPlatform;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for alarm manager operations.
 * Allows for dependency injection and easier testing.
 */
public interface IAlarmManager {

    // ==================== Rule Management ====================

    /**
     * Add a new alarm rule
     *
     * @param rule the alarm rule to add
     * @return the added rule (with generated id if not set)
     */
    AlarmRule addRule(AlarmRule rule);

    /**
     * Update an existing alarm rule
     *
     * @param rule the alarm rule to update
     * @return the updated rule
     */
    AlarmRule updateRule(AlarmRule rule);

    /**
     * Delete an alarm rule
     *
     * @param ruleId the rule id to delete
     */
    void deleteRule(String ruleId);

    /**
     * Get all alarm rules
     *
     * @return list of all rules
     */
    List<AlarmRule> listRules();

    /**
     * Get a specific alarm rule
     *
     * @param ruleId the rule id
     * @return the rule, or null if not found
     */
    AlarmRule getRule(String ruleId);

    /**
     * Enable or disable a rule
     *
     * @param ruleId  the rule id
     * @param enabled whether to enable or disable
     */
    void setRuleEnabled(String ruleId, boolean enabled);

    // ==================== Platform Management ====================

    /**
     * Register a notification platform
     *
     * @param platform the platform to register
     */
    void registerPlatform(NotifyPlatform platform);

    /**
     * Enable a notification platform
     *
     * @param platformType the platform type to enable
     */
    void enablePlatform(String platformType);

    /**
     * Disable a notification platform
     *
     * @param platformType the platform type to disable
     */
    void disablePlatform(String platformType);

    /**
     * Check if a platform is enabled
     *
     * @param platformType the platform type
     * @return true if enabled
     */
    boolean isPlatformEnabled(String platformType);

    /**
     * Get enabled platforms
     *
     * @return set of enabled platform types
     */
    Set<String> getEnabledPlatforms();

    /**
     * Get a registered notification platform by type
     *
     * @param platformType the platform type (e.g., "DING", "WECHAT", "WEBHOOK")
     * @return the registered platform, or null if not found
     */
    NotifyPlatform getPlatform(String platformType);

    // ==================== Alarm Checking ====================

    /**
     * Check all rules against a thread pool state and trigger alarms if needed
     *
     * @param state the thread pool state to check
     * @return list of triggered alarm records
     */
    List<AlarmRecord> checkAndTrigger(ThreadPoolState state);

    // ==================== History Management ====================

    /**
     * Get alarm history
     *
     * @return list of all alarm records
     */
    List<AlarmRecord> getHistory();

    /**
     * Get alarm history with pagination
     *
     * @param page the page number (0-based)
     * @param size the page size
     * @return list of alarm records for the page
     */
    List<AlarmRecord> getHistory(int page, int size);

    /**
     * Get alarm history for a specific thread pool
     *
     * @param threadPoolId the thread pool id
     * @return list of alarm records for the thread pool
     */
    List<AlarmRecord> getHistoryByThreadPool(String threadPoolId);

    /**
     * Get alarm statistics
     *
     * @return map of statistic name to value
     */
    Map<String, Integer> getStatistics();

    /**
     * Mark an alarm as resolved
     *
     * @param recordId the alarm record id
     */
    void resolveAlarm(String recordId);

    /**
     * Clear alarm history
     */
    void clearHistory();
}
