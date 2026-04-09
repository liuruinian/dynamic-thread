package com.dynamic.thread.core.executor;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.config.AlarmConfig;
import com.dynamic.thread.core.config.NotifyConfig;
import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.model.ConfigChangeResult;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.queue.ResizableCapacityLinkedBlockingQueue;
import com.dynamic.thread.core.reject.RejectedExecutionHandlerProxy;
import com.dynamic.thread.core.reject.RejectedStatistics;
import com.dynamic.thread.core.reject.RejectedTaskListener;
import com.dynamic.thread.core.reject.RejectedTaskRecord;
import com.dynamic.thread.core.reject.RetryBufferConfig;
import com.dynamic.thread.core.reject.RetryBufferPolicy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dynamic thread pool executor with runtime parameter adjustment support.
 * Extends ThreadPoolExecutor to add dynamic configuration capabilities.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Dynamic configuration updates at runtime</li>
 *   <li>Real-time rejection statistics via dynamic proxy</li>
 *   <li>Pluggable rejection listeners for custom handling</li>
 *   <li>Integrated alarm system for threshold monitoring</li>
 * </ul>
 */
@Slf4j
public class DynamicThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * Thread pool unique identifier
     */
    @Getter
    private final String threadPoolId;

    /**
     * Rejected task count
     */
    private final AtomicLong rejectedCount = new AtomicLong(0);

    /**
     * Alarm configuration
     */
    @Getter
    @Setter
    private AlarmConfig alarmConfig;

    /**
     * Notification configuration
     */
    @Getter
    @Setter
    private NotifyConfig notifyConfig;

    /**
     * Rejected handler name for state reporting
     */
    @Getter
    private volatile String rejectedHandlerName = "AbortPolicy";

    /**
     * Rejection handler proxy for statistics and listeners
     */
    @Getter
    private volatile RejectedExecutionHandlerProxy rejectionProxy;

    /**
     * RetryBufferPolicy instance if using this rejection policy
     */
    @Getter
    private volatile RetryBufferPolicy retryBufferPolicy;

    /**
     * Last alarm timestamp
     */
    private volatile long lastAlarmTime = 0;

    /**
     * Creates a new DynamicThreadPoolExecutor with the given initial parameters.
     */
    public DynamicThreadPoolExecutor(String threadPoolId,
                                     int corePoolSize,
                                     int maximumPoolSize,
                                     long keepAliveTime,
                                     TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue,
                                     ThreadFactory threadFactory,
                                     RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        this.threadPoolId = threadPoolId;
    }

    /**
     * Creates a new DynamicThreadPoolExecutor with the given initial parameters.
     */
    public DynamicThreadPoolExecutor(String threadPoolId,
                                     int corePoolSize,
                                     int maximumPoolSize,
                                     long keepAliveTime,
                                     TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue,
                                     RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        this.threadPoolId = threadPoolId;
    }

    /**
     * Creates a new DynamicThreadPoolExecutor with the given initial parameters.
     */
    public DynamicThreadPoolExecutor(String threadPoolId,
                                     int corePoolSize,
                                     int maximumPoolSize,
                                     long keepAliveTime,
                                     TimeUnit unit,
                                     BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        this.threadPoolId = threadPoolId;
    }

    /**
     * Creates a DynamicThreadPoolExecutor from ThreadPoolConfig.
     * Uses dynamic proxy for rejection handling with statistics.
     */
    public static DynamicThreadPoolExecutor create(ThreadPoolConfig config) {
        BlockingQueue<Runnable> workQueue = createWorkQueue(config);
        ThreadFactory threadFactory = createThreadFactory(config);
        RejectedExecutionHandler handler = createRejectedHandler(config);

        String policyName = config.getRejectedHandler() != null 
                ? config.getRejectedHandler() : "AbortPolicy";

        DynamicThreadPoolExecutor executor = new DynamicThreadPoolExecutor(
                config.getThreadPoolId(),
                config.getCorePoolSize(),
                config.getMaximumPoolSize(),
                config.getKeepAliveTime(),
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                handler
        );

        executor.allowCoreThreadTimeOut(Boolean.TRUE.equals(config.getAllowCoreThreadTimeOut()));
        executor.setAlarmConfig(config.getAlarm());
        executor.setNotifyConfig(config.getNotify());
        executor.rejectedHandlerName = policyName;

        // Setup rejection proxy with statistics
        executor.setupRejectionProxy(handler, policyName);

        return executor;
    }

    /**
     * Setup rejection proxy with dynamic proxy wrapper.
     * This enables real-time rejection statistics and listener support.
     *
     * @param handler    the original rejection handler
     * @param policyName the policy name
     */
    private void setupRejectionProxy(RejectedExecutionHandler handler, String policyName) {
        // Save RetryBufferPolicy reference if applicable
        if (handler instanceof RetryBufferPolicy) {
            this.retryBufferPolicy = (RetryBufferPolicy) handler;
        } else {
            this.retryBufferPolicy = null;
        }

        // Create proxy wrapper
        this.rejectionProxy = RejectedExecutionHandlerProxy.wrap(handler, threadPoolId, policyName);
        
        // Set queue capacity supplier for accurate reporting
        BlockingQueue<Runnable> queue = getQueue();
        if (queue instanceof ResizableCapacityLinkedBlockingQueue) {
            ResizableCapacityLinkedBlockingQueue<?> resizableQueue = 
                    (ResizableCapacityLinkedBlockingQueue<?>) queue;
            rejectionProxy.setQueueCapacitySupplier(resizableQueue::getCapacity);
        }

        // Register with global statistics
        RejectedStatistics.getInstance().registerProxy(rejectionProxy);

        // Apply proxy handler
        super.setRejectedExecutionHandler(rejectionProxy.getProxyHandler());

        log.debug("[{}] Rejection proxy setup complete with policy: {}", threadPoolId, policyName);
    }

    /**
     * Creates the work queue based on configuration.
     */
    private static BlockingQueue<Runnable> createWorkQueue(ThreadPoolConfig config) {
        int capacity = config.getQueueCapacity() != null ? config.getQueueCapacity() : 1000;
        return new ResizableCapacityLinkedBlockingQueue<>(capacity);
    }

    /**
     * Creates the thread factory based on configuration.
     */
    private static ThreadFactory createThreadFactory(ThreadPoolConfig config) {
        String prefix = config.getThreadNamePrefix() != null 
                ? config.getThreadNamePrefix() 
                : "dynamic-pool-" + config.getThreadPoolId() + "-";
        
        AtomicLong threadNumber = new AtomicLong(0);
        return r -> {
            Thread thread = new Thread(r);
            thread.setName(prefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    /**
     * Creates the rejected execution handler based on configuration.
     */
    private static RejectedExecutionHandler createRejectedHandler(ThreadPoolConfig config) {
        String handlerName = config.getRejectedHandler();
        if (handlerName == null) {
            return new AbortPolicy();
        }

        switch (handlerName) {
            case "CallerRunsPolicy":
                return new CallerRunsPolicy();
            case "DiscardPolicy":
                return new DiscardPolicy();
            case "DiscardOldestPolicy":
                return new DiscardOldestPolicy();
            case "RetryBufferPolicy":
                // Use configuration if provided, otherwise use defaults
                RetryBufferConfig retryConfig = config.getRetryBufferConfig();
                return retryConfig != null 
                        ? new RetryBufferPolicy(retryConfig) 
                        : new RetryBufferPolicy();
            default:
                return new AbortPolicy();
        }
    }

    /**
     * Update thread pool configuration dynamically.
     * Only applies changes when values actually differ from current state.
     *
     * @param config the new configuration
     * @return result containing what changed
     */
    public ConfigChangeResult updateConfig(ThreadPoolConfig config) {
        ConfigChangeResult result = ConfigChangeResult.builder()
                .threadPoolId(threadPoolId)
                .changed(false)
                .build();

        // Compare and update core pool size
        if (config.getCorePoolSize() != null && config.getCorePoolSize() != getCorePoolSize()) {
            int oldValue = getCorePoolSize();
            // If new corePoolSize > old maximumPoolSize, update maximumPoolSize first
            if (config.getCorePoolSize() > getMaximumPoolSize()) {
                if (config.getMaximumPoolSize() != null) {
                    setMaximumPoolSize(config.getMaximumPoolSize());
                }
            }
            setCorePoolSize(config.getCorePoolSize());
            result.addChange("corePoolSize", oldValue, config.getCorePoolSize());
        }

        // Compare and update maximum pool size
        if (config.getMaximumPoolSize() != null && config.getMaximumPoolSize() != getMaximumPoolSize()) {
            int oldValue = getMaximumPoolSize();
            setMaximumPoolSize(config.getMaximumPoolSize());
            result.addChange("maximumPoolSize", oldValue, config.getMaximumPoolSize());
        }

        // Compare and update keep alive time
        if (config.getKeepAliveTime() != null && config.getKeepAliveTime() != getKeepAliveTime(TimeUnit.SECONDS)) {
            long oldValue = getKeepAliveTime(TimeUnit.SECONDS);
            setKeepAliveTime(config.getKeepAliveTime(), TimeUnit.SECONDS);
            result.addChange("keepAliveTime", oldValue, config.getKeepAliveTime());
        }

        // Compare and update allow core thread timeout
        if (config.getAllowCoreThreadTimeOut() != null && config.getAllowCoreThreadTimeOut() != allowsCoreThreadTimeOut()) {
            boolean oldValue = allowsCoreThreadTimeOut();
            allowCoreThreadTimeOut(config.getAllowCoreThreadTimeOut());
            result.addChange("allowCoreThreadTimeOut", oldValue, config.getAllowCoreThreadTimeOut());
        }

        // Compare and update queue capacity
        if (config.getQueueCapacity() != null && getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
            ResizableCapacityLinkedBlockingQueue<?> queue = (ResizableCapacityLinkedBlockingQueue<?>) getQueue();
            if (config.getQueueCapacity() != queue.getCapacity()) {
                int oldValue = queue.getCapacity();
                queue.setCapacity(config.getQueueCapacity());
                result.addChange("queueCapacity", oldValue, config.getQueueCapacity());
            }
        }

        // Compare and update rejected execution handler
        if (config.getRejectedHandler() != null && !config.getRejectedHandler().equals(this.rejectedHandlerName)) {
            String oldHandler = this.rejectedHandlerName;
            RejectedExecutionHandler handler = createRejectedHandler(config);
            this.rejectedHandlerName = config.getRejectedHandler();
            // Re-setup rejection proxy with new handler
            setupRejectionProxy(handler, this.rejectedHandlerName);
            result.addChange("rejectedHandler", oldHandler, this.rejectedHandlerName);
        }

        // Update alarm config (always apply, no comparison needed for complex objects)
        if (config.getAlarm() != null) {
            this.alarmConfig = config.getAlarm();
        }

        // Update notify config
        if (config.getNotify() != null) {
            this.notifyConfig = config.getNotify();
        }

        // Log changes if any
        if (result.isChanged()) {
            log.info("[{}] Config updated: {}", threadPoolId, result.getSummary());
        } else {
            log.debug("[{}] No config changes detected", threadPoolId);
        }

        return result;
    }

    /**
     * Get current thread pool state snapshot.
     */
    public ThreadPoolState getState() {
        BlockingQueue<Runnable> queue = getQueue();
        int queueCapacity = queue instanceof ResizableCapacityLinkedBlockingQueue
                ? ((ResizableCapacityLinkedBlockingQueue<?>) queue).getCapacity()
                : queue.size() + queue.remainingCapacity();

        ThreadPoolState.ThreadPoolStateBuilder builder = ThreadPoolState.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .poolSize(getPoolSize())
                .activeCount(getActiveCount())
                .largestPoolSize(getLargestPoolSize())
                .queueCapacity(queueCapacity)
                .queueSize(queue.size())
                .queueRemainingCapacity(queue.remainingCapacity())
                .completedTaskCount(getCompletedTaskCount())
                .taskCount(getTaskCount())
                .rejectedCount(getRejectedCount())
                .keepAliveTime(getKeepAliveTime(TimeUnit.SECONDS))
                .allowCoreThreadTimeOut(allowsCoreThreadTimeOut())
                .rejectedHandler(rejectedHandlerName)
                .timestamp(LocalDateTime.now());

        // Add RetryBufferPolicy statistics if applicable
        if (retryBufferPolicy != null) {
            builder.retryBufferEnabled(true)
                    .retryBufferSize(retryBufferPolicy.getBufferedTaskCount())
                    .retryBufferCapacity(retryBufferPolicy.getConfig().getBufferCapacity())
                    .retryBufferTotalBuffered(retryBufferPolicy.getTotalBufferedCount().get())
                    .retryBufferTotalRetried(retryBufferPolicy.getTotalRetriedCount().get())
                    .retryBufferTotalFallback(retryBufferPolicy.getTotalFallbackCount().get())
                    .retryBufferTotalExpired(retryBufferPolicy.getTotalExpiredCount().get());
            
            // Calculate buffer usage percent
            int bufferCapacity = retryBufferPolicy.getConfig().getBufferCapacity();
            if (bufferCapacity > 0) {
                builder.retryBufferUsagePercent(
                        (double) retryBufferPolicy.getBufferedTaskCount() / bufferCapacity * 100);
            }
        } else {
            builder.retryBufferEnabled(false);
        }

        ThreadPoolState state = builder.build();
        state.calculateMetrics();
        return state;
    }

    /**
     * Get rejected task count from proxy.
     */
    public long getRejectedCount() {
        return rejectionProxy != null ? rejectionProxy.getRejectedCount() : rejectedCount.get();
    }

    /**
     * Reset rejected task count.
     */
    public void resetRejectedCount() {
        rejectedCount.set(0);
        if (rejectionProxy != null) {
            rejectionProxy.resetCount();
        }
        log.info("[{}] Rejected count reset", threadPoolId);
    }

    /**
     * Get recent rejection records.
     *
     * @return list of recent rejection records
     */
    public List<RejectedTaskRecord> getRecentRejectedRecords() {
        return rejectionProxy != null ? rejectionProxy.getRecentRecords() : java.util.Collections.<RejectedTaskRecord>emptyList();
    }

    /**
     * Get recent rejection records with limit.
     *
     * @param limit maximum number of records
     * @return list of recent rejection records
     */
    public List<RejectedTaskRecord> getRecentRejectedRecords(int limit) {
        return rejectionProxy != null ? rejectionProxy.getRecentRecords(limit) : java.util.Collections.<RejectedTaskRecord>emptyList();
    }

    /**
     * Add a rejection listener.
     *
     * @param listener the listener to add
     */
    public void addRejectionListener(RejectedTaskListener listener) {
        if (rejectionProxy != null) {
            rejectionProxy.addListener(listener);
        }
    }

    /**
     * Remove a rejection listener.
     *
     * @param listener the listener to remove
     * @return true if removed
     */
    public boolean removeRejectionListener(RejectedTaskListener listener) {
        return rejectionProxy != null && rejectionProxy.removeListener(listener);
    }

    /**
     * Override to maintain proxy consistency.
     * Note: Direct use of this method bypasses the proxy. Use updateConfig() instead.
     */
    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        // Update internal counter for backward compatibility
        super.setRejectedExecutionHandler((r, executor) -> {
            rejectedCount.incrementAndGet();
            log.warn("[{}] Task rejected, rejectedCount={}", threadPoolId, rejectedCount.get());
            handler.rejectedExecution(r, executor);
        });
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        checkAlarm();
    }

    /**
     * Check if alarm should be triggered using AlarmManager.
     */
    private void checkAlarm() {
        // First check with built-in AlarmConfig (backward compatibility)
        if (alarmConfig != null && Boolean.TRUE.equals(alarmConfig.getEnable())) {
            checkBuiltInAlarm();
        }

        // Then check with AlarmManager rules
        try {
            ThreadPoolState state = getState();
            AlarmManager.getInstance().checkAndTrigger(state);
        } catch (Exception e) {
            log.debug("Error checking alarm with AlarmManager: {}", e.getMessage());
        }
    }

    /**
     * Built-in alarm check for backward compatibility with AlarmConfig.
     */
    private void checkBuiltInAlarm() {
        if (alarmConfig == null || !Boolean.TRUE.equals(alarmConfig.getEnable())) {
            return;
        }

        long now = System.currentTimeMillis();
        int interval = alarmConfig.getInterval() != null ? alarmConfig.getInterval() : 60;
        
        // Check alarm interval
        if (now - lastAlarmTime < interval * 1000L) {
            return;
        }

        ThreadPoolState state = getState();
        boolean shouldAlarm = false;
        StringBuilder alarmMessage = new StringBuilder();

        // Check queue threshold
        if (alarmConfig.getQueueThreshold() != null && 
            state.getQueueUsagePercent() >= alarmConfig.getQueueThreshold()) {
            shouldAlarm = true;
            alarmMessage.append(String.format("Queue usage %.2f%% exceeds threshold %d%%. ", 
                    state.getQueueUsagePercent(), alarmConfig.getQueueThreshold()));
        }

        // Check active thread threshold
        if (alarmConfig.getActiveThreshold() != null && 
            state.getActivePercent() >= alarmConfig.getActiveThreshold()) {
            shouldAlarm = true;
            alarmMessage.append(String.format("Active thread %.2f%% exceeds threshold %d%%. ", 
                    state.getActivePercent(), alarmConfig.getActiveThreshold()));
        }

        if (shouldAlarm) {
            lastAlarmTime = now;
            log.warn("[{}] ALARM: {}", threadPoolId, alarmMessage);
            // Trigger alarm through AlarmManager for notification
            AlarmManager.getInstance().checkAndTrigger(state);
        }
    }
}
