package com.dynamic.thread.starter.nacos.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import com.dynamic.thread.starter.nacos.listener.NacosCloudConfigChangeListener;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Nacos Cloud dynamic thread pool starter.
 */
@Configuration
@ConditionalOnClass(NacosConfigManager.class)
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NacosCloudStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(NacosConfigManager.class)
    public NacosCloudConfigChangeListener nacosCloudConfigChangeListener(
            ThreadPoolRefresher refresher,
            NacosConfigManager nacosConfigManager,
            DynamicThreadPoolProperties properties) {
        return new NacosCloudConfigChangeListener(refresher, nacosConfigManager, properties);
    }

    /**
     * Lifecycle bean to start/stop the listener
     */
    @Bean
    @ConditionalOnBean(NacosCloudConfigChangeListener.class)
    public NacosListenerLifecycle nacosListenerLifecycle(NacosCloudConfigChangeListener listener) {
        return new NacosListenerLifecycle(listener);
    }

    /**
     * Lifecycle management for Nacos listener
     */
    public static class NacosListenerLifecycle {
        private final NacosCloudConfigChangeListener listener;

        public NacosListenerLifecycle(NacosCloudConfigChangeListener listener) {
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
