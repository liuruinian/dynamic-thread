package com.dynamic.thread.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input validator for security checks.
 * Validates all incoming data to prevent injection attacks.
 */
@Slf4j
@Component
public class InputValidator {

    /**
     * Maximum length for application ID
     */
    private static final int MAX_APP_ID_LENGTH = 128;

    /**
     * Maximum length for instance ID
     */
    private static final int MAX_INSTANCE_ID_LENGTH = 256;

    /**
     * Maximum length for thread pool ID
     */
    private static final int MAX_POOL_ID_LENGTH = 128;

    /**
     * Maximum number of thread pools per instance
     */
    private static final int MAX_POOLS_PER_INSTANCE = 100;

    /**
     * Maximum message body size (1MB)
     */
    private static final int MAX_MESSAGE_BODY_SIZE = 1024 * 1024;

    /**
     * Pattern for valid identifiers (alphanumeric, dash, underscore, dot)
     */
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Pattern to detect potential injection patterns
     */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            ".*[<>\"'`;\\\\].*|.*\\$\\{.*|.*#\\{.*"
    );

    /**
     * Validate application ID.
     */
    public ValidationResult validateAppId(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return ValidationResult.error("appId is required");
        }
        if (appId.length() > MAX_APP_ID_LENGTH) {
            return ValidationResult.error("appId exceeds maximum length of " + MAX_APP_ID_LENGTH);
        }
        if (!VALID_ID_PATTERN.matcher(appId).matches()) {
            return ValidationResult.error("appId contains invalid characters");
        }
        if (containsInjection(appId)) {
            log.warn("Potential injection detected in appId: {}", sanitizeForLog(appId));
            return ValidationResult.error("appId contains suspicious patterns");
        }
        return ValidationResult.ok();
    }

    /**
     * Validate instance ID.
     */
    public ValidationResult validateInstanceId(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return ValidationResult.error("instanceId is required");
        }
        if (instanceId.length() > MAX_INSTANCE_ID_LENGTH) {
            return ValidationResult.error("instanceId exceeds maximum length of " + MAX_INSTANCE_ID_LENGTH);
        }
        if (!VALID_ID_PATTERN.matcher(instanceId).matches()) {
            // Instance ID may contain IP:port, so allow colon
            if (!instanceId.matches("^[a-zA-Z0-9._:-]+$")) {
                return ValidationResult.error("instanceId contains invalid characters");
            }
        }
        if (containsInjection(instanceId)) {
            log.warn("Potential injection detected in instanceId: {}", sanitizeForLog(instanceId));
            return ValidationResult.error("instanceId contains suspicious patterns");
        }
        return ValidationResult.ok();
    }

    /**
     * Validate thread pool ID.
     */
    public ValidationResult validateThreadPoolId(String poolId) {
        if (poolId == null || poolId.trim().isEmpty()) {
            return ValidationResult.error("threadPoolId is required");
        }
        if (poolId.length() > MAX_POOL_ID_LENGTH) {
            return ValidationResult.error("threadPoolId exceeds maximum length of " + MAX_POOL_ID_LENGTH);
        }
        if (!poolId.matches("^[a-zA-Z0-9._-]+$")) {
            return ValidationResult.error("threadPoolId contains invalid characters");
        }
        return ValidationResult.ok();
    }

    /**
     * Validate thread pool count.
     */
    public ValidationResult validatePoolCount(int count) {
        if (count < 0) {
            return ValidationResult.error("Invalid pool count");
        }
        if (count > MAX_POOLS_PER_INSTANCE) {
            return ValidationResult.error("Too many thread pools: " + count + ", max allowed: " + MAX_POOLS_PER_INSTANCE);
        }
        return ValidationResult.ok();
    }

    /**
     * Validate message body size.
     */
    public ValidationResult validateMessageSize(byte[] body) {
        if (body == null) {
            return ValidationResult.ok();
        }
        if (body.length > MAX_MESSAGE_BODY_SIZE) {
            return ValidationResult.error("Message body exceeds maximum size of " + MAX_MESSAGE_BODY_SIZE + " bytes");
        }
        return ValidationResult.ok();
    }

    /**
     * Validate thread pool configuration values.
     */
    public ValidationResult validatePoolConfig(int coreSize, int maxSize, int queueCapacity) {
        if (coreSize < 0 || coreSize > 10000) {
            return ValidationResult.error("corePoolSize out of range (0-10000)");
        }
        if (maxSize < coreSize || maxSize > 10000) {
            return ValidationResult.error("maxPoolSize invalid (must be >= coreSize and <= 10000)");
        }
        if (queueCapacity < 0 || queueCapacity > 1000000) {
            return ValidationResult.error("queueCapacity out of range (0-1000000)");
        }
        return ValidationResult.ok();
    }

    /**
     * Check for potential injection patterns.
     */
    private boolean containsInjection(String input) {
        return INJECTION_PATTERN.matcher(input).matches();
    }

    /**
     * Sanitize string for safe logging.
     */
    public String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        // Truncate and remove potentially dangerous characters
        String sanitized = input.length() > 50 ? input.substring(0, 50) + "..." : input;
        return sanitized.replaceAll("[<>\"'`;\\\\$#{}]", "?");
    }

    /**
     * Validation result holder.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
}
