package com.dynamic.thread.starter.file.config;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import com.dynamic.thread.starter.file.listener.FileConfigChangeListener;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for local file dynamic thread pool starter.
 */
@Configuration
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic-thread.file", name = "path")
    public FileConfigChangeListener fileConfigChangeListener(
            ThreadPoolRefresher refresher,
            DynamicThreadPoolProperties properties) {
        return new FileConfigChangeListener(refresher, properties);
    }

    /**
     * Lifecycle bean to start/stop the listener
     */
    @Bean
    @ConditionalOnProperty(prefix = "dynamic-thread.file", name = "path")
    public FileListenerLifecycle fileListenerLifecycle(FileConfigChangeListener listener) {
        return new FileListenerLifecycle(listener);
    }

    /**
     * Lifecycle management for File listener
     */
    public static class FileListenerLifecycle {
        private final FileConfigChangeListener listener;

        public FileListenerLifecycle(FileConfigChangeListener listener) {
            this.listener = listener;
        }

        @PostConstruct
        public void start() {
            listener.startListening();
        }

        @PreDestroy
        public void stop() {
            listener.stopListening();
        }
    }
}
