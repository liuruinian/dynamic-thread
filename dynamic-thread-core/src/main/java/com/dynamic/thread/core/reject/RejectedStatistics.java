package com.dynamic.thread.core.reject;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.i18n.I18nUtil;
import com.dynamic.thread.core.model.ThreadPoolState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized statistics manager for rejected tasks across all thread pools.
 * Provides real-time statistics and alarm triggering.
 * 
 * <p>This class implements the singleton pattern and collects rejection
 * statistics from all thread pools, enabling:</p>
 * <ul>
 *   <li>Real-time rejection counting per pool</li>
 *   <li>Instant alarm triggering on rejection</li>
 *   <li>Historical rejection records</li>
 *   <li>Statistics aggregation and reporting</li>
 * </ul>
 */
@Slf4j
public class RejectedStatistics {

    private static final RejectedStatistics INSTANCE = new RejectedStatistics();

    /**
     * Per-pool rejection counters
     */
    private final Map<String, AtomicLong> poolRejectedCounts = new ConcurrentHashMap<>();

    /**
     * Per-pool proxy handlers
     */
    private final Map<String, RejectedExecutionHandlerProxy> proxyHandlers = new ConcurrentHashMap<>();

    /**
     * Global rejection counter
     */
    private final AtomicLong globalRejectedCount = new AtomicLong(0);

    /**
     * Whether instant alarm is enabled
     */
    private volatile boolean instantAlarmEnabled = true;

    private RejectedStatistics() {
    }

    public static RejectedStatistics getInstance() {
        return INSTANCE;
    }

    /**
     * Register a proxy handler for statistics tracking.
     *
     * @param proxy the proxy handler
     */
    public void registerProxy(RejectedExecutionHandlerProxy proxy) {
        if (proxy == null || proxy.getThreadPoolId() == null) {
            return;
        }

        String poolId = proxy.getThreadPoolId();
        proxyHandlers.put(poolId, proxy);
        poolRejectedCounts.putIfAbsent(poolId, new AtomicLong(0));

        // Add alarm listener if instant alarm is enabled
        if (instantAlarmEnabled) {
            proxy.addListener(createAlarmListener(poolId));
        }

        log.info("[RejectedStatistics] Registered proxy for pool: {}", poolId);
    }

    /**
     * Unregister a proxy handler.
     *
     * @param threadPoolId the thread pool id
     */
    public void unregisterProxy(String threadPoolId) {
        proxyHandlers.remove(threadPoolId);
        log.info("[RejectedStatistics] Unregistered proxy for pool: {}", threadPoolId);
    }

    /**
     * Create an alarm listener for instant rejection notification.
     */
    private RejectedTaskListener createAlarmListener(String poolId) {
        return new RejectedTaskListener() {
            @Override
            public void onTaskRejected(RejectedTaskRecord record) {
                // Update statistics
                poolRejectedCounts.computeIfAbsent(poolId, k -> new AtomicLong(0)).incrementAndGet();
                globalRejectedCount.incrementAndGet();

                // Trigger instant alarm
                triggerInstantAlarm(record);
            }

            @Override
            public int getOrder() {
                return Integer.MIN_VALUE; // Execute first
            }
        };
    }

