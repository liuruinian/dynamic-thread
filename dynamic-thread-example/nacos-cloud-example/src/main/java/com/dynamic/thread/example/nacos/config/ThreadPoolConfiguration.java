package com.dynamic.thread.example.nacos.config;

import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.queue.ResizableCapacityLinkedBlockingQueue;
import com.dynamic.thread.spring.annotation.DynamicThreadPool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool configuration for the example application.
 */
@Configuration
public class ThreadPoolConfiguration {

    private final MeterRegistry meterRegistry;

    public ThreadPoolConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Producer thread pool for message processing
     */
    @Bean
    @DynamicThreadPool("onethread-producer")
    public DynamicThreadPoolExecutor producerThreadPool() {
        DynamicThreadPoolExecutor executor = new DynamicThreadPoolExecutor(
                "onethread-producer",
                10,
                20,
                60,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        // Register metrics
        new ExecutorServiceMetrics(executor, "producer-pool", null).bindTo(meterRegistry);
        return executor;
    }

    /**
     * Consumer thread pool for data processing
     */
    @Bean
    @DynamicThreadPool("onethread-consumer")
    public DynamicThreadPoolExecutor consumerThreadPool() {
        DynamicThreadPoolExecutor executor = new DynamicThreadPoolExecutor(
                "onethread-consumer",
                5,
                15,
                60,
                TimeUnit.SECONDS,
                new ResizableCapacityLinkedBlockingQueue<>(500),
                new ThreadPoolExecutor.AbortPolicy()
        );
        // Register metrics
        new ExecutorServiceMetrics(executor, "consumer-pool", null).bindTo(meterRegistry);
        return executor;
    }
}
