package com.dynamic.thread.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;

/**
 * Common components factory providing shared instances of frequently used objects.
 * Implements Singleton pattern for ObjectMapper to avoid repeated instantiation.
 */
public final class CommonComponents {

    /**
     * Shared ObjectMapper instance with common configurations
     */
    private static final ObjectMapper OBJECT_MAPPER;

    /**
     * Default connection timeout in seconds
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    /**
     * Default request timeout in seconds
     */
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 10;

    static {
        // Initialize ObjectMapper with common modules and configurations
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.findAndRegisterModules();
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Private constructor to prevent instantiation
    private CommonComponents() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Get the shared ObjectMapper instance.
     * This instance is thread-safe for read operations and serialization/deserialization.
     *
     * @return the shared ObjectMapper instance
     */
    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Get the default request timeout in milliseconds.
     *
     * @return the default request timeout in ms
     */
    public static int defaultRequestTimeoutMs() {
        return DEFAULT_REQUEST_TIMEOUT_SECONDS * 1000;
    }

    /**
     * Get the default request timeout duration.
     *
     * @return the default request timeout
     */
    public static Duration defaultRequestTimeout() {
        return Duration.ofSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS);
    }

    /**
     * Get the default connect timeout in milliseconds.
     *
     * @return the default connect timeout in ms
     */
    public static int defaultConnectTimeoutMs() {
        return DEFAULT_CONNECT_TIMEOUT_SECONDS * 1000;
    }

    /**
     * Get the default connect timeout duration.
     *
     * @return the default connect timeout
     */
    public static Duration defaultConnectTimeout() {
        return Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS);
    }
}
