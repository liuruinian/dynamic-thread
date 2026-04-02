package com.dynamic.thread.core.config;

import com.dynamic.thread.core.enums.QueueTypeEnum;
import com.dynamic.thread.core.enums.RejectedPolicyEnum;
import com.dynamic.thread.core.reject.RetryBufferConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thread pool configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolConfig {

    /**
     * Thread pool unique identifier
     */
    private String threadPoolId;

    /**
     * Core pool size
     */
    @Builder.Default
    private Integer corePoolSize = 10;

    /**
     * Maximum pool size
     */
    @Builder.Default
    private Integer maximumPoolSize = 20;

    /**
     * Queue capacity
     */
    @Builder.Default
    private Integer queueCapacity = 1000;

    /**
     * Work queue type
     */
    @Builder.Default
    private String workQueue = QueueTypeEnum.RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE.getName();

    /**
     * Rejected execution handler
     */
    @Builder.Default
    private String rejectedHandler = RejectedPolicyEnum.ABORT_POLICY.getName();

    /**
     * Keep alive time in seconds
     */
    @Builder.Default
    private Long keepAliveTime = 60L;

    /**
     * Allow core thread timeout
     */
    @Builder.Default
    private Boolean allowCoreThreadTimeOut = false;

    /**
     * Thread name prefix
     */
    private String threadNamePrefix;

    /**
     * Notification configuration
     */
    private NotifyConfig notify;

    /**
     * Alarm configuration
     */
    private AlarmConfig alarm;

    /**
     * Configuration for RetryBufferPolicy.
     * Only used when rejectedHandler is "RetryBufferPolicy".
     */
    private RetryBufferConfig retryBufferConfig;

    /**
     * Create default configuration with thread pool id
     */
    public static ThreadPoolConfig defaultConfig(String threadPoolId) {
        return ThreadPoolConfig.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(10)
                .maximumPoolSize(20)
                .queueCapacity(1000)
                .keepAliveTime(60L)
                .allowCoreThreadTimeOut(false)
                .workQueue(QueueTypeEnum.RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE.getName())
                .rejectedHandler(RejectedPolicyEnum.ABORT_POLICY.getName())
                .alarm(AlarmConfig.builder().build())
                .build();
    }
}
