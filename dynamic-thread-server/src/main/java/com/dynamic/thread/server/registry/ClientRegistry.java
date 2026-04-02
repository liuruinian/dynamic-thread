package com.dynamic.thread.server.registry;

import com.dynamic.thread.core.model.ThreadPoolState;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing connected Agent clients.
 */
@Slf4j
public class ClientRegistry {

    private static final ClientRegistry INSTANCE = new ClientRegistry();

    /**
     * Map of instanceId -> ClientInfo
     */
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    /**
     * Map of appId -> Set of instanceIds
     */
    private final Map<String, Set<String>> appInstances = new ConcurrentHashMap<>();

    /**
     * Map of instanceId -> List of ThreadPoolState
     */
    private final Map<String, List<ThreadPoolState>> instanceStates = new ConcurrentHashMap<>();

    private ClientRegistry() {
    }

    public static ClientRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a client
     */
    public void register(String appId, String instanceId, Channel channel, Collection<String> threadPoolIds) {
        ClientInfo info = new ClientInfo();
        info.setAppId(appId);
        info.setInstanceId(instanceId);
        info.setChannel(channel);
        info.setThreadPoolIds(new ArrayList<>(threadPoolIds));
        info.setConnectedAt(LocalDateTime.now());
        info.setLastHeartbeat(LocalDateTime.now());

        clients.put(instanceId, info);
        appInstances.computeIfAbsent(appId, k -> ConcurrentHashMap.newKeySet()).add(instanceId);

        log.info("Client registered: appId={}, instanceId={}, threadPools={}", 
                appId, instanceId, threadPoolIds.size());
    }

    /**
     * Unregister a client
     */
    public void unregister(String instanceId) {
        ClientInfo info = clients.remove(instanceId);
        if (info != null) {
            Set<String> instances = appInstances.get(info.getAppId());
            if (instances != null) {
                instances.remove(instanceId);
                if (instances.isEmpty()) {
                    appInstances.remove(info.getAppId());
                }
            }
            instanceStates.remove(instanceId);
            log.info("Client unregistered: instanceId={}", instanceId);
        }
    }

    /**
     * Update heartbeat time
     */
    public void updateHeartbeat(String instanceId) {
        ClientInfo info = clients.get(instanceId);
        if (info != null) {
            info.setLastHeartbeat(LocalDateTime.now());
        }
    }

    /**
     * Update thread pool states for an instance
     */
    public void updateStates(String instanceId, List<ThreadPoolState> states) {
        instanceStates.put(instanceId, states);
        ClientInfo info = clients.get(instanceId);
        if (info != null) {
            info.setLastHeartbeat(LocalDateTime.now());
        }
    }

    /**
     * Get all registered apps
     */
    public Set<String> listApps() {
        return new HashSet<>(appInstances.keySet());
    }

    /**
     * Get instances for an app
     */
    public Set<String> listInstances(String appId) {
        Set<String> instances = appInstances.get(appId);
        return instances != null ? new HashSet<>(instances) : Collections.emptySet();
    }

    /**
     * Get client info
     */
    public ClientInfo getClient(String instanceId) {
        return clients.get(instanceId);
    }

    /**
     * Get all clients
     */
    public Collection<ClientInfo> listClients() {
        return new ArrayList<>(clients.values());
    }

    /**
     * Get thread pool states for an instance (excludes web container pools)
     */
    public List<ThreadPoolState> getStates(String instanceId) {
        List<ThreadPoolState> allStates = instanceStates.getOrDefault(instanceId, Collections.emptyList());
        // Filter out web container pools
        return allStates.stream()
                .filter(state -> !isWebContainerPool(state.getThreadPoolId()))
                .toList();
    }

    /**
     * Get all thread pool states grouped by app and instance (excludes web container pools)
     */
    public Map<String, Map<String, List<ThreadPoolState>>> getAllStates() {
        Map<String, Map<String, List<ThreadPoolState>>> result = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : appInstances.entrySet()) {
            String appId = entry.getKey();
            Map<String, List<ThreadPoolState>> instanceMap = new HashMap<>();
            
            for (String instanceId : entry.getValue()) {
                List<ThreadPoolState> states = instanceStates.get(instanceId);
                if (states != null && !states.isEmpty()) {
                    // Filter out web container pools
                    List<ThreadPoolState> businessPools = states.stream()
                            .filter(state -> !isWebContainerPool(state.getThreadPoolId()))
                            .toList();
                    if (!businessPools.isEmpty()) {
                        instanceMap.put(instanceId, businessPools);
                    }
                }
            }
            
            if (!instanceMap.isEmpty()) {
                result.put(appId, instanceMap);
            }
        }
        
