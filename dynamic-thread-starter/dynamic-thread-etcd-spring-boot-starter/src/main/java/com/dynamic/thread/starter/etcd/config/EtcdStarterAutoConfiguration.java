package com.dynamic.thread.starter.etcd.config;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import com.dynamic.thread.starter.etcd.listener.EtcdConfigChangeListener;
import io.etcd.jetcd.Client;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for ETCD dynamic thread pool starter.
 */
@Configuration
@ConditionalOnClass(Client.class)
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EtcdStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic-thread.etcd", name = "endpoints")
    public EtcdConfigChangeListener etcdConfigChangeListener(
            ThreadPoolRefresher refresher,
            DynamicThreadPoolProperties properties) {
        return new EtcdConfigChangeListener(refresher, properties);
    }

    /**
     * Lifecycle bean to start/stop the listener
     */
    @Bean
    @ConditionalOnProperty(prefix = "dynamic-thread.etcd", name = "endpoints")
    public EtcdListenerLifecycle etcdListenerLifecycle(EtcdConfigChangeListener listener) {
        return new EtcdListenerLifecycle(listener);
    }

    /**
     * Lifecycle management for ETCD listener
     */
    public static class EtcdListenerLifecycle {
        private final EtcdConfigChangeListener listener;

        public EtcdListenerLifecycle(EtcdConfigChangeListener listener) {
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
