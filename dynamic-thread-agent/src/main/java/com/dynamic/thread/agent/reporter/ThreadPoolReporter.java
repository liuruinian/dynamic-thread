package com.dynamic.thread.agent.reporter;

import com.dynamic.thread.agent.client.AgentNettyClient;
import com.dynamic.thread.agent.config.AgentProperties;
import com.dynamic.thread.agent.handler.AgentChannelHandler;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Reporter for sending thread pool state to Dashboard Server.
 * Supports both business thread pools and web container thread pools.
 */
@Slf4j
public class ThreadPoolReporter {

    private final AgentProperties properties;
    private final AgentNettyClient client;
    private final AgentChannelHandler channelHandler;
    private final ThreadPoolRegistry registry;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    
    /** Supplier for web container thread pool state (set via setter) */
    private Supplier<ThreadPoolState> webContainerStateSupplier;

    public ThreadPoolReporter(AgentProperties properties,
                              AgentNettyClient client,
                              AgentChannelHandler channelHandler,
                              ThreadPoolRegistry registry) {
        this.properties = properties;
        this.client = client;
        this.channelHandler = channelHandler;
        this.registry = registry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "thread-pool-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set the web container thread pool state supplier
     * @param supplier supplier that returns web container thread pool state
     */
    public void setWebContainerStateSupplier(Supplier<ThreadPoolState> supplier) {
        this.webContainerStateSupplier = supplier;
        log.info("Web container thread pool state supplier registered");
    }

    /**
     * Start the reporter
     */
    public void start() {
        int interval = properties.getReport().getInterval();
        log.info("Starting thread pool reporter with interval: {}s", interval);
        
        scheduler.scheduleAtFixedRate(this::report, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * Report current thread pool states (including web container if available)
     */
    private void report() {
        if (!client.isConnected()) {
            log.debug("Not connected to server, skip reporting");
            return;
        }

        try {
            // Collect business thread pool states
            Map<String, ThreadPoolState> states = registry.listStates();
            List<ThreadPoolState> stateList = new ArrayList<>(states.values());
            
            // Add web container thread pool state if available
            if (webContainerStateSupplier != null) {
                try {
                    ThreadPoolState webState = webContainerStateSupplier.get();
                    if (webState != null) {
                        stateList.add(webState);
                    }
                } catch (Exception e) {
                    log.debug("Failed to get web container state: {}", e.getMessage());
                }
            }
            
            if (stateList.isEmpty()) {
                return;
            }

            String body = objectMapper.writeValueAsString(stateList);

            Message message = Message.stateReport(
                    channelHandler.getAppId(),
                    channelHandler.getInstanceId(),
                    body
            );

            client.send(message);
            log.debug("Reported {} thread pool states", stateList.size());
            
        } catch (Exception e) {
            log.error("Failed to report thread pool states: {}", e.getMessage());
        }
    }

    /**
     * Stop the reporter
     */
    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Thread pool reporter stopped");
    }
}
