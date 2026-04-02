package com.dynamic.thread.example.nacos.controller;

import com.dynamic.thread.core.executor.DynamicThreadPoolExecutor;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test controller for demonstrating dynamic thread pool functionality
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    private final DynamicThreadPoolExecutor producerThreadPool;
    private final DynamicThreadPoolExecutor consumerThreadPool;
    private final ThreadPoolRegistry registry;
    
    private final AtomicLong taskCounter = new AtomicLong(0);

    public TestController(
            @Qualifier("producerThreadPool") DynamicThreadPoolExecutor producerThreadPool,
            @Qualifier("consumerThreadPool") DynamicThreadPoolExecutor consumerThreadPool,
            ThreadPoolRegistry registry) {
        this.producerThreadPool = producerThreadPool;
        this.consumerThreadPool = consumerThreadPool;
        this.registry = registry;
    }

    /**
     * Submit a single task to producer thread pool
     */
    @PostMapping("/submit")
    public Map<String, Object> submitTask(@RequestParam(defaultValue = "100") int sleepMs) {
        Map<String, Object> result = new HashMap<>();
        long taskId = taskCounter.incrementAndGet();
        
        producerThreadPool.execute(() -> {
            log.info("Task {} started, will sleep {}ms", taskId, sleepMs);
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Task {} completed", taskId);
        });
        
        result.put("success", true);
        result.put("taskId", taskId);
        result.put("threadPool", "onethread-producer");
        result.put("activeCount", producerThreadPool.getActiveCount());
        result.put("queueSize", producerThreadPool.getQueue().size());
        
        return result;
    }

    /**
     * Submit batch tasks to stress test the thread pool
     */
    @PostMapping("/batch")
    public Map<String, Object> submitBatchTasks(
            @RequestParam(defaultValue = "50") int count,
            @RequestParam(defaultValue = "500") int sleepMs) {
        
        Map<String, Object> result = new HashMap<>();
        long startTaskId = taskCounter.get() + 1;
        
        for (int i = 0; i < count; i++) {
            long taskId = taskCounter.incrementAndGet();
            producerThreadPool.execute(() -> {
                log.info("Batch task {} started", taskId);
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Batch task {} completed", taskId);
            });
        }
        
        result.put("success", true);
        result.put("taskCount", count);
        result.put("taskIdRange", startTaskId + " - " + taskCounter.get());
        result.put("activeCount", producerThreadPool.getActiveCount());
        result.put("queueSize", producerThreadPool.getQueue().size());
        
        return result;
    }

    /**
     * Get current state of all thread pools
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        
        ThreadPoolState producerState = registry.getState("onethread-producer");
        ThreadPoolState consumerState = registry.getState("onethread-consumer");
        
        result.put("producer", producerState);
        result.put("consumer", consumerState);
        result.put("totalTasksSubmitted", taskCounter.get());
        
        return result;
    }

    /**
     * Async task demonstration
     */
    @PostMapping("/async")
    public CompletableFuture<Map<String, Object>> asyncTask(@RequestParam(defaultValue = "1000") int sleepMs) {
        return CompletableFuture.supplyAsync(() -> {
            long taskId = taskCounter.incrementAndGet();
            log.info("Async task {} started", taskId);
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Async task {} completed", taskId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", "completed");
            result.put("duration", sleepMs);
            return result;
        }, producerThreadPool);
    }

    /**
     * Submit many tasks to trigger rejection
     * This will fill up the queue and trigger the rejection policy
     */
    @PostMapping("/stress")
    public Map<String, Object> stressTest(
            @RequestParam(defaultValue = "5000") int count,
            @RequestParam(defaultValue = "5000") int sleepMs) {
        
        Map<String, Object> result = new HashMap<>();
        int submitted = 0;
        int rejected = 0;
        long startTaskId = taskCounter.get() + 1;
        
        for (int i = 0; i < count; i++) {
            try {
                long taskId = taskCounter.incrementAndGet();
                producerThreadPool.execute(() -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                submitted++;
            } catch (Exception e) {
                rejected++;
                log.warn("Task rejected: {}", e.getMessage());
            }
        }
        
        result.put("success", true);
        result.put("totalRequested", count);
        result.put("submitted", submitted);
        result.put("rejected", rejected);
        result.put("taskIdRange", startTaskId + " - " + taskCounter.get());
        result.put("activeCount", producerThreadPool.getActiveCount());
        result.put("queueSize", producerThreadPool.getQueue().size());
        result.put("rejectedCount", producerThreadPool.getRejectedCount());
        
        return result;
    }
}
