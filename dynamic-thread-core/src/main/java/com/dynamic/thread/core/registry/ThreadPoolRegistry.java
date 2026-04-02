package com.dynamic.thread.core.registry;

import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.model.ConfigChangeResult;
import com.dynamic.thread.core.model.ThreadPoolState;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool registry for managing all dynamic thread pools.
 * Singleton pattern to ensure global access to all registered thread pools.
 * Implements IThreadPoolRegistry interface for dependency injection and testing.
 */
@Slf4j
public class ThreadPoolRegistry implements IThreadPoolRegistry {

    /**
     * Singleton instance
     */
    private static final ThreadPoolRegistry INSTANCE = new ThreadPoolRegistry();

    /**
     * Registry map: threadPoolId -> DynamicThreadPoolExecutor
     */
    private final Map<String, DynamicThreadPoolExecutor> registry = new ConcurrentHashMap<>();

    /**
     * Configuration map: threadPoolId -> ThreadPoolConfig
     */
    private final Map<String, ThreadPoolConfig> configMap = new ConcurrentHashMap<>();

    /**
     * Private constructor for singleton
     */
    private ThreadPoolRegistry() {
    }

    /**
     * Get singleton instance
     */
    public static ThreadPoolRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a dynamic thread pool
     *
     * @param threadPoolId the unique identifier
     * @param executor     the thread pool executor
     */
    public void register(String threadPoolId, DynamicThreadPoolExecutor executor) {
        if (threadPoolId == null || executor == null) {
            throw new IllegalArgumentException("ThreadPoolId and executor cannot be null");
        }

        DynamicThreadPoolExecutor existing = registry.putIfAbsent(threadPoolId, executor);
        if (existing != null) {
            log.warn("Thread pool [{}] already registered, skipping", threadPoolId);
        } else {
            log.info("Thread pool [{}] registered successfully", threadPoolId);
        }
    }

    /**
     * Register a dynamic thread pool with configuration
     *
     * @param threadPoolId the unique identifier
     * @param executor     the thread pool executor
     * @param config       the thread pool configuration
     */
    public void register(String threadPoolId, DynamicThreadPoolExecutor executor, ThreadPoolConfig config) {
        register(threadPoolId, executor);
        if (config != null) {
            configMap.put(threadPoolId, config);
        }
    }

    /**
     * Unregister a thread pool
     *
     * @param threadPoolId the unique identifier
     * @return the removed executor, or null if not found
     */
    public DynamicThreadPoolExecutor unregister(String threadPoolId) {
        configMap.remove(threadPoolId);
        DynamicThreadPoolExecutor removed = registry.remove(threadPoolId);
        if (removed != null) {
            log.info("Thread pool [{}] unregistered", threadPoolId);
        }
        return removed;
    }

    /**
     * Get a thread pool by id
     *
     * @param threadPoolId the unique identifier
     * @return the thread pool executor, or null if not found
     */
    public DynamicThreadPoolExecutor get(String threadPoolId) {
        return registry.get(threadPoolId);
    }

    /**
     * Get thread pool configuration by id
     *
     * @param threadPoolId the unique identifier
     * @return the configuration, or null if not found
     */
    public ThreadPoolConfig getConfig(String threadPoolId) {
        return configMap.get(threadPoolId);
    }

    /**
     * Update thread pool configuration.
     * Only updates config storage if actual changes were made.
     *
     * @param threadPoolId the unique identifier
     * @param config       the new configuration
     * @return the change result, or null if thread pool not found
     */
    public ConfigChangeResult updateConfig(String threadPoolId, ThreadPoolConfig config) {
        DynamicThreadPoolExecutor executor = registry.get(threadPoolId);
        if (executor == null) {
            log.warn("Thread pool [{}] not found, cannot update config", threadPoolId);
            return null;
        }

        // Execute update and get change result
        ConfigChangeResult result = executor.updateConfig(config);

        // Only update stored config if changes were made
        if (result.isChanged()) {
            configMap.put(threadPoolId, config);
            log.info("Thread pool [{}] configuration stored", threadPoolId);
        }

        return result;
    }

    /**
     * Check if a thread pool is registered
     *
     * @param threadPoolId the unique identifier
     * @return true if registered
     */
    public boolean contains(String threadPoolId) {
        return registry.containsKey(threadPoolId);
    }

    /**
     * Get all registered thread pool ids
     *
     * @return collection of thread pool ids
     */
    public Collection<String> listThreadPoolIds() {
        return registry.keySet();
    }

    /**
     * Get all registered thread pools
     *
     * @return collection of thread pool executors
     */
    public Collection<DynamicThreadPoolExecutor> listThreadPools() {
        return registry.values();
    }

    /**
     * Get all thread pool states
     *
     * @return map of thread pool id to state
     */
    public Map<String, ThreadPoolState> listStates() {
        Map<String, ThreadPoolState> states = new ConcurrentHashMap<>();
        registry.forEach((id, executor) -> states.put(id, executor.getState()));
        return states;
    }

    /**
     * Get thread pool state by id
     *
     * @param threadPoolId the unique identifier
     * @return the state, or null if not found
     */
    public ThreadPoolState getState(String threadPoolId) {
        DynamicThreadPoolExecutor executor = registry.get(threadPoolId);
        return executor != null ? executor.getState() : null;
    }

    /**
     * Get the count of registered thread pools
     *
     * @return the count
     */
    public int size() {
        return registry.size();
    }

    /**
     * Clear all registered thread pools
     */
    public void clear() {
        registry.clear();
        configMap.clear();
        log.info("All thread pools cleared from registry");
    }

    /**
     * Reset rejected count for a specific thread pool
     *
     * @param threadPoolId the unique identifier
     * @return true if pool found and reset, false otherwise
     */
    public boolean resetRejectedCount(String threadPoolId) {
        DynamicThreadPoolExecutor executor = registry.get(threadPoolId);
        if (executor != null) {
            executor.resetRejectedCount();
            log.info("Rejected count reset for thread pool [{}]", threadPoolId);
            return true;
        }
        return false;
    }

    /**
     * Reset rejected counts for all thread pools
     */
    public void resetAllRejectedCounts() {
        registry.values().forEach(DynamicThreadPoolExecutor::resetRejectedCount);
        log.info("Rejected counts reset for all {} thread pools", registry.size());
    }
}
