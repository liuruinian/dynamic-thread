package com.dynamic.thread.core.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Base message class for Netty communication.
 * 
 * Message format:
 * +--------+--------+----------+------+
 * | Magic  | Type   | Length   | Body |
 * | 4bytes | 1byte  | 4bytes   | ...  |
 * +--------+--------+----------+------+
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Magic number for protocol identification
     */
    public static final int MAGIC_NUMBER = 0x44545001; // "DTP\1" - Dynamic Thread Pool v1

    /**
     * Message type
     */
    private MessageType type;

    /**
     * Message ID for request-response correlation
     */
    private String messageId;

    /**
     * Source application identifier
     */
    private String appId;

    /**
     * Source instance identifier
     */
    private String instanceId;

    /**
     * Timestamp
     */
    private long timestamp;

    /**
     * Message body (JSON serialized)
     */
    private String body;

    /**
     * Success flag for response messages
     */
    private Boolean success;

    /**
     * Error message for failed responses
     */
    private String errorMsg;

    /**
     * Create a heartbeat message
     */
    public static Message heartbeat(String appId, String instanceId) {
        return Message.builder()
                .type(MessageType.HEARTBEAT)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a register message
     */
    public static Message register(String appId, String instanceId, String body) {
        return Message.builder()
                .type(MessageType.REGISTER)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(body)
                .build();
    }

    /**
     * Create a state report message
     */
    public static Message stateReport(String appId, String instanceId, String body) {
        return Message.builder()
                .type(MessageType.STATE_REPORT)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(body)
                .build();
    }

    /**
     * Create a config update message
     */
    public static Message configUpdate(String appId, String instanceId, String body) {
        return Message.builder()
                .type(MessageType.CONFIG_UPDATE)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(body)
                .build();
    }

    /**
     * Create a response message
     */
    public static Message response(String messageId, boolean success, String errorMsg) {
        return Message.builder()
                .type(MessageType.RESPONSE)
                .messageId(messageId)
                .timestamp(System.currentTimeMillis())
                .success(success)
                .errorMsg(errorMsg)
                .build();
    }

    /**
     * Create a reset rejection statistics message
     * @param threadPoolId specific pool id to reset, or null/empty for all pools
     */
    public static Message resetRejectStats(String appId, String instanceId, String threadPoolId) {
        return Message.builder()
                .type(MessageType.RESET_REJECT_STATS)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(threadPoolId) // null or specific threadPoolId
                .build();
    }

    /**
     * Create a web container config update message
     * @param configJson JSON config with corePoolSize, maximumPoolSize, keepAliveTime
     */
    public static Message webContainerConfigUpdate(String appId, String instanceId, String configJson) {
        return Message.builder()
                .type(MessageType.WEB_CONTAINER_CONFIG_UPDATE)
                .messageId(generateMessageId())
                .appId(appId)
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(configJson)
                .build();
    }

    private static String generateMessageId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
