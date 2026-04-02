package com.dynamic.thread.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Login attempt limiter to prevent brute-force attacks.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    /**
     * Maximum failed login attempts before lockout
     */
    private int maxAttempts = 5;

    /**
     * Lockout duration in minutes
     */
    private int lockoutMinutes = 15;

    /**
     * Failed attempts tracking
     */
    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    /**
     * Check if login attempt should be allowed.
     *
     * @param username Username attempting to login
     * @param ip       IP address of the request
     * @return true if attempt should be allowed
     */
    public boolean isAllowed(String username, String ip) {
        String key = buildKey(username, ip);
        AttemptInfo info = attempts.get(key);
        
        if (info == null) {
            return true;
        }
        
        // Check if lockout has expired
        if (info.isLocked() && System.currentTimeMillis() > info.lockoutExpiry) {
            attempts.remove(key);
            log.info("Lockout expired for: {}", key);
            return true;
        }
        
        return !info.isLocked();
    }

    /**
     * Record a failed login attempt.
     *
     * @param username Username that failed to login
     * @param ip       IP address of the request
     */
    public void recordFailure(String username, String ip) {
        String key = buildKey(username, ip);
        
        AttemptInfo info = attempts.computeIfAbsent(key, k -> new AttemptInfo());
        
        synchronized (info) {
            info.failedAttempts++;
            info.lastAttempt = System.currentTimeMillis();
            
            if (info.failedAttempts >= maxAttempts) {
                info.lockoutExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(lockoutMinutes);
                log.warn("Account locked due to {} failed attempts: username={}, ip={}, lockout={}min",
                        info.failedAttempts, username, ip, lockoutMinutes);
            } else {
                log.info("Failed login attempt {}/{} for username={}, ip={}",
                        info.failedAttempts, maxAttempts, username, ip);
            }
        }
    }

    /**
     * Clear attempts on successful login.
     *
     * @param username Username that logged in successfully
     * @param ip       IP address of the request
     */
    public void recordSuccess(String username, String ip) {
        String key = buildKey(username, ip);
        attempts.remove(key);
    }

    /**
     * Get remaining attempts before lockout.
     *
     * @param username Username
     * @param ip       IP address
     * @return remaining attempts, or -1 if locked
     */
    public int getRemainingAttempts(String username, String ip) {
        String key = buildKey(username, ip);
        AttemptInfo info = attempts.get(key);
        
        if (info == null) {
            return maxAttempts;
        }
        
        if (info.isLocked()) {
            return -1;
        }
        
        return Math.max(0, maxAttempts - info.failedAttempts);
    }

    /**
     * Get lockout expiry time in milliseconds.
     *
     * @param username Username
     * @param ip       IP address
     * @return lockout expiry timestamp, or 0 if not locked
     */
    public long getLockoutExpiry(String username, String ip) {
        String key = buildKey(username, ip);
        AttemptInfo info = attempts.get(key);
        
        if (info != null && info.isLocked()) {
            return info.lockoutExpiry;
        }
        
        return 0;
    }

    /**
     * Build tracking key from username and IP.
     */
    private String buildKey(String username, String ip) {
        return username + "@" + ip;
    }

    // Configuration setters
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setLockoutMinutes(int lockoutMinutes) {
        this.lockoutMinutes = lockoutMinutes;
    }

    /**
     * Attempt tracking info.
     */
    private static class AttemptInfo {
        int failedAttempts = 0;
        long lastAttempt = 0;
        long lockoutExpiry = 0;

        boolean isLocked() {
            return lockoutExpiry > 0 && System.currentTimeMillis() < lockoutExpiry;
        }
    }
}
