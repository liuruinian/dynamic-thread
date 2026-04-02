package com.dynamic.thread.core.enums;

import com.dynamic.thread.core.reject.RetryBufferConfig;
import com.dynamic.thread.core.reject.RetryBufferPolicy;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Rejected execution policy enumeration.
 * 
 * <p>Supported policies:</p>
 * <ul>
 *   <li>{@link #ABORT_POLICY} - Throws RejectedExecutionException</li>
 *   <li>{@link #CALLER_RUNS_POLICY} - Runs the task in the caller's thread</li>
 *   <li>{@link #DISCARD_OLDEST_POLICY} - Discards the oldest unhandled request</li>
 *   <li>{@link #DISCARD_POLICY} - Silently discards the rejected task</li>
 *   <li>{@link #RETRY_BUFFER_POLICY} - Buffers rejected tasks and retries when pool is idle</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum RejectedPolicyEnum {

    /**
     * Abort policy - throws RejectedExecutionException
     */
    ABORT_POLICY("AbortPolicy", new ThreadPoolExecutor.AbortPolicy()),

    /**
     * Caller runs policy - runs the task in the caller's thread
     */
    CALLER_RUNS_POLICY("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy()),

    /**
     * Discard oldest policy - discards the oldest unhandled request
     */
    DISCARD_OLDEST_POLICY("DiscardOldestPolicy", new ThreadPoolExecutor.DiscardOldestPolicy()),

    /**
     * Discard policy - silently discards the rejected task
     */
    DISCARD_POLICY("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy()),

    /**
     * Retry buffer policy - buffers rejected tasks and retries when pool becomes idle.
     * When buffer is full or task expires, falls back to CallerRunsPolicy.
     * Note: handler is null here, use createHandler() with config for this policy.
     */
    RETRY_BUFFER_POLICY("RetryBufferPolicy", null);

    private final String name;
    private final RejectedExecutionHandler handler;

    /**
     * Find policy enum by name.
     *
     * @param name policy name
     * @return the matching policy, or ABORT_POLICY if not found
     */
    public static RejectedPolicyEnum of(String name) {
        for (RejectedPolicyEnum policy : values()) {
            if (policy.getName().equalsIgnoreCase(name)) {
                return policy;
            }
        }
        return ABORT_POLICY;
    }

    /**
     * Get handler by policy name.
     * Note: For RETRY_BUFFER_POLICY, this returns a default-configured instance.
     * Use {@link #createHandler(String, RetryBufferConfig)} for custom configuration.
     *
     * @param name policy name
     * @return the rejection handler
     */
    public static RejectedExecutionHandler getHandler(String name) {
        RejectedPolicyEnum policy = of(name);
        if (policy == RETRY_BUFFER_POLICY) {
            return new RetryBufferPolicy();
        }
        return policy != null ? policy.getHandler() : new ThreadPoolExecutor.AbortPolicy();
    }

    /**
     * Create a handler with custom configuration.
     * This is primarily for RetryBufferPolicy which requires configuration.
     *
     * @param name              policy name
     * @param retryBufferConfig configuration for RetryBufferPolicy (can be null for other policies)
     * @return the rejection handler
     */
    public static RejectedExecutionHandler createHandler(String name, RetryBufferConfig retryBufferConfig) {
        RejectedPolicyEnum policy = of(name);
        if (policy == RETRY_BUFFER_POLICY) {
            return retryBufferConfig != null 
                    ? new RetryBufferPolicy(retryBufferConfig) 
                    : new RetryBufferPolicy();
        }
        return policy != null ? policy.getHandler() : new ThreadPoolExecutor.AbortPolicy();
    }

    /**
     * Check if the given policy name is RetryBufferPolicy.
     *
     * @param name policy name
     * @return true if it's RetryBufferPolicy
     */
    public static boolean isRetryBufferPolicy(String name) {
        return RETRY_BUFFER_POLICY.getName().equalsIgnoreCase(name);
    }
}
