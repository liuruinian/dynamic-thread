package com.dynamic.thread.core.alarm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Alarm rate limiter to prevent alarm storms.
 * 
 * Uses sliding time window algorithm to control alarm frequency per thread pool and metric type.
 * 
 * Key features:
 * - Group rate limiting by threadPoolId + metric
 * - Configurable time window interval
 * - Statistics tracking for throttled alarms
 * - Thread-safe implementation
 */
@Slf4j
public class AlarmRateLimiter {

    private static final AlarmRateLimiter INSTANCE = new AlarmRateLimiter();

    /**
     * Rate limit state per key (threadPoolId:metric)
     */
    private final Map<String, RateLimitState> limitStates = new ConcurrentHashMap<>();

    /**
     * Default rate limit interval in seconds
     */
    private volatile int defaultIntervalSeconds = 60;

    /**
     * Whether rate limiting is enabled globally
     */
    private volatile boolean enabled = true;

    /**
     * Total throttled count
     */
    private final AtomicLong totalThrottledCount = new AtomicLong(0);

    private AlarmRateLimiter() {
    }

    public static AlarmRateLimiter getInstance() {
        return INSTANCE;
    }

    /**
     * Check if an alarm can be sent based on rate limiting rules.
     * 
     * @param threadPoolId the thread pool identifier
     * @param metric       the alarm metric type (e.g., threadUsage, queueUsage, rejectedCount)
     * @return true if alarm can be sent, false if throttled
     */
    public boolean tryAcquire(String threadPoolId, String metric) {
        return tryAcquire(threadPoolId, metric, defaultIntervalSeconds);
    }

    /**
     * Check if an alarm can be sent based on rate limiting rules.
     * 
     * @param threadPoolId    the thread pool identifier
     * @param metric          the alarm metric type
     * @param intervalSeconds custom interval for this alarm type
     * @return true if alarm can be sent, false if throttled
     */
    public boolean tryAcquire(String threadPoolId, String metric, int intervalSeconds) {
        if (!enabled) {
            return true;
        }

        String key = buildKey(threadPoolId, metric);
        long now = System.currentTimeMillis();
        long intervalMs = intervalSeconds * 1000L;

        RateLimitState state = limitStates.computeIfAbsent(key, k -> new RateLimitState());

        synchronized (state) {
            long lastTime = state.getLastAlarmTime();
            
            if (lastTime == 0 || (now - lastTime) >= intervalMs) {
                // Allow alarm
                state.setLastAlarmTime(now);
                state.setLastMetricValue(0); // Will be updated by caller
                state.setWindowStartTime(now);
                state.getThrottledCountInWindow().set(0);
                log.debug("[RateLimiter] Allowed alarm for key={}, interval={}s", key, intervalSeconds);
                return true;
            } else {
                // Throttle alarm
                state.getThrottledCountInWindow().incrementAndGet();
                state.setLastThrottledTime(now);
                totalThrottledCount.incrementAndGet();
                
                long remainingSeconds = (intervalMs - (now - lastTime)) / 1000;
                log.debug("[RateLimiter] Throttled alarm for key={}, next allowed in {}s, throttled {} times in window",
                        key, remainingSeconds, state.getThrottledCountInWindow().get());
                return false;
            }
        }
    }

    /**
     * Record additional context when an alarm is sent.
     * 
     * @param threadPoolId the thread pool identifier
     * @param metric       the alarm metric type
     * @param metricValue  the current metric value
     */
    public void recordAlarmSent(String threadPoolId, String metric, double metricValue) {
        String key = buildKey(threadPoolId, metric);
        RateLimitState state = limitStates.get(key);
        if (state != null) {
            state.setLastMetricValue(metricValue);
            state.getSentCount().incrementAndGet();
        }
    }

    /**
     * Get rate limit statistics for a specific key.
     */
    public RateLimitState getState(String threadPoolId, String metric) {
        return limitStates.get(buildKey(threadPoolId, metric));
    }

