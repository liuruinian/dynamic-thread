package com.dynamic.thread.server.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Access Log Filter for recording HTTP request/response information.
 * 
 * <p>This filter logs all HTTP requests with timing information, status codes,
 * and request metadata for monitoring and debugging purposes.</p>
 * 
 * <h3>Log Format:</h3>
 * <pre>
 * {traceId} {method} {uri} {status} {duration}ms
 * </pre>
 * 
 * @author Dynamic Thread Pool
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter implements Filter {

    /**
     * Dedicated logger for access logs.
     * This logger writes to a separate access log file.
     */
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    /** Paths to exclude from access logging */
    private static final String[] EXCLUDED_PATHS = {
        "/actuator/health",
        "/actuator/prometheus",
        "/favicon.ico"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip logging for excluded paths
        String uri = httpRequest.getRequestURI();
        if (shouldExclude(uri)) {
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logAccess(httpRequest, httpResponse, duration);
        }
    }

    /**
     * Check if the request URI should be excluded from access logging.
     *
     * @param uri the request URI
     * @return true if should be excluded
     */
    private boolean shouldExclude(String uri) {
        for (String excluded : EXCLUDED_PATHS) {
            if (uri.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Log access information.
     *
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param duration request processing time in milliseconds
     */
    private void logAccess(HttpServletRequest request, HttpServletResponse response, long duration) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        int status = response.getStatus();

        // Build log message
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(method).append(" ");
        logMessage.append(uri);
        if (queryString != null) {
            logMessage.append("?").append(queryString);
        }
        logMessage.append(" -> ").append(status);
        logMessage.append(" (").append(duration).append("ms)");

        // Log with appropriate level based on status code
        if (status >= 500) {
            ACCESS_LOG.error(logMessage.toString());
        } else if (status >= 400) {
            ACCESS_LOG.warn(logMessage.toString());
        } else {
            ACCESS_LOG.info(logMessage.toString());
        }
    }
}
