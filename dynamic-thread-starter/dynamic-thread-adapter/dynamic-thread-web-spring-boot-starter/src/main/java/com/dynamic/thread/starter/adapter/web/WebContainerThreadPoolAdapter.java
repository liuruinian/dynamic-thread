package com.dynamic.thread.starter.adapter.web;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import org.springframework.boot.web.server.WebServer;

/**
 * Web container thread pool adapter interface.
 * Provides abstraction for different web container implementations.
 */
public interface WebContainerThreadPoolAdapter {

    /**
     * Get adapter name
     *
     * @return the adapter name (e.g., "Tomcat", "Jetty")
     */
    String getAdapterName();

    /**
     * Check if this adapter matches the given web server
     *
     * @param webServer the web server to check
     * @return true if this adapter can handle the web server
     */
    boolean match(WebServer webServer);

    /**
     * Get thread pool state from web server
     *
     * @param webServer the web server
     * @return the thread pool state
     */
    ThreadPoolState getThreadPoolState(WebServer webServer);

    /**
     * Update thread pool configuration
     *
     * @param webServer the web server
     * @param config    the new configuration
     */
    void updateThreadPool(WebServer webServer, DynamicThreadPoolProperties.WebThreadPoolProperties config);
}
