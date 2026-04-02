package com.dynamic.thread.server.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC Trace Filter for request tracking and log correlation.
 * 
 * <p>This filter automatically generates or propagates trace IDs for each HTTP request,
 * enabling distributed tracing and log correlation across services.</p>
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Generates unique trace ID for each request if not provided</li>
 *   <li>Propagates trace ID from upstream services via X-Trace-Id header</li>
 *   <li>Adds trace ID to response headers for client correlation</li>
 *   <li>Records request metadata (method, URI, client IP) in MDC</li>
 * </ul>
 * 
 * @author Dynamic Thread Pool
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    /** MDC key for trace ID */
    public static final String TRACE_ID = "traceId";
    
    /** HTTP header name for trace ID propagation */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    
    /** MDC key for HTTP method */
    public static final String METHOD = "method";
    
    /** MDC key for request URI */
    public static final String URI = "uri";
    
    /** MDC key for client IP address */
    public static final String CLIENT_IP = "clientIp";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // Get or generate trace ID
            String traceId = getOrGenerateTraceId(httpRequest);
            
            // Put trace info into MDC
            MDC.put(TRACE_ID, traceId);
            MDC.put(METHOD, httpRequest.getMethod());
            MDC.put(URI, httpRequest.getRequestURI());
            MDC.put(CLIENT_IP, getClientIp(httpRequest));
            
            // Add trace ID to response header for client correlation
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            
            // Continue filter chain
            chain.doFilter(request, response);
            
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Get trace ID from request header or generate a new one.
     *
     * @param request the HTTP request
     * @return trace ID
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        
        if (!StringUtils.hasText(traceId)) {
            // Generate short trace ID (first 8 chars of UUID)
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        
        return traceId;
    }

    /**
     * Extract client IP address from request, considering proxy headers.
     *
     * @param request the HTTP request
     * @return client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        // Check common proxy headers first
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // X-Forwarded-For may contain multiple IPs, take the first one
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        // Fall back to remote address
        return request.getRemoteAddr();
    }

    /**
     * Check if the IP address is valid and not unknown.
     *
     * @param ip the IP address to check
     * @return true if valid
     */
    private boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip);
    }
}
