package com.dynamic.thread.starter.adapter.web;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * Tomcat web container thread pool adapter.
 * Provides dynamic configuration and monitoring for Tomcat's thread pool.
 */
@Slf4j
public class TomcatThreadPoolAdapter implements WebContainerThreadPoolAdapter {

    private static final String ADAPTER_NAME = "Tomcat";

    @Override
    public String getAdapterName() {
        return ADAPTER_NAME;
    }

    @Override
    public boolean match(WebServer webServer) {
        return webServer instanceof TomcatWebServer;
    }

    @Override
    public ThreadPoolState getThreadPoolState(WebServer webServer) {
        TomcatWebServer tomcatWebServer = (TomcatWebServer) webServer;
        ThreadPoolExecutor executor = getTomcatExecutor(tomcatWebServer);
        
        if (executor == null) {
            return null;
        }

        ThreadPoolState state = ThreadPoolState.builder()
                .threadPoolId("tomcat-web-container")
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .poolSize(executor.getPoolSize())
                .activeCount(executor.getActiveCount())
                .largestPoolSize(executor.getLargestPoolSize())
                .queueCapacity(executor.getQueue().size() + executor.getQueue().remainingCapacity())
                .queueSize(executor.getQueue().size())
                .queueRemainingCapacity(executor.getQueue().remainingCapacity())
                .completedTaskCount(executor.getCompletedTaskCount())
                .taskCount(executor.getTaskCount())
                .keepAliveTime(executor.getKeepAliveTime(java.util.concurrent.TimeUnit.SECONDS))
                .rejectedHandler(executor.getRejectedExecutionHandler().getClass().getSimpleName())
                .timestamp(LocalDateTime.now())
                .build();

        state.calculateMetrics();
        return state;
    }

    @Override
    public void updateThreadPool(WebServer webServer, DynamicThreadPoolProperties.WebThreadPoolProperties config) {
        TomcatWebServer tomcatWebServer = (TomcatWebServer) webServer;
        ThreadPoolExecutor executor = getTomcatExecutor(tomcatWebServer);

        if (executor == null) {
            log.warn("Cannot find Tomcat thread pool executor");
            return;
        }

        int oldCoreSize = executor.getCorePoolSize();
        int oldMaxSize = executor.getMaximumPoolSize();

        // Update core pool size
        if (config.getCorePoolSize() != null) {
            if (config.getCorePoolSize() > executor.getMaximumPoolSize()) {
                executor.setMaximumPoolSize(config.getMaximumPoolSize());
                executor.setCorePoolSize(config.getCorePoolSize());
            } else {
                executor.setCorePoolSize(config.getCorePoolSize());
            }
        }

        // Update maximum pool size
        if (config.getMaximumPoolSize() != null) {
            executor.setMaximumPoolSize(config.getMaximumPoolSize());
        }

        // Update keep alive time
        if (config.getKeepAliveTime() != null) {
            executor.setKeepAliveTime(config.getKeepAliveTime(), java.util.concurrent.TimeUnit.SECONDS);
        }

        log.info("Tomcat thread pool updated: coreSize={}->{}, maxSize={}->{}",
                oldCoreSize, executor.getCorePoolSize(),
                oldMaxSize, executor.getMaximumPoolSize());
    }

    /**
     * Get Tomcat's ThreadPoolExecutor from TomcatWebServer
     */
    private ThreadPoolExecutor getTomcatExecutor(TomcatWebServer tomcatWebServer) {
        try {
            Connector connector = tomcatWebServer.getTomcat().getConnector();
            ProtocolHandler protocolHandler = connector.getProtocolHandler();
            Executor executor = protocolHandler.getExecutor();

            if (executor instanceof ThreadPoolExecutor) {
                return (ThreadPoolExecutor) executor;
            }
        } catch (Exception e) {
            log.error("Failed to get Tomcat thread pool executor", e);
        }
        return null;
    }
}
