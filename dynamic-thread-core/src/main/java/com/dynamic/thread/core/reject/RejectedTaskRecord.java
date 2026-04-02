package com.dynamic.thread.core.reject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Record of a rejected task with detailed information.
 * Used for statistics tracking and alarm notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectedTaskRecord {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Unique record identifier
     */
    private String id;

    /**
     * Thread pool identifier
     */
    private String threadPoolId;

    /**
     * Rejected task class name
     */
    private String taskClassName;

    /**
     * Task description (from toString if available)
     */
    private String taskDescription;

    /**
     * Rejection policy name (e.g., AbortPolicy, CallerRunsPolicy)
     */
    private String rejectedPolicy;

    /**
     * Rejection reason or exception message
     */
    private String reason;

    /**
     * Thread pool core size at rejection time
     */
    private Integer corePoolSize;

    /**
     * Thread pool maximum size at rejection time
     */
    private Integer maximumPoolSize;

    /**
     * Thread pool active count at rejection time
     */
    private Integer activeCount;

    /**
     * Queue size at rejection time
     */
    private Integer queueSize;

    /**
     * Queue capacity at rejection time
     */
    private Integer queueCapacity;

    /**
     * Whether the executor was shutting down
     */
    private Boolean executorShutdown;

    /**
     * Thread name that submitted the task
     */
    private String submitterThread;

    /**
     * Rejection timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Total rejected count at this point
     */
    private Long totalRejectedCount;

    /**
     * Get formatted timestamp
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(FORMATTER) : "";
    }

    /**
     * Get a summary of the rejection for logging/notification
     */
    public String getSummary() {
        return String.format(
                "[%s] Task rejected: %s, Policy: %s, Pool: %d/%d, Queue: %d/%d, Reason: %s",
                threadPoolId,
                taskClassName != null ? taskClassName : "unknown",
                rejectedPolicy != null ? rejectedPolicy : "unknown",
                activeCount != null ? activeCount : 0,
                maximumPoolSize != null ? maximumPoolSize : 0,
                queueSize != null ? queueSize : 0,
                queueCapacity != null ? queueCapacity : 0,
                reason != null ? reason : "capacity exceeded"
        );
    }

    /**
     * Get detailed rejection information for alarm notification
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Thread Pool: ").append(threadPoolId).append("\n");
        sb.append("Rejected Task: ").append(taskClassName).append("\n");
        sb.append("Rejection Policy: ").append(rejectedPolicy).append("\n");
        sb.append("Pool Status: ").append(activeCount).append("/").append(maximumPoolSize).append(" threads active\n");
        sb.append("Queue Status: ").append(queueSize).append("/").append(queueCapacity).append(" tasks queued\n");
        sb.append("Submitter Thread: ").append(submitterThread).append("\n");
        sb.append("Executor Shutdown: ").append(executorShutdown != null ? executorShutdown : false).append("\n");
        sb.append("Timestamp: ").append(getFormattedTimestamp()).append("\n");
        sb.append("Total Rejected: ").append(totalRejectedCount);
        return sb.toString();
    }
}
