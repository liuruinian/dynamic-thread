package com.dynamic.thread.core.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Notification configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyConfig {

    /**
     * Receivers (user IDs, phone numbers, or email addresses)
     */
    private String receives;

    /**
     * Notification interval in seconds
     */
    @Builder.Default
    private Integer interval = 60;

    /**
     * Parse receivers into list
     */
    public List<String> getReceiveList() {
        if (receives == null || receives.isBlank()) {
            return List.of();
        }
        return List.of(receives.split(","));
    }
}
