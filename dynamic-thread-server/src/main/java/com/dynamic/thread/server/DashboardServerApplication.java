package com.dynamic.thread.server;

import com.dynamic.thread.server.cluster.ClusterAutoConfiguration;
import com.dynamic.thread.server.config.ServerProperties;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.netty.DashboardNettyServer;
import com.dynamic.thread.server.registry.ClientRegistry;
import com.dynamic.thread.server.security.AgentAuthenticator;
import com.dynamic.thread.server.security.ConnectionRateLimiter;
import com.dynamic.thread.server.security.InputValidator;
import com.dynamic.thread.server.security.LoginAttemptLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Dashboard Server Application.
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(ServerProperties.class)
public class DashboardServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardServerApplication.class, args);
    }

    @Bean
    public ClientRegistry clientRegistry() {
        return ClientRegistry.getInstance();
    }

    @Bean
    public ConnectionRateLimiter connectionRateLimiter(ServerProperties properties) {
        ConnectionRateLimiter limiter = new ConnectionRateLimiter();
        ServerProperties.SecurityConfig security = properties.getSecurity();
        limiter.setMaxConnections(security.getMaxConnections());
        limiter.setMaxConnectionsPerIp(security.getMaxConnectionsPerIp());
        limiter.setMaxConnectionsPerApp(security.getMaxConnectionsPerApp());
        limiter.setConnectRateLimitPerMinute(security.getConnectRateLimitPerMinute());
        return limiter;
    }

    @Bean
    public InputValidator inputValidator() {
        return new InputValidator();
    }

    @Bean
    public AgentAuthenticator agentAuthenticator(ServerProperties properties) {
        AgentAuthenticator authenticator = new AgentAuthenticator();
        authenticator.setSecretKey(properties.getSecurity().getAgentSecretKey());
        return authenticator;
    }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(ServerProperties properties) {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        ServerProperties.SecurityConfig security = properties.getSecurity();
        limiter.setMaxAttempts(security.getMaxLoginAttempts());
        limiter.setLockoutMinutes(security.getLoginLockoutMinutes());
        return limiter;
    }

    @Bean
    public ServerChannelHandler serverChannelHandler(ClientRegistry clientRegistry,
                                                     ConnectionRateLimiter rateLimiter,
                                                     InputValidator inputValidator,
                                                     @Autowired(required = false) ClusterAutoConfiguration clusterAutoConfig) {
        ServerChannelHandler handler = new ServerChannelHandler(clientRegistry, rateLimiter, inputValidator);
        // Wire handler into cluster config for config forwarding
        if (clusterAutoConfig != null) {
            clusterAutoConfig.wireServerChannelHandler(handler);
        }
        return handler;
    }

    @Bean
    public DashboardNettyServer dashboardNettyServer(ServerProperties properties,
                                                      ServerChannelHandler channelHandler) {
        DashboardNettyServer server = new DashboardNettyServer(properties, channelHandler);
        server.start();
        return server;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
