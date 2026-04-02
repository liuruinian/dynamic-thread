package com.dynamic.thread.starter.common.metrics;

import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus metrics collector for dynamic thread pools.
 * Exposes thread pool metrics to Prometheus via Micrometer.
 */
@Slf4j
public class ThreadPoolMetricsCollector {

    private static final String METRIC_PREFIX = "dynamic_thread_pool_";

    private final ThreadPoolRegistry registry;
    private final MeterRegistry meterRegistry;
    private final Map<String, Boolean> registeredPools = new ConcurrentHashMap<>();

    public ThreadPoolMetricsCollector(ThreadPoolRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
        log.info("Thread pool metrics collector initialized");
    }

    /**
     * Register metrics for all thread pools periodically
     */
    @Scheduled(fixedDelay = 5000)
    public void registerMetrics() {
        for (String threadPoolId : registry.listThreadPoolIds()) {
            if (!registeredPools.containsKey(threadPoolId)) {
                registerPoolMetrics(threadPoolId);
                registeredPools.put(threadPoolId, true);
            }
        }
    }

    /**
     * Register metrics for a single thread pool
     */
    private void registerPoolMetrics(String threadPoolId) {
        Tags tags = Tags.of("pool_id", threadPoolId);

        // Core pool size
        Gauge.builder(METRIC_PREFIX + "core_pool_size", () -> getMetricValue(threadPoolId, "corePoolSize"))
                .tags(tags)
                .description("Core pool size")
                .register(meterRegistry);

        // Maximum pool size
        Gauge.builder(METRIC_PREFIX + "maximum_pool_size", () -> getMetricValue(threadPoolId, "maximumPoolSize"))
                .tags(tags)
                .description("Maximum pool size")
                .register(meterRegistry);

        // Current pool size
        Gauge.builder(METRIC_PREFIX + "pool_size", () -> getMetricValue(threadPoolId, "poolSize"))
                .tags(tags)
                .description("Current pool size")
                .register(meterRegistry);

        // Active count
        Gauge.builder(METRIC_PREFIX + "active_count", () -> getMetricValue(threadPoolId, "activeCount"))
                .tags(tags)
                .description("Active thread count")
                .register(meterRegistry);

        // Queue size
        Gauge.builder(METRIC_PREFIX + "queue_size", () -> getMetricValue(threadPoolId, "queueSize"))
                .tags(tags)
                .description("Current queue size")
                .register(meterRegistry);

        // Queue capacity
        Gauge.builder(METRIC_PREFIX + "queue_capacity", () -> getMetricValue(threadPoolId, "queueCapacity"))
                .tags(tags)
                .description("Queue capacity")
                .register(meterRegistry);

        // Queue usage percent
        Gauge.builder(METRIC_PREFIX + "queue_usage_percent", () -> getMetricValue(threadPoolId, "queueUsagePercent"))
                .tags(tags)
                .description("Queue usage percentage")
                .register(meterRegistry);

        // Active percent
        Gauge.builder(METRIC_PREFIX + "active_percent", () -> getMetricValue(threadPoolId, "activePercent"))
                .tags(tags)
                .description("Active thread percentage")
                .register(meterRegistry);

        // Completed task count
        Gauge.builder(METRIC_PREFIX + "completed_task_count", () -> getMetricValue(threadPoolId, "completedTaskCount"))
                .tags(tags)
                .description("Completed task count")
                .register(meterRegistry);

        // Rejected count
        Gauge.builder(METRIC_PREFIX + "rejected_count", () -> getMetricValue(threadPoolId, "rejectedCount"))
                .tags(tags)
                .description("Rejected task count")
                .register(meterRegistry);

        log.info("Registered metrics for thread pool: {}", threadPoolId);
    }

    /**
     * Get metric value from thread pool state
     */
    private Number getMetricValue(String threadPoolId, String metric) {
        DynamicThreadPoolExecutor executor = registry.get(threadPoolId);
        if (executor == null) {
            return 0;
        }

        ThreadPoolState state = executor.getState();
        if (state == null) {
            return 0;
        }

        switch (metric) {
            case "corePoolSize": return state.getCorePoolSize();
            case "maximumPoolSize": return state.getMaximumPoolSize();
            case "poolSize": return state.getPoolSize();
            case "activeCount": return state.getActiveCount();
            case "queueSize": return state.getQueueSize();
            case "queueCapacity": return state.getQueueCapacity();
            case "queueUsagePercent": return state.getQueueUsagePercent();
            case "activePercent": return state.getActivePercent();
            case "completedTaskCount": return state.getCompletedTaskCount();
            case "rejectedCount": return state.getRejectedCount();
            default: return 0;
        }
    }
}
