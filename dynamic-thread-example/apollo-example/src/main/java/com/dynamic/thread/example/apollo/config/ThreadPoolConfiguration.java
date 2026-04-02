package com.dynamic.thread.example.apollo.config;

import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.queue.ResizableCapacityLinkedBlockingQueue;
import com.dynamic.thread.spring.annotation.DynamicThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool configuration for Apollo example.
 */
@Configuration
public class ThreadPoolConfiguration {

    @Bean
    @DynamicThreadPool("apollo-producer")
    public DynamicThreadPoolExecutor producerThreadPool() {
        return new DynamicThreadPoolExecutor(
                "apollo-producer",
                8,
                16,
                60,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(800),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
