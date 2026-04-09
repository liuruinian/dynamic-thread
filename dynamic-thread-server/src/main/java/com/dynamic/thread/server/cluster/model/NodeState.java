package com.dynamic.thread.server.cluster.model;

/**
 * Cluster node state enumeration.
 */
public enum NodeState {

    /**
     * Node is healthy and reachable
     */
    UP,

    /**
     * Node is unreachable or down
     */
    DOWN,

    /**
     * Node is suspected to be down (heartbeat missed but not yet timed out)
     */
    SUSPECT
}
