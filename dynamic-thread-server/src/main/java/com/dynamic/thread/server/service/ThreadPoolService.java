package com.dynamic.thread.server.service;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.util.CommonComponents;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.registry.ClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service layer for thread pool management operations.
 * Encapsulates business logic for thread pool monitoring and configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolService {

    private final ClientRegistry clientRegistry;
    private final ServerChannelHandler channelHandler;

    /**
     * Get all connected apps
     *
     * @return set of app ids
     */
    public Set<String> listApps() {
        return clientRegistry.listApps();
    }

    /**
     * Get all instances for an app with detailed info
     *
     * @param appId the app id
     * @return list of instance info maps
     */
    public List<Map<String, Object>> listInstances(String appId) {
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

        return instanceList;
    }

    /**
     * Get all clients with their status
     *
     * @return list of client info maps
     */
    public List<Map<String, Object>> listClients() {
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

        return clientList;
    }

    /**
     * Get thread pool states for an instance
     *
     * @param instanceId the instance id
     * @return list of thread pool states
     */
    public List<ThreadPoolState> getInstanceStates(String instanceId) {
        return clientRegistry.getStates(instanceId);
    }

    /**
     * Get all thread pool states grouped by app and instance
     *
     * @return map of all states
     */
    public Map<String, Map<String, List<ThreadPoolState>>> getAllStates() {
        return clientRegistry.getAllStates();
    }

    /**
     * Get health status
     *
     * @return health status map
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();

        Collection<ClientRegistry.ClientInfo> clients = clientRegistry.listClients();
        long onlineCount = clients.stream().filter(ClientRegistry.ClientInfo::isOnline).count();

        health.put("status", onlineCount > 0 ? "UP" : "DOWN");
        health.put("clientCount", clients.size());
        health.put("onlineCount", onlineCount);
        health.put("appCount", clientRegistry.listApps().size());

        return health;
    }

    /**
     * Update thread pool config for an instance
     *
     * @param instanceId the instance id
     * @param config     the config update request
     * @return result containing success status and message
     */
    public ConfigUpdateResult updateInstanceConfig(String instanceId, Object config) {
        try {
            String configJson = CommonComponents.objectMapper().writeValueAsString(config);
            boolean sent = channelHandler.sendConfigUpdate(instanceId, configJson);

            if (sent) {
                return new ConfigUpdateResult(true, "配置更新已发送到实例: " + instanceId, List.of(instanceId), List.of());
            } else {
                return new ConfigUpdateResult(false, "实例不在线或不存在: " + instanceId, List.of(), List.of());
            }
        } catch (Exception e) {
            log.error("Failed to send config update: {}", e.getMessage());
            return new ConfigUpdateResult(false, e.getMessage(), List.of(), List.of());
        }
    }

    /**
     * Broadcast config update to all instances containing the specified thread pool
     *
     * @param threadPoolId the thread pool id
     * @param config       the config update request
     * @return result containing success status and affected instances
     */
    public ConfigUpdateResult broadcastConfig(String threadPoolId, Object config) {
        if (threadPoolId == null || threadPoolId.isEmpty()) {
            return new ConfigUpdateResult(false, "threadPoolId 不能为空", List.of(), List.of());
        }

        try {
            String configJson = CommonComponents.objectMapper().writeValueAsString(config);

            List<String> sentInstances = new ArrayList<>();
            List<String> failedInstances = new ArrayList<>();

            for (ClientRegistry.ClientInfo client : clientRegistry.listClients()) {
                if (client.getThreadPoolIds().contains(threadPoolId) && client.isOnline()) {
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
                return new ConfigUpdateResult(false, 
                        "未找到包含线程池 " + threadPoolId + " 的在线实例", 
                        sentInstances, failedInstances);
            } else {
                return new ConfigUpdateResult(!sentInstances.isEmpty(),
                        String.format("配置已发送到 %d 个实例", sentInstances.size()),
                        sentInstances, failedInstances);
            }
        } catch (Exception e) {
            log.error("Failed to broadcast config update: {}", e.getMessage());
            return new ConfigUpdateResult(false, e.getMessage(), List.of(), List.of());
        }
    }

    /**
     * Result of a configuration update operation
     */
    public record ConfigUpdateResult(
            boolean success,
            String message,
            List<String> sentInstances,
            List<String> failedInstances
    ) {}
}
