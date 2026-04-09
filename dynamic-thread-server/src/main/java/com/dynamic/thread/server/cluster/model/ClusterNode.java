package com.dynamic.thread.server.cluster.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a cluster node with its metadata and health status.
 */
@Data
public class ClusterNode {

    /**
     * Unique node identifier
     */
    private String nodeId;

    /**
     * Node address in format "ip:clusterPort"
     */
    private String address;

    /**
     * Parsed IP from address
     */
    private String ip;

    /**
     * Parsed cluster port from address
     */
    private int clusterPort;

    /**
     * Current node state
     */
    private NodeState state = NodeState.UP;

    /**
     * Last heartbeat received time
     */
    private volatile long lastHeartbeat;

    /**
     * Node join time
     */
    private LocalDateTime joinTime;

    /**
     * Whether this is the current (self) node
     */
    private boolean self;

    /**
     * Number of agents connected to this node
     */
    private int agentCount;

    public ClusterNode() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.joinTime = LocalDateTime.now();
    }

    public ClusterNode(String nodeId, String address) {
        this();
        this.nodeId = nodeId;
        this.address = address;
        parseAddress(address);
    }

    private void parseAddress(String address) {
        if (address != null && address.contains(":")) {
            String[] parts = address.split(":");
            this.ip = parts[0];
            this.clusterPort = Integer.parseInt(parts[1]);
        }
    }

    /**
     * Check if the node is healthy (UP state)
     */
    public boolean isHealthy() {
        return state == NodeState.UP;
    }
}
