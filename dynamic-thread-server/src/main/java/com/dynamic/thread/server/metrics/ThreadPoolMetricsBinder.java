package com.dynamic.thread.server.metrics;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.server.registry.ClientRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds thread pool metrics from ClientRegistry to Micrometer registry for Prometheus export.
 * Dynamically tracks all registered thread pools across all connected agents.
 */
@Slf4j
@Component
@EnableScheduling
public class ThreadPoolMetricsBinder implements MeterBinder {

    private final ClientRegistry clientRegistry = ClientRegistry.getInstance();
    private MeterRegistry registry;
    
    // Track registered metrics to avoid duplicates
    private final Set<String> registeredPools = ConcurrentHashMap.newKeySet();
    
    // MultiGauge for dynamic thread pool metrics
    private MultiGauge corePoolSizeGauge;
    private MultiGauge maxPoolSizeGauge;
    private MultiGauge activeCountGauge;
    private MultiGauge poolSizeGauge;
    private MultiGauge queueSizeGauge;
    private MultiGauge queueCapacityGauge;
    private MultiGauge rejectedCountGauge;
    private MultiGauge completedTaskCountGauge;

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        
        // Create MultiGauge for each metric
        corePoolSizeGauge = MultiGauge.builder("thread_pool_core_size")
                .description("Thread pool core size")
                .register(registry);
        
        maxPoolSizeGauge = MultiGauge.builder("thread_pool_max_size")
                .description("Thread pool maximum size")
                .register(registry);
        
        activeCountGauge = MultiGauge.builder("thread_pool_active_count")
                .description("Thread pool active thread count")
                .register(registry);
        
        poolSizeGauge = MultiGauge.builder("thread_pool_size")
                .description("Thread pool current size")
                .register(registry);
        
        queueSizeGauge = MultiGauge.builder("thread_pool_queue_size")
                .description("Thread pool queue size")
                .register(registry);
        
        queueCapacityGauge = MultiGauge.builder("thread_pool_queue_capacity")
                .description("Thread pool queue capacity")
                .register(registry);
        
        rejectedCountGauge = MultiGauge.builder("thread_pool_rejected_total")
                .description("Thread pool total rejected task count")
                .register(registry);
        
        completedTaskCountGauge = MultiGauge.builder("thread_pool_completed_total")
                .description("Thread pool total completed task count")
                .register(registry);
        
        // Summary metrics
        Gauge.builder("thread_pool_connected_agents", () -> 
                clientRegistry.listClients().size())
                .description("Number of connected agents")
                .register(registry);
        
        Gauge.builder("thread_pool_registered_apps", () -> 
                clientRegistry.listApps().size())
                .description("Number of registered applications")
                .register(registry);
        
        log.info("Thread pool metrics bound to Micrometer registry for Prometheus export");
    }

    /**
     * Refresh metrics every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void refreshMetrics() {
        if (registry == null) return;
        
        Map<String, Map<String, List<ThreadPoolState>>> allStates = clientRegistry.getAllStates();
        
        List<MultiGauge.Row<?>> coreRows = new ArrayList<>();
        List<MultiGauge.Row<?>> maxRows = new ArrayList<>();
        List<MultiGauge.Row<?>> activeRows = new ArrayList<>();
        List<MultiGauge.Row<?>> sizeRows = new ArrayList<>();
        List<MultiGauge.Row<?>> queueSizeRows = new ArrayList<>();
        List<MultiGauge.Row<?>> queueCapacityRows = new ArrayList<>();
        List<MultiGauge.Row<?>> rejectedRows = new ArrayList<>();
        List<MultiGauge.Row<?>> completedRows = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, List<ThreadPoolState>>> appEntry : allStates.entrySet()) {
            String appId = appEntry.getKey();
            
            for (Map.Entry<String, List<ThreadPoolState>> instanceEntry : appEntry.getValue().entrySet()) {
                String instanceId = instanceEntry.getKey();
                
                for (ThreadPoolState state : instanceEntry.getValue()) {
                    Tags tags = Tags.of(
                            "app", appId,
                            "instance", shortenInstanceId(instanceId),
                            "pool", state.getThreadPoolId()
                    );
                    
                    coreRows.add(MultiGauge.Row.of(tags, state.getCorePoolSize() != null ? state.getCorePoolSize() : 0));
                    maxRows.add(MultiGauge.Row.of(tags, state.getMaximumPoolSize() != null ? state.getMaximumPoolSize() : 0));
                    activeRows.add(MultiGauge.Row.of(tags, state.getActiveCount() != null ? state.getActiveCount() : 0));
                    sizeRows.add(MultiGauge.Row.of(tags, state.getPoolSize() != null ? state.getPoolSize() : 0));
                    queueSizeRows.add(MultiGauge.Row.of(tags, state.getQueueSize() != null ? state.getQueueSize() : 0));
                    queueCapacityRows.add(MultiGauge.Row.of(tags, state.getQueueCapacity() != null ? state.getQueueCapacity() : 0));
                    rejectedRows.add(MultiGauge.Row.of(tags, state.getRejectedCount() != null ? state.getRejectedCount() : 0));
                    completedRows.add(MultiGauge.Row.of(tags, state.getCompletedTaskCount() != null ? state.getCompletedTaskCount() : 0));
                }
            }
        }
        
        // Update all gauges
        corePoolSizeGauge.register(coreRows, true);
        maxPoolSizeGauge.register(maxRows, true);
        activeCountGauge.register(activeRows, true);
        poolSizeGauge.register(sizeRows, true);
        queueSizeGauge.register(queueSizeRows, true);
        queueCapacityGauge.register(queueCapacityRows, true);
        rejectedCountGauge.register(rejectedRows, true);
        completedTaskCountGauge.register(completedRows, true);
    }
    
    /**
     * Shorten instance ID for cleaner labels (keep last part after colon)
     */
    private String shortenInstanceId(String instanceId) {
        if (instanceId == null) return "unknown";
        int colonIndex = instanceId.lastIndexOf(':');
        if (colonIndex > 0 && colonIndex < instanceId.length() - 1) {
            // Return hostname:port format
            return instanceId;
        }
        // If too long, truncate
        if (instanceId.length() > 50) {
            return instanceId.substring(0, 47) + "...";
        }
        return instanceId;
    }
}
