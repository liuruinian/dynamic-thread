package com.dynamic.thread.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Notification platform enumeration
 */
@Getter
@AllArgsConstructor
public enum NotifyPlatformEnum {

    /**
     * DingTalk
     */
    DING("DING", "DingTalk"),

    /**
     * WeChat Work
     */
    WECHAT("WECHAT", "WeChat Work"),

    /**
     * Lark/Feishu
     */
    LARK("LARK", "Lark"),

    /**
     * Email
     */
    EMAIL("EMAIL", "Email"),

    /**
     * Webhook (Generic HTTP callback)
     */
    WEBHOOK("WEBHOOK", "Webhook");

    private final String code;
    private final String description;

    public static NotifyPlatformEnum of(String code) {
        for (NotifyPlatformEnum platform : values()) {
            if (platform.getCode().equalsIgnoreCase(code)) {
                return platform;
            }
        }
        return null;
    }
}
