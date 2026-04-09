package com.dynamic.thread.starter.apollo.config;

import com.ctrip.framework.apollo.ConfigService;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.apollo.listener.ApolloConfigChangeListener;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Apollo dynamic thread pool starter.
 */
@Configuration
@ConditionalOnClass(ConfigService.class)
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApolloStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApolloConfigChangeListener apolloConfigChangeListener(
            ThreadPoolRefresher refresher,
            DynamicThreadPoolProperties properties) {
        return new ApolloConfigChangeListener(refresher, properties);
    }

    /**
     * Lifecycle bean to start/stop the listener
     */
    @Bean
    public ApolloListenerLifecycle apolloListenerLifecycle(ApolloConfigChangeListener listener) {
        return new ApolloListenerLifecycle(listener);
    }

    /**
     * Lifecycle management for Apollo listener
     */
    public static class ApolloListenerLifecycle {
        private final ApolloConfigChangeListener listener;

        public ApolloListenerLifecycle(ApolloConfigChangeListener listener) {
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
