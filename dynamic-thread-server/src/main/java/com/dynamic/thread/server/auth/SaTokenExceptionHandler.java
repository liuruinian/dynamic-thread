package com.dynamic.thread.server.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for security and server exceptions.
 * Hides internal error details from clients.
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleNotLoginException(NotLoginException e) {
        log.warn("Authentication required: {}", e.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 401);
        result.put("message", "Please login first");
        return result;
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotPermissionException(NotPermissionException e) {
        log.warn("Permission denied: {}", e.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 403);
        result.put("message", "Permission denied");
        return result;
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotRoleException(NotRoleException e) {
        log.warn("Role required: {}", e.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 403);
        result.put("message", "Insufficient privileges");
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 400);
        result.put("message", "Invalid request parameters");
        return result;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        // Generate error ID for tracking
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        
        // Log full details for debugging
        log.error("Server error [{}]: {}", errorId, e.getMessage(), e);
        
        // Return sanitized response to client
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", 500);
        result.put("message", "Internal server error");
        result.put("errorId", errorId); // For support tracking
        return result;
    }
}
