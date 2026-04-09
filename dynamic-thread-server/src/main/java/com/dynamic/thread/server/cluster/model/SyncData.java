package com.dynamic.thread.server.cluster.model;

import com.dynamic.thread.core.model.ThreadPoolState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data structure for cluster state synchronization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncData {

    /**
     * Source node ID that owns this data
     */
    private String sourceNodeId;

    /**
     * Agent instance ID
     */
    private String instanceId;

    /**
     * Application ID
     */
    private String appId;

    /**
     * Thread pool IDs registered by this agent
     */
    private List<String> threadPoolIds;

    /**
     * Thread pool states
     */
    private List<ThreadPoolState> states;

    /**
     * Data timestamp for conflict resolution (latest wins)
     */
    private long timestamp;

    /**
     * Sync operation type
     */
    private SyncType type;

    /**
     * Sync operation types
     */
    public enum SyncType {
        REGISTER,
        UNREGISTER,
        STATE_UPDATE
    }
}
