package com.dynamic.thread.core.alarm;

import com.dynamic.thread.core.i18n.I18nUtil;
import com.dynamic.thread.core.model.AlarmRecord;
import com.dynamic.thread.core.model.AlarmRule;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.notify.NotifyPlatform;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Alarm manager for handling alarm rules, notifications, and alarm history.
 * Implements IAlarmManager interface for dependency injection and testing.
 * 
 * Includes rate limiting support to prevent alarm storms:
 * - Groups rate limiting by threadPoolId + metric
 * - Uses sliding time window algorithm
 * - Configurable interval per rule
 */
@Slf4j
public class AlarmManager implements IAlarmManager {

    private static final AlarmManager INSTANCE = new AlarmManager();

    /**
     * Alarm rules storage
     */
    private final Map<String, AlarmRule> rules = new ConcurrentHashMap<>();

    /**
     * Alarm history records (limited to last 1000 records)
     */
    private final List<AlarmRecord> history = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY_SIZE = 1000;

    /**
     * Registered notification platforms
     */
    private final Map<String, NotifyPlatform> platforms = new ConcurrentHashMap<>();

    /**
     * Enabled notification platform types
     */
    private final Set<String> enabledPlatforms = ConcurrentHashMap.newKeySet();

    /**
     * Rate limiter for preventing alarm storms
     */
    private final AlarmRateLimiter rateLimiter = AlarmRateLimiter.getInstance();

    private AlarmManager() {
        initDefaultRules();
    }

    public static AlarmManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize default alarm rules
     */
    private void initDefaultRules() {
        // Default thread usage rule
        AlarmRule threadUsageRule = AlarmRule.builder()
                .id(UUID.randomUUID().toString())
                .name(I18nUtil.get("alarm.rule.thread-usage-high"))
                .threadPoolId("*")
                .metric("threadUsage")
                .operator(">")
                .threshold(80.0)
                .level("WARNING")
                .enabled(true)
                .interval(60)
                .build();
        rules.put(threadUsageRule.getId(), threadUsageRule);

        // Default queue usage rule
        AlarmRule queueUsageRule = AlarmRule.builder()
                .id(UUID.randomUUID().toString())
                .name(I18nUtil.get("alarm.rule.queue-usage-high"))
                .threadPoolId("*")
                .metric("queueUsage")
                .operator(">")
                .threshold(70.0)
                .level("WARNING")
                .enabled(true)
                .interval(60)
                .build();
        rules.put(queueUsageRule.getId(), queueUsageRule);

        // Default rejected task rule
        AlarmRule rejectedRule = AlarmRule.builder()
                .id(UUID.randomUUID().toString())
                .name(I18nUtil.get("alarm.rule.task-rejected"))
                .threadPoolId("*")
                .metric("rejectedCount")
                .operator(">")
                .threshold(0.0)
                .level("CRITICAL")
                .enabled(true)
                .interval(60)
                .build();
        rules.put(rejectedRule.getId(), rejectedRule);
    }

    // ==================== Rule Management ====================

    /**
     * Add a new alarm rule
     */
    public AlarmRule addRule(AlarmRule rule) {
        if (rule.getId() == null || rule.getId().isEmpty()) {
            rule.setId(UUID.randomUUID().toString());
        }
        rules.put(rule.getId(), rule);
        log.info("Added alarm rule: {}", rule.getName());
        return rule;
    }

    /**
     * Update an existing alarm rule
     */
    public AlarmRule updateRule(AlarmRule rule) {
        if (rule.getId() == null || !rules.containsKey(rule.getId())) {
            throw new IllegalArgumentException("Rule not found: " + rule.getId());
        }
        rules.put(rule.getId(), rule);
        log.info("Updated alarm rule: {}", rule.getName());
        return rule;
    }

    /**
     * Delete an alarm rule
     */
    public void deleteRule(String ruleId) {
        AlarmRule removed = rules.remove(ruleId);
        if (removed != null) {
            log.info("Deleted alarm rule: {}", removed.getName());
        }
    }

    /**
     * Get all alarm rules
     */
    public List<AlarmRule> listRules() {
        return new ArrayList<>(rules.values());
    }

    /**
     * Get a specific alarm rule
     */
    public AlarmRule getRule(String ruleId) {
        return rules.get(ruleId);
    }

    /**
     * Enable or disable a rule
     */
    public void setRuleEnabled(String ruleId, boolean enabled) {
        AlarmRule rule = rules.get(ruleId);
        if (rule != null) {
            rule.setEnabled(enabled);
            log.info("Rule {} {}", rule.getName(), enabled ? "enabled" : "disabled");
        }
    }

