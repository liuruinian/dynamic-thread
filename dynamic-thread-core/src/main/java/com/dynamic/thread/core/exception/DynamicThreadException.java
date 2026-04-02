package com.dynamic.thread.core.exception;

/**
 * Base exception for dynamic thread pool framework.
 * All custom exceptions in this framework should extend this class.
 */
public class DynamicThreadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DynamicThreadException(String message) {
        super(message);
    }

    public DynamicThreadException(String message, Throwable cause) {
        super(message, cause);
    }

    public DynamicThreadException(Throwable cause) {
        super(cause);
    }
}
