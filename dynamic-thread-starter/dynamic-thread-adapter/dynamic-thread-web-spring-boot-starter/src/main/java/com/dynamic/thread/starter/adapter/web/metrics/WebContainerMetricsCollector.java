package com.dynamic.thread.starter.adapter.web.metrics;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.starter.adapter.web.WebContainerThreadPoolManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;

/**
 * Prometheus metrics collector for web container thread pools.
 * Exposes web container thread pool metrics to Prometheus via Micrometer.
 */
@Slf4j
public class WebContainerMetricsCollector {

    private static final String METRIC_PREFIX = "web_container_";

    private final WebContainerThreadPoolManager manager;
    private final MeterRegistry meterRegistry;
    private volatile boolean registered = false;

    public WebContainerMetricsCollector(WebContainerThreadPoolManager manager, MeterRegistry meterRegistry) {
        this.manager = manager;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Delay registration to ensure web server is fully started
        Thread registerThread = new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait for web server to start
                registerMetrics();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        registerThread.setDaemon(true);
        registerThread.start();
    }

    /**
     * Register metrics for web container thread pool
     */
    private void registerMetrics() {
        if (registered || !manager.isAvailable()) {
            return;
        }

        String containerType = manager.getAdapterName();
        Tags tags = Tags.of("container", containerType);

        // Core pool size
        Gauge.builder(METRIC_PREFIX + "core_pool_size", this::getCorePoolSize)
                .tags(tags)
                .description("Web container core pool size")
                .register(meterRegistry);

        // Maximum pool size
        Gauge.builder(METRIC_PREFIX + "maximum_pool_size", this::getMaximumPoolSize)
                .tags(tags)
                .description("Web container maximum pool size")
                .register(meterRegistry);

        // Current pool size
        Gauge.builder(METRIC_PREFIX + "pool_size", this::getPoolSize)
                .tags(tags)
                .description("Web container current pool size")
                .register(meterRegistry);

        // Active count
        Gauge.builder(METRIC_PREFIX + "active_count", this::getActiveCount)
                .tags(tags)
                .description("Web container active thread count")
                .register(meterRegistry);

        // Queue size
        Gauge.builder(METRIC_PREFIX + "queue_size", this::getQueueSize)
                .tags(tags)
                .description("Web container queue size")
                .register(meterRegistry);

        // Active percent
        Gauge.builder(METRIC_PREFIX + "active_percent", this::getActivePercent)
                .tags(tags)
                .description("Web container active thread percentage")
                .register(meterRegistry);

        // Queue usage percent
        Gauge.builder(METRIC_PREFIX + "queue_usage_percent", this::getQueueUsagePercent)
                .tags(tags)
                .description("Web container queue usage percentage")
                .register(meterRegistry);

        registered = true;
        log.info("Web container metrics registered for: {}", containerType);
    }

    private Number getCorePoolSize() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null ? state.getCorePoolSize() : 0;
    }

    private Number getMaximumPoolSize() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null ? state.getMaximumPoolSize() : 0;
    }

    private Number getPoolSize() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null ? state.getPoolSize() : 0;
    }

    private Number getActiveCount() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null ? state.getActiveCount() : 0;
    }

    private Number getQueueSize() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null ? state.getQueueSize() : 0;
    }

    private Number getActivePercent() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null && state.getActivePercent() != null ? state.getActivePercent() : 0;
    }

    private Number getQueueUsagePercent() {
        ThreadPoolState state = manager.getThreadPoolState();
        return state != null && state.getQueueUsagePercent() != null ? state.getQueueUsagePercent() : 0;
    }
}
