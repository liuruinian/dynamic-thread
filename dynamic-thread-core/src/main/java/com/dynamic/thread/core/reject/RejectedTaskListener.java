package com.dynamic.thread.core.reject;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Listener interface for rejected task events.
 * Implementations can perform custom actions when a task is rejected.
 * 
 * <p>Example usage:</p>
 * <pre>
 * RejectedTaskListener listener = new RejectedTaskListener() {
 *     @Override
 *     public void onTaskRejected(RejectedTaskRecord record) {
 *         // Custom handling: logging, metrics, alerting
 *         System.out.println("Task rejected: " + record.getSummary());
 *     }
 * };
 * </pre>
 */
@FunctionalInterface
public interface RejectedTaskListener {

    /**
     * Called when a task is rejected by the thread pool.
     * This method is called synchronously before the actual rejection handler executes.
     *
     * @param record the rejected task record containing detailed information
     */
    void onTaskRejected(RejectedTaskRecord record);

    /**
     * Called after the rejection handler has finished processing.
     * Default implementation does nothing.
     *
     * @param record the rejected task record
     * @param exception the exception thrown by the handler, or null if successful
     */
    default void afterRejection(RejectedTaskRecord record, Exception exception) {
        // Default: do nothing
    }

    /**
     * Get the listener order for execution priority.
     * Lower values execute first. Default is 0.
     *
     * @return the order value
     */
    default int getOrder() {
        return 0;
    }
}
