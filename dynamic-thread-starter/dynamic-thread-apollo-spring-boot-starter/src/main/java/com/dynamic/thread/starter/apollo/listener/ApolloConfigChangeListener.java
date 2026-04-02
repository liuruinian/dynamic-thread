package com.dynamic.thread.starter.apollo.listener;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.listener.AbstractConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Apollo configuration change listener.
 * Listens for configuration changes from Apollo and triggers thread pool refresh.
 */
@Slf4j
public class ApolloConfigChangeListener extends AbstractConfigChangeListener implements ConfigChangeListener {

    private final DynamicThreadPoolProperties properties;
    private Config config;

    public ApolloConfigChangeListener(ThreadPoolRefresher refresher,
                                      DynamicThreadPoolProperties properties) {
        super(refresher);
        this.properties = properties;
    }

    @Override
    public void startListening() {
        DynamicThreadPoolProperties.ApolloProperties apollo = properties.getApollo();
        String namespace = apollo != null && apollo.getNamespace() != null 
                ? apollo.getNamespace() 
                : "application";

        try {
            config = ConfigService.getConfig(namespace);
            config.addChangeListener(this);
            log.info("Apollo config listener registered for namespace: {}", namespace);

            // Load initial configuration
            loadInitialConfig();

        } catch (Exception e) {
            log.error("Failed to register Apollo config listener", e);
        }
    }

    @Override
    public void stopListening() {
        if (config != null) {
            config.removeChangeListener(this);
            log.info("Apollo config listener removed");
        }
    }

    @Override
    public void onChange(ConfigChangeEvent changeEvent) {
        Set<String> changedKeys = changeEvent.changedKeys();
        
        // Check if any dynamic-thread related keys changed
        boolean hasRelevantChange = changedKeys.stream()
                .anyMatch(key -> key.startsWith("dynamic-thread"));

        if (hasRelevantChange) {
            log.info("Apollo config changed, affected keys: {}", changedKeys);
            loadInitialConfig();
        }
    }

    /**
     * Load configuration from Apollo and trigger refresh
     */
    private void loadInitialConfig() {
        if (config == null) {
            return;
        }

        try {
            // Build configuration content from Apollo properties
            StringBuilder yamlBuilder = new StringBuilder();
            yamlBuilder.append("dynamic-thread:\n");
            yamlBuilder.append("  executors:\n");

            // Get executor properties
            Set<String> propertyNames = config.getPropertyNames();
            
            // Find all unique thread pool ids
            Set<String> indices = new java.util.HashSet<>();
            String prefix = "dynamic-thread.executors[";
            for (String key : propertyNames) {
                if (key.startsWith(prefix)) {
                    int endIndex = key.indexOf("]", prefix.length());
                    if (endIndex > prefix.length()) {
                        indices.add(key.substring(prefix.length(), endIndex));
                    }
                }
            }

            // Build YAML for each executor
            for (String index : indices) {
                String keyPrefix = prefix + index + "].";
                String threadPoolId = config.getProperty(keyPrefix + "thread-pool-id", null);
                if (threadPoolId != null) {
                    yamlBuilder.append("    - thread-pool-id: ").append(threadPoolId).append("\n");
                    
                    String corePoolSize = config.getProperty(keyPrefix + "core-pool-size", null);
                    if (corePoolSize != null) {
                        yamlBuilder.append("      core-pool-size: ").append(corePoolSize).append("\n");
                    }
                    
                    String maxPoolSize = config.getProperty(keyPrefix + "maximum-pool-size", null);
                    if (maxPoolSize != null) {
                        yamlBuilder.append("      maximum-pool-size: ").append(maxPoolSize).append("\n");
                    }
                    
                    String queueCapacity = config.getProperty(keyPrefix + "queue-capacity", null);
                    if (queueCapacity != null) {
                        yamlBuilder.append("      queue-capacity: ").append(queueCapacity).append("\n");
                    }
                    
                    String keepAliveTime = config.getProperty(keyPrefix + "keep-alive-time", null);
                    if (keepAliveTime != null) {
                        yamlBuilder.append("      keep-alive-time: ").append(keepAliveTime).append("\n");
                    }

                    String rejectedHandler = config.getProperty(keyPrefix + "rejected-handler", null);
                    if (rejectedHandler != null) {
                        yamlBuilder.append("      rejected-handler: ").append(rejectedHandler).append("\n");
                    }
                }
            }

            String configContent = yamlBuilder.toString();
            if (!indices.isEmpty()) {
                onConfigChange(configContent, "YAML");
            }

        } catch (Exception e) {
            log.error("Failed to load configuration from Apollo", e);
        }
    }
}
