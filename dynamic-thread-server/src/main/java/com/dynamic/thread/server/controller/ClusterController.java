package com.dynamic.thread.server.controller;

import com.dynamic.thread.server.cluster.ClusterMemberManager;
import com.dynamic.thread.server.cluster.model.ClusterNode;
import com.dynamic.thread.server.cluster.transport.ClusterTransportClient;
import com.dynamic.thread.server.registry.ClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for cluster management.
 * Only available when cluster mode is enabled.
 */
@Slf4j
@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
@ConditionalOnBean(ClusterMemberManager.class)
public class ClusterController {

    private final ClusterMemberManager memberManager;
    private final ClusterTransportClient transportClient;
    private final ClientRegistry clientRegistry;

    /**
     * Get cluster overview information.
     */
    @GetMapping("/info")
    public Map<String, Object> getClusterInfo() {
        Map<String, Object> result = new HashMap<>();

        ClusterNode self = memberManager.getSelfNode();
        result.put("nodeId", self.getNodeId());
        result.put("nodeAddress", self.getAddress());
        result.put("isLeader", memberManager.isLeader());
        result.put("totalMembers", memberManager.getMemberCount());
        result.put("healthyMembers", memberManager.getHealthyMemberCount());
        result.put("activePeerConnections", transportClient.getActivePeerCount());
        result.put("localAgentCount", clientRegistry.getLocalClientCount());
        result.put("totalAgentCount", clientRegistry.listClients().size());

        return result;
    }

    /**
     * Get all cluster nodes with their details.
     */
    @GetMapping("/nodes")
    public Map<String, Object> getNodes() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ClusterNode node : memberManager.getAllMembers()) {
            nodes.add(buildNodeInfo(node));
        }

        // Sort: self first, then by nodeId
        nodes.sort((a, b) -> {
            boolean aSelf = (boolean) a.get("self");
            boolean bSelf = (boolean) b.get("self");
            if (aSelf && !bSelf) return -1;
            if (!aSelf && bSelf) return 1;
            return ((String) a.get("nodeId")).compareTo((String) b.get("nodeId"));
        });

        result.put("nodes", nodes);
        result.put("count", nodes.size());
        return result;
    }

    /**
     * Get details for a specific node.
     */
    @GetMapping("/nodes/{nodeId}")
    public Map<String, Object> getNode(@PathVariable String nodeId) {
        Map<String, Object> result = new HashMap<>();

        ClusterNode node = memberManager.getMember(nodeId);
        if (node == null) {
            result.put("found", false);
            result.put("message", "Node not found: " + nodeId);
            return result;
        }

        result.put("found", true);
        result.put("node", buildNodeInfo(node));
        return result;
    }

    /**
     * Cluster health check.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();

        int totalMembers = memberManager.getMemberCount();
        int healthyMembers = memberManager.getHealthyMemberCount();
        int activePeers = transportClient.getActivePeerCount();

        // Cluster is healthy if more than half of the members are UP
        boolean healthy = healthyMembers > totalMembers / 2;

        result.put("status", healthy ? "UP" : "DEGRADED");
        result.put("totalMembers", totalMembers);
        result.put("healthyMembers", healthyMembers);
        result.put("activePeerConnections", activePeers);
        result.put("selfNodeId", memberManager.getSelfNode().getNodeId());
        result.put("leader", memberManager.isLeader());

        // List unhealthy nodes
        List<String> unhealthyNodes = memberManager.getAllMembers().stream()
                .filter(n -> !n.isHealthy())
                .map(ClusterNode::getNodeId)
                .collect(Collectors.toList());
        if (!unhealthyNodes.isEmpty()) {
            result.put("unhealthyNodes", unhealthyNodes);
        }

        return result;
    }

    /**
     * Build node info map for API response.
     */
    private Map<String, Object> buildNodeInfo(ClusterNode node) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("nodeId", node.getNodeId());
        info.put("address", node.getAddress());
        info.put("state", node.getState().name());
        info.put("self", node.isSelf());
        info.put("healthy", node.isHealthy());
        info.put("agentCount", node.getAgentCount());
        info.put("joinTime", node.getJoinTime());
        info.put("lastHeartbeat", node.getLastHeartbeat());

        if (!node.isSelf()) {
            info.put("connected", transportClient.isConnected(node.getNodeId()));
        }

        return info;
    }
}
