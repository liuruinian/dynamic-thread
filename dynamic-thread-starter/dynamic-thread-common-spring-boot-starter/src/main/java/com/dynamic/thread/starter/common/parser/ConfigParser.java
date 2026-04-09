package com.dynamic.thread.starter.common.parser;

import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Configuration parser for parsing thread pool configuration from various formats.
 */
@Slf4j
public class ConfigParser {

    private static final String PREFIX = "dynamic-thread";

    /**
     * Parse configuration content to executor properties list
     *
     * @param content    the configuration content
     * @param configType the configuration type (YAML, PROPERTIES, JSON)
     * @return list of executor properties
     */
    public List<DynamicThreadPoolProperties.ExecutorProperties> parse(String content, String configType) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if ("YAML".equalsIgnoreCase(configType) || "YML".equalsIgnoreCase(configType)) {
            return parseYaml(content);
        } else if ("PROPERTIES".equalsIgnoreCase(configType)) {
            return parseProperties(content);
        } else {
            log.warn("Unsupported config type: {}, trying YAML", configType);
            return parseYaml(content);
        }
    }

    /**
     * Parse YAML configuration
     */
    @SuppressWarnings("unchecked")
    private List<DynamicThreadPoolProperties.ExecutorProperties> parseYaml(String content) {
        List<DynamicThreadPoolProperties.ExecutorProperties> result = new ArrayList<>();

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(content);

            if (root == null) {
                return result;
            }

            // Get dynamic-thread configuration
            Map<String, Object> dynamicThread = (Map<String, Object>) root.get(PREFIX);
            if (dynamicThread == null) {
                // Try direct access if content is already under dynamic-thread
                dynamicThread = root;
            }

            // Parse executors
            List<Map<String, Object>> executors = (List<Map<String, Object>>) dynamicThread.get("executors");
            if (executors == null) {
                return result;
            }

            for (Map<String, Object> executor : executors) {
                DynamicThreadPoolProperties.ExecutorProperties props = parseExecutorMap(executor);
                if (props != null && props.getThreadPoolId() != null) {
                    result.add(props);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse YAML configuration", e);
        }

        return result;
    }

    /**
     * Parse executor map to ExecutorProperties
     */
    @SuppressWarnings("unchecked")
    private DynamicThreadPoolProperties.ExecutorProperties parseExecutorMap(Map<String, Object> map) {
        DynamicThreadPoolProperties.ExecutorProperties props = new DynamicThreadPoolProperties.ExecutorProperties();

        props.setThreadPoolId(getString(map, "thread-pool-id"));
        props.setCorePoolSize(getInteger(map, "core-pool-size"));
        props.setMaximumPoolSize(getInteger(map, "maximum-pool-size"));
        props.setQueueCapacity(getInteger(map, "queue-capacity"));
        props.setWorkQueue(getString(map, "work-queue"));
        props.setRejectedHandler(getString(map, "rejected-handler"));
        props.setKeepAliveTime(getLong(map, "keep-alive-time"));
        props.setAllowCoreThreadTimeOut(getBoolean(map, "allow-core-thread-time-out"));

        // Parse alarm config
        Map<String, Object> alarmMap = (Map<String, Object>) map.get("alarm");
        if (alarmMap != null) {
            com.dynamic.thread.core.config.AlarmConfig alarm = new com.dynamic.thread.core.config.AlarmConfig();
            alarm.setEnable(getBoolean(alarmMap, "enable"));
            alarm.setQueueThreshold(getInteger(alarmMap, "queue-threshold"));
            alarm.setActiveThreshold(getInteger(alarmMap, "active-threshold"));
            props.setAlarm(alarm);
        }

        // Parse notify config
        Map<String, Object> notifyMap = (Map<String, Object>) map.get("notify");
        if (notifyMap != null) {
            com.dynamic.thread.core.config.NotifyConfig notify = new com.dynamic.thread.core.config.NotifyConfig();
            notify.setReceives(getString(notifyMap, "receives"));
            notify.setInterval(getInteger(notifyMap, "interval"));
            props.setNotify(notify);
        }

        return props;
    }

    /**
     * Parse properties format configuration
     */
    private List<DynamicThreadPoolProperties.ExecutorProperties> parseProperties(String content) {
        List<DynamicThreadPoolProperties.ExecutorProperties> result = new ArrayList<>();
        
        try {
            Properties props = new Properties();
            props.load(new java.io.StringReader(content));
            
            // Find all unique thread pool ids
            Set<String> threadPoolIds = new HashSet<>();
            String prefix = PREFIX + ".executors[";
            
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    int endIndex = key.indexOf("]", prefix.length());
                    if (endIndex > prefix.length()) {
                        String index = key.substring(prefix.length(), endIndex);
                        threadPoolIds.add(index);
                    }
                }
            }
            
            // Parse each thread pool
            for (String index : threadPoolIds) {
                String keyPrefix = prefix + index + "].";
                DynamicThreadPoolProperties.ExecutorProperties executor = new DynamicThreadPoolProperties.ExecutorProperties();
                
                executor.setThreadPoolId(props.getProperty(keyPrefix + "thread-pool-id"));
                String corePoolSize = props.getProperty(keyPrefix + "core-pool-size");
                if (corePoolSize != null) {
                    executor.setCorePoolSize(Integer.parseInt(corePoolSize));
                }
                String maxPoolSize = props.getProperty(keyPrefix + "maximum-pool-size");
                if (maxPoolSize != null) {
                    executor.setMaximumPoolSize(Integer.parseInt(maxPoolSize));
                }
                String queueCapacity = props.getProperty(keyPrefix + "queue-capacity");
                if (queueCapacity != null) {
                    executor.setQueueCapacity(Integer.parseInt(queueCapacity));
                }
                
                if (executor.getThreadPoolId() != null) {
                    result.add(executor);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse properties configuration", e);
        }
        
        return result;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        return Integer.parseInt(value.toString());
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
}