    /**
     * Replace all alarm rules (used for cluster sync).
     * Clears existing rules and replaces with the provided list.
     */
    public void replaceAllRules(List<AlarmRule> newRules) {
        rules.clear();
        if (newRules != null) {
            for (AlarmRule rule : newRules) {
                if (rule.getId() != null) {
                    rules.put(rule.getId(), rule);
                }
            }
        }
        log.info("Replaced all alarm rules: {} rules loaded", rules.size());
    }

    // ==================== Platform Management ====================

    /**
     * Register a notification platform
     */
    public void registerPlatform(NotifyPlatform platform) {
        platforms.put(platform.getType().toUpperCase(), platform);
        log.info("Registered notification platform: {}", platform.getType());
    }

    /**
     * Enable a notification platform
     */
    public void enablePlatform(String platformType) {
        enabledPlatforms.add(platformType.toUpperCase());
    }

    /**
     * Disable a notification platform
     */
    public void disablePlatform(String platformType) {
        enabledPlatforms.remove(platformType.toUpperCase());
    }

    /**
     * Check if a platform is enabled
     */
    public boolean isPlatformEnabled(String platformType) {
        return enabledPlatforms.contains(platformType.toUpperCase());
    }

    /**
     * Get enabled platforms
     */
    public Set<String> getEnabledPlatforms() {
        return new HashSet<>(enabledPlatforms);
    }

    /**
     * Get a registered notification platform by type
     */
    @Override
    public NotifyPlatform getPlatform(String platformType) {
        if (platformType == null) {
            return null;
        }
        return platforms.get(platformType.toUpperCase());
    }

    /**
     * Reset all rules' last alarm time (for testing)
     */
    public void resetAllRuleAlarmTimes() {
        for (AlarmRule rule : rules.values()) {
            rule.setLastAlarmTime(null);
        }
        log.info("All rule alarm times have been reset");
    }

    // ==================== Alarm Checking ====================

    /**
     * Check all rules against a thread pool state and trigger alarms if needed.
     * Uses rate limiting to prevent alarm storms.
     */
    public List<AlarmRecord> checkAndTrigger(ThreadPoolState state) {
        if (state == null) {
            return Collections.emptyList();
        }

        List<AlarmRecord> triggeredAlarms = new ArrayList<>();

        for (AlarmRule rule : rules.values()) {
            // Skip disabled rules
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }

            // Check if rule applies to this thread pool
            if (!"*".equals(rule.getThreadPoolId()) && 
                !rule.getThreadPoolId().equals(state.getThreadPoolId())) {
                continue;
            }

            // Get metric value
            Double metricValue = getMetricValue(state, rule.getMetric());
            if (metricValue == null) {
                continue;
            }

            // Evaluate rule
            if (rule.evaluate(metricValue)) {
                // Use rate limiter to check if alarm can be sent
                // Rate limit by threadPoolId + metric (not by rule, so each pool has its own limit)
                // 
                // Interval strategy: max(rule.interval, rateLimiter.defaultInterval)
                // - Rule interval: defines per-rule alarm frequency
                // - RateLimiter interval: global minimum protection against alarm storms
                // - Actual interval: the maximum of both, ensuring global protection
                int ruleInterval = rule.getInterval() != null ? rule.getInterval() : 60;
                int globalInterval = rateLimiter.getDefaultIntervalSeconds();
                int effectiveInterval = Math.max(ruleInterval, globalInterval);
                
                if (rateLimiter.tryAcquire(state.getThreadPoolId(), rule.getMetric(), effectiveInterval)) {
                    // Alarm allowed - trigger it
                    AlarmRecord record = triggerAlarm(rule, state, metricValue);
                    triggeredAlarms.add(record);
                    
                    // Record the sent alarm for statistics
                    rateLimiter.recordAlarmSent(state.getThreadPoolId(), rule.getMetric(), metricValue);
                } else {
                    // Alarm throttled - log at debug level
                    log.debug("[ALARM-THROTTLED] {} - {} {} (throttled by rate limiter, effective interval={}s)", 
                            state.getThreadPoolId(), 
                            rule.getName(),
                            metricValue,
                            effectiveInterval);
                }
            }
        }

