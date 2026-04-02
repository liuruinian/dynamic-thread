package com.dynamic.thread.core.queue;

import com.dynamic.thread.core.enums.QueueTypeEnum;

import java.util.concurrent.*;

/**
 * Factory for creating work queues.
 * 
 * Default queue type is {@link VariableLinkedBlockingQueue} which supports
 * runtime capacity adjustment - essential for dynamic thread pool management.
 */
public class WorkQueueFactory {

    /**
     * Create a blocking queue based on queue type
     *
     * @param queueType the queue type name
     * @param capacity  the queue capacity
     * @return the created blocking queue
     */
    public static BlockingQueue<Runnable> createQueue(String queueType, int capacity) {
        QueueTypeEnum type = QueueTypeEnum.of(queueType);
        return createQueue(type, capacity);
    }

    /**
     * Create a blocking queue based on queue type enum
     *
     * @param type     the queue type enum
     * @param capacity the queue capacity
     * @return the created blocking queue
     */
    public static BlockingQueue<Runnable> createQueue(QueueTypeEnum type, int capacity) {
        if (type == null) {
            type = QueueTypeEnum.VARIABLE_LINKED_BLOCKING_QUEUE;
        }

        switch (type) {
            case VARIABLE_LINKED_BLOCKING_QUEUE:
            case RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE: // backward compatible
                return new VariableLinkedBlockingQueue<>(capacity);
            case LINKED_BLOCKING_QUEUE:
                return new LinkedBlockingQueue<>(capacity);
            case ARRAY_BLOCKING_QUEUE:
                return new ArrayBlockingQueue<>(capacity);
            case SYNCHRONOUS_QUEUE:
                return new SynchronousQueue<>();
            case LINKED_TRANSFER_QUEUE:
                return new LinkedTransferQueue<>();
            case PRIORITY_BLOCKING_QUEUE:
                return new PriorityBlockingQueue<>(capacity);
            case DELAY_QUEUE:
                return new DelayQueue();
            default:
                return new VariableLinkedBlockingQueue<>(capacity);
        }
    }
}
