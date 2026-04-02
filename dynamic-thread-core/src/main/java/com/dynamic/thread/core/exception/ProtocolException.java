package com.dynamic.thread.core.exception;

/**
 * Exception thrown when protocol communication fails.
 * This includes Netty message encoding/decoding and network communication errors.
 */
public class ProtocolException extends DynamicThreadException {

    private static final long serialVersionUID = 1L;

    private final String messageType;

    public ProtocolException(String message) {
        super(message);
        this.messageType = null;
    }

    public ProtocolException(String message, String messageType) {
        super(message);
        this.messageType = messageType;
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
        this.messageType = null;
    }

    public ProtocolException(String message, String messageType, Throwable cause) {
        super(message, cause);
        this.messageType = messageType;
    }

    /**
     * Get the message type that caused the protocol error.
     *
     * @return the message type
     */
    public String getMessageType() {
        return messageType;
    }
}
