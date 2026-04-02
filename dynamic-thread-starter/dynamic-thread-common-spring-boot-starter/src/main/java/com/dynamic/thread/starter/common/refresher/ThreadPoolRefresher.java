package com.dynamic.thread.starter.common.refresher;

import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.model.ConfigChangeResult;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.event.ThreadPoolConfigChangeEvent;
import com.dynamic.thread.starter.common.parser.ConfigParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread pool refresher for applying configuration changes.
 * Publishes Spring events when configurations change.
 */
@Slf4j
public class ThreadPoolRefresher {

    private final ThreadPoolRegistry registry;
    private final ConfigParser parser;
    private final ApplicationEventPublisher eventPublisher;

    public ThreadPoolRefresher(ThreadPoolRegistry registry, ConfigParser parser, 
                               ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.parser = parser;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Refresh thread pools with new configuration content.
     * Publishes ThreadPoolConfigChangeEvent if any changes occurred.
     *
     * @param configContent the new configuration content
     * @param configType    the configuration type (YAML, PROPERTIES, JSON)
     */
    public void refresh(String configContent, String configType) {
        List<DynamicThreadPoolProperties.ExecutorProperties> executorConfigs = parser.parse(configContent, configType);
        List<ConfigChangeResult> allResults = new ArrayList<>();

        for (DynamicThreadPoolProperties.ExecutorProperties config : executorConfigs) {
            ConfigChangeResult result = refreshSingle(config);
            if (result != null) {
                allResults.add(result);
            }
        }

        // Publish event if any changes occurred
        publishChangeEvent(allResults, configType);
    }

    /**
     * Refresh a single thread pool with new configuration
     *
     * @param config the new executor configuration
     * @return the change result, or null if thread pool not found
     */
    public ConfigChangeResult refreshSingle(DynamicThreadPoolProperties.ExecutorProperties config) {
        String threadPoolId = config.getThreadPoolId();
        if (threadPoolId == null || threadPoolId.isBlank()) {
            log.warn("Thread pool id is empty, skipping");
            return null;
        }

        if (!registry.contains(threadPoolId)) {
            log.warn("Thread pool [{}] not found in registry, skipping", threadPoolId);
            return null;
        }

        // Convert to ThreadPoolConfig and delegate to registry
        // Registry handles both executor update and config storage
        ThreadPoolConfig poolConfig = config.toThreadPoolConfig();
        return registry.updateConfig(threadPoolId, poolConfig);
    }

    /**
     * Refresh thread pool with ThreadPoolConfig directly.
     * Publishes ThreadPoolConfigChangeEvent if changes occurred.
     *
     * @param config the thread pool configuration
     * @return the change result, or null if thread pool not found
     */
    public ConfigChangeResult refresh(ThreadPoolConfig config) {
        String threadPoolId = config.getThreadPoolId();
        if (threadPoolId == null || threadPoolId.isBlank()) {
            log.warn("Thread pool id is empty, skipping");
            return null;
        }

        if (!registry.contains(threadPoolId)) {
            log.warn("Thread pool [{}] not found in registry, skipping", threadPoolId);
            return null;
        }

        // Delegate to registry - it handles executor update and config storage
        ConfigChangeResult result = registry.updateConfig(threadPoolId, config);

        // Publish event if changes occurred
        if (result != null && result.isChanged()) {
            List<ConfigChangeResult> results = new ArrayList<>();
            results.add(result);
            publishChangeEvent(results, "DIRECT");
        }

        return result;
    }

    /**
     * Publish configuration change event if any changes occurred
     */
    private void publishChangeEvent(List<ConfigChangeResult> results, String configType) {
        if (eventPublisher == null) {
            return;
        }

        ThreadPoolConfigChangeEvent event = new ThreadPoolConfigChangeEvent(this, results, configType);
        if (event.hasChanges()) {
            eventPublisher.publishEvent(event);
            log.info("Published config change event: {} thread pool(s) changed", event.getChangedCount());
        }
    }
}
