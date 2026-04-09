package com.dynamic.thread.core.reject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RetryBufferPolicy}.
 */
class RetryBufferPolicyTest {

    private ThreadPoolExecutor executor;
    private RetryBufferPolicy policy;

    @BeforeEach
    void setUp() {
        // Create a small thread pool that will easily reject tasks
        policy = new RetryBufferPolicy(RetryBufferConfig.builder()
                .bufferCapacity(10)
                .retryIntervalMs(50)
                .maxRetryCount(3)
                .maxBufferTimeMs(5000)
                .idleThreshold(0.8)
                .enableLogging(true)
                .build());

        executor = new ThreadPoolExecutor(
                1, 1,           // Only 1 thread
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),  // Queue of size 1
                policy
        );
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (policy != null) {
            policy.shutdown();
        }
    }

    @Test
    void testBasicBuffering() throws InterruptedException {
        AtomicInteger executedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        // Submit 3 tasks - with pool size 1 and queue size 1, 
        // the 3rd task should be buffered
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            executor.execute(() -> {
                try {
                    Thread.sleep(100);
                    executedCount.incrementAndGet();
                    System.out.println("Task " + taskId + " completed");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All tasks should complete within timeout");
        assertEquals(3, executedCount.get(), "All 3 tasks should be executed");

        // Check statistics
        assertTrue(policy.getTotalBufferedCount().get() > 0 || policy.getTotalFallbackCount().get() > 0,
                "Some tasks should have been buffered or executed via fallback");
    }

    @Test
    void testBufferCapacityLimit() {
        // Fill up the executor
        executor.execute(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Fill up the queue
        executor.execute(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Now submit more tasks than buffer capacity
        long beforeFallback = policy.getTotalFallbackCount().get();
        for (int i = 0; i < 15; i++) {  // More than buffer capacity (10)
            final int taskId = i;
            executor.execute(() -> System.out.println("Task " + taskId));
        }

        // Some tasks should go to buffer, some to fallback when buffer is full
        assertTrue(policy.getTotalBufferedCount().get() > 0, "Some tasks should be buffered");
    }

    @Test
    void testStatistics() throws InterruptedException {
        // Reset statistics
        policy.resetStatistics();
        assertEquals(0, policy.getTotalBufferedCount().get());
        assertEquals(0, policy.getTotalRetriedCount().get());
        assertEquals(0, policy.getTotalFallbackCount().get());

        // Block the executor
        CountDownLatch blocker = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                blocker.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Submit a task that will be rejected/buffered
        executor.execute(() -> System.out.println("Test task"));
        executor.execute(() -> System.out.println("Test task 2"));

        // Release the blocker
        blocker.countDown();

        // Wait a bit for retry scheduler
        Thread.sleep(500);

        // Check statistics map
        Map<String, Object> stats = policy.getStatistics();
        assertNotNull(stats);
        assertTrue(stats.containsKey("bufferCapacity"));
        assertTrue(stats.containsKey("totalBuffered"));
        assertTrue(stats.containsKey("running"));
    }

    @Test
    void testShutdown() throws InterruptedException {
        // Block the executor
        executor.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Buffer some tasks
        for (int i = 0; i < 5; i++) {
            executor.execute(() -> System.out.println("Shutdown test task"));
        }

        // Shutdown policy
        assertTrue(policy.isRunning());
        policy.shutdown();
        assertFalse(policy.isRunning());

        // Remaining buffered tasks should be executed via fallback
        // (or at least attempted)
    }

    @Test
    void testConfigValidation() {
        // Valid config should not throw
        assertDoesNotThrow(() -> RetryBufferConfig.builder()
                .bufferCapacity(100)
                .retryIntervalMs(50)
                .maxRetryCount(5)
                .idleThreshold(0.5)
                .build()
                .validate());

        // Invalid buffer capacity
        assertThrows(IllegalArgumentException.class, () -> 
                RetryBufferConfig.builder().bufferCapacity(0).build().validate());

        // Invalid idle threshold
        assertThrows(IllegalArgumentException.class, () -> 
                RetryBufferConfig.builder().idleThreshold(1.5).build().validate());
    }

    @Test
    void testBufferedTask() {
        Runnable task = () -> {};
        BufferedTask bufferedTask = new BufferedTask(task, executor);

        assertEquals(task, bufferedTask.getTask());
        assertEquals(executor, bufferedTask.getExecutor());
        assertEquals(0, bufferedTask.getRetryCount());
        assertTrue(bufferedTask.getTimeInBuffer() >= 0);

        // Test retry count increment
        assertEquals(1, bufferedTask.incrementRetryCount());
        assertEquals(2, bufferedTask.incrementRetryCount());
        assertEquals(2, bufferedTask.getRetryCount());

        // Test expiration
        assertFalse(bufferedTask.isExpired(10000));
        assertFalse(bufferedTask.hasExceededMaxRetries(3));
        assertTrue(bufferedTask.hasExceededMaxRetries(1));
    }
}
