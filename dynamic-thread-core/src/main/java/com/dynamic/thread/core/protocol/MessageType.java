package com.dynamic.thread.core.protocol;

/**
 * Message types for Netty communication between Agent and Server.
 */
public enum MessageType {

    /**
     * Agent registration message
     */
    REGISTER((byte) 1),

    /**
     * Heartbeat message
     */
    HEARTBEAT((byte) 2),

    /**
     * Thread pool state report
     */
    STATE_REPORT((byte) 3),

    /**
     * Configuration update command from server
     */
    CONFIG_UPDATE((byte) 4),

    /**
     * Response message
     */
    RESPONSE((byte) 5),

    /**
     * Agent unregister message
     */
    UNREGISTER((byte) 6),

    /**
     * Reset rejection statistics command from server
     */
    RESET_REJECT_STATS((byte) 7),

    /**
     * Web container configuration update command from server
     */
    WEB_CONTAINER_CONFIG_UPDATE((byte) 8),

    // ==================== Cluster Messages ====================

    /**
     * Cluster node heartbeat
     */
    CLUSTER_HEARTBEAT((byte) 20),

    /**
     * Cluster full state sync request/response
     */
    CLUSTER_FULL_SYNC((byte) 21),

    /**
     * Cluster incremental state sync
     */
    CLUSTER_INCREMENTAL_SYNC((byte) 22),

    /**
     * Cluster config forward (relay config update to target node)
     */
    CLUSTER_CONFIG_FORWARD((byte) 23),

    /**
     * Cluster node join notification
     */
    CLUSTER_NODE_JOIN((byte) 24),

    /**
     * Cluster node leave notification
     */
    CLUSTER_NODE_LEAVE((byte) 25),

    /**
     * Cluster alarm rule sync
     */
    CLUSTER_ALARM_SYNC((byte) 26);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
