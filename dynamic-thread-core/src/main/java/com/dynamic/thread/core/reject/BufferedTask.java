package com.dynamic.thread.core.reject;

import lombok.Getter;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper class for buffered rejected tasks.
 * Stores the original task along with metadata for retry scheduling.
 *
 * <p>This class is used by {@link RetryBufferPolicy} to track rejected tasks
 * that are waiting to be re-submitted to the thread pool.</p>
 */
@Getter
public class BufferedTask {

    /**
     * The original runnable task that was rejected
     */
    private final Runnable task;

    /**
     * The executor that rejected the task
     */
    private final ThreadPoolExecutor executor;

    /**
     * Timestamp when the task was buffered (System.currentTimeMillis())
     */
    private final long bufferTime;

    /**
     * Number of retry attempts
     */
    private final AtomicInteger retryCount;

    /**
     * Task class name for logging/monitoring
     */
    private final String taskClassName;

    /**
     * Creates a new BufferedTask.
     *
     * @param task     the rejected task
     * @param executor the executor that rejected the task
     */
    public BufferedTask(Runnable task, ThreadPoolExecutor executor) {
        this.task = task;
        this.executor = executor;
        this.bufferTime = System.currentTimeMillis();
        this.retryCount = new AtomicInteger(0);
        this.taskClassName = task != null ? task.getClass().getSimpleName() : "unknown";
    }

    /**
     * Increment and return the retry count.
     *
     * @return the new retry count after increment
     */
    public int incrementRetryCount() {
        return retryCount.incrementAndGet();
    }

    /**
     * Get the current retry count.
     *
     * @return the current retry count
     */
    public int getRetryCount() {
        return retryCount.get();
    }

    /**
     * Calculate how long the task has been in the buffer.
     *
     * @return time in buffer in milliseconds
     */
    public long getTimeInBuffer() {
        return System.currentTimeMillis() - bufferTime;
    }

    /**
     * Check if the task has expired based on the given max buffer time.
     *
     * @param maxBufferTimeMs maximum allowed time in buffer
     * @return true if the task has been in buffer longer than maxBufferTimeMs
     */
    public boolean isExpired(long maxBufferTimeMs) {
        return getTimeInBuffer() > maxBufferTimeMs;
    }

    /**
     * Check if the task has exceeded the maximum retry count.
     *
     * @param maxRetryCount maximum allowed retries
     * @return true if retry count exceeds the limit
     */
    public boolean hasExceededMaxRetries(int maxRetryCount) {
        return retryCount.get() > maxRetryCount;
    }

    @Override
    public String toString() {
        return String.format("BufferedTask{task=%s, bufferTime=%d, retryCount=%d, timeInBuffer=%dms}",
                taskClassName, bufferTime, retryCount.get(), getTimeInBuffer());
    }
}
