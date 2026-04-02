package com.dynamic.thread.core.exception;

/**
 * Exception thrown when notification sending fails.
 * This includes errors from DingTalk, WeChatWork, Webhook, etc.
 */
public class NotifyException extends DynamicThreadException {

    private static final long serialVersionUID = 1L;

    private final String platformType;

    public NotifyException(String message) {
        super(message);
        this.platformType = null;
    }

    public NotifyException(String message, String platformType) {
        super(message);
        this.platformType = platformType;
    }

    public NotifyException(String message, Throwable cause) {
        super(message, cause);
        this.platformType = null;
    }

    public NotifyException(String message, String platformType, Throwable cause) {
        super(message, cause);
        this.platformType = platformType;
    }

    /**
     * Get the notification platform type that failed.
     *
     * @return the platform type (e.g., "DINGTALK", "WECHAT", "WEBHOOK")
     */
    public String getPlatformType() {
        return platformType;
    }
}
