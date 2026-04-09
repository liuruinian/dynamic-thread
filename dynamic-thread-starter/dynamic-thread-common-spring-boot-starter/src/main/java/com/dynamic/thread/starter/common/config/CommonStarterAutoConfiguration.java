package com.dynamic.thread.starter.common.config;

import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.starter.common.metrics.ThreadPoolMetricsCollector;
import com.dynamic.thread.starter.common.parser.ConfigParser;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for common starter components.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommonStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigParser configParser() {
        return new ConfigParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolRefresher threadPoolRefresher(ThreadPoolRegistry registry, 
                                                    ConfigParser parser,
                                                    ApplicationEventPublisher eventPublisher) {
        return new ThreadPoolRefresher(registry, parser, eventPublisher);
    }

    /**
     * Metrics configuration - isolated in nested class to ensure MeterRegistry
     * is available when condition is evaluated.
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ThreadPoolMetricsCollector threadPoolMetricsCollector(
                ThreadPoolRegistry registry,
                MeterRegistry meterRegistry) {
            return new ThreadPoolMetricsCollector(registry, meterRegistry);
        }
    }
}
