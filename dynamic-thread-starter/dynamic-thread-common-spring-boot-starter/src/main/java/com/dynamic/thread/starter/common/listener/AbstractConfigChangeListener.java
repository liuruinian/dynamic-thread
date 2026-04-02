package com.dynamic.thread.starter.common.listener;

import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract configuration change listener.
 * Subclasses should implement platform-specific listening logic.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractConfigChangeListener {

    protected final ThreadPoolRefresher refresher;

    /**
     * Handle configuration change event
     *
     * @param configContent the new configuration content
     * @param configType    the configuration type (YAML, PROPERTIES, JSON)
     */
    public void onConfigChange(String configContent, String configType) {
        log.info("Configuration changed, refreshing thread pools...");
        try {
            refresher.refresh(configContent, configType);
            log.info("Thread pools refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh thread pools", e);
        }
    }

    /**
     * Start listening for configuration changes.
     * Subclasses should implement platform-specific logic.
     */
    public abstract void startListening();

    /**
     * Stop listening for configuration changes.
     */
    public abstract void stopListening();
}
