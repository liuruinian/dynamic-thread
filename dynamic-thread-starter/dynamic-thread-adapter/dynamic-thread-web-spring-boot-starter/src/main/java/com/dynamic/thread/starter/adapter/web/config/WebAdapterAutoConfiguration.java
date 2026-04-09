package com.dynamic.thread.starter.adapter.web.config;

import com.dynamic.thread.agent.handler.AgentChannelHandler;
import com.dynamic.thread.agent.reporter.ThreadPoolReporter;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.adapter.web.*;
import com.dynamic.thread.starter.adapter.web.metrics.WebContainerMetricsCollector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Auto-configuration for web container thread pool adapters.
 * 
 * <p>This configuration automatically detects and registers the appropriate
 * adapter based on the web container in use:
 * <ul>
 *   <li>Tomcat - {@link TomcatThreadPoolAdapter}</li>
 *   <li>Jetty - {@link JettyThreadPoolAdapter}</li>
 *   <li>Undertow - {@link UndertowThreadPoolAdapter}</li>
 * </ul>
 * 
 * <p>Configuration property: {@code dynamic-thread.enabled=true} (default)
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebAdapterAutoConfiguration {

    // ==================== Adapter Beans ====================

    /**
     * Tomcat thread pool adapter
     */
    @Bean
    @ConditionalOnMissingBean(TomcatThreadPoolAdapter.class)
    @ConditionalOnClass(name = "org.apache.catalina.startup.Tomcat")
    public TomcatThreadPoolAdapter tomcatThreadPoolAdapter() {
        return new TomcatThreadPoolAdapter();
    }

    /**
     * Jetty thread pool adapter
     */
    @Bean
    @ConditionalOnMissingBean(JettyThreadPoolAdapter.class)
    @ConditionalOnClass(name = "org.eclipse.jetty.server.Server")
    public JettyThreadPoolAdapter jettyThreadPoolAdapter() {
        return new JettyThreadPoolAdapter();
    }

    /**
     * Undertow thread pool adapter
     */
    @Bean
    @ConditionalOnMissingBean(UndertowThreadPoolAdapter.class)
    @ConditionalOnClass(name = "io.undertow.Undertow")
    public UndertowThreadPoolAdapter undertowThreadPoolAdapter() {
        return new UndertowThreadPoolAdapter();
    }

    // ==================== Manager Bean ====================

    /**
     * Web container thread pool manager
     */
    @Bean
    @ConditionalOnMissingBean
    public WebContainerThreadPoolManager webContainerThreadPoolManager(
            List<WebContainerThreadPoolAdapter> adapters,
            ApplicationContext applicationContext) {
        return new WebContainerThreadPoolManager(adapters, applicationContext);
    }

    // ==================== Metrics Integration ====================

    /**
     * Metrics configuration - isolated in nested class to ensure MeterRegistry
     * is available when condition is evaluated.
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public WebContainerMetricsCollector webContainerMetricsCollector(
                WebContainerThreadPoolManager manager,
                MeterRegistry meterRegistry) {
            return new WebContainerMetricsCollector(manager, meterRegistry);
        }
    }

    // ==================== Reporter Integration ====================

    /**
     * Integration configuration to register web container state with ThreadPoolReporter
     */
    @Configuration
    @ConditionalOnBean(ThreadPoolReporter.class)
    static class WebContainerReporterIntegration {

        @Autowired(required = false)
        private ThreadPoolReporter threadPoolReporter;

        @Autowired
        private WebContainerThreadPoolManager webContainerManager;

        @PostConstruct
        public void registerWebContainerWithReporter() {
            if (threadPoolReporter != null && webContainerManager != null) {
                threadPoolReporter.setWebContainerStateSupplier(webContainerManager::getThreadPoolState);
                log.info("Web container thread pool integrated with reporter");
            }
        }
    }

    /**
     * Integration to handle web container config updates from Server via Agent
     */
    @Configuration
    @ConditionalOnBean(AgentChannelHandler.class)
    static class WebContainerAgentIntegration {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        @Autowired
        private AgentChannelHandler agentChannelHandler;

        @Autowired
        private WebContainerThreadPoolManager webContainerManager;

        @PostConstruct
        public void registerWebContainerConfigHandler() {
            agentChannelHandler.setWebContainerConfigHandler(this::handleConfigUpdate);
            log.info("Web container config handler registered with agent");
        }

        private void handleConfigUpdate(String configJson) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(configJson);
                
                DynamicThreadPoolProperties.WebThreadPoolProperties config = 
                        new DynamicThreadPoolProperties.WebThreadPoolProperties();
                
                if (root.has("corePoolSize") && !root.get("corePoolSize").isNull()) {
                    config.setCorePoolSize(root.get("corePoolSize").asInt());
                }
                if (root.has("maximumPoolSize") && !root.get("maximumPoolSize").isNull()) {
                    config.setMaximumPoolSize(root.get("maximumPoolSize").asInt());
                }
                if (root.has("keepAliveTime") && !root.get("keepAliveTime").isNull()) {
                    config.setKeepAliveTime(root.get("keepAliveTime").asLong());
                }
                
                boolean success = webContainerManager.updateThreadPool(config);
                if (!success) {
                    throw new RuntimeException("Failed to update web container thread pool");
                }
                
                log.info("Web container config updated: corePoolSize={}, maximumPoolSize={}, keepAliveTime={}",
                        config.getCorePoolSize(), config.getMaximumPoolSize(), config.getKeepAliveTime());
                        
            } catch (Exception e) {
                log.error("Failed to parse web container config: {}", e.getMessage());
                throw new RuntimeException("Failed to parse config: " + e.getMessage(), e);
            }
        }
    }
}
