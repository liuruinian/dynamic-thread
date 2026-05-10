package com.dynamic.thread.example.nacos.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load test controller for Web container thread pool
 * These endpoints are designed to stress test Tomcat/Jetty/Undertow thread pool
 */
@Slf4j
@RestController
@RequestMapping("/load")
public class LoadTestController {

    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * Simulate a slow API that blocks Web container thread
     * Use this to increase Web container thread pool active rate
     */
    @GetMapping("/slow")
    public Map<String, Object> slowRequest(@RequestParam(defaultValue = "2000") int delayMs) {
        long requestId = requestCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        log.info("[{}] Request {} started, will block for {}ms", threadName, requestId, delayMs);
        
        long startTime = System.currentTimeMillis();
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("[{}] Request {} completed in {}ms", threadName, requestId, duration);
        
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("thread", threadName);
        result.put("delayMs", delayMs);
        result.put("actualDuration", duration);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * Simulate CPU intensive work
     */
    @GetMapping("/cpu")
    public Map<String, Object> cpuIntensive(@RequestParam(defaultValue = "10000000") int iterations) {
        long requestId = requestCounter.incrementAndGet();
        String threadName = Thread.currentThread().getName();
        
        log.info("[{}] CPU intensive request {} started with {} iterations", threadName, requestId, iterations);
        
        long startTime = System.currentTimeMillis();
        double result = 0;
        for (int i = 0; i < iterations; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("[{}] CPU intensive request {} completed in {}ms", threadName, requestId, duration);
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("thread", threadName);
        response.put("iterations", iterations);
        response.put("duration", duration);
        response.put("result", result);
        return response;
    }

    /**
     * Mixed load: random delay between min and max
     */
    @GetMapping("/random")
    public Map<String, Object> randomDelay(
            @RequestParam(defaultValue = "500") int minMs,
            @RequestParam(defaultValue = "3000") int maxMs) {
        
        int delay = minMs + (int) (Math.random() * (maxMs - minMs));
        return slowRequest(delay);
    }

    /**
     * Get current request statistics
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalRequests", requestCounter.get());
        result.put("currentThread", Thread.currentThread().getName());
        result.put("activeThreads", Thread.activeCount());
        return result;
    }

    /**
     * Reset request counter
     */
    @PostMapping("/reset")
    public Map<String, Object> resetStats() {
        long previous = requestCounter.getAndSet(0);
        Map<String, Object> result = new HashMap<>();
        result.put("previousCount", previous);
        result.put("currentCount", 0);
        return result;
    }
}
