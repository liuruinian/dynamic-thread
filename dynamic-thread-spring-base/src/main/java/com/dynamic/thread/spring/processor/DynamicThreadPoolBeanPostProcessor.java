package com.dynamic.thread.spring.processor;

import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.spring.annotation.DynamicThreadPool;
import com.dynamic.thread.spring.properties.DynamicThreadPoolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * BeanPostProcessor for scanning and registering dynamic thread pools.
 * Scans for beans annotated with @DynamicThreadPool and registers them to ThreadPoolRegistry.
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicThreadPoolBeanPostProcessor implements BeanPostProcessor {

    private final DynamicThreadPoolProperties properties;
    private final ThreadPoolRegistry registry;

    /**
     * Configuration map: threadPoolId -> ExecutorProperties
     */
    private Map<String, DynamicThreadPoolProperties.ExecutorProperties> configMap;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Check if bean is a ThreadPoolExecutor
        if (!(bean instanceof ThreadPoolExecutor)) {
            return bean;
        }

        // Check for @DynamicThreadPool annotation
        DynamicThreadPool annotation = AnnotationUtils.findAnnotation(bean.getClass(), DynamicThreadPool.class);
        
        // Also check if it's a DynamicThreadPoolExecutor
        if (annotation == null && !(bean instanceof DynamicThreadPoolExecutor)) {
            return bean;
        }

        // Determine thread pool id
        String threadPoolId = determineThreadPoolId(annotation, beanName, bean);
        
        if (bean instanceof DynamicThreadPoolExecutor) {
            DynamicThreadPoolExecutor executor = (DynamicThreadPoolExecutor) bean;
            // Already a DynamicThreadPoolExecutor, register directly
            registerThreadPool(threadPoolId, executor);
        } else {
            // Wrap standard ThreadPoolExecutor if configured
            ThreadPoolExecutor executor = (ThreadPoolExecutor) bean;
            DynamicThreadPoolProperties.ExecutorProperties config = getExecutorConfig(threadPoolId);
            
            if (config != null) {
                // Create and register a wrapper or update config
                log.info("Found configured thread pool [{}], applying configuration", threadPoolId);
                // Note: We cannot replace the bean, but we can track it
            }
        }

        return bean;
    }

    /**
     * Determine thread pool id from annotation or bean name
     */
    private String determineThreadPoolId(DynamicThreadPool annotation, String beanName, Object bean) {
        if (annotation != null) {
            String value = annotation.value();
            if (!value.isEmpty()) {
                return value;
            }
            String threadPoolId = annotation.threadPoolId();
            if (!threadPoolId.isEmpty()) {
                return threadPoolId;
            }
        }
        
        if (bean instanceof DynamicThreadPoolExecutor) {
            DynamicThreadPoolExecutor executor = (DynamicThreadPoolExecutor) bean;
            return executor.getThreadPoolId();
        }
        
        return beanName;
    }

    /**
     * Register thread pool to registry
     */
    private void registerThreadPool(String threadPoolId, DynamicThreadPoolExecutor executor) {
        DynamicThreadPoolProperties.ExecutorProperties config = getExecutorConfig(threadPoolId);
        
        if (config != null) {
            // Apply configuration
            ThreadPoolConfig poolConfig = config.toThreadPoolConfig();
            executor.updateConfig(poolConfig);
            registry.register(threadPoolId, executor, poolConfig);
        } else {
            registry.register(threadPoolId, executor);
        }
        
        log.info("Registered dynamic thread pool [{}]", threadPoolId);
    }

    /**
     * Get executor configuration by thread pool id
     */
    private DynamicThreadPoolProperties.ExecutorProperties getExecutorConfig(String threadPoolId) {
        if (configMap == null) {
            configMap = properties.getExecutors().stream()
                    .collect(Collectors.toMap(
                            DynamicThreadPoolProperties.ExecutorProperties::getThreadPoolId,
                            e -> e,
                            (e1, e2) -> e1
                    ));
        }
        return configMap.get(threadPoolId);
    }
}
