package com.dynamic.thread.spring.properties;

import com.dynamic.thread.core.config.AlarmConfig;
import com.dynamic.thread.core.config.NotifyConfig;
import com.dynamic.thread.core.config.ThreadPoolConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic thread pool configuration properties.
 * Binds to 'dynamic-thread' prefix in application configuration.
 */
@Data
@ConfigurationProperties(prefix = "dynamic-thread")
public class DynamicThreadPoolProperties {

    /**
     * Whether to enable dynamic thread pool
     */
    private Boolean enabled = true;

    /**
     * Whether to print banner on startup
     */
    private Boolean banner = true;

    /**
     * Configuration file type (YAML, PROPERTIES, JSON)
     */
    private String configFileType = "YAML";

    /**
     * Web container thread pool configuration
     */
    private WebThreadPoolProperties web;

    /**
     * Notification platforms configuration
     */
    private List<NotifyPlatformProperties> notifyPlatforms = new ArrayList<>();

    /**
     * Thread pool executors configuration
     */
    private List<ExecutorProperties> executors = new ArrayList<>();

    /**
     * Nacos configuration
     */
    private NacosProperties nacos;

    /**
     * Apollo configuration
     */
    private ApolloProperties apollo;

    /**
     * Local file configuration
     */
    private FileProperties file;

    /**
     * JDBC database configuration
     */
    private JdbcProperties jdbc;

    /**
     * ETCD configuration
     */
    private EtcdProperties etcd;

    /**
     * Web container thread pool properties
     */
    @Data
    public static class WebThreadPoolProperties {
        private Integer corePoolSize;
        private Integer maximumPoolSize;
        private Long keepAliveTime;
        private NotifyConfig notify;
    }

    /**
     * Notification platform properties
     */
    @Data
    public static class NotifyPlatformProperties {
        private String platform;
        private String url;
        private String secret;
    }

    /**
     * Thread pool executor properties
     */
    @Data
    public static class ExecutorProperties {
        private String threadPoolId;
        private Integer corePoolSize = 10;
        private Integer maximumPoolSize = 20;
        private Integer queueCapacity = 1000;
        private String workQueue = "ResizableCapacityLinkedBlockingQueue";
        private String rejectedHandler = "AbortPolicy";
        private Long keepAliveTime = 60L;
        private Boolean allowCoreThreadTimeOut = false;
        private NotifyConfig notify;
        private AlarmConfig alarm;

        /**
         * Convert to ThreadPoolConfig
         */
        public ThreadPoolConfig toThreadPoolConfig() {
            return ThreadPoolConfig.builder()
                    .threadPoolId(threadPoolId)
                    .corePoolSize(corePoolSize)
                    .maximumPoolSize(maximumPoolSize)
                    .queueCapacity(queueCapacity)
                    .workQueue(workQueue)
                    .rejectedHandler(rejectedHandler)
                    .keepAliveTime(keepAliveTime)
                    .allowCoreThreadTimeOut(allowCoreThreadTimeOut)
                    .notify(notify)
                    .alarm(alarm)
                    .build();
        }
    }

    /**
     * Nacos configuration properties
     */
    @Data
    public static class NacosProperties {
        private String dataId;
        private String group = "DEFAULT_GROUP";
    }

    /**
     * Apollo configuration properties
     */
    @Data
    public static class ApolloProperties {
        private String namespace = "application";
    }

    /**
     * Local file configuration properties
     */
    @Data
    public static class FileProperties {
        /**
         * Configuration file path
         */
        private String path;

        /**
         * Configuration file type (YAML, PROPERTIES, JSON)
         */
        private String configType = "YAML";
    }

    /**
     * JDBC database configuration properties
     */
    @Data
    public static class JdbcProperties {
        /**
         * Table name for storing thread pool configuration
         */
        private String tableName = "dynamic_thread_config";

        /**
         * Poll interval in milliseconds for checking configuration changes
         */
        private Long pollInterval = 30000L;

        /**
         * Configuration key to identify the configuration record
         */
        private String configKey = "default";
    }

    /**
     * ETCD configuration properties
     */
    @Data
    public static class EtcdProperties {
        /**
         * ETCD server endpoints, comma separated
         */
        private String endpoints = "http://localhost:2379";

        /**
         * Key prefix for thread pool configuration
         */
        private String keyPrefix = "/dynamic-thread/config";

        /**
         * Username for authentication (optional)
         */
        private String username;

        /**
         * Password for authentication (optional)
         */
        private String password;

        /**
         * Configuration type (YAML, PROPERTIES, JSON)
         */
        private String configType = "YAML";
    }
}
