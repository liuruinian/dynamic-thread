package com.dynamic.thread.core.queue;

/**
 * Resizable capacity linked blocking queue.
 * 
 * @deprecated Use {@link VariableLinkedBlockingQueue} instead.
 *             This class is kept for backward compatibility.
 *             The old implementation was flawed because it extended LinkedBlockingQueue
 *             but couldn't actually modify the parent's final capacity field.
 *
 * @param <E> the type of elements held in this queue
 * @see VariableLinkedBlockingQueue
 */
@Deprecated
public class ResizableCapacityLinkedBlockingQueue<E> extends VariableLinkedBlockingQueue<E> {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a queue with the given capacity.
     *
     * @param capacity the capacity of this queue
     */
    public ResizableCapacityLinkedBlockingQueue(int capacity) {
        super(capacity);
    }

    /**
     * Creates a queue with default capacity (Integer.MAX_VALUE).
     */
    public ResizableCapacityLinkedBlockingQueue() {
        super();
    }
}