        return result;
    }

    /**
     * Get channel by instanceId
     */
    public Channel getChannel(String instanceId) {
        ClientInfo info = clients.get(instanceId);
        return info != null ? info.getChannel() : null;
    }

    /**
     * Get all channels for instances that have a specific threadPoolId
     */
    public Map<String, Channel> getChannelsForPool(String threadPoolId) {
        Map<String, Channel> channels = new HashMap<>();
        for (Map.Entry<String, List<ThreadPoolState>> entry : instanceStates.entrySet()) {
            String instanceId = entry.getKey();
            List<ThreadPoolState> states = entry.getValue();
            boolean hasPool = states.stream()
                    .anyMatch(s -> threadPoolId.equals(s.getThreadPoolId()));
            if (hasPool) {
                ClientInfo info = clients.get(instanceId);
                if (info != null && info.isOnline()) {
                    channels.put(instanceId, info.getChannel());
                }
            }
        }
        return channels;
    }

    /**
     * Get all online channels
     */
    public Map<String, Channel> getAllOnlineChannels() {
        Map<String, Channel> channels = new HashMap<>();
        for (ClientInfo info : clients.values()) {
            if (info.isOnline()) {
                channels.put(info.getInstanceId(), info.getChannel());
            }
        }
        return channels;
    }

    /**
     * Reset rejection count for a specific pool in local cache
     */
    public void resetPoolRejectionStats(String threadPoolId) {
        for (List<ThreadPoolState> states : instanceStates.values()) {
            for (ThreadPoolState state : states) {
                if (threadPoolId.equals(state.getThreadPoolId())) {
                    state.setRejectedCount(0L);
                }
            }
        }
        log.info("Reset local cache for pool: {}", threadPoolId);
    }

    /**
     * Reset all rejection counts in local cache
     */
    public void resetAllRejectionStats() {
        for (List<ThreadPoolState> states : instanceStates.values()) {
            for (ThreadPoolState state : states) {
                state.setRejectedCount(0L);
            }
        }
        log.info("Reset all local cache rejection stats");
    }

    // ==================== Web Container Thread Pool Methods ====================

    /**
     * Get all web container thread pool states across all instances
     * Web container pools have threadPoolId starting with container name (e.g., "tomcat-web-container")
     */
    public List<WebContainerState> getAllWebContainerStates() {
        List<WebContainerState> result = new ArrayList<>();
        
        for (Map.Entry<String, List<ThreadPoolState>> entry : instanceStates.entrySet()) {
            String instanceId = entry.getKey();
            ClientInfo clientInfo = clients.get(instanceId);
            if (clientInfo == null) continue;
            
            for (ThreadPoolState state : entry.getValue()) {
                if (isWebContainerPool(state.getThreadPoolId())) {
                    WebContainerState webState = new WebContainerState();
                    webState.setAppId(clientInfo.getAppId());
                    webState.setInstanceId(instanceId);
                    webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                    webState.setState(state);
                    webState.setOnline(clientInfo.isOnline());
                    result.add(webState);
                }
            }
        }
        
        return result;
    }

    /**
     * Get web container thread pool states for a specific app
     */
    public List<WebContainerState> getWebContainerStatesByApp(String appId) {
        List<WebContainerState> result = new ArrayList<>();
        Set<String> instances = appInstances.get(appId);
        if (instances == null) return result;
        
        for (String instanceId : instances) {
            ClientInfo clientInfo = clients.get(instanceId);
            List<ThreadPoolState> states = instanceStates.get(instanceId);
            if (clientInfo == null || states == null) continue;
            
            for (ThreadPoolState state : states) {
                if (isWebContainerPool(state.getThreadPoolId())) {
                    WebContainerState webState = new WebContainerState();
                    webState.setAppId(appId);
                    webState.setInstanceId(instanceId);
                    webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                    webState.setState(state);
                    webState.setOnline(clientInfo.isOnline());
                    result.add(webState);
                }
            }
        }
        
        return result;
    }

    /**
     * Get web container thread pool state for a specific instance
     */
    public WebContainerState getWebContainerState(String instanceId) {
        ClientInfo clientInfo = clients.get(instanceId);
        List<ThreadPoolState> states = instanceStates.get(instanceId);
        if (clientInfo == null || states == null) return null;
        
        for (ThreadPoolState state : states) {
            if (isWebContainerPool(state.getThreadPoolId())) {
                WebContainerState webState = new WebContainerState();
                webState.setAppId(clientInfo.getAppId());
                webState.setInstanceId(instanceId);
                webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                webState.setState(state);
                webState.setOnline(clientInfo.isOnline());
                return webState;
            }
        }
        
        return null;
    }

    /**
     * Check if a thread pool is a web container pool
     */
    private boolean isWebContainerPool(String threadPoolId) {
        return threadPoolId != null && (
                threadPoolId.contains("web-container") ||
                threadPoolId.startsWith("tomcat-") ||
                threadPoolId.startsWith("jetty-") ||
                threadPoolId.startsWith("undertow-")
        );
    }

    /**
     * Extract container type from thread pool id
     */
    private String extractContainerType(String threadPoolId) {
        if (threadPoolId.startsWith("tomcat")) return "Tomcat";
        if (threadPoolId.startsWith("jetty")) return "Jetty";
        if (threadPoolId.startsWith("undertow")) return "Undertow";
        return "Unknown";
    }

    /**
     * Web container state with app and instance info
     */
    @Data
    public static class WebContainerState {
        private String appId;
        private String instanceId;
        private String containerType;
        private ThreadPoolState state;
        private boolean online;
    }

    /**
     * Client information
     */
    @Data
    public static class ClientInfo {
        private String appId;
        private String instanceId;
        private Channel channel;
        private List<String> threadPoolIds;
        private LocalDateTime connectedAt;
        private LocalDateTime lastHeartbeat;

        public boolean isOnline() {
            return channel != null && channel.isActive();
        }
    }
}
