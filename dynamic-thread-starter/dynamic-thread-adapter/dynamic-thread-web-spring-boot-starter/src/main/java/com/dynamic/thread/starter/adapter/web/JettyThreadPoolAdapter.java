package com.dynamic.thread.starter.adapter.web;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.springframework.boot.web.embedded.jetty.JettyWebServer;
import org.springframework.boot.web.server.WebServer;

import java.time.LocalDateTime;

/**
 * Jetty web container thread pool adapter.
 * Provides dynamic configuration and monitoring for Jetty's thread pool.
 * 
 * <p>Jetty uses {@link QueuedThreadPool} as its default thread pool implementation,
 * which supports dynamic parameter adjustment at runtime.
 * 
 * <h3>Supported Parameters:</h3>
 * <ul>
 *   <li>minThreads (core pool size)</li>
 *   <li>maxThreads (maximum pool size)</li>
 *   <li>idleTimeout (keep alive time)</li>
 * </ul>
 */
@Slf4j
public class JettyThreadPoolAdapter implements WebContainerThreadPoolAdapter {

    private static final String ADAPTER_NAME = "Jetty";

    @Override
    public String getAdapterName() {
        return ADAPTER_NAME;
    }

    @Override
    public boolean match(WebServer webServer) {
        return webServer instanceof JettyWebServer;
    }

    @Override
    public ThreadPoolState getThreadPoolState(WebServer webServer) {
        JettyWebServer jettyWebServer = (JettyWebServer) webServer;
        QueuedThreadPool threadPool = getJettyThreadPool(jettyWebServer);

        if (threadPool == null) {
            return null;
        }

        ThreadPoolState state = ThreadPoolState.builder()
                .threadPoolId("jetty-web-container")
                .corePoolSize(threadPool.getMinThreads())
                .maximumPoolSize(threadPool.getMaxThreads())
                .poolSize(threadPool.getThreads())
                .activeCount(threadPool.getBusyThreads())
                .largestPoolSize(threadPool.getThreads()) // Jetty doesn't track largest
                .queueCapacity(getQueueCapacity(threadPool))
                .queueSize(threadPool.getQueueSize())
                .queueRemainingCapacity(Math.max(0, getQueueCapacity(threadPool) - threadPool.getQueueSize()))
                .completedTaskCount(0L) // Jetty doesn't expose this directly
                .taskCount(0L)
                .keepAliveTime((long) threadPool.getIdleTimeout() / 1000) // Convert ms to seconds
                .rejectedHandler("N/A")
                .timestamp(LocalDateTime.now())
                .build();

        state.calculateMetrics();
        return state;
    }

    @Override
    public void updateThreadPool(WebServer webServer, DynamicThreadPoolProperties.WebThreadPoolProperties config) {
        JettyWebServer jettyWebServer = (JettyWebServer) webServer;
        QueuedThreadPool threadPool = getJettyThreadPool(jettyWebServer);

        if (threadPool == null) {
            log.warn("Cannot find Jetty thread pool");
            return;
        }

        int oldMinThreads = threadPool.getMinThreads();
        int oldMaxThreads = threadPool.getMaxThreads();
        int oldIdleTimeout = threadPool.getIdleTimeout();

        try {
            // Update maximum threads first to avoid validation errors
            if (config.getMaximumPoolSize() != null) {
                threadPool.setMaxThreads(config.getMaximumPoolSize());
            }

            // Update minimum threads (core pool size)
            if (config.getCorePoolSize() != null) {
                threadPool.setMinThreads(config.getCorePoolSize());
            }

            // Update idle timeout (keep alive time in seconds, convert to ms)
            if (config.getKeepAliveTime() != null) {
                threadPool.setIdleTimeout(config.getKeepAliveTime().intValue() * 1000);
            }

            log.info("Jetty thread pool updated: minThreads={}->{}, maxThreads={}->{}, idleTimeout={}ms->{}ms",
                    oldMinThreads, threadPool.getMinThreads(),
                    oldMaxThreads, threadPool.getMaxThreads(),
                    oldIdleTimeout, threadPool.getIdleTimeout());
        } catch (Exception e) {
            log.error("Failed to update Jetty thread pool", e);
            // Rollback on failure
            try {
                threadPool.setMaxThreads(oldMaxThreads);
                threadPool.setMinThreads(oldMinThreads);
                threadPool.setIdleTimeout(oldIdleTimeout);
            } catch (Exception rollbackException) {
                log.error("Failed to rollback Jetty thread pool configuration", rollbackException);
            }
        }
    }

    /**
     * Get Jetty's QueuedThreadPool from JettyWebServer
     */
    private QueuedThreadPool getJettyThreadPool(JettyWebServer jettyWebServer) {
        try {
            Server server = jettyWebServer.getServer();
            ThreadPool threadPool = server.getThreadPool();

            if (threadPool instanceof QueuedThreadPool) {
                return (QueuedThreadPool) threadPool;
            } else {
                log.warn("Jetty is using non-standard thread pool: {}", threadPool.getClass().getName());
            }
        } catch (Exception e) {
            log.error("Failed to get Jetty thread pool", e);
        }
        return null;
    }

    /**
     * Get queue capacity from QueuedThreadPool
     * Jetty's queue capacity is typically unbounded, we estimate based on max threads
     */
    private int getQueueCapacity(QueuedThreadPool threadPool) {
        // Jetty uses a BlockingArrayQueue with dynamic capacity
        // We return a reasonable estimate based on max threads
        return Math.max(threadPool.getMaxThreads() * 10, 1000);
    }
}
