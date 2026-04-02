package com.dynamic.thread.example.file.config;

import com.dynamic.thread.spring.annotation.DynamicThreadPool;
import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.queue.ResizableCapacityLinkedBlockingQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool configuration
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * Order processing thread pool
     */
    @Bean
    @DynamicThreadPool
    public DynamicThreadPoolExecutor orderExecutor() {
        return new DynamicThreadPoolExecutor(
                "order-executor",
                10,
                20,
                60L,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Payment processing thread pool
     */
    @Bean
    @DynamicThreadPool
    public DynamicThreadPoolExecutor paymentExecutor() {
        return new DynamicThreadPoolExecutor(
                "payment-executor",
                5,
                10,
                120L,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
