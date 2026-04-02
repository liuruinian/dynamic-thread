package com.dynamic.thread.core.reject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A rejection policy that buffers rejected tasks and retries them when the thread pool becomes idle.
 * 
 * <p>When a task is rejected by the thread pool, instead of immediately failing or discarding,
 * this policy:</p>
 * <ol>
 *   <li>Buffers the task in a bounded queue</li>
 *   <li>Monitors the thread pool for idle capacity</li>
 *   <li>Re-submits buffered tasks when the pool has available threads</li>
 *   <li>Falls back to CallerRunsPolicy if buffer is full, task expires, or max retries exceeded</li>
 * </ol>
 *
 * <p>This policy is useful for scenarios where:</p>
 * <ul>
 *   <li>Temporary bursts of tasks may exceed pool capacity</li>
 *   <li>Tasks should not be lost or immediately rejected</li>
 *   <li>The system can tolerate some delay in task execution</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * RetryBufferConfig config = RetryBufferConfig.builder()
 *     .bufferCapacity(500)
 *     .idleThreshold(0.3)
 *     .build();
 * RetryBufferPolicy policy = new RetryBufferPolicy(config);
 * 
 * ThreadPoolExecutor executor = new ThreadPoolExecutor(
 *     10, 20, 60, TimeUnit.SECONDS,
 *     new LinkedBlockingQueue&lt;&gt;(100),
 *     policy
 * );
 * </pre>
 */
@Slf4j
public class RetryBufferPolicy implements RejectedExecutionHandler {

    /**
     * Configuration for this policy
     */
    @Getter
    private final RetryBufferConfig config;

    /**
     * Bounded buffer queue for rejected tasks
     */
    private final BlockingQueue<BufferedTask> taskBuffer;

    /**
     * Scheduler for retry operations
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Fallback policy when buffer is full or task expires
     */
    private final ThreadPoolExecutor.CallerRunsPolicy fallbackPolicy;

    /**
     * Flag indicating if the policy is running
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    // ==================== Statistics ====================

    /**
     * Total number of tasks that have been buffered
     */
    @Getter
    private final AtomicLong totalBufferedCount = new AtomicLong(0);

    /**
     * Total number of tasks successfully re-submitted
     */
    @Getter
    private final AtomicLong totalRetriedCount = new AtomicLong(0);

    /**
     * Total number of tasks executed via fallback (CallerRunsPolicy)
     */
    @Getter
    private final AtomicLong totalFallbackCount = new AtomicLong(0);

    /**
     * Total number of tasks that expired in buffer
     */
    @Getter
    private final AtomicLong totalExpiredCount = new AtomicLong(0);

    /**
     * Creates a RetryBufferPolicy with default configuration.
     */
    public RetryBufferPolicy() {
        this(RetryBufferConfig.defaultConfig());
    }

    /**
     * Creates a RetryBufferPolicy with the specified configuration.
     *
     * @param config the configuration for this policy
     */
    public RetryBufferPolicy(RetryBufferConfig config) {
        if (config == null) {
            config = RetryBufferConfig.defaultConfig();
        }
        config.validate();
        
        this.config = config;
        this.taskBuffer = new LinkedBlockingQueue<>(config.getBufferCapacity());
        this.fallbackPolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RetryBufferPolicy-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        
        startRetryScheduler();
        
        if (config.isEnableLogging()) {
            log.info("[RetryBufferPolicy] Initialized with config: bufferCapacity={}, retryInterval={}ms, " +
                    "maxRetryCount={}, maxBufferTime={}ms, idleThreshold={}",
                    config.getBufferCapacity(), config.getRetryIntervalMs(),
                    config.getMaxRetryCount(), config.getMaxBufferTimeMs(), config.getIdleThreshold());
        }
    }

