package com.dynamic.thread.core.registry;

import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.model.ConfigChangeResult;
import com.dynamic.thread.core.model.ThreadPoolState;

import java.util.Collection;
import java.util.Map;

/**
 * Interface for thread pool registry operations.
 * Allows for dependency injection and easier testing.
 */
public interface IThreadPoolRegistry {

    /**
     * Register a dynamic thread pool
     *
     * @param threadPoolId the unique identifier
     * @param executor     the thread pool executor
     */
    void register(String threadPoolId, DynamicThreadPoolExecutor executor);

    /**
     * Register a dynamic thread pool with configuration
     *
     * @param threadPoolId the unique identifier
     * @param executor     the thread pool executor
     * @param config       the thread pool configuration
     */
    void register(String threadPoolId, DynamicThreadPoolExecutor executor, ThreadPoolConfig config);

    /**
     * Unregister a thread pool
     *
     * @param threadPoolId the unique identifier
     * @return the removed executor, or null if not found
     */
    DynamicThreadPoolExecutor unregister(String threadPoolId);

    /**
     * Get a thread pool by id
     *
     * @param threadPoolId the unique identifier
     * @return the thread pool executor, or null if not found
     */
    DynamicThreadPoolExecutor get(String threadPoolId);

    /**
     * Get thread pool configuration by id
     *
     * @param threadPoolId the unique identifier
     * @return the configuration, or null if not found
     */
    ThreadPoolConfig getConfig(String threadPoolId);

    /**
     * Update thread pool configuration
     *
     * @param threadPoolId the unique identifier
     * @param config       the new configuration
     * @return the change result, or null if thread pool not found
     */
    ConfigChangeResult updateConfig(String threadPoolId, ThreadPoolConfig config);

    /**
     * Check if a thread pool is registered
     *
     * @param threadPoolId the unique identifier
     * @return true if registered
     */
    boolean contains(String threadPoolId);

    /**
     * Get all registered thread pool ids
     *
     * @return collection of thread pool ids
     */
    Collection<String> listThreadPoolIds();

    /**
     * Get all registered thread pools
     *
     * @return collection of thread pool executors
     */
    Collection<DynamicThreadPoolExecutor> listThreadPools();

    /**
     * Get all thread pool states
     *
     * @return map of thread pool id to state
     */
    Map<String, ThreadPoolState> listStates();

    /**
     * Get thread pool state by id
     *
     * @param threadPoolId the unique identifier
     * @return the state, or null if not found
     */
    ThreadPoolState getState(String threadPoolId);

    /**
     * Get the count of registered thread pools
     *
     * @return the count
     */
    int size();

    /**
     * Clear all registered thread pools
     */
    void clear();
}
