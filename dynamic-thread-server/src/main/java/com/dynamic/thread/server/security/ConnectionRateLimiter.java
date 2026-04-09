package com.dynamic.thread.server.security;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection rate limiter for Netty server.
 * Provides protection against DDoS and resource exhaustion attacks.
 */
@Slf4j
@Component
public class ConnectionRateLimiter {

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
     * Connection attempts rate limit per IP (requests per minute)
     */
    private int connectRateLimitPerMinute = 60;

    /**
     * Current total connection count
     */
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    /**
     * Connections per IP address
     */
    private final Map<String, AtomicInteger> connectionsPerIp = new ConcurrentHashMap<>();

    /**
     * Connections per application
     */
    private final Map<String, AtomicInteger> connectionsPerApp = new ConcurrentHashMap<>();

    /**
     * Connection attempts per IP (for rate limiting)
     */
    private final Map<String, RateLimitEntry> connectionAttempts = new ConcurrentHashMap<>();

    /**
     * Blacklisted IP addresses
     */
    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();

    /**
     * Whitelisted IP addresses (bypass rate limiting)
     */
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();

    /**
     * Check if a new connection should be accepted.
     *
     * @param ctx Channel context
     * @return true if connection should be accepted
     */
    public boolean acceptConnection(ChannelHandlerContext ctx) {
        String ip = getClientIp(ctx.channel());

        // Check blacklist
        if (blacklistedIps.contains(ip)) {
            log.warn("Connection rejected: IP is blacklisted. ip={}", ip);
            return false;
        }

        // Check whitelist (skip rate limiting)
        if (whitelistedIps.contains(ip)) {
            return true;
        }

        // Check rate limit
        if (!checkRateLimit(ip)) {
            log.warn("Connection rejected: rate limit exceeded. ip={}", ip);
            return false;
        }

        // Check total connections
        if (totalConnections.get() >= maxConnections) {
            log.warn("Connection rejected: max total connections reached. current={}, max={}",
                    totalConnections.get(), maxConnections);
            return false;
        }

        // Check per-IP limit
        AtomicInteger ipCount = connectionsPerIp.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (ipCount.get() >= maxConnectionsPerIp) {
            log.warn("Connection rejected: max connections per IP reached. ip={}, current={}, max={}",
                    ip, ipCount.get(), maxConnectionsPerIp);
            return false;
        }

        return true;
    }

    /**
     * Record a successful connection.
     */
    public void onConnect(Channel channel, String appId) {
        String ip = getClientIp(channel);

        totalConnections.incrementAndGet();
        connectionsPerIp.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        
        if (appId != null) {
            connectionsPerApp.computeIfAbsent(appId, k -> new AtomicInteger(0)).incrementAndGet();
        }

        log.debug("Connection recorded: ip={}, appId={}, total={}", ip, appId, totalConnections.get());
    }

    /**
     * Record a disconnection.
     */
    public void onDisconnect(Channel channel, String appId) {
        String ip = getClientIp(channel);

        totalConnections.decrementAndGet();
        
        AtomicInteger ipCount = connectionsPerIp.get(ip);
        if (ipCount != null && ipCount.decrementAndGet() <= 0) {
            connectionsPerIp.remove(ip);
        }

        if (appId != null) {
            AtomicInteger appCount = connectionsPerApp.get(appId);
            if (appCount != null && appCount.decrementAndGet() <= 0) {
                connectionsPerApp.remove(appId);
            }
        }

        log.debug("Disconnection recorded: ip={}, appId={}, total={}", ip, appId, totalConnections.get());
    }

    /**
     * Check if an app can add more connections.
     */
    public boolean canAppConnect(String appId) {
        AtomicInteger appCount = connectionsPerApp.get(appId);
        if (appCount == null) {
            return true;
        }
        return appCount.get() < maxConnectionsPerApp;
    }

    /**
     * Check rate limit for IP.
     */
    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = connectionAttempts.computeIfAbsent(ip, k -> new RateLimitEntry());
        
        synchronized (entry) {
            // Reset if window expired (1 minute)
            if (now - entry.windowStart > 60_000) {
                entry.windowStart = now;
                entry.count = 0;
            }
            
            entry.count++;
            
            if (entry.count > connectRateLimitPerMinute) {
                // Auto-blacklist if excessive attempts
                if (entry.count > connectRateLimitPerMinute * 3) {
                    log.warn("Auto-blacklisting IP due to excessive connection attempts: ip={}", ip);
                    blacklistedIps.add(ip);
                }
                return false;
            }
        }
        
        return true;
    }

    /**
     * Extract client IP from channel.
     */
    private String getClientIp(Channel channel) {
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        if (address != null) {
            return address.getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * Add IP to blacklist.
     */
    public void blacklistIp(String ip) {
        blacklistedIps.add(ip);
        log.info("IP blacklisted: {}", ip);
    }

    /**
     * Remove IP from blacklist.
     */
    public void unblacklistIp(String ip) {
        blacklistedIps.remove(ip);
        log.info("IP removed from blacklist: {}", ip);
    }

    /**
     * Add IP to whitelist.
     */
    public void whitelistIp(String ip) {
        whitelistedIps.add(ip);
        log.info("IP whitelisted: {}", ip);
    }

    /**
     * Get current statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConnections", totalConnections.get());
        stats.put("maxConnections", maxConnections);
        stats.put("ipCount", connectionsPerIp.size());
        stats.put("appCount", connectionsPerApp.size());
        stats.put("blacklistedIps", blacklistedIps.size());
        return stats;
    }

    // Setters for configuration
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public void setMaxConnectionsPerIp(int maxConnectionsPerIp) {
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }

    public void setMaxConnectionsPerApp(int maxConnectionsPerApp) {
        this.maxConnectionsPerApp = maxConnectionsPerApp;
    }

    public void setConnectRateLimitPerMinute(int connectRateLimitPerMinute) {
        this.connectRateLimitPerMinute = connectRateLimitPerMinute;
    }

    /**
     * Rate limit tracking entry.
     */
    private static class RateLimitEntry {
        long windowStart = System.currentTimeMillis();
        int count = 0;
    }
}
