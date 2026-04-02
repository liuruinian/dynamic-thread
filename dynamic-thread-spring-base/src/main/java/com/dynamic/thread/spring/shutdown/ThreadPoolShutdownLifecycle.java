package com.dynamic.thread.spring.shutdown;

import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.core.shutdown.GracefulShutdownManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Lifecycle integration for graceful thread pool shutdown.
 * 
 * Implements SmartLifecycle to participate in Spring's shutdown sequence,
 * ensuring thread pools are properly closed before the application context closes.
 * 
 * Configuration:
 * - dynamic-thread.shutdown.await-termination-seconds: Max wait time (default: 30)
 * - dynamic-thread.shutdown.force-shutdown-seconds: Force shutdown after (default: 60)
 */
@Slf4j
public class ThreadPoolShutdownLifecycle implements SmartLifecycle {

    private final GracefulShutdownManager shutdownManager;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Lower phase = later shutdown (we want thread pools to shutdown early)
     * Default Spring beans are phase 0, we use negative to shutdown earlier
     */
    private static final int SHUTDOWN_PHASE = Integer.MAX_VALUE - 100;

    public ThreadPoolShutdownLifecycle(ThreadPoolRegistry registry,
                                        @Value("${dynamic-thread.shutdown.await-termination-seconds:30}") int awaitSeconds,
                                        @Value("${dynamic-thread.shutdown.force-shutdown-seconds:60}") int forceSeconds) {
        this.shutdownManager = new GracefulShutdownManager(registry, awaitSeconds, forceSeconds);
        log.info("[ThreadPoolShutdown] Lifecycle initialized with awaitSeconds={}, forceSeconds={}", awaitSeconds, forceSeconds);
    }

    public GracefulShutdownManager getShutdownManager() {
        return shutdownManager;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("[ThreadPoolShutdown] Lifecycle started, monitoring thread pools for graceful shutdown");
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        if (running.compareAndSet(true, false)) {
            log.info("[ThreadPoolShutdown] Spring shutdown signal received, initiating graceful shutdown");
            try {
                GracefulShutdownManager.ShutdownResult result = shutdownManager.shutdownAll();
                
                if (result.unfinishedTaskCount > 0) {
                    log.warn("[ThreadPoolShutdown] {} tasks were not completed", result.unfinishedTaskCount);
                } else {
                    log.info("[ThreadPoolShutdown] All tasks completed successfully");
                }
            } finally {
                callback.run();
            }
        } else {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Higher phase means earlier shutdown
        return SHUTDOWN_PHASE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Configure unfinished task handler for distributed recovery
     */
    public void setUnfinishedTaskHandler(java.util.function.Consumer<List<Runnable>> handler) {
        shutdownManager.setUnfinishedTaskHandler(handler);
    }
}
