package com.dynamic.thread.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Work queue type enumeration.
 * 
 * <p>The recommended default queue type is {@link #VARIABLE_LINKED_BLOCKING_QUEUE}
 * which is based on RabbitMQ's implementation and truly supports runtime capacity adjustment.
 */
@Getter
@AllArgsConstructor
public enum QueueTypeEnum {

    /**
     * Variable linked blocking queue (supports true dynamic capacity adjustment).
     * Based on RabbitMQ's VariableLinkedBlockingQueue implementation.
     * Recommended for dynamic thread pool scenarios.
     */
    VARIABLE_LINKED_BLOCKING_QUEUE("VariableLinkedBlockingQueue"),

    /**
     * @deprecated Use {@link #VARIABLE_LINKED_BLOCKING_QUEUE} instead.
     * Kept for backward compatibility.
     */
    @Deprecated
    RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE("ResizableCapacityLinkedBlockingQueue"),

    /**
     * Standard JDK linked blocking queue (fixed capacity).
     */
    LINKED_BLOCKING_QUEUE("LinkedBlockingQueue"),

    /**
     * Array blocking queue (fixed capacity, backed by array).
     */
    ARRAY_BLOCKING_QUEUE("ArrayBlockingQueue"),

    /**
     * Synchronous queue (no internal capacity, direct handoff).
     */
    SYNCHRONOUS_QUEUE("SynchronousQueue"),

    /**
     * Linked transfer queue (unbounded, supports transfer operations).
     */
    LINKED_TRANSFER_QUEUE("LinkedTransferQueue"),

    /**
     * Priority blocking queue (ordered by priority).
     */
    PRIORITY_BLOCKING_QUEUE("PriorityBlockingQueue"),

    /**
     * Delay queue (elements available only after delay expires).
     */
    DELAY_QUEUE("DelayQueue");

    private final String name;

    public static QueueTypeEnum of(String name) {
        for (QueueTypeEnum type : values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return VARIABLE_LINKED_BLOCKING_QUEUE;
    }
}
