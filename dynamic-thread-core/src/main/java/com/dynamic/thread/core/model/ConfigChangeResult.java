package com.dynamic.thread.core.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of thread pool configuration change detection.
 * Contains information about what parameters changed.
 */
@Data
@Builder
public class ConfigChangeResult {

    /**
     * Whether any configuration actually changed
     */
    private boolean changed;

    /**
     * Thread pool identifier
     */
    private String threadPoolId;

    /**
     * List of changed parameter descriptions
     */
    @Builder.Default
    private List<String> changes = new ArrayList<>();

    /**
     * Create a no-change result
     */
    public static ConfigChangeResult noChange(String threadPoolId) {
        return ConfigChangeResult.builder()
                .threadPoolId(threadPoolId)
                .changed(false)
                .build();
    }

    /**
     * Add a change record
     */
    public void addChange(String paramName, Object oldValue, Object newValue) {
        if (changes == null) {
            changes = new ArrayList<>();
        }
        changes.add(String.format("%s: %s -> %s", paramName, oldValue, newValue));
        this.changed = true;
    }

    /**
     * Get formatted change summary
     */
    public String getSummary() {
        if (!changed || changes == null || changes.isEmpty()) {
            return "No changes";
        }
        return String.join(", ", changes);
    }
}
