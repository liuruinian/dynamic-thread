package com.dynamic.thread.agent.config;

import com.dynamic.thread.agent.client.AgentNettyClient;
import com.dynamic.thread.agent.handler.AgentChannelHandler;
import com.dynamic.thread.agent.reporter.ThreadPoolReporter;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Dynamic Thread Pool Agent.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "dynamic-thread.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry() {
        return ThreadPoolRegistry.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentChannelHandler agentChannelHandler(AgentProperties properties, 
                                                    ThreadPoolRegistry registry) {
        return new AgentChannelHandler(properties, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentNettyClient agentNettyClient(AgentProperties properties,
                                              AgentChannelHandler channelHandler) {
        AgentNettyClient client = new AgentNettyClient(properties, channelHandler);
        
        // Set reconnect callback
        channelHandler.setReconnectCallback(client::scheduleReconnect);
        
        // Start client
        client.start();
        
        log.info("Agent Netty client started, connecting to {}:{}",
                properties.getServer().getHost(), properties.getServer().getPort());
        
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolReporter threadPoolReporter(AgentProperties properties,
                                                  AgentNettyClient client,
                                                  AgentChannelHandler channelHandler,
                                                  ThreadPoolRegistry registry) {
        ThreadPoolReporter reporter = new ThreadPoolReporter(properties, client, channelHandler, registry);
        reporter.start();
        return reporter;
    }
}