    /**
     * Trigger instant alarm for task rejection.
     */
    private void triggerInstantAlarm(RejectedTaskRecord record) {
        try {
            // Build ThreadPoolState from rejection record
            ThreadPoolState state = ThreadPoolState.builder()
                    .threadPoolId(record.getThreadPoolId())
                    .corePoolSize(record.getCorePoolSize())
                    .maximumPoolSize(record.getMaximumPoolSize())
                    .activeCount(record.getActiveCount())
                    .poolSize(record.getActiveCount()) // Approximate
                    .queueSize(record.getQueueSize())
                    .queueCapacity(record.getQueueCapacity())
                    .queueRemainingCapacity(
                            record.getQueueCapacity() != null && record.getQueueSize() != null
                                    ? record.getQueueCapacity() - record.getQueueSize()
                                    : 0
                    )
                    .rejectedCount(record.getTotalRejectedCount())
                    .rejectedHandler(record.getRejectedPolicy())
                    .timestamp(LocalDateTime.now())
                    .build();
            state.calculateMetrics();

            // Trigger alarm check through AlarmManager
            AlarmManager.getInstance().checkAndTrigger(state);

            log.debug("[RejectedStatistics] Triggered instant alarm for pool: {}, total rejected: {}",
                    record.getThreadPoolId(), record.getTotalRejectedCount());
        } catch (Exception e) {
            log.warn("[RejectedStatistics] Failed to trigger instant alarm: {}", e.getMessage());
        }
    }

    // ==================== Statistics API ====================

    /**
     * Get global rejected count.
     *
     * @return total rejected tasks across all pools
     */
    public long getGlobalRejectedCount() {
        return globalRejectedCount.get();
    }

    /**
     * Get rejected count for a specific pool.
     *
     * @param threadPoolId the pool id
     * @return rejected count, or 0 if pool not found
     */
    public long getPoolRejectedCount(String threadPoolId) {
        AtomicLong counter = poolRejectedCounts.get(threadPoolId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get all pool rejected counts.
     *
     * @return map of pool id to count
     */
    public Map<String, Long> getAllPoolRejectedCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        poolRejectedCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Get recent rejection records for a pool.
     *
     * @param threadPoolId the pool id
     * @param limit maximum records
     * @return list of records
     */
    public List<RejectedTaskRecord> getRecentRecords(String threadPoolId, int limit) {
        RejectedExecutionHandlerProxy proxy = proxyHandlers.get(threadPoolId);
        return proxy != null ? proxy.getRecentRecords(limit) : java.util.Collections.emptyList();
    }

    /**
     * Get all recent rejection records across all pools.
     *
     * @param limit maximum records per pool
     * @return map of pool id to records
     */
    public Map<String, List<RejectedTaskRecord>> getAllRecentRecords(int limit) {
        Map<String, List<RejectedTaskRecord>> result = new ConcurrentHashMap<>();
        proxyHandlers.forEach((poolId, proxy) -> 
            result.put(poolId, proxy.getRecentRecords(limit))
        );
        return result;
    }

    /**
     * Reset statistics for a specific pool.
     *
     * @param threadPoolId the pool id
     */
    public void resetPoolStatistics(String threadPoolId) {
        AtomicLong counter = poolRejectedCounts.get(threadPoolId);
        if (counter != null) {
            globalRejectedCount.addAndGet(-counter.get());
            counter.set(0);
        }
        RejectedExecutionHandlerProxy proxy = proxyHandlers.get(threadPoolId);
        if (proxy != null) {
            proxy.resetCount();
        }
    }

    /**
     * Reset all statistics.
     */
    public void resetAllStatistics() {
        poolRejectedCounts.values().forEach(c -> c.set(0));
        globalRejectedCount.set(0);
        proxyHandlers.values().forEach(RejectedExecutionHandlerProxy::resetCount);
    }

    /**
     * Enable or disable instant alarm on rejection.
     *
     * @param enabled whether to enable
     */
    public void setInstantAlarmEnabled(boolean enabled) {
        this.instantAlarmEnabled = enabled;
        log.info("[RejectedStatistics] Instant alarm {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if instant alarm is enabled.
     *
     * @return true if enabled
     */
    public boolean isInstantAlarmEnabled() {
        return instantAlarmEnabled;
    }

    /**
     * Get statistics summary.
     *
     * @return summary map
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("globalRejectedCount", globalRejectedCount.get());
        summary.put("poolCount", proxyHandlers.size());
        summary.put("poolStatistics", getAllPoolRejectedCounts());
        summary.put("instantAlarmEnabled", instantAlarmEnabled);
        return summary;
    }
}