    /**
     * Handle a rejected task by buffering it for later retry.
     * If the buffer is full, falls back to CallerRunsPolicy.
     *
     * @param r        the runnable task
     * @param executor the executor that rejected the task
     */
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        if (!running.get()) {
            // Policy is shutting down, use fallback immediately
            executeFallback(r, executor, "policy shutting down");
            return;
        }

        if (executor.isShutdown()) {
            // Executor is shutting down, use fallback
            executeFallback(r, executor, "executor shutdown");
            return;
        }

        BufferedTask bufferedTask = new BufferedTask(r, executor);
        
        // Try to add to buffer
        boolean buffered = taskBuffer.offer(bufferedTask);
        
        if (buffered) {
            totalBufferedCount.incrementAndGet();
            if (config.isEnableLogging()) {
                log.debug("[RetryBufferPolicy] Task buffered: {}, buffer size: {}/{}",
                        bufferedTask.getTaskClassName(), taskBuffer.size(), config.getBufferCapacity());
            }
        } else {
            // Buffer is full, execute via fallback
            executeFallback(r, executor, "buffer full");
        }
    }

    /**
     * Start the scheduler that monitors the buffer and retries tasks.
     */
    private void startRetryScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) {
                return;
            }

            try {
                processBufferedTasks();
            } catch (Exception e) {
                log.error("[RetryBufferPolicy] Error processing buffered tasks", e);
            }
        }, config.getRetryIntervalMs(), config.getRetryIntervalMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Process all buffered tasks, attempting to resubmit them.
     */
    private void processBufferedTasks() {
        int processed = 0;
        int maxBatchSize = Math.min(taskBuffer.size(), 100); // Process up to 100 tasks per cycle

        while (processed < maxBatchSize && !taskBuffer.isEmpty()) {
            BufferedTask task = taskBuffer.poll();
            if (task == null) {
                break;
            }

            processed++;
            processTask(task);
        }
    }

    /**
     * Process a single buffered task.
     */
    private void processTask(BufferedTask task) {
        ThreadPoolExecutor executor = task.getExecutor();

        // Check if task has expired
        if (task.isExpired(config.getMaxBufferTimeMs())) {
            totalExpiredCount.incrementAndGet();
            executeFallback(task.getTask(), executor, "task expired after " + task.getTimeInBuffer() + "ms");
            return;
        }

        // Check if executor is still valid
        if (executor.isShutdown() || executor.isTerminated()) {
            executeFallback(task.getTask(), executor, "executor no longer available");
            return;
        }

        // Check if the pool is idle enough to accept new tasks
        if (isPoolIdle(executor)) {
            try {
                executor.execute(task.getTask());
                totalRetriedCount.incrementAndGet();
                if (config.isEnableLogging()) {
                    log.debug("[RetryBufferPolicy] Task resubmitted successfully: {}, retries: {}",
                            task.getTaskClassName(), task.getRetryCount());
                }
            } catch (RejectedExecutionException e) {
                // Still rejected, handle retry
                handleRetryFailure(task);
            }
        } else {
            // Pool not idle, re-queue the task
            handleRetryFailure(task);
        }
    }

    /**
     * Handle a failed retry attempt.
     */
    private void handleRetryFailure(BufferedTask task) {
        task.incrementRetryCount();

        if (task.hasExceededMaxRetries(config.getMaxRetryCount())) {
            // Max retries exceeded, use fallback
            executeFallback(task.getTask(), task.getExecutor(), 
                    "max retries exceeded (" + task.getRetryCount() + ")");
        } else if (task.isExpired(config.getMaxBufferTimeMs())) {
            // Task expired during retry
            totalExpiredCount.incrementAndGet();
            executeFallback(task.getTask(), task.getExecutor(), 
                    "expired during retry after " + task.getTimeInBuffer() + "ms");
        } else {
            // Re-queue for another attempt
            boolean requeued = taskBuffer.offer(task);
            if (!requeued) {
                // Buffer full, use fallback
                executeFallback(task.getTask(), task.getExecutor(), "buffer full during re-queue");
            }
        }
    }

    /**
     * Execute a task using the fallback CallerRunsPolicy.
     */
    private void executeFallback(Runnable task, ThreadPoolExecutor executor, String reason) {
        totalFallbackCount.incrementAndGet();
        if (config.isEnableLogging()) {
            log.info("[RetryBufferPolicy] Executing via CallerRunsPolicy, reason: {}", reason);
        }
        
        try {
            fallbackPolicy.rejectedExecution(task, executor);
        } catch (Exception e) {
            log.error("[RetryBufferPolicy] Fallback execution failed", e);
        }
    }

    /**
     * Check if the thread pool has idle capacity.
     *
     * @param executor the thread pool executor
     * @return true if the pool is idle enough to accept new tasks
     */
    private boolean isPoolIdle(ThreadPoolExecutor executor) {
        int activeCount = executor.getActiveCount();
        int maximumPoolSize = executor.getMaximumPoolSize();
        int queueSize = executor.getQueue().size();
        int queueCapacity = executor.getQueue().remainingCapacity() + queueSize;

        // Check thread idle ratio
        double activeRatio = (double) activeCount / maximumPoolSize;
        boolean threadIdle = activeRatio < config.getIdleThreshold();

        // Also check if queue has space
        boolean queueHasSpace = queueSize < queueCapacity;

        return threadIdle && queueHasSpace;
    }

    // ==================== Lifecycle Management ====================

    /**
     * Shutdown this policy gracefully.
     * Remaining buffered tasks will be executed via CallerRunsPolicy.
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return; // Already shutdown
        }

        if (config.isEnableLogging()) {
            log.info("[RetryBufferPolicy] Shutting down, processing {} remaining buffered tasks",
                    taskBuffer.size());
        }

        // Stop the scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Execute remaining tasks via fallback
        BufferedTask task;
        while ((task = taskBuffer.poll()) != null) {
            try {
                executeFallback(task.getTask(), task.getExecutor(), "policy shutdown");
            } catch (Exception e) {
                log.error("[RetryBufferPolicy] Error executing task during shutdown", e);
            }
        }

        if (config.isEnableLogging()) {
            log.info("[RetryBufferPolicy] Shutdown complete. Stats: buffered={}, retried={}, fallback={}, expired={}",
                    totalBufferedCount.get(), totalRetriedCount.get(), 
                    totalFallbackCount.get(), totalExpiredCount.get());
        }
    }

    /**
     * Check if this policy is still running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    // ==================== Monitoring API ====================

    /**
     * Get the current number of tasks in the buffer.
     *
     * @return current buffer size
     */
    public int getBufferedTaskCount() {
        return taskBuffer.size();
    }

    /**
     * Get the remaining capacity of the buffer.
     *
     * @return remaining capacity
     */
    public int getRemainingBufferCapacity() {
        return taskBuffer.remainingCapacity();
    }

    /**
     * Get statistics summary as a map.
     *
     * @return statistics map
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("bufferCapacity", config.getBufferCapacity());
        stats.put("currentBufferSize", taskBuffer.size());
        stats.put("remainingCapacity", taskBuffer.remainingCapacity());
        stats.put("totalBuffered", totalBufferedCount.get());
        stats.put("totalRetried", totalRetriedCount.get());
        stats.put("totalFallback", totalFallbackCount.get());
        stats.put("totalExpired", totalExpiredCount.get());
        stats.put("running", running.get());
        return stats;
    }

    /**
     * Reset all statistics counters.
     */
    public void resetStatistics() {
        totalBufferedCount.set(0);
        totalRetriedCount.set(0);
        totalFallbackCount.set(0);
        totalExpiredCount.set(0);
    }

    @Override
    public String toString() {
        return String.format("RetryBufferPolicy{buffer=%d/%d, retried=%d, fallback=%d, running=%s}",
                taskBuffer.size(), config.getBufferCapacity(),
                totalRetriedCount.get(), totalFallbackCount.get(), running.get());
    }
}
