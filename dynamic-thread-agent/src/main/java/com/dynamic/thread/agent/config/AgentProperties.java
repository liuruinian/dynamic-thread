package com.dynamic.thread.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Dynamic Thread Pool Agent.
 */
@Data
@ConfigurationProperties(prefix = "dynamic-thread.agent")
public class AgentProperties {

    /**
     * Whether to enable the agent
     */
    private boolean enabled = true;

    /**
     * Application ID for identification
     */
    private String appId;

    /**
     * Dashboard server configuration
     */
    private ServerConfig server = new ServerConfig();

    /**
     * Heartbeat configuration
     */
    private HeartbeatConfig heartbeat = new HeartbeatConfig();

    /**
     * Report configuration
     */
    private ReportConfig report = new ReportConfig();

    @Data
    public static class ServerConfig {
        /**
         * Dashboard server host
         */
        private String host = "127.0.0.1";

        /**
         * Dashboard server port
         */
        private int port = 9527;

        /**
         * Connection timeout in milliseconds
         */
        private int connectTimeout = 5000;

        /**
         * Reconnect delay in milliseconds
         */
        private int reconnectDelay = 5000;
    }

    @Data
    public static class HeartbeatConfig {
        /**
         * Heartbeat interval in seconds
         */
        private int interval = 30;

        /**
         * Read idle timeout in seconds (trigger reconnect)
         */
        private int readIdleTimeout = 60;
    }

    @Data
    public static class ReportConfig {
        /**
         * State report interval in seconds
         */
        private int interval = 5;
    }
}
