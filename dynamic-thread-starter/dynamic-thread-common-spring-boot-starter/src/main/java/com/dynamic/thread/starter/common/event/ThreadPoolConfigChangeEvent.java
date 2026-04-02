package com.dynamic.thread.starter.common.event;

import com.dynamic.thread.core.model.ConfigChangeResult;
import org.springframework.context.ApplicationEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring application event published when thread pool configuration changes.
 * Can be used by other components to react to configuration updates.
 */
public class ThreadPoolConfigChangeEvent extends ApplicationEvent {

    /**
     * List of change results for all updated thread pools
     */
    private final List<ConfigChangeResult> changeResults;

    /**
     * Configuration content type (YAML, PROPERTIES, JSON)
     */
    private final String configType;

    public ThreadPoolConfigChangeEvent(Object source, List<ConfigChangeResult> changeResults, String configType) {
        super(source);
        this.changeResults = changeResults != null ? changeResults : new ArrayList<>();
        this.configType = configType;
    }

    public List<ConfigChangeResult> getChangeResults() {
        return changeResults;
    }

    public String getConfigType() {
        return configType;
    }

    /**
     * Check if any thread pool actually changed
     */
    public boolean hasChanges() {
        return changeResults.stream().anyMatch(ConfigChangeResult::isChanged);
    }

    /**
     * Get count of changed thread pools
     */
    public int getChangedCount() {
        return (int) changeResults.stream().filter(ConfigChangeResult::isChanged).count();
    }

    /**
     * Get all thread pool IDs that changed
     */
    public List<String> getChangedThreadPoolIds() {
        return changeResults.stream()
                .filter(ConfigChangeResult::isChanged)
                .map(ConfigChangeResult::getThreadPoolId)
                .toList();
    }

    @Override
    public String toString() {
        return String.format("ThreadPoolConfigChangeEvent{changedCount=%d, configType='%s', timestamp=%d}",
                getChangedCount(), configType, getTimestamp());
    }
}
