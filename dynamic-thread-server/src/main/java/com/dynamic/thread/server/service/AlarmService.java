package com.dynamic.thread.server.service;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.model.AlarmRecord;
import com.dynamic.thread.core.model.AlarmRule;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.notify.DingTalkNotifyPlatform;
import com.dynamic.thread.core.notify.NotifyPlatform;
import com.dynamic.thread.core.notify.WeChatWorkNotifyPlatform;
import com.dynamic.thread.core.notify.WebhookNotifyPlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service layer for alarm management operations.
 * Encapsulates business logic for alarm rules, history, and notifications.
 */
@Slf4j
@Service
public class AlarmService {

    private final AlarmManager alarmManager;

    public AlarmService() {
        this.alarmManager = AlarmManager.getInstance();
    }

    // ==================== Rule Management ====================

    /**
     * Get all alarm rules
     *
     * @return list of all alarm rules
     */
    public List<AlarmRule> listRules() {
        return alarmManager.listRules();
    }

    /**
     * Get a specific alarm rule
     *
     * @param ruleId the rule id
     * @return the rule or null if not found
     */
    public AlarmRule getRule(String ruleId) {
        return alarmManager.getRule(ruleId);
    }

    /**
     * Add a new alarm rule
     *
     * @param rule the rule to add
     * @return the created rule
     */
    public AlarmRule addRule(AlarmRule rule) {
        return alarmManager.addRule(rule);
    }

    /**
     * Update an existing alarm rule
     *
     * @param ruleId the rule id
     * @param rule   the updated rule data
     * @return the updated rule
     */
    public AlarmRule updateRule(String ruleId, AlarmRule rule) {
        rule.setId(ruleId);
        return alarmManager.updateRule(rule);
    }

    /**
     * Delete an alarm rule
     *
     * @param ruleId the rule id to delete
     */
    public void deleteRule(String ruleId) {
        alarmManager.deleteRule(ruleId);
    }

    /**
     * Enable or disable a rule
     *
     * @param ruleId  the rule id
     * @param enabled whether to enable or disable
     */
    public void setRuleEnabled(String ruleId, boolean enabled) {
        alarmManager.setRuleEnabled(ruleId, enabled);
    }

    // ==================== History Management ====================

    /**
     * Get alarm history with pagination
     *
     * @param page the page number
     * @param size the page size
     * @return list of alarm records
     */
    public List<AlarmRecord> getHistory(int page, int size) {
        return alarmManager.getHistory(page, size);
    }

    /**
     * Get total history count
     *
     * @return total number of records
     */
    public int getHistoryCount() {
        return alarmManager.getHistory().size();
    }

    /**
     * Get alarm history for a specific thread pool
     *
     * @param threadPoolId the thread pool id
     * @return list of alarm records
     */
    public List<AlarmRecord> getHistoryByThreadPool(String threadPoolId) {
        return alarmManager.getHistoryByThreadPool(threadPoolId);
    }

    /**
     * Get alarm statistics
     *
     * @return map of statistic name to value
     */
    public Map<String, Integer> getStatistics() {
        return alarmManager.getStatistics();
    }

    /**
     * Resolve an alarm
     *
     * @param recordId the alarm record id
     */
    public void resolveAlarm(String recordId) {
        alarmManager.resolveAlarm(recordId);
    }

    /**
     * Clear alarm history
     */
    public void clearHistory() {
        alarmManager.clearHistory();
    }

    // ==================== Platform Management ====================

    /**
     * Get enabled notification platforms
     *
     * @return set of enabled platform types
     */
    public Set<String> getEnabledPlatforms() {
        return alarmManager.getEnabledPlatforms();
    }

    /**
     * Enable a notification platform
     *
     * @param platformType the platform type
     */
    public void enablePlatform(String platformType) {
        alarmManager.enablePlatform(platformType);
    }

    /**
     * Disable a notification platform
     *
     * @param platformType the platform type
     */
    public void disablePlatform(String platformType) {
        alarmManager.disablePlatform(platformType);
    }

