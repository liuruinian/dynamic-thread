package com.dynamic.thread.server.controller;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.server.cluster.ClusterMemberManager;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.registry.ClientRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API controller for thread pool management.
 */
@Slf4j
@RestController
@RequestMapping("/api/thread-pools")
@RequiredArgsConstructor
public class ThreadPoolController {

    private final ClientRegistry clientRegistry;
    private final ServerChannelHandler channelHandler;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private ClusterMemberManager clusterMemberManager;

    /**
     * Get all connected apps
     */
    @GetMapping("/apps")
    public Map<String, Object> listApps() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", clientRegistry.listApps());
        return result;
    }

    /**
     * Get all instances for an app
     */
    @GetMapping("/apps/{appId}/instances")
    public Map<String, Object> listInstances(@PathVariable String appId) {
        Map<String, Object> result = new HashMap<>();
        
        Set<String> instances = clientRegistry.listInstances(appId);
        List<Map<String, Object>> instanceList = new ArrayList<>();
        
        for (String instanceId : instances) {
            ClientRegistry.ClientInfo info = clientRegistry.getClient(instanceId);
            if (info != null) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("instanceId", instanceId);
                instanceInfo.put("appId", info.getAppId());
                instanceInfo.put("threadPoolIds", info.getThreadPoolIds());
                instanceInfo.put("connectedAt", info.getConnectedAt());
                instanceInfo.put("lastHeartbeat", info.getLastHeartbeat());
                instanceInfo.put("online", info.isOnline());
                instanceList.add(instanceInfo);
            }
        }
        
        result.put("success", true);
        result.put("data", instanceList);
        return result;
    }

    /**
     * Get all clients with their status
     */
    @GetMapping("/clients")
    public Map<String, Object> listClients() {
        Map<String, Object> result = new HashMap<>();
        
        Collection<ClientRegistry.ClientInfo> clients = clientRegistry.listClients();
        List<Map<String, Object>> clientList = new ArrayList<>();
        
        for (ClientRegistry.ClientInfo info : clients) {
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("instanceId", info.getInstanceId());
            clientInfo.put("appId", info.getAppId());
            clientInfo.put("threadPoolIds", info.getThreadPoolIds());
            clientInfo.put("connectedAt", info.getConnectedAt());
            clientInfo.put("lastHeartbeat", info.getLastHeartbeat());
            clientInfo.put("online", info.isOnline());
            clientList.add(clientInfo);
        }
        
        result.put("success", true);
        result.put("data", clientList);
        return result;
    }

    /**
     * Get thread pool states for an instance
     */
    @GetMapping("/instances/{instanceId}/states")
    public Map<String, Object> getInstanceStates(@PathVariable String instanceId) {
        Map<String, Object> result = new HashMap<>();
        
        List<ThreadPoolState> states = clientRegistry.getStates(instanceId);
        
        result.put("success", true);
        result.put("data", states);
        return result;
    }

    /**
     * Get all thread pool states grouped by app and instance
     */
    @GetMapping("/states")
    public Map<String, Object> getAllStates() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", clientRegistry.getAllStates());
        return result;
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        
        Collection<ClientRegistry.ClientInfo> clients = clientRegistry.listClients();
        long onlineCount = clients.stream().filter(ClientRegistry.ClientInfo::isOnline).count();
        
        result.put("status", onlineCount > 0 ? "UP" : "DOWN");
        result.put("clientCount", clients.size());
        result.put("onlineCount", onlineCount);
        result.put("appCount", clientRegistry.listApps().size());

        // Add cluster info if available
        if (clusterMemberManager != null) {
            Map<String, Object> clusterInfo = new HashMap<>();
            clusterInfo.put("enabled", true);
            clusterInfo.put("nodeId", clusterMemberManager.getSelfNode().getNodeId());
            clusterInfo.put("totalMembers", clusterMemberManager.getMemberCount());
            clusterInfo.put("healthyMembers", clusterMemberManager.getHealthyMemberCount());
            clusterInfo.put("isLeader", clusterMemberManager.isLeader());
            clusterInfo.put("localAgentCount", clientRegistry.getLocalClientCount());
            result.put("cluster", clusterInfo);
        } else {
            result.put("cluster", java.util.Collections.singletonMap("enabled", false));
        }

        return result;
    }

    /**
     * Update thread pool config for an instance
     */
    @PostMapping("/instances/{instanceId}/config")
    public Map<String, Object> updateConfig(@PathVariable String instanceId,
                                             @RequestBody ConfigUpdateRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String configJson = objectMapper.writeValueAsString(request);
            boolean sent = channelHandler.sendConfigUpdate(instanceId, configJson);
            
            if (sent) {
                result.put("success", true);
                result.put("message", "配置更新已发送到实例: " + instanceId);
            } else {
                result.put("success", false);
                result.put("message", "实例不在线或不存在: " + instanceId);
            }
        } catch (Exception e) {
            log.error("Failed to send config update: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Update thread pool config by threadPoolId (broadcast to all instances)
     */
    @PostMapping("/config")
    public Map<String, Object> updateConfigByThreadPoolId(@RequestBody ConfigUpdateRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        if (request.getThreadPoolId() == null || request.getThreadPoolId().isEmpty()) {
            result.put("success", false);
            result.put("message", "threadPoolId 不能为空");
            return result;
        }
        
        try {
            String configJson = objectMapper.writeValueAsString(request);
            
            // 找到所有包含该线程池的实例并发送配置
            List<String> sentInstances = new ArrayList<>();
            List<String> failedInstances = new ArrayList<>();
            
            for (ClientRegistry.ClientInfo client : clientRegistry.listClients()) {
                if (client.getThreadPoolIds().contains(request.getThreadPoolId()) && client.isOnline()) {
                    try {
                        boolean sent = channelHandler.sendConfigUpdate(client.getInstanceId(), configJson);
                        if (sent) {
                            sentInstances.add(client.getInstanceId());
                        } else {
                            failedInstances.add(client.getInstanceId());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to send to {}: {}", client.getInstanceId(), e.getMessage());
                        failedInstances.add(client.getInstanceId());
                    }
                }
            }
            
            if (sentInstances.isEmpty() && failedInstances.isEmpty()) {
                result.put("success", false);
                result.put("message", "未找到包含线程池 " + request.getThreadPoolId() + " 的在线实例");
            } else {
                result.put("success", !sentInstances.isEmpty());
                result.put("message", String.format("配置已发送到 %d 个实例", sentInstances.size()));
                result.put("sentInstances", sentInstances);
                if (!failedInstances.isEmpty()) {
                    result.put("failedInstances", failedInstances);
                }
            }
        } catch (Exception e) {
            log.error("Failed to broadcast config update: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    @Data
    public static class ConfigUpdateRequest {
        private String threadPoolId;
        private Integer corePoolSize;
        private Integer maximumPoolSize;
        private Integer queueCapacity;
        private Long keepAliveTime;
        private String rejectedHandler;
    }
}
