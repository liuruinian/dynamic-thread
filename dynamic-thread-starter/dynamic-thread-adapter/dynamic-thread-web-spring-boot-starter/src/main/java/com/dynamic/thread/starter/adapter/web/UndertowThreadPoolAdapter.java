package com.dynamic.thread.starter.adapter.web;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import io.undertow.Undertow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.undertow.UndertowWebServer;
import org.springframework.boot.web.server.WebServer;
import org.xnio.XnioWorker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * Undertow web container thread pool adapter.
 * Provides dynamic configuration and monitoring for Undertow's worker pool.
 * 
 * <p>Undertow uses XNIO as its underlying I/O framework, which provides:
 * <ul>
 *   <li>I/O threads - handle non-blocking I/O operations</li>
 *   <li>Worker threads - handle blocking operations (our focus)</li>
 * </ul>
 * 
 * <h3>Supported Parameters:</h3>
 * <ul>
 *   <li>coreWorkerThreads (core worker pool size)</li>
 *   <li>maxWorkerThreads (maximum worker pool size)</li>
 * </ul>
 * 
 * <p><b>Note:</b> Undertow's worker pool parameters are set at startup and 
 * some parameters may require special handling or restart to take effect.
 * This adapter focuses on monitoring capabilities with best-effort configuration updates.
 */
@Slf4j
public class UndertowThreadPoolAdapter implements WebContainerThreadPoolAdapter {

    private static final String ADAPTER_NAME = "Undertow";

    @Override
    public String getAdapterName() {
        return ADAPTER_NAME;
    }

    @Override
    public boolean match(WebServer webServer) {
        return webServer instanceof UndertowWebServer;
    }

    @Override
    public ThreadPoolState getThreadPoolState(WebServer webServer) {
        UndertowWebServer undertowWebServer = (UndertowWebServer) webServer;
        XnioWorker worker = getXnioWorker(undertowWebServer);

        if (worker == null) {
            return null;
        }

        try {
            // Use reflection to get thread pool statistics
            // Undertow/XNIO doesn't expose these directly through public API
            int corePoolSize = getWorkerOption(worker, "WORKER_TASK_CORE_THREADS", 8);
            int maxPoolSize = getWorkerOption(worker, "WORKER_TASK_MAX_THREADS", 64);
            int ioThreads = getWorkerOption(worker, "WORKER_IO_THREADS", Runtime.getRuntime().availableProcessors());

            // Try to get actual statistics via reflection
            int poolSize = getIntField(worker, "taskPoolSize", maxPoolSize);
            int activeCount = getIntField(worker, "taskCorePoolSize", 0);

            ThreadPoolState state = ThreadPoolState.builder()
                    .threadPoolId("undertow-web-container")
                    .corePoolSize(corePoolSize)
                    .maximumPoolSize(maxPoolSize)
                    .poolSize(poolSize)
                    .activeCount(activeCount)
                    .largestPoolSize(poolSize)
                    .queueCapacity(maxPoolSize * 10) // Estimated
                    .queueSize(0) // XNIO doesn't expose queue size easily
                    .queueRemainingCapacity(maxPoolSize * 10)
                    .completedTaskCount(0L)
                    .taskCount(0L)
                    .keepAliveTime(60L) // Default
                    .rejectedHandler("N/A")
                    .timestamp(LocalDateTime.now())
                    .build();

            // Add custom info
            state.setRejectedHandler(String.format("IO Threads: %d", ioThreads));

            state.calculateMetrics();
            return state;
        } catch (Exception e) {
            log.error("Failed to get Undertow thread pool state", e);
            return null;
        }
    }

    @Override
    public void updateThreadPool(WebServer webServer, DynamicThreadPoolProperties.WebThreadPoolProperties config) {
        UndertowWebServer undertowWebServer = (UndertowWebServer) webServer;
        XnioWorker worker = getXnioWorker(undertowWebServer);

        if (worker == null) {
            log.warn("Cannot find Undertow XnioWorker");
            return;
        }

        log.warn("Undertow worker pool configuration is set at startup. " +
                "Dynamic reconfiguration of worker threads has limited support. " +
                "Consider restarting the application for full parameter changes.");

        // Try to update via reflection (best effort)
        try {
            if (config.getMaximumPoolSize() != null) {
                boolean updated = setWorkerPoolSize(worker, "taskPoolSize", config.getMaximumPoolSize());
                if (updated) {
                    log.info("Undertow worker pool size updated to: {}", config.getMaximumPoolSize());
                }
            }

            if (config.getCorePoolSize() != null) {
                boolean updated = setWorkerPoolSize(worker, "taskCorePoolSize", config.getCorePoolSize());
                if (updated) {
                    log.info("Undertow core worker pool size updated to: {}", config.getCorePoolSize());
                }
            }
        } catch (Exception e) {
            log.error("Failed to update Undertow worker pool configuration", e);
        }
    }

    /**
     * Get XnioWorker from UndertowWebServer
     */
    private XnioWorker getXnioWorker(UndertowWebServer undertowWebServer) {
        try {
            // Use reflection to get the Undertow instance
            Field undertowField = UndertowWebServer.class.getDeclaredField("undertow");
            undertowField.setAccessible(true);
            Undertow undertow = (Undertow) undertowField.get(undertowWebServer);

            if (undertow != null) {
                // Get worker from Undertow
                Field workerField = Undertow.class.getDeclaredField("worker");
                workerField.setAccessible(true);
                return (XnioWorker) workerField.get(undertow);
            }
        } catch (NoSuchFieldException e) {
            log.debug("Cannot find Undertow fields - this might be due to version differences", e);
        } catch (Exception e) {
            log.error("Failed to get XnioWorker from Undertow", e);
        }
        return null;
    }

    /**
     * Get worker option value via reflection
     */
    private int getWorkerOption(XnioWorker worker, String optionName, int defaultValue) {
        try {
            // Try to get option class and value
            Class<?> optionsClass = Class.forName("org.xnio.Options");
            Field optionField = optionsClass.getDeclaredField(optionName);
            Object option = optionField.get(null);

            // Try to get the value from worker
            Method getOptionMethod = XnioWorker.class.getMethod("getOption", option.getClass().getInterfaces()[0]);
            Object value = getOptionMethod.invoke(worker, option);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception e) {
            log.trace("Could not get option {}: {}", optionName, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Get int field value via reflection
     */
    private int getIntField(XnioWorker worker, String fieldName, int defaultValue) {
        try {
            Field field = XnioWorker.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(worker);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception e) {
            // Field might not exist in this version
            log.trace("Could not get field {}: {}", fieldName, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Try to set worker pool size via reflection
     */
    private boolean setWorkerPoolSize(XnioWorker worker, String fieldName, int value) {
        try {
            Field field = XnioWorker.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(worker, value);
            return true;
        } catch (Exception e) {
            log.debug("Could not set {} to {}: {}", fieldName, value, e.getMessage());
            return false;
        }
    }
}
