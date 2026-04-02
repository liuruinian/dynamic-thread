package com.dynamic.thread.core.reject;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dynamic proxy for RejectedExecutionHandler that provides:
 * <ul>
 *   <li>Real-time task rejection statistics</li>
 *   <li>Detailed rejection event recording</li>
 *   <li>Pluggable listener mechanism for custom handling</li>
 *   <li>Instant alarm triggering on rejection</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * // Create proxy for any rejection handler
 * RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
 * RejectedExecutionHandlerProxy proxy = RejectedExecutionHandlerProxy.wrap(
 *     handler, "my-pool", "AbortPolicy"
 * );
 * 
 * // Add custom listener
 * proxy.addListener(record -> {
 *     System.out.println("Task rejected: " + record.getSummary());
 *     // Trigger alarm, send notification, etc.
 * });
 * 
 * // Use proxy as the handler
 * executor.setRejectedExecutionHandler(proxy.getProxyHandler());
 * </pre>
 */
@Slf4j
public class RejectedExecutionHandlerProxy implements InvocationHandler {

    /**
     * The original rejection handler
     */
    private final RejectedExecutionHandler originalHandler;

    /**
     * Thread pool identifier
     */
    private final String threadPoolId;

    /**
     * Handler policy name
     */
    private final String policyName;

    /**
     * Total rejected count
     */
    private final AtomicLong rejectedCount = new AtomicLong(0);

    /**
     * Registered rejection listeners
     */
    private final List<RejectedTaskListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Recent rejection records (limited size)
     */
    private final List<RejectedTaskRecord> recentRecords = new CopyOnWriteArrayList<>();
    private static final int MAX_RECENT_RECORDS = 100;

    /**
     * Supplier for getting queue capacity (set by executor)
     */
    private volatile java.util.function.Supplier<Integer> queueCapacitySupplier;

    private RejectedExecutionHandlerProxy(RejectedExecutionHandler handler, 
                                          String threadPoolId, 
                                          String policyName) {
        this.originalHandler = handler;
        this.threadPoolId = threadPoolId;
        this.policyName = policyName;
    }

    /**
     * Create a proxy for the given rejection handler.
     *
     * @param handler      the original rejection handler
     * @param threadPoolId the thread pool identifier
     * @param policyName   the policy name (e.g., "AbortPolicy")
     * @return the proxy instance
     */
    public static RejectedExecutionHandlerProxy wrap(RejectedExecutionHandler handler,
                                                     String threadPoolId,
                                                     String policyName) {
        return new RejectedExecutionHandlerProxy(handler, threadPoolId, policyName);
    }

