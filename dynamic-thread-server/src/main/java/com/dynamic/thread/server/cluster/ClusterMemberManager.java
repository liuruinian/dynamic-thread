package com.dynamic.thread.server.cluster;

import com.dynamic.thread.server.cluster.model.ClusterNode;
import com.dynamic.thread.server.cluster.model.NodeState;
import com.dynamic.thread.server.config.ServerProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages cluster membership, health checking and node lifecycle.
 * Maintains the list of known cluster nodes and their health status.
 */
@Slf4j
public class ClusterMemberManager {

    private final ServerProperties.ClusterConfig clusterConfig;

    /**
     * All known cluster members: nodeId -> ClusterNode
     */
    private final Map<String, ClusterNode> members = new ConcurrentHashMap<>();

    /**
     * The current (self) node
     */
    private ClusterNode selfNode;

    /**
     * Scheduler for periodic health checks
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-member-manager");
        t.setDaemon(true);
        return t;
    });

    public ClusterMemberManager(ServerProperties.ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
    }

    /**
     * Initialize cluster membership from configuration.
     */
    public void init() {
        // Determine self node ID and address
        String nodeId = clusterConfig.getNodeId();
        String nodeAddress = clusterConfig.getNodeAddress();

        if (nodeId == null || nodeId.trim().isEmpty()) {
            nodeId = generateNodeId(nodeAddress);
        }

        if (nodeAddress == null || nodeAddress.trim().isEmpty()) {
            nodeAddress = detectLocalAddress() + ":" + clusterConfig.getClusterPort();
            log.info("Auto-detected node address: {}", nodeAddress);
        }

        // Create self node
        selfNode = new ClusterNode(nodeId, nodeAddress);
        selfNode.setSelf(true);
        selfNode.setState(NodeState.UP);
        members.put(selfNode.getNodeId(), selfNode);

        // Register other members from config
        List<String> configMembers = clusterConfig.getMembers();
        if (configMembers != null) {
            for (String memberAddress : configMembers) {
                if (memberAddress.equals(nodeAddress)) {
                    continue; // Skip self
                }
                String memberId = generateNodeId(memberAddress);
                ClusterNode node = new ClusterNode(memberId, memberAddress);
                node.setState(NodeState.DOWN); // Initially down until heartbeat received
                members.put(memberId, node);
                log.info("Registered cluster member: nodeId={}, address={}", memberId, memberAddress);
            }
        }

        // Start health check scheduler
        scheduler.scheduleAtFixedRate(this::healthCheck,
                clusterConfig.getHeartbeatIntervalMs(),
                clusterConfig.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info("Cluster member manager initialized: selfNodeId={}, selfAddress={}, totalMembers={}",
                selfNode.getNodeId(), selfNode.getAddress(), members.size());
    }

    /**
     * Periodic health check: mark nodes as SUSPECT or DOWN based on heartbeat timeout.
     */
    private void healthCheck() {
        long now = System.currentTimeMillis();
        long timeout = clusterConfig.getNodeTimeoutMs();
        long suspectThreshold = timeout / 2;

        for (ClusterNode node : members.values()) {
            if (node.isSelf()) {
                continue;
            }

            long elapsed = now - node.getLastHeartbeat();

            if (elapsed > timeout && node.getState() != NodeState.DOWN) {
                NodeState oldState = node.getState();
                node.setState(NodeState.DOWN);
                log.warn("Cluster node DOWN: nodeId={}, address={}, elapsed={}ms (was {})",
                        node.getNodeId(), node.getAddress(), elapsed, oldState);
            } else if (elapsed > suspectThreshold && node.getState() == NodeState.UP) {
                node.setState(NodeState.SUSPECT);
                log.warn("Cluster node SUSPECT: nodeId={}, address={}, elapsed={}ms",
                        node.getNodeId(), node.getAddress(), elapsed);
            }
        }
    }

    /**
     * Update heartbeat for a node (called when heartbeat message is received).
     */
    public void onHeartbeatReceived(String nodeId, int agentCount) {
        ClusterNode node = members.get(nodeId);
        // Fallback: try matching by address if nodeId was remapped
        if (node == null) {
            node = findMemberByNodeIdOrAddress(nodeId);
        }
        if (node != null) {
            node.setLastHeartbeat(System.currentTimeMillis());
            node.setAgentCount(agentCount);
            if (node.getState() != NodeState.UP) {
                log.info("Cluster node recovered: nodeId={}, address={}", node.getNodeId(), node.getAddress());
                node.setState(NodeState.UP);
            }
        } else {
            log.warn("Received heartbeat from unknown node: {}", nodeId);
        }
    }

    /**
     * Handle node join: remap the auto-generated nodeId to the node's actual configured nodeId.
     * When a remote node joins, it sends its configured nodeId and address.
     * We find the member by address and update the map key to the real nodeId.
     */
    public void onNodeJoin(String realNodeId, String nodeAddress) {
        if (realNodeId == null || realNodeId.trim().isEmpty() || nodeAddress == null || nodeAddress.trim().isEmpty()) {
            return;
        }
        // Already registered with the correct nodeId?
        ClusterNode existing = members.get(realNodeId);
        if (existing != null && nodeAddress.equals(existing.getAddress())) {
            existing.setLastHeartbeat(System.currentTimeMillis());
            if (existing.getState() != NodeState.UP) {
                existing.setState(NodeState.UP);
                log.info("Cluster node joined (already known): nodeId={}, address={}", realNodeId, nodeAddress);
            }
            return;
        }
        // Find the member by address (it may be registered with an auto-generated nodeId)
        ClusterNode node = findNodeByAddress(nodeAddress);
        if (node != null && !node.isSelf()) {
            String oldId = node.getNodeId();
            if (!oldId.equals(realNodeId)) {
                // Remap: remove old key, update nodeId, put with new key
                members.remove(oldId);
                node.setNodeId(realNodeId);
                members.put(realNodeId, node);
                log.info("Cluster node remapped: {} -> {}, address={}", oldId, realNodeId, nodeAddress);
            }
            node.setLastHeartbeat(System.currentTimeMillis());
            node.setState(NodeState.UP);
            log.info("Cluster node joined: nodeId={}, address={}", realNodeId, nodeAddress);
        } else {
            log.warn("Node join from unknown address: nodeId={}, address={}", realNodeId, nodeAddress);
        }
    }

    /**
     * Find a member by nodeId or by scanning all members for matching nodeId pattern.
     */
    private ClusterNode findMemberByNodeIdOrAddress(String nodeId) {
        for (ClusterNode node : members.values()) {
            if (nodeId.equals(node.getNodeId())) {
                return node;
            }
        }
        return null;
    }

    /**
     * Get the self (current) node.
     */
    public ClusterNode getSelfNode() {
        return selfNode;
    }

    /**
     * Get all known cluster members.
     */
    public Collection<ClusterNode> getAllMembers() {
        return Collections.unmodifiableCollection(members.values());
    }

    /**
     * Get only healthy (UP) members excluding self.
     */
    public List<ClusterNode> getHealthyMembers() {
        return members.values().stream()
                .filter(n -> !n.isSelf() && n.isHealthy())
                .collect(Collectors.toList());
    }

    /**
     * Get all other members (excluding self), regardless of health.
     */
    public List<ClusterNode> getOtherMembers() {
        return members.values().stream()
                .filter(n -> !n.isSelf())
                .collect(Collectors.toList());
    }

    /**
     * Get a specific member by nodeId.
     */
    public ClusterNode getMember(String nodeId) {
        return members.get(nodeId);
    }

    /**
     * Find which node owns a specific agent instance.
     * Returns the nodeId of the node, or null if not found.
     */
    public ClusterNode findNodeByAddress(String address) {
        for (ClusterNode node : members.values()) {
            if (address.equals(node.getAddress())) {
                return node;
            }
        }
        return null;
    }

    /**
     * Check if the current node is the leader (smallest nodeId among healthy nodes).
     * Used for coordinating cluster-wide tasks.
     */
    public boolean isLeader() {
        String selfId = selfNode.getNodeId();
        for (ClusterNode node : members.values()) {
            if (node.isHealthy() && node.getNodeId().compareTo(selfId) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get cluster member count.
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * Get healthy member count.
     */
    public int getHealthyMemberCount() {
        return (int) members.values().stream().filter(ClusterNode::isHealthy).count();
    }

    /**
     * Shutdown the member manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        log.info("Cluster member manager shutdown");
    }

    /**
     * Generate a node ID from the address.
     */
    private String generateNodeId(String address) {
        if (address != null && !address.trim().isEmpty()) {
            return address.replace(":", "-").replace(".", "-");
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Detect local IP address.
     */
    private String detectLocalAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to detect local address, using 127.0.0.1");
            return "127.0.0.1";
        }
    }
}
