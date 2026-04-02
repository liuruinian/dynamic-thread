package com.dynamic.thread.core.reject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for {@link RetryBufferPolicy}.
 * 
 * <p>This class defines all configurable parameters for the retry buffer
 * rejection policy, including buffer capacity, retry intervals, and thresholds.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * RetryBufferConfig config = RetryBufferConfig.builder()
 *     .bufferCapacity(500)
 *     .retryIntervalMs(200)
 *     .maxRetryCount(5)
 *     .maxBufferTimeMs(60000)
 *     .idleThreshold(0.3)
 *     .build();
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryBufferConfig {

    /**
     * Maximum number of tasks that can be buffered.
     * When buffer is full, tasks will be executed using CallerRunsPolicy.
     * Default: 1000
     */
    @Builder.Default
    private int bufferCapacity = 1000;

    /**
     * Interval in milliseconds between retry attempts.
     * The scheduler will check for buffered tasks at this interval.
     * Default: 100ms
     */
    @Builder.Default
    private long retryIntervalMs = 100;

    /**
     * Maximum number of retry attempts for a single task.
     * After exceeding this limit, the task will be executed using CallerRunsPolicy.
     * Default: 3
     */
    @Builder.Default
    private int maxRetryCount = 3;

    /**
     * Maximum time in milliseconds a task can stay in the buffer.
     * Expired tasks will be executed using CallerRunsPolicy.
     * Default: 30000ms (30 seconds)
     */
    @Builder.Default
    private long maxBufferTimeMs = 30000;

    /**
     * Thread pool idle threshold ratio (0.0 to 1.0).
     * When activeCount/maximumPoolSize is below this threshold, 
     * the pool is considered idle and buffered tasks can be resubmitted.
     * Default: 0.5 (50%)
     */
    @Builder.Default
    private double idleThreshold = 0.5;

    /**
     * Whether to enable logging for retry operations.
     * Default: true
     */
    @Builder.Default
    private boolean enableLogging = true;

    /**
     * Validate the configuration values.
     *
     * @throws IllegalArgumentException if any value is invalid
     */
    public void validate() {
        if (bufferCapacity <= 0) {
            throw new IllegalArgumentException("bufferCapacity must be positive");
        }
        if (retryIntervalMs <= 0) {
            throw new IllegalArgumentException("retryIntervalMs must be positive");
        }
        if (maxRetryCount < 0) {
            throw new IllegalArgumentException("maxRetryCount cannot be negative");
        }
        if (maxBufferTimeMs <= 0) {
            throw new IllegalArgumentException("maxBufferTimeMs must be positive");
        }
        if (idleThreshold <= 0 || idleThreshold > 1) {
            throw new IllegalArgumentException("idleThreshold must be between 0 and 1 (exclusive of 0)");
        }
    }

    /**
     * Create a default configuration.
     *
     * @return default configuration instance
     */
    public static RetryBufferConfig defaultConfig() {
        return RetryBufferConfig.builder().build();
    }
}