        return triggeredAlarms;
    }

    /**
     * Get metric value from thread pool state
     */
    private Double getMetricValue(ThreadPoolState state, String metric) {
        if (metric == null || state == null) {
            return null;
        }
        if ("threadUsage".equals(metric)) {
            return state.getActivePercent();
        } else if ("queueUsage".equals(metric)) {
            return state.getQueueUsagePercent();
        } else if ("activeCount".equals(metric)) {
            return state.getActiveCount() != null ? state.getActiveCount().doubleValue() : null;
        } else if ("queueSize".equals(metric)) {
            return state.getQueueSize() != null ? state.getQueueSize().doubleValue() : null;
        } else if ("rejectedCount".equals(metric)) {
            return state.getRejectedCount() != null ? state.getRejectedCount().doubleValue() : null;
        }
        return null;
    }

    /**
     * Trigger an alarm and send notifications
     */
    private AlarmRecord triggerAlarm(AlarmRule rule, ThreadPoolState state, double value) {
        // Create alarm record
        AlarmRecord record = AlarmRecord.builder()
                .id(UUID.randomUUID().toString())
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .threadPoolId(state.getThreadPoolId())
                .metric(rule.getMetric())
                .value(value)
                .threshold(rule.getThreshold())
                .operator(rule.getOperator())
                .level(rule.getLevel())
                .time(LocalDateTime.now())
                .state(state)
                .build();

        // Send notifications
        List<String> notifiedPlatforms = sendNotifications(record, state);
        record.setNotified(!notifiedPlatforms.isEmpty());
        record.setNotifyPlatforms(String.join(",", notifiedPlatforms));

        // Add to history
        addToHistory(record);

        log.warn("[ALARM] {} - {} {} {} (threshold: {})", 
                state.getThreadPoolId(), 
                rule.getName(),
                value, 
                rule.getOperator(), 
                rule.getThreshold());

        return record;
    }

    /**
     * Send notifications to all enabled platforms
     */
    private List<String> sendNotifications(AlarmRecord record, ThreadPoolState state) {
        List<String> notifiedPlatforms = new ArrayList<>();
        String message = record.getAlarmMessage();

        for (String platformType : enabledPlatforms) {
            NotifyPlatform platform = platforms.get(platformType);
            if (platform != null) {
                try {
                    boolean success = platform.sendAlarm(state, message);
                    if (success) {
                        notifiedPlatforms.add(platformType);
                        log.info("Alarm notification sent via {}", platformType);
                    }
                } catch (Exception e) {
                    log.error("Failed to send notification via {}: {}", platformType, e.getMessage());
                }
            }
        }

        return notifiedPlatforms;
    }

    // ==================== History Management ====================

    /**
     * Add record to history
     */
    private void addToHistory(AlarmRecord record) {
        history.add(0, record);
        
        // Trim history if too large
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * Get alarm history
     */
    public List<AlarmRecord> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Get alarm history with pagination
     */
    public List<AlarmRecord> getHistory(int page, int size) {
        int start = page * size;
        if (start >= history.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + size, history.size());
        return new ArrayList<>(history.subList(start, end));
    }

    /**
     * Get alarm history for a specific thread pool
     */
    public List<AlarmRecord> getHistoryByThreadPool(String threadPoolId) {
        return history.stream()
                .filter(r -> threadPoolId.equals(r.getThreadPoolId()))
                .collect(Collectors.toList());
    }

    /**
     * Get alarm statistics
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        int critical = 0;
        int warning = 0;
        int info = 0;
        int resolved = 0;

        for (AlarmRecord record : history) {
            if (Boolean.TRUE.equals(record.getResolved())) {
                resolved++;
            } else if ("CRITICAL".equals(record.getLevel())) {
                critical++;
            } else if ("WARNING".equals(record.getLevel())) {
                warning++;
            } else if ("INFO".equals(record.getLevel())) {
                info++;
            }
        }

        stats.put("critical", critical);
        stats.put("warning", warning);
        stats.put("info", info);
        stats.put("resolved", resolved);
        stats.put("total", history.size());

        return stats;
    }

    /**
     * Mark an alarm as resolved
     */
    public void resolveAlarm(String recordId) {
        for (AlarmRecord record : history) {
            if (recordId.equals(record.getId())) {
                record.setResolved(true);
                record.setResolvedTime(LocalDateTime.now());
                log.info("Alarm {} resolved", recordId);
                break;
            }
        }
    }

    /**
     * Clear alarm history
     */
    public void clearHistory() {
        history.clear();
        log.info("Alarm history cleared");
    }

    // ==================== Rate Limiter Management ====================

    /**
     * Get the rate limiter instance.
     */
    public AlarmRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Get rate limiter statistics.
     */
    public AlarmRateLimiter.RateLimitStatistics getRateLimitStatistics() {
        return rateLimiter.getStatistics();
    }

    /**
     * Enable or disable rate limiting.
     */
    public void setRateLimitEnabled(boolean enabled) {
        rateLimiter.setEnabled(enabled);
    }

    /**
     * Set default rate limit interval in seconds.
     */
    public void setRateLimitInterval(int seconds) {
        rateLimiter.setDefaultIntervalSeconds(seconds);
    }

    /**
     * Reset all rate limit states.
     */
    public void resetRateLimits() {
        rateLimiter.reset();
    }

    /**
     * Reset rate limit state for a specific thread pool and metric.
     */
    public void resetRateLimit(String threadPoolId, String metric) {
        rateLimiter.reset(threadPoolId, metric);
    }
}
