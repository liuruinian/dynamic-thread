package com.dynamic.thread.starter.adapter.web;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manager for web container thread pool adapters.
 * 
 * <p>This manager handles:
 * <ul>
 *   <li>Automatic detection of the running web container (Tomcat/Jetty/Undertow)</li>
 *   <li>Adapter selection based on web server type</li>
 *   <li>Thread pool state monitoring</li>
 *   <li>Dynamic parameter configuration</li>
 *   <li>Safety validation for parameter changes</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class WebContainerThreadPoolManager {

    private final List<WebContainerThreadPoolAdapter> adapters;
    private final ApplicationContext applicationContext;

    /** Cached adapter for performance */
    private volatile WebContainerThreadPoolAdapter cachedAdapter;

    /** Whether the manager is initialized */
    private volatile boolean initialized = false;

    /**
     * Get the web server from application context
     */
    public WebServer getWebServer() {
        if (applicationContext instanceof WebServerApplicationContext) {
            return ((WebServerApplicationContext) applicationContext).getWebServer();
        }
        return null;
    }

    /**
     * Find the appropriate adapter for the current web server
     */
    public Optional<WebContainerThreadPoolAdapter> findAdapter() {
        // Return cached adapter if available
        if (cachedAdapter != null) {
            return Optional.of(cachedAdapter);
        }

        WebServer webServer = getWebServer();
        if (webServer == null) {
            return Optional.empty();
        }

        Optional<WebContainerThreadPoolAdapter> adapter = adapters.stream()
                .filter(a -> a.match(webServer))
                .findFirst();

        // Cache the adapter
        adapter.ifPresent(a -> {
            cachedAdapter = a;
            initialized = true;
            log.info("Web container thread pool adapter initialized: {}", a.getAdapterName());
        });

        return adapter;
    }

    /**
     * Check if the manager is properly initialized
     */
    public boolean isAvailable() {
        return getWebServer() != null && findAdapter().isPresent();
    }

    /**
     * Get thread pool state from web container
     */
    public ThreadPoolState getThreadPoolState() {
        WebServer webServer = getWebServer();
        if (webServer == null) {
            return null;
        }

        return findAdapter()
                .map(adapter -> adapter.getThreadPoolState(webServer))
                .orElse(null);
    }

    /**
     * Update web container thread pool configuration with validation
     * 
     * @param config the new configuration
     * @return true if update was successful
     */
    public boolean updateThreadPool(DynamicThreadPoolProperties.WebThreadPoolProperties config) {
        WebServer webServer = getWebServer();
        if (webServer == null) {
            log.warn("No web server found, cannot update thread pool");
            return false;
        }

        Optional<WebContainerThreadPoolAdapter> adapterOpt = findAdapter();
        if (adapterOpt.isEmpty()) {
            log.warn("No adapter found for web server: {}", webServer.getClass().getName());
            return false;
        }

        // Validate configuration before applying
        String validationError = validateConfig(config);
        if (validationError != null) {
            log.error("Configuration validation failed: {}", validationError);
            return false;
        }

        try {
            adapterOpt.get().updateThreadPool(webServer, config);
            log.info("Web container thread pool configuration updated successfully");
            return true;
        } catch (Exception e) {
            log.error("Failed to update web container thread pool", e);
            return false;
        }
    }

    /**
     * Validate thread pool configuration
     * 
     * @param config the configuration to validate
     * @return error message if validation fails, null if valid
     */
    private String validateConfig(DynamicThreadPoolProperties.WebThreadPoolProperties config) {
        if (config == null) {
            return "Configuration cannot be null";
        }

        Integer coreSize = config.getCorePoolSize();
        Integer maxSize = config.getMaximumPoolSize();
        Long keepAlive = config.getKeepAliveTime();

        if (coreSize != null && coreSize <= 0) {
            return "Core pool size must be positive";
        }

        if (maxSize != null && maxSize <= 0) {
            return "Maximum pool size must be positive";
        }

        if (coreSize != null && maxSize != null && coreSize > maxSize) {
            return "Core pool size cannot exceed maximum pool size";
        }

        if (keepAlive != null && keepAlive < 0) {
            return "Keep alive time cannot be negative";
        }

        // Get current state and check for reasonable changes
        ThreadPoolState currentState = getThreadPoolState();
        if (currentState != null) {
            int currentActive = currentState.getActiveCount();
            if (maxSize != null && maxSize < currentActive) {
                log.warn("New maximum pool size ({}) is less than current active threads ({}). " +
                        "This may cause performance issues.", maxSize, currentActive);
            }
        }

        return null;
    }

    /**
     * Get adapter name for current web server
     */
    public String getAdapterName() {
        return findAdapter()
                .map(WebContainerThreadPoolAdapter::getAdapterName)
                .orElse("Unknown");
    }

    /**
     * Get summary information about the web container thread pool
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("available", isAvailable());
        summary.put("adapterName", getAdapterName());
        
        ThreadPoolState state = getThreadPoolState();
        if (state != null) {
            summary.put("threadPoolId", state.getThreadPoolId());
            summary.put("corePoolSize", state.getCorePoolSize());
            summary.put("maximumPoolSize", state.getMaximumPoolSize());
            summary.put("poolSize", state.getPoolSize());
            summary.put("activeCount", state.getActiveCount());
            summary.put("queueSize", state.getQueueSize());
            summary.put("activePercent", state.getActivePercent());
            summary.put("queueUsagePercent", state.getQueueUsagePercent());
        }
        
        return summary;
    }

    /**
     * Get list of supported adapter names
     */
    public List<String> getSupportedAdapters() {
        return adapters.stream()
                .map(WebContainerThreadPoolAdapter::getAdapterName)
                .toList();
    }
}