    /**
     * Configure and register a notification platform
     *
     * @param platformType the platform type
     * @param webhookUrl   the webhook URL
     * @param secret       the optional secret
     * @return true if configured successfully
     */
    public boolean configurePlatform(String platformType, String webhookUrl, String secret) {
        NotifyPlatform platform = createPlatform(platformType, webhookUrl, secret);
        if (platform != null) {
            alarmManager.registerPlatform(platform);
            alarmManager.enablePlatform(platform.getType().toUpperCase());
            log.info("{} platform configured and enabled", platformType);
            return true;
        }
        return false;
    }

    /**
     * Test a notification platform
     *
     * @param platformType the platform type
     * @param webhookUrl   the webhook URL
     * @param secret       the optional secret
     * @return true if test notification sent successfully
     */
    public boolean testPlatform(String platformType, String webhookUrl, String secret) {
        NotifyPlatform platform = createPlatform(platformType, webhookUrl, secret);
        if (platform != null) {
            return platform.sendTest();
        }
        return false;
    }

    /**
     * Create a notification platform instance
     */
    private NotifyPlatform createPlatform(String platformType, String webhookUrl, String secret) {
        return switch (platformType.toUpperCase()) {
            case "DING", "DINGTALK" -> new DingTalkNotifyPlatform(webhookUrl, secret);
            case "WECHAT", "WECHATWORK" -> new WeChatWorkNotifyPlatform(webhookUrl);
            case "WEBHOOK" -> new WebhookNotifyPlatform(webhookUrl, secret);
            default -> null;
        };
    }

    // ==================== Alarm Simulation ====================

    /**
     * Simulate an alarm for testing
     *
     * @param type         the alarm type (queue, thread, reject)
     * @param usage        the usage percentage
     * @param count        the count (for reject type)
     * @param threadPoolId the thread pool id
     * @return simulation result
     */
    public SimulationResult simulateAlarm(String type, double usage, int count, String threadPoolId) {
        // Reset alarm times so rules can trigger immediately
        alarmManager.resetAllRuleAlarmTimes();

        // Build simulated ThreadPoolState
        ThreadPoolState state = buildSimulatedState(type, usage, count, threadPoolId);

        log.info("[SIMULATE] Triggering {} alarm for pool={}, usage={}, count={}",
                type, threadPoolId, usage, count);

        // Trigger alarm check
        List<AlarmRecord> triggered = alarmManager.checkAndTrigger(state);

        return new SimulationResult(
                triggered.size(),
                triggered,
                state,
                alarmManager.getEnabledPlatforms()
        );
    }

    /**
     * Build a simulated ThreadPoolState based on alarm type
     */
    private ThreadPoolState buildSimulatedState(String type, double usage, int count, String threadPoolId) {
        ThreadPoolState state = ThreadPoolState.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(10)
                .maximumPoolSize(20)
                .timestamp(LocalDateTime.now())
                .completedTaskCount(1000L)
                .taskCount(1050L)
                .build();

        if ("queue".equalsIgnoreCase(type)) {
            int queueCapacity = 100;
            int queueSize = (int) (queueCapacity * usage / 100);
            state.setQueueCapacity(queueCapacity);
            state.setQueueSize(queueSize);
            state.setActiveCount(5);
            state.setPoolSize(10);
            state.setRejectedCount(0L);
        } else if ("thread".equalsIgnoreCase(type)) {
            int activeCount = (int) (20 * usage / 100);
            state.setActiveCount(activeCount);
            state.setPoolSize(activeCount);
            state.setQueueCapacity(100);
            state.setQueueSize(10);
            state.setRejectedCount(0L);
        } else if ("reject".equalsIgnoreCase(type)) {
            state.setActiveCount(20);
            state.setPoolSize(20);
            state.setQueueCapacity(100);
            state.setQueueSize(100);
            state.setRejectedCount((long) count);
        }

        state.calculateMetrics();
        return state;
    }

    /**
     * Result of alarm simulation
     */
    public record SimulationResult(
            int triggeredCount,
            List<AlarmRecord> records,
            ThreadPoolState simulatedState,
            Set<String> enabledPlatforms
    ) {}
}
