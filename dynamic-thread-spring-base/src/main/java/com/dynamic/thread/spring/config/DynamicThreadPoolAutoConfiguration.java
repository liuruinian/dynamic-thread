package com.dynamic.thread.spring.config;

import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.spring.banner.DynamicThreadPoolBanner;
import com.dynamic.thread.spring.processor.DynamicThreadPoolBeanPostProcessor;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.spring.shutdown.ThreadPoolShutdownLifecycle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Dynamic Thread Pool Spring Base module.
 */
@Configuration
@EnableConfigurationProperties(DynamicThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicThreadPoolAutoConfiguration {

    /**
     * Create ThreadPoolRegistry bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRegistry threadPoolRegistry() {
        return ThreadPoolRegistry.getInstance();
    }

    /**
     * Create BeanPostProcessor for scanning dynamic thread pools
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicThreadPoolBeanPostProcessor dynamicThreadPoolBeanPostProcessor(
            DynamicThreadPoolProperties properties,
            ThreadPoolRegistry registry) {
        return new DynamicThreadPoolBeanPostProcessor(properties, registry);
    }

    /**
     * Create graceful shutdown lifecycle bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic-thread.shutdown", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ThreadPoolShutdownLifecycle threadPoolShutdownLifecycle(
            ThreadPoolRegistry registry,
            @Value("${dynamic-thread.shutdown.await-termination-seconds:30}") int awaitSeconds,
            @Value("${dynamic-thread.shutdown.force-shutdown-seconds:60}") int forceSeconds) {
        return new ThreadPoolShutdownLifecycle(registry, awaitSeconds, forceSeconds);
    }

    /**
     * Create banner printer
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic-thread", name = "banner", havingValue = "true", matchIfMissing = true)
    public DynamicThreadPoolBanner dynamicThreadPoolBanner(DynamicThreadPoolProperties properties) {
        return new DynamicThreadPoolBanner(Boolean.TRUE.equals(properties.getBanner()));
    }
}