    /**
     * Get all rate limit states.
     */
    public Map<String, RateLimitState> getAllStates() {
        return new ConcurrentHashMap<>(limitStates);
    }

    /**
     * Get total throttled count across all keys.
     */
    public long getTotalThrottledCount() {
        return totalThrottledCount.get();
    }

    /**
     * Get statistics summary.
     */
    public RateLimitStatistics getStatistics() {
        RateLimitStatistics stats = new RateLimitStatistics();
        stats.setEnabled(enabled);
        stats.setDefaultIntervalSeconds(defaultIntervalSeconds);
        stats.setTotalThrottledCount(totalThrottledCount.get());
        stats.setActiveKeys(limitStates.size());
        
        long totalSent = 0;
        long totalThrottledInWindows = 0;
        for (RateLimitState state : limitStates.values()) {
            totalSent += state.getSentCount().get();
            totalThrottledInWindows += state.getThrottledCountInWindow().get();
        }
        stats.setTotalSentCount(totalSent);
        stats.setCurrentWindowThrottledCount(totalThrottledInWindows);
        
        return stats;
    }

    /**
     * Reset all rate limit states.
     */
    public void reset() {
        limitStates.clear();
        totalThrottledCount.set(0);
        log.info("[RateLimiter] All rate limit states reset");
    }

    /**
     * Reset rate limit state for a specific key.
     */
    public void reset(String threadPoolId, String metric) {
        String key = buildKey(threadPoolId, metric);
        limitStates.remove(key);
        log.info("[RateLimiter] Reset rate limit state for key={}", key);
    }

    /**
     * Set the default interval in seconds.
     */
    public void setDefaultIntervalSeconds(int seconds) {
        this.defaultIntervalSeconds = Math.max(1, seconds);
        log.info("[RateLimiter] Default interval set to {}s", this.defaultIntervalSeconds);
    }

    public int getDefaultIntervalSeconds() {
        return defaultIntervalSeconds;
    }

    /**
     * Enable or disable rate limiting.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("[RateLimiter] Rate limiting {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String buildKey(String threadPoolId, String metric) {
        return threadPoolId + ":" + metric;
    }

    /**
     * Rate limit state for a specific threadPoolId + metric combination.
     */
    @Data
    public static class RateLimitState {
        /**
         * Last time an alarm was actually sent
         */
        private volatile long lastAlarmTime;

        /**
         * Last metric value when alarm was sent
         */
        private volatile double lastMetricValue;

        /**
         * Window start time
         */
        private volatile long windowStartTime;

        /**
         * Last time an alarm was throttled
         */
        private volatile long lastThrottledTime;

        /**
         * Count of throttled alarms in current window
         */
        private final AtomicLong throttledCountInWindow = new AtomicLong(0);

        /**
         * Total count of sent alarms
         */
        private final AtomicLong sentCount = new AtomicLong(0);

        /**
         * Get human-readable last alarm time.
         */
        public LocalDateTime getLastAlarmDateTime() {
            if (lastAlarmTime == 0) {
                return null;
            }
            return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastAlarmTime),
                    java.time.ZoneId.systemDefault()
            );
        }

        /**
         * Get seconds until next alarm is allowed.
         */
        public long getSecondsUntilNextAllowed(int intervalSeconds) {
            if (lastAlarmTime == 0) {
                return 0;
            }
            long elapsed = System.currentTimeMillis() - lastAlarmTime;
            long remaining = (intervalSeconds * 1000L) - elapsed;
            return Math.max(0, remaining / 1000);
        }
    }

    /**
     * Rate limit statistics summary.
     */
    @Data
    public static class RateLimitStatistics {
        private boolean enabled;
        private int defaultIntervalSeconds;
        private long totalThrottledCount;
        private long totalSentCount;
        private long currentWindowThrottledCount;
        private int activeKeys;
    }
}