    /**
     * Get the proxied RejectedExecutionHandler.
     *
     * @return a proxy implementing RejectedExecutionHandler
     */
    public RejectedExecutionHandler getProxyHandler() {
        return (RejectedExecutionHandler) Proxy.newProxyInstance(
                RejectedExecutionHandler.class.getClassLoader(),
                new Class<?>[]{RejectedExecutionHandler.class},
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Handle rejectedExecution method
        if ("rejectedExecution".equals(method.getName()) && args != null && args.length == 2) {
            Runnable task = (Runnable) args[0];
            ThreadPoolExecutor executor = (ThreadPoolExecutor) args[1];
            return handleRejection(task, executor);
        }

        // Handle other Object methods (toString, hashCode, equals)
        if ("toString".equals(method.getName())) {
            return "RejectedExecutionHandlerProxy[" + policyName + " for " + threadPoolId + "]";
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return proxy == args[0];
        }

        // Delegate to original handler for any other methods
        return method.invoke(originalHandler, args);
    }

    /**
     * Handle task rejection with statistics and notification.
     */
    private Object handleRejection(Runnable task, ThreadPoolExecutor executor) {
        // Increment counter
        long count = rejectedCount.incrementAndGet();

        // Build rejection record
        RejectedTaskRecord record = buildRecord(task, executor, count);

        // Add to recent records
        addToRecentRecords(record);

        // Notify listeners before rejection
        notifyListenersBefore(record);

        Exception thrownException = null;
        try {
            // Execute original rejection handler
            originalHandler.rejectedExecution(task, executor);
        } catch (Exception e) {
            thrownException = e;
            throw e;
        } finally {
            // Notify listeners after rejection
            notifyListenersAfter(record, thrownException);

            // Log rejection
            logRejection(record);
        }

        return null;
    }

    /**
     * Build a detailed rejection record.
     */
    private RejectedTaskRecord buildRecord(Runnable task, ThreadPoolExecutor executor, long count) {
        int queueCapacity = getQueueCapacity(executor);
        
        return RejectedTaskRecord.builder()
                .id(UUID.randomUUID().toString())
                .threadPoolId(threadPoolId)
                .taskClassName(task.getClass().getName())
                .taskDescription(getTaskDescription(task))
                .rejectedPolicy(policyName)
                .reason(executor.isShutdown() ? "Executor shutdown" : "Pool and queue full")
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .activeCount(executor.getActiveCount())
                .queueSize(executor.getQueue().size())
                .queueCapacity(queueCapacity)
                .executorShutdown(executor.isShutdown())
                .submitterThread(Thread.currentThread().getName())
                .timestamp(LocalDateTime.now())
                .totalRejectedCount(count)
                .build();
    }

    /**
     * Get queue capacity from the executor.
     */
    private int getQueueCapacity(ThreadPoolExecutor executor) {
        // Try supplier first (for ResizableCapacityLinkedBlockingQueue)
        if (queueCapacitySupplier != null) {
            try {
                return queueCapacitySupplier.get();
            } catch (Exception ignored) {}
        }
        
        // Try to get remaining capacity + size
        try {
            int remaining = executor.getQueue().remainingCapacity();
            int size = executor.getQueue().size();
            if (remaining != Integer.MAX_VALUE) {
                return remaining + size;
            }
        } catch (Exception ignored) {}
        
        return Integer.MAX_VALUE;
    }

    /**
     * Get task description safely.
     */
    private String getTaskDescription(Runnable task) {
        try {
            String desc = task.toString();
            if (desc != null && desc.length() > 200) {
                return desc.substring(0, 200) + "...";
            }
            return desc;
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Add record to recent records list.
     */
    private void addToRecentRecords(RejectedTaskRecord record) {
        recentRecords.add(0, record);
        while (recentRecords.size() > MAX_RECENT_RECORDS) {
            recentRecords.remove(recentRecords.size() - 1);
        }
    }

    /**
     * Notify all listeners before rejection.
     */
    private void notifyListenersBefore(RejectedTaskRecord record) {
        for (RejectedTaskListener listener : listeners) {
            try {
                listener.onTaskRejected(record);
            } catch (Exception e) {
                log.warn("[{}] Rejection listener error: {}", threadPoolId, e.getMessage());
            }
        }
    }

    /**
     * Notify all listeners after rejection.
     */
    private void notifyListenersAfter(RejectedTaskRecord record, Exception exception) {
        for (RejectedTaskListener listener : listeners) {
            try {
                listener.afterRejection(record, exception);
            } catch (Exception e) {
                log.warn("[{}] Rejection listener afterRejection error: {}", threadPoolId, e.getMessage());
            }
        }
    }

    /**
     * Log the rejection event.
     */
    private void logRejection(RejectedTaskRecord record) {
        log.warn("[{}] Task rejected! Policy: {}, Count: {}, Pool: {}/{}, Queue: {}/{}", 
                record.getThreadPoolId(),
                record.getRejectedPolicy(),
                record.getTotalRejectedCount(),
                record.getActiveCount(),
                record.getMaximumPoolSize(),
                record.getQueueSize(),
                record.getQueueCapacity());
    }

    // ==================== Public API ====================

    /**
     * Add a rejection listener.
     *
     * @param listener the listener to add
     * @return this proxy for chaining
     */
    public RejectedExecutionHandlerProxy addListener(RejectedTaskListener listener) {
        if (listener != null) {
            listeners.add(listener);
            // Sort by order
            listeners.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        }
        return this;
    }

    /**
     * Remove a rejection listener.
     *
     * @param listener the listener to remove
     * @return true if removed
     */
    public boolean removeListener(RejectedTaskListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Clear all listeners.
     */
    public void clearListeners() {
        listeners.clear();
    }

    /**
     * Get the total rejected count.
     *
     * @return the count
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * Reset the rejected count.
     */
    public void resetCount() {
        rejectedCount.set(0);
        recentRecords.clear();
    }

    /**
     * Get recent rejection records.
     *
     * @return list of recent records
     */
    public List<RejectedTaskRecord> getRecentRecords() {
        return List.copyOf(recentRecords);
    }

    /**
     * Get recent rejection records with limit.
     *
     * @param limit maximum records to return
     * @return list of recent records
     */
    public List<RejectedTaskRecord> getRecentRecords(int limit) {
        if (limit >= recentRecords.size()) {
            return List.copyOf(recentRecords);
        }
        return List.copyOf(recentRecords.subList(0, limit));
    }

    /**
     * Get the thread pool identifier.
     *
     * @return the thread pool id
     */
    public String getThreadPoolId() {
        return threadPoolId;
    }

    /**
     * Get the policy name.
     *
     * @return the policy name
     */
    public String getPolicyName() {
        return policyName;
    }

    /**
     * Get the original handler.
     *
     * @return the original RejectedExecutionHandler
     */
    public RejectedExecutionHandler getOriginalHandler() {
        return originalHandler;
    }

    /**
     * Set queue capacity supplier for accurate capacity reporting.
     *
     * @param supplier the supplier
     */
    public void setQueueCapacitySupplier(java.util.function.Supplier<Integer> supplier) {
        this.queueCapacitySupplier = supplier;
    }
}
