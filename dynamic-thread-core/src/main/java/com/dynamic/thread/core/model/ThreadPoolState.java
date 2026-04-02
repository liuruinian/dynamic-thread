package com.dynamic.thread.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Thread pool runtime state snapshot
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolState {

    /**
     * Thread pool unique identifier
     */
    private String threadPoolId;

    /**
     * Core pool size
     */
    private Integer corePoolSize;

    /**
     * Maximum pool size
     */
    private Integer maximumPoolSize;

    /**
     * Current pool size
     */
    private Integer poolSize;

    /**
     * Active thread count
     */
    private Integer activeCount;

    /**
     * Largest pool size ever reached
     */
    private Integer largestPoolSize;

    /**
     * Queue capacity
     */
    private Integer queueCapacity;

    /**
     * Current queue size
     */
    private Integer queueSize;

    /**
     * Remaining queue capacity
     */
    private Integer queueRemainingCapacity;

    /**
     * Completed task count
     */
    private Long completedTaskCount;

    /**
     * Total task count (including queued tasks)
     */
    private Long taskCount;

    /**
     * Rejected task count
     */
    private Long rejectedCount;

    /**
     * Keep alive time in seconds
     */
    private Long keepAliveTime;

    /**
     * Whether allow core thread timeout
     */
    private Boolean allowCoreThreadTimeOut;

    /**
     * Rejected handler class name
     */
    private String rejectedHandler;

    // ==================== RetryBufferPolicy Statistics ====================

    /**
     * Whether the rejected handler is RetryBufferPolicy
     */
    private Boolean retryBufferEnabled;

    /**
     * Current number of tasks in the retry buffer
     */
    private Integer retryBufferSize;

    /**
     * Retry buffer capacity
     */
    private Integer retryBufferCapacity;

    /**
     * Total number of tasks that have been buffered
     */
    private Long retryBufferTotalBuffered;

    /**
     * Total number of tasks successfully re-submitted
     */
    private Long retryBufferTotalRetried;

    /**
     * Total number of tasks executed via fallback (CallerRunsPolicy)
     */
    private Long retryBufferTotalFallback;

    /**
     * Total number of tasks that expired in buffer
     */
    private Long retryBufferTotalExpired;

    /**
     * Retry buffer usage percentage (0-100)
     */
    private Double retryBufferUsagePercent;

    /**
     * Queue usage percentage (0-100)
     */
    private Double queueUsagePercent;

    /**
     * Active thread percentage (0-100)
     */
    private Double activePercent;

    /**
     * Snapshot timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Calculate queue usage percentage
     */
    public void calculateMetrics() {
        if (queueCapacity != null && queueCapacity > 0) {
            this.queueUsagePercent = (double) queueSize / queueCapacity * 100;
        } else {
            this.queueUsagePercent = 0.0;
        }

        if (maximumPoolSize != null && maximumPoolSize > 0) {
            this.activePercent = (double) activeCount / maximumPoolSize * 100;
        } else {
            this.activePercent = 0.0;
        }
    }
}
