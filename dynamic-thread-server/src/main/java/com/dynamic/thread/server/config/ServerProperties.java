package com.dynamic.thread.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for Dynamic Thread Pool Server.
 */
@Data
@ConfigurationProperties(prefix = "dynamic-thread.server")
public class ServerProperties {

    /**
     * Netty server configuration
     */
    private final NettyConfig netty = new NettyConfig();

    /**
     * Authentication configuration
     */
    private final AuthConfig auth = new AuthConfig();

    /**
     * Security configuration
     */
    private final SecurityConfig security = new SecurityConfig();

    /**
     * Cluster configuration
     */
    private final ClusterConfig cluster = new ClusterConfig();

    /**
     * Notification platforms configuration
     */
    private List<Map<String, String>> notifyPlatforms = new ArrayList<>();

    @Data
    public static class NettyConfig {
        /**
         * Netty server port
         */
        private int port = 9527;

        /**
         * Boss group thread count
         */
        private int bossThreads = 1;

        /**
         * Worker group thread count
         */
        private int workerThreads = 4;

        /**
         * Read idle timeout in seconds
         */
        private int readIdleTimeout = 90;

        /**
         * Enable SSL/TLS for Netty server
         */
        private boolean sslEnabled = false;

        /**
         * Path to SSL certificate file (PEM format)
         */
        private String sslCertPath;

        /**
         * Path to SSL private key file (PEM format)
         */
        private String sslKeyPath;
    }

    @Data
    public static class AuthConfig {
        /**
         * Admin username
         */
        private String username = "admin";

        /**
         * Admin password
         */
        private String password = "admin123";
    }

    @Data
    public static class SecurityConfig {
        /**
         * Maximum total connections allowed
         */
        private int maxConnections = 10000;

        /**
         * Maximum connections per IP address
         */
        private int maxConnectionsPerIp = 100;

        /**
         * Maximum connections per application
         */
        private int maxConnectionsPerApp = 500;

        /**
         * Connection rate limit per IP (requests per minute)
         */
        private int connectRateLimitPerMinute = 60;

        /**
         * Maximum login attempts before lockout
         */
        private int maxLoginAttempts = 5;

        /**
         * Login lockout duration in minutes
         */
        private int loginLockoutMinutes = 15;

        /**
         * Agent authentication secret key (HMAC-SHA256)
         */
        private String agentSecretKey = "dynamic-thread-default-secret-key-change-in-production";

        /**
         * Enable agent authentication (HMAC signature validation)
         */
        private boolean agentAuthEnabled = false;
    }

    @Data
    public static class ClusterConfig {
        /**
         * Enable cluster mode
         */
        private boolean enabled = false;

        /**
         * Current node ID (auto-generated if not specified)
         */
        private String nodeId;

        /**
         * Current node external address (ip:clusterPort)
         */
        private String nodeAddress;

        /**
         * Inter-node communication port
         */
        private int clusterPort = 9528;

        /**
         * Cluster member list (e.g., ["ip1:port1","ip2:port2"])
         */
        private List<String> members = new ArrayList<>();

        /**
         * Full sync interval in milliseconds
         */
        private int syncIntervalMs = 3000;

        /**
         * Node heartbeat interval in milliseconds
         */
        private int heartbeatIntervalMs = 5000;

        /**
         * Node timeout in milliseconds
         */
        private int nodeTimeoutMs = 15000;
    }
}
