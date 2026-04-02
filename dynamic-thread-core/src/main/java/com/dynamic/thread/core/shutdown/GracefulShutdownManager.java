package com.dynamic.thread.core.shutdown;

import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Graceful shutdown manager for dynamic thread pools.
 * Ensures no task loss during application shutdown in distributed environments.
 * 
 * Features:
 * 1. Wait for running tasks to complete
 * 2. Persist pending tasks for recovery
 * 3. Configurable timeout policies
 * 4. Shutdown hooks and Spring lifecycle integration
 */
@Slf4j
public class GracefulShutdownManager {

    private static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 30;
    private static final int DEFAULT_FORCE_SHUTDOWN_SECONDS = 60;

    private final ThreadPoolRegistry registry;
    private final int awaitTerminationSeconds;
    private final int forceShutdownSeconds;
    
    /**
     * Callback for persisting unfinished tasks (for distributed recovery)
     */
    private Consumer<List<Runnable>> unfinishedTaskHandler;
    
    /**
     * Callback invoked before shutdown starts
     */
    private Runnable preShutdownHook;
    
    /**
     * Callback invoked after shutdown completes
     */
    private Runnable postShutdownHook;

    public GracefulShutdownManager(ThreadPoolRegistry registry) {
        this(registry, DEFAULT_AWAIT_TERMINATION_SECONDS, DEFAULT_FORCE_SHUTDOWN_SECONDS);
    }

    public GracefulShutdownManager(ThreadPoolRegistry registry, int awaitTerminationSeconds, int forceShutdownSeconds) {
        this.registry = registry;
        this.awaitTerminationSeconds = awaitTerminationSeconds;
        this.forceShutdownSeconds = forceShutdownSeconds;
    }

    /**
     * Set handler for unfinished tasks (e.g., persist to Redis/MQ for recovery)
     */
    public void setUnfinishedTaskHandler(Consumer<List<Runnable>> handler) {
        this.unfinishedTaskHandler = handler;
    }

    public void setPreShutdownHook(Runnable hook) {
        this.preShutdownHook = hook;
    }

    public void setPostShutdownHook(Runnable hook) {
        this.postShutdownHook = hook;
    }

    /**
     * Gracefully shutdown all registered thread pools
     * 
     * Strategy:
     * 1. Stop accepting new tasks (shutdown)
     * 2. Wait for running tasks to complete
     * 3. If timeout, force shutdown and collect unfinished tasks
     * 4. Persist unfinished tasks for recovery
     */
    public ShutdownResult shutdownAll() {
        log.info("[GracefulShutdown] Starting graceful shutdown of all thread pools...");
        
        if (preShutdownHook != null) {
            try {
                preShutdownHook.run();
            } catch (Exception e) {
                log.warn("[GracefulShutdown] Pre-shutdown hook failed", e);
            }
        }

        ShutdownResult result = new ShutdownResult();
        List<String> poolIds = new ArrayList<>(registry.listThreadPoolIds());
        
        // Phase 1: Initiate shutdown for all pools (stop accepting new tasks)
        log.info("[GracefulShutdown] Phase 1: Stopping {} thread pool(s) from accepting new tasks", poolIds.size());
        for (String poolId : poolIds) {
            ThreadPoolExecutor executor = registry.get(poolId);
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                result.poolsShutdown++;
                log.info("[GracefulShutdown] Pool [{}] shutdown initiated, active={}, queued={}", 
                        poolId, executor.getActiveCount(), executor.getQueue().size());
            }
        }

        // Phase 2: Wait for all pools to terminate
        log.info("[GracefulShutdown] Phase 2: Waiting up to {}s for tasks to complete", awaitTerminationSeconds);
        long startTime = System.currentTimeMillis();
        List<Runnable> allUnfinishedTasks = new ArrayList<>();

