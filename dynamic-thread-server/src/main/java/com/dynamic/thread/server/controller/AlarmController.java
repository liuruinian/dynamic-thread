package com.dynamic.thread.server.controller;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.alarm.AlarmRateLimiter;
import com.dynamic.thread.core.i18n.I18nUtil;
import com.dynamic.thread.core.model.AlarmRecord;
import com.dynamic.thread.core.model.AlarmRule;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.notify.DingTalkNotifyPlatform;
import com.dynamic.thread.core.notify.EmailNotifyPlatform;
import com.dynamic.thread.core.notify.NotifyPlatform;
import com.dynamic.thread.core.notify.WeChatWorkNotifyPlatform;
import com.dynamic.thread.core.notify.WebhookNotifyPlatform;
import com.dynamic.thread.server.config.ServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API controller for alarm management.
 */
@Slf4j
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final ServerProperties serverProperties;

    // ==================== Rule Management ====================

    /**
     * Get all alarm rules
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> listRules() {
        Map<String, Object> result = new HashMap<>();
        List<AlarmRule> rules = AlarmManager.getInstance().listRules();
        result.put("rules", rules);
        result.put("count", rules.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Get a specific alarm rule
     */
    @GetMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> getRule(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        AlarmRule rule = AlarmManager.getInstance().getRule(id);
        if (rule == null) {
            result.put("found", false);
            result.put("message", I18nUtil.get("api.rule.not-found", id));
            return ResponseEntity.ok(result);
        }
        result.put("found", true);
        result.put("rule", rule);
        return ResponseEntity.ok(result);
    }

    /**
     * Add a new alarm rule
     */
    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> addRule(@RequestBody AlarmRule rule) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmRule created = AlarmManager.getInstance().addRule(rule);
            result.put("success", true);
            result.put("rule", created);
            result.put("message", I18nUtil.get("api.rule.added"));
            log.info("Alarm rule added: {}", created.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", I18nUtil.get("api.rule.add-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Update an existing alarm rule
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> updateRule(
            @PathVariable String id,
            @RequestBody AlarmRule rule) {
        Map<String, Object> result = new HashMap<>();
        try {
            rule.setId(id);
            AlarmRule updated = AlarmManager.getInstance().updateRule(rule);
            result.put("success", true);
            result.put("rule", updated);
            result.put("message", I18nUtil.get("api.rule.updated"));
            log.info("Alarm rule updated: {}", updated.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", I18nUtil.get("api.rule.update-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Delete an alarm rule
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager.getInstance().deleteRule(id);
            result.put("success", true);
            result.put("message", I18nUtil.get("api.rule.deleted"));
            log.info("Alarm rule deleted: {}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", I18nUtil.get("api.rule.delete-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Enable or disable a rule
     */
    @PostMapping("/rules/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager.getInstance().setRuleEnabled(id, enabled);
            result.put("success", true);
            result.put("enabled", enabled);
            result.put("message", enabled ? I18nUtil.get("api.rule.enabled") : I18nUtil.get("api.rule.disabled"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", I18nUtil.get("api.rule.toggle-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ==================== History Management ====================

    /**
     * Get alarm history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> result = new HashMap<>();
        List<AlarmRecord> records = AlarmManager.getInstance().getHistory(page, size);
        result.put("records", records);
        result.put("page", page);
        result.put("size", size);
        result.put("total", AlarmManager.getInstance().getHistory().size());
        return ResponseEntity.ok(result);
    }

    /**
     * Get alarm history for a specific thread pool
     */
    @GetMapping("/history/{threadPoolId}")
    public ResponseEntity<Map<String, Object>> getHistoryByThreadPool(@PathVariable String threadPoolId) {
        Map<String, Object> result = new HashMap<>();
        List<AlarmRecord> records = AlarmManager.getInstance().getHistoryByThreadPool(threadPoolId);
        result.put("records", records);
        result.put("threadPoolId", threadPoolId);
        result.put("count", records.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Get alarm statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> stats = AlarmManager.getInstance().getStatistics();
        result.put("statistics", stats);
        return ResponseEntity.ok(result);
    }

    /**
     * Resolve an alarm
     */
    @PostMapping("/history/{recordId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAlarm(@PathVariable String recordId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager.getInstance().resolveAlarm(recordId);
            result.put("success", true);
            result.put("message", I18nUtil.get("api.alarm.resolved"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", I18nUtil.get("api.alarm.resolve-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Clear alarm history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, Object>> clearHistory() {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().clearHistory();
        result.put("success", true);
        result.put("message", I18nUtil.get("api.alarm.history-cleared"));
        return ResponseEntity.ok(result);
    }

    // ==================== Platform Management ====================

    /**
     * Get enabled notification platforms
     */
    @GetMapping("/platforms")
    public ResponseEntity<Map<String, Object>> getEnabledPlatforms() {
        Map<String, Object> result = new HashMap<>();
        Set<String> platforms = AlarmManager.getInstance().getEnabledPlatforms();
        result.put("platforms", platforms);
        return ResponseEntity.ok(result);
    }

    /**
     * Enable a notification platform
     */
    @PostMapping("/platforms/{platform}/enable")
    public ResponseEntity<Map<String, Object>> enablePlatform(@PathVariable String platform) {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().enablePlatform(platform);
        result.put("success", true);
        result.put("platform", platform);
        result.put("message", I18nUtil.get("api.platform.enabled"));
        return ResponseEntity.ok(result);
    }

    /**
     * Disable a notification platform
     */
    @PostMapping("/platforms/{platform}/disable")
    public ResponseEntity<Map<String, Object>> disablePlatform(@PathVariable String platform) {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().disablePlatform(platform);
        result.put("success", true);
        result.put("platform", platform);
        result.put("message", I18nUtil.get("api.platform.disabled"));
        return ResponseEntity.ok(result);
    }

    /**
     * Simulate alarm trigger for testing.
     * Creates a fake ThreadPoolState with specified metric values to trigger alarm rules.
     *
     * Examples:
     *   POST /test/simulate?type=queue&usage=90      -> simulate queue usage 90%
     *   POST /test/simulate?type=thread&usage=95     -> simulate thread usage 95%
     *   POST /test/simulate?type=reject&count=5      -> simulate 5 rejected tasks
     */
    @PostMapping("/test/simulate")
    public ResponseEntity<Map<String, Object>> simulateAlarm(
            @RequestParam(defaultValue = "queue") String type,
            @RequestParam(defaultValue = "90") double usage,
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "simulate-pool") String threadPoolId) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager manager = AlarmManager.getInstance();

            // Reset alarm times so rules can trigger immediately
            manager.resetAllRuleAlarmTimes();

            // Build simulated ThreadPoolState based on alarm type
            ThreadPoolState state = buildSimulatedState(type, usage, count, threadPoolId);

            log.info("[SIMULATE] Triggering {} alarm for pool={}, usage={}, count={}",
                    type, threadPoolId, usage, count);

            // Trigger alarm check
            List<AlarmRecord> triggered = manager.checkAndTrigger(state);

            result.put("success", true);
            result.put("triggeredCount", triggered.size());
            result.put("records", triggered);
            result.put("simulatedState", state);
            result.put("enabledPlatforms", manager.getEnabledPlatforms());

            if (triggered.isEmpty()) {
                result.put("message", I18nUtil.get("api.alarm.no-triggered"));
            } else {
                result.put("message", I18nUtil.get("api.alarm.triggered", triggered.size()));
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to simulate alarm", e);
            result.put("success", false);
            result.put("message", I18nUtil.get("api.alarm.simulate-failed", e.getMessage()));
            return ResponseEntity.badRequest().body(result);
        }
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
            // Simulate high queue usage
            int queueCapacity = 100;
            int queueSize = (int) (queueCapacity * usage / 100);
            state.setQueueCapacity(queueCapacity);
            state.setQueueSize(queueSize);
            state.setActiveCount(5);
            state.setPoolSize(10);
            state.setRejectedCount(0L);
        } else if ("thread".equalsIgnoreCase(type)) {
            // Simulate high thread usage
            int activeCount = (int) (20 * usage / 100);
            state.setActiveCount(activeCount);
            state.setPoolSize(activeCount);
            state.setQueueCapacity(100);
            state.setQueueSize(10);
            state.setRejectedCount(0L);
        } else if ("reject".equalsIgnoreCase(type)) {
            // Simulate rejected tasks
            state.setActiveCount(20);
            state.setPoolSize(20);
            state.setQueueCapacity(100);
            state.setQueueSize(100);
            state.setRejectedCount((long) count);
        }

        // Calculate derived metrics
        state.calculateMetrics();
        return state;
    }

    // ==================== Platform Configuration ====================

    /**
     * Get platform configuration (from registered platform or application.yml)
     */
    @GetMapping("/platforms/{platform}/config")
    public ResponseEntity<Map<String, Object>> getPlatformConfig(@PathVariable String platform) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager manager = AlarmManager.getInstance();
            String normalizedType = normalizePlatformType(platform);

            if (normalizedType == null) {
                result.put("found", false);
                result.put("message", "Unknown platform: " + platform);
                return ResponseEntity.ok(result);
            }

            // Try to get from registered platforms first
            NotifyPlatform registeredPlatform = manager.getPlatform(normalizedType);
            if (registeredPlatform != null) {
                Map<String, String> config = registeredPlatform.getConfig();
                result.put("found", true);
                result.put("platform", config);
                result.put("enabled", manager.isPlatformEnabled(normalizedType));
                result.put("configSource", "server");
                return ResponseEntity.ok(result);
            }

            // Fall back to application.yml configuration
            List<Map<String, String>> notifyPlatforms = serverProperties.getNotifyPlatforms();
            if (notifyPlatforms != null) {
                for (Map<String, String> platformConfig : notifyPlatforms) {
                    String configType = platformConfig.get("platform");
                    if (configType != null && normalizePlatformType(configType) != null
                            && normalizePlatformType(configType).equals(normalizedType)) {
                        Map<String, String> config = new HashMap<>();
                        config.put("webhookUrl", platformConfig.get("url"));
                        config.put("platform", normalizedType);
                        String secret = platformConfig.get("secret");
                        if (secret != null && !secret.isBlank()) {
                            String masked = secret.length() > 10
                                    ? secret.substring(0, 6) + "****" + secret.substring(secret.length() - 4)
                                    : "****";
                            config.put("secret", masked);
                            config.put("hasSecret", "true");
                        } else {
                            config.put("hasSecret", "false");
                        }
                        result.put("found", true);
                        result.put("platform", config);
                        result.put("enabled", manager.isPlatformEnabled(normalizedType));
                        result.put("configSource", "application.yml");
                        return ResponseEntity.ok(result);
                    }
                }
            }

            result.put("found", false);
            result.put("message", "Platform not configured: " + platform);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get platform config: {}", e.getMessage());
            result.put("found", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Normalize platform type string to standard form
     */
    private String normalizePlatformType(String platform) {
        if (platform == null) return null;
        return switch (platform.toUpperCase()) {
            case "DING", "DINGTALK" -> "DING";
            case "WECHAT", "WECHATWORK" -> "WECHAT";
            case "WEBHOOK" -> "WEBHOOK";
            case "EMAIL" -> "EMAIL";
            case "LARK" -> "LARK";
            default -> null;
        };
    }

    /**
     * Configure and register a notification platform
     */
    @PostMapping("/platforms/{platform}/configure")
    public ResponseEntity<Map<String, Object>> configurePlatform(
            @PathVariable String platform,
            @RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            AlarmManager manager = AlarmManager.getInstance();
            String webhookUrl = config.get("webhookUrl");
            String secret = config.get("secret");

            switch (platform.toUpperCase()) {
                case "DING":
                case "DINGTALK":
                    if (webhookUrl == null || webhookUrl.isEmpty()) {
                        result.put("success", false);
                        result.put("message", "Webhook URL is required");
                        return ResponseEntity.badRequest().body(result);
                    }
                    DingTalkNotifyPlatform dingPlatform = new DingTalkNotifyPlatform(webhookUrl, secret);
                    manager.registerPlatform(dingPlatform);
                    manager.enablePlatform("DING");
                    log.info("DingTalk platform configured and enabled");
                    break;
                case "WECHAT":
                case "WECHATWORK":
                    if (webhookUrl == null || webhookUrl.isEmpty()) {
                        result.put("success", false);
                        result.put("message", "Webhook URL is required");
                        return ResponseEntity.badRequest().body(result);
                    }
                    WeChatWorkNotifyPlatform wechatPlatform = new WeChatWorkNotifyPlatform(webhookUrl);
                    manager.registerPlatform(wechatPlatform);
                    manager.enablePlatform("WECHAT");
                    log.info("WeChatWork platform configured and enabled");
                    break;
                case "WEBHOOK":
                    if (webhookUrl == null || webhookUrl.isEmpty()) {
                        result.put("success", false);
                        result.put("message", "Webhook URL is required");
                        return ResponseEntity.badRequest().body(result);
                    }
                    WebhookNotifyPlatform webhookPlatform = new WebhookNotifyPlatform(webhookUrl, secret);
                    manager.registerPlatform(webhookPlatform);
                    manager.enablePlatform("WEBHOOK");
                    log.info("Webhook platform configured and enabled");
                    break;
                case "EMAIL":
                    String smtpHost = config.get("smtpHost");
                    String smtpPortStr = config.get("smtpPort");
                    String emailUsername = config.get("username");
                    String emailPassword = config.get("password");
                    String fromAddress = config.get("fromAddress");
                    String toAddresses = config.get("toAddresses");
                    boolean ssl = "true".equalsIgnoreCase(config.get("ssl"));

                    if (smtpHost == null || smtpHost.isBlank()) {
                        result.put("success", false);
                        result.put("message", "SMTP host is required");
                        return ResponseEntity.badRequest().body(result);
                    }
                    if (toAddresses == null || toAddresses.isBlank()) {
                        result.put("success", false);
                        result.put("message", "Recipient addresses are required");
                        return ResponseEntity.badRequest().body(result);
                    }

                    int smtpPort = 465;
                    try {
                        if (smtpPortStr != null && !smtpPortStr.isBlank()) {
                            smtpPort = Integer.parseInt(smtpPortStr);
                        }
                    } catch (NumberFormatException e) {
                        // use default
                    }

                    EmailNotifyPlatform emailPlatform = new EmailNotifyPlatform(
                            smtpHost, smtpPort, emailUsername, emailPassword,
                            fromAddress, toAddresses, ssl
                    );
                    manager.registerPlatform(emailPlatform);
                    manager.enablePlatform("EMAIL");
                    log.info("Email platform configured and enabled");
                    break;
                default:
                    result.put("success", false);
                    result.put("message", "Unknown platform: " + platform);
                    return ResponseEntity.badRequest().body(result);
            }

            result.put("success", true);
            result.put("platform", platform.toUpperCase());
            result.put("message", "Platform configured and enabled successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to configure platform: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Test notification platform
     */
    @PostMapping("/platforms/{platform}/test")
    public ResponseEntity<Map<String, Object>> testPlatform(
            @PathVariable String platform,
            @RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            String webhookUrl = config.get("webhookUrl");
            String secret = config.get("secret");

            // For non-email platforms, webhookUrl is required
            if (!"EMAIL".equalsIgnoreCase(platform) && (webhookUrl == null || webhookUrl.isEmpty())) {
                result.put("success", false);
                result.put("message", "Webhook URL is required");
                return ResponseEntity.badRequest().body(result);
            }

            boolean success = false;
            switch (platform.toUpperCase()) {
                case "DING":
                case "DINGTALK":
                    DingTalkNotifyPlatform dingPlatform = new DingTalkNotifyPlatform(webhookUrl, secret);
                    success = dingPlatform.sendTest();
                    break;
                case "WECHAT":
                case "WECHATWORK":
                    WeChatWorkNotifyPlatform wechatPlatform = new WeChatWorkNotifyPlatform(webhookUrl);
                    success = wechatPlatform.sendTest();
                    break;
                case "WEBHOOK":
                    WebhookNotifyPlatform webhookPlatform = new WebhookNotifyPlatform(webhookUrl, secret);
                    success = webhookPlatform.sendTest();
                    break;
                case "EMAIL":
                    String smtpHost = config.get("smtpHost");
                    String smtpPortStr = config.get("smtpPort");
                    String emailUsername = config.get("username");
                    String emailPassword = config.get("password");
                    String fromAddress = config.get("fromAddress");
                    String toAddresses = config.get("toAddresses");
                    boolean ssl = "true".equalsIgnoreCase(config.get("ssl"));

                    if (smtpHost == null || smtpHost.isBlank()) {
                        result.put("success", false);
                        result.put("message", "SMTP host is required for email test");
                        return ResponseEntity.badRequest().body(result);
                    }
                    if (toAddresses == null || toAddresses.isBlank()) {
                        result.put("success", false);
                        result.put("message", "Recipient addresses are required for email test");
                        return ResponseEntity.badRequest().body(result);
                    }

                    int smtpPort = 465;
                    try {
                        if (smtpPortStr != null && !smtpPortStr.isBlank()) {
                            smtpPort = Integer.parseInt(smtpPortStr);
                        }
                    } catch (NumberFormatException e) {
                        // use default
                    }

                    EmailNotifyPlatform emailPlatform = new EmailNotifyPlatform(
                            smtpHost, smtpPort, emailUsername, emailPassword,
                            fromAddress, toAddresses, ssl
                    );
                    success = emailPlatform.sendTest();
                    break;
                default:
                    result.put("success", false);
                    result.put("message", "Unknown platform: " + platform);
                    return ResponseEntity.badRequest().body(result);
            }

            result.put("success", success);
            result.put("platform", platform.toUpperCase());
            result.put("message", success ? "Test notification sent successfully" : "Failed to send test notification");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to test platform: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ==================== Rate Limiting Management ====================

    /**
     * Get rate limiter statistics.
     * Returns information about throttled alarms and current rate limit states.
     */
    @GetMapping("/rate-limit/statistics")
    public ResponseEntity<Map<String, Object>> getRateLimitStatistics() {
        Map<String, Object> result = new HashMap<>();
        AlarmRateLimiter.RateLimitStatistics stats = AlarmManager.getInstance().getRateLimitStatistics();
        result.put("statistics", stats);
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed rate limit states for all thread pool + metric combinations.
     */
    @GetMapping("/rate-limit/states")
    public ResponseEntity<Map<String, Object>> getRateLimitStates() {
        Map<String, Object> result = new HashMap<>();
        Map<String, AlarmRateLimiter.RateLimitState> states = AlarmManager.getInstance().getRateLimiter().getAllStates();
        
        // Convert to serializable format
        Map<String, Map<String, Object>> stateDetails = new HashMap<>();
        for (Map.Entry<String, AlarmRateLimiter.RateLimitState> entry : states.entrySet()) {
            Map<String, Object> stateInfo = new HashMap<>();
            AlarmRateLimiter.RateLimitState state = entry.getValue();
            stateInfo.put("lastAlarmTime", state.getLastAlarmDateTime());
            stateInfo.put("lastMetricValue", state.getLastMetricValue());
            stateInfo.put("throttledCountInWindow", state.getThrottledCountInWindow().get());
            stateInfo.put("sentCount", state.getSentCount().get());
            stateInfo.put("secondsUntilNextAllowed", state.getSecondsUntilNextAllowed(
                    AlarmManager.getInstance().getRateLimiter().getDefaultIntervalSeconds()));
            stateDetails.put(entry.getKey(), stateInfo);
        }
        
        result.put("states", stateDetails);
        result.put("count", states.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Enable or disable rate limiting globally.
     */
    @PostMapping("/rate-limit/toggle")
    public ResponseEntity<Map<String, Object>> toggleRateLimiting(@RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().setRateLimitEnabled(enabled);
        result.put("success", true);
        result.put("enabled", enabled);
        result.put("message", enabled ? "Rate limiting enabled" : "Rate limiting disabled");
        return ResponseEntity.ok(result);
    }

    /**
     * Set the default rate limit interval.
     */
    @PostMapping("/rate-limit/interval")
    public ResponseEntity<Map<String, Object>> setRateLimitInterval(@RequestParam int seconds) {
        Map<String, Object> result = new HashMap<>();
        if (seconds < 1) {
            result.put("success", false);
            result.put("message", "Interval must be at least 1 second");
            return ResponseEntity.badRequest().body(result);
        }
        AlarmManager.getInstance().setRateLimitInterval(seconds);
        result.put("success", true);
        result.put("interval", seconds);
        result.put("message", "Rate limit interval set to " + seconds + " seconds");
        return ResponseEntity.ok(result);
    }

    /**
     * Reset all rate limit states.
     * This will allow alarms to be sent immediately for all thread pools and metrics.
     */
    @PostMapping("/rate-limit/reset")
    public ResponseEntity<Map<String, Object>> resetRateLimits() {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().resetRateLimits();
        result.put("success", true);
        result.put("message", "All rate limit states have been reset");
        return ResponseEntity.ok(result);
    }

    /**
     * Reset rate limit state for a specific thread pool and metric.
     */
    @PostMapping("/rate-limit/reset/{threadPoolId}/{metric}")
    public ResponseEntity<Map<String, Object>> resetRateLimit(
            @PathVariable String threadPoolId,
            @PathVariable String metric) {
        Map<String, Object> result = new HashMap<>();
        AlarmManager.getInstance().resetRateLimit(threadPoolId, metric);
        result.put("success", true);
        result.put("threadPoolId", threadPoolId);
        result.put("metric", metric);
        result.put("message", "Rate limit state reset for " + threadPoolId + ":" + metric);
        return ResponseEntity.ok(result);
    }
}
