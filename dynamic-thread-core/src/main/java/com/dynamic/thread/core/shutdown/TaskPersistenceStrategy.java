package com.dynamic.thread.core.shutdown;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy interface for persisting unfinished tasks during shutdown.
 * Implementations can use Redis, MQ, Database, etc. for distributed recovery.
 */
public interface TaskPersistenceStrategy {

    /**
     * Persist unfinished tasks for later recovery
     * 
     * @param tasks list of unfinished Runnable tasks
     * @param poolId the thread pool id
     * @param instanceId the application instance id
     */
    void persistTasks(List<Runnable> tasks, String poolId, String instanceId);

    /**
     * Recover persisted tasks on startup
     * 
     * @param poolId the thread pool id
     * @param instanceId optionally filter by instance, null for all
     * @return list of recovered task records
     */
    List<PersistedTask> recoverTasks(String poolId, String instanceId);

    /**
     * Mark task as completed/processed
     * 
     * @param taskId the task id
     */
    void markCompleted(String taskId);

    /**
     * Clean up expired tasks
     * 
     * @param olderThan clean tasks older than this time
     */
    void cleanup(LocalDateTime olderThan);

    /**
     * Persisted task record
     */
    class PersistedTask implements Serializable {
        private String taskId;
        private String poolId;
        private String instanceId;
        private String taskClassName;
        private byte[] serializedTask;
        private LocalDateTime persistedAt;
        private int retryCount;
        private String status; // PENDING, PROCESSING, COMPLETED, FAILED

        // Getters and setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getPoolId() { return poolId; }
        public void setPoolId(String poolId) { this.poolId = poolId; }
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
        public String getTaskClassName() { return taskClassName; }
        public void setTaskClassName(String taskClassName) { this.taskClassName = taskClassName; }
        public byte[] getSerializedTask() { return serializedTask; }
        public void setSerializedTask(byte[] serializedTask) { this.serializedTask = serializedTask; }
        public LocalDateTime getPersistedAt() { return persistedAt; }
        public void setPersistedAt(LocalDateTime persistedAt) { this.persistedAt = persistedAt; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