        for (String poolId : poolIds) {
            ThreadPoolExecutor executor = registry.get(poolId);
            if (executor == null) continue;

            try {
                long remainingTime = awaitTerminationSeconds * 1000L - (System.currentTimeMillis() - startTime);
                if (remainingTime <= 0) remainingTime = 1000;

                boolean terminated = executor.awaitTermination(remainingTime, TimeUnit.MILLISECONDS);
                
                if (terminated) {
                    log.info("[GracefulShutdown] Pool [{}] terminated gracefully", poolId);
                    result.poolsTerminatedGracefully++;
                } else {
                    // Force shutdown and collect unfinished tasks
                    log.warn("[GracefulShutdown] Pool [{}] did not terminate in time, forcing shutdown", poolId);
                    List<Runnable> unfinished = executor.shutdownNow();
                    allUnfinishedTasks.addAll(unfinished);
                    result.poolsForcedShutdown++;
                    result.unfinishedTaskCount += unfinished.size();
                    log.warn("[GracefulShutdown] Pool [{}] force shutdown, {} unfinished tasks", poolId, unfinished.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[GracefulShutdown] Interrupted while waiting for pool [{}]", poolId);
                List<Runnable> unfinished = executor.shutdownNow();
                allUnfinishedTasks.addAll(unfinished);
                result.poolsForcedShutdown++;
                result.unfinishedTaskCount += unfinished.size();
            }
        }

        // Phase 3: Handle unfinished tasks (persist for recovery)
        if (!allUnfinishedTasks.isEmpty() && unfinishedTaskHandler != null) {
            log.info("[GracefulShutdown] Phase 3: Persisting {} unfinished tasks for recovery", allUnfinishedTasks.size());
            try {
                unfinishedTaskHandler.accept(allUnfinishedTasks);
                result.tasksPersisted = allUnfinishedTasks.size();
                log.info("[GracefulShutdown] Successfully persisted {} tasks", allUnfinishedTasks.size());
            } catch (Exception e) {
                log.error("[GracefulShutdown] Failed to persist unfinished tasks", e);
                result.persistenceFailed = true;
            }
        }

        result.totalTimeMs = System.currentTimeMillis() - startTime;
        
        if (postShutdownHook != null) {
            try {
                postShutdownHook.run();
            } catch (Exception e) {
                log.warn("[GracefulShutdown] Post-shutdown hook failed", e);
            }
        }

        log.info("[GracefulShutdown] Complete: {} pools shutdown, {} graceful, {} forced, {} tasks unfinished, {}ms", 
                result.poolsShutdown, result.poolsTerminatedGracefully, result.poolsForcedShutdown, 
                result.unfinishedTaskCount, result.totalTimeMs);
        
        return result;
    }

    /**
     * Shutdown a specific thread pool
     */
    public SinglePoolShutdownResult shutdown(String poolId) {
        log.info("[GracefulShutdown] Shutting down pool: {}", poolId);
        
        SinglePoolShutdownResult result = new SinglePoolShutdownResult();
        result.poolId = poolId;
        
        ThreadPoolExecutor executor = registry.get(poolId);
        if (executor == null) {
            log.warn("[GracefulShutdown] Pool [{}] not found", poolId);
            result.success = false;
            result.message = "Pool not found";
            return result;
        }

        if (executor.isShutdown()) {
            log.info("[GracefulShutdown] Pool [{}] already shutdown", poolId);
            result.success = true;
            result.message = "Already shutdown";
            return result;
        }

        long startTime = System.currentTimeMillis();
        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        
        executor.shutdown();
        
        try {
            boolean terminated = executor.awaitTermination(awaitTerminationSeconds, TimeUnit.SECONDS);
            
            if (terminated) {
                result.success = true;
                result.graceful = true;
                result.message = "Terminated gracefully";
            } else {
                List<Runnable> unfinished = executor.shutdownNow();
                result.success = true;
                result.graceful = false;
                result.unfinishedTasks = unfinished.size();
                result.message = "Force shutdown with " + unfinished.size() + " unfinished tasks";
                
                if (!unfinished.isEmpty() && unfinishedTaskHandler != null) {
                    unfinishedTaskHandler.accept(unfinished);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            result.success = false;
            result.message = "Interrupted";
        }

        result.activeCountBefore = activeCount;
        result.queueSizeBefore = queueSize;
        result.timeMs = System.currentTimeMillis() - startTime;
        
        log.info("[GracefulShutdown] Pool [{}] shutdown result: {}", poolId, result.message);
        return result;
    }

    /**
     * Register JVM shutdown hook
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[GracefulShutdown] JVM shutdown hook triggered");
            shutdownAll();
        }, "DynamicThreadPool-ShutdownHook"));
        log.info("[GracefulShutdown] JVM shutdown hook registered");
    }

    /**
     * Shutdown result statistics
     */
    public static class ShutdownResult {
        public int poolsShutdown;
        public int poolsTerminatedGracefully;
        public int poolsForcedShutdown;
        public int unfinishedTaskCount;
        public int tasksPersisted;
        public boolean persistenceFailed;
        public long totalTimeMs;
    }

    /**
     * Single pool shutdown result
     */
    public static class SinglePoolShutdownResult {
        public String poolId;
        public boolean success;
        public boolean graceful;
        public int unfinishedTasks;
        public int activeCountBefore;
        public int queueSizeBefore;
        public long timeMs;
        public String message;
    }
}
