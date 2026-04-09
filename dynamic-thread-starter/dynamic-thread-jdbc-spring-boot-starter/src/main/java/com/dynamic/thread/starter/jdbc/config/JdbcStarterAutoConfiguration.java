package com.dynamic.thread.starter.jdbc.config;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import com.dynamic.thread.starter.common.refresher.ThreadPoolRefresher;
import com.dynamic.thread.starter.jdbc.listener.JdbcConfigChangeListener;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Auto-configuration for JDBC database dynamic thread pool starter.
 */
@Configuration
@EnableScheduling
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "dynamic-thread", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "dynamic-thread.jdbc", name = "table-name")
    public JdbcConfigChangeListener jdbcConfigChangeListener(
            ThreadPoolRefresher refresher,
            JdbcTemplate jdbcTemplate,
            DynamicThreadPoolProperties properties) {
        return new JdbcConfigChangeListener(refresher, jdbcTemplate, properties);
    }

    /**
     * Lifecycle bean to start/stop the listener
     */
    @Bean
    @ConditionalOnBean(JdbcConfigChangeListener.class)
    public JdbcListenerLifecycle jdbcListenerLifecycle(JdbcConfigChangeListener listener) {
        return new JdbcListenerLifecycle(listener);
    }

    /**
     * Lifecycle management for JDBC listener
     */
    public static class JdbcListenerLifecycle {
        private final JdbcConfigChangeListener listener;

        public JdbcListenerLifecycle(JdbcConfigChangeListener listener) {
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
