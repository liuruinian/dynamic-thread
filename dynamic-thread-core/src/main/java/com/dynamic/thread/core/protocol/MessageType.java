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
    WEB_CONTAINER_CONFIG_UPDATE((byte) 8);

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
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
