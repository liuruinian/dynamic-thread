package com.dynamic.thread.server.registry;

import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.server.cluster.model.SyncData;
import com.dynamic.thread.server.cluster.sync.DataSyncer;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing connected Agent clients.
 * Supports both standalone mode (single node) and cluster mode (local + remote clients).
 *
 * In cluster mode:
 * - localClients: agents directly connected to this node (have Channel)
 * - remoteClients: agents connected to other nodes (synced, no Channel)
 * - Query methods merge both sources for a unified global view
 */
@Slf4j
public class ClientRegistry {

    private static final ClientRegistry INSTANCE = new ClientRegistry();

    // ==================== Local data (agents connected to THIS node) ====================

    /**
     * Map of instanceId -> ClientInfo for locally connected agents
     */
    private final Map<String, ClientInfo> localClients = new ConcurrentHashMap<>();

    /**
     * Map of appId -> Set of instanceIds for local agents
     */
    private final Map<String, Set<String>> localAppInstances = new ConcurrentHashMap<>();

    /**
     * Map of instanceId -> List of ThreadPoolState for local agents
     */
    private final Map<String, List<ThreadPoolState>> localInstanceStates = new ConcurrentHashMap<>();

    // ==================== Remote data (agents connected to OTHER nodes) ====================

    /**
     * Map of instanceId -> RemoteClientInfo for remote agents
     */
    private final Map<String, RemoteClientInfo> remoteClients = new ConcurrentHashMap<>();

    /**
     * Map of appId -> Set of instanceIds for remote agents
     */
    private final Map<String, Set<String>> remoteAppInstances = new ConcurrentHashMap<>();

    /**
     * Map of instanceId -> List of ThreadPoolState for remote agents
     */
    private final Map<String, List<ThreadPoolState>> remoteInstanceStates = new ConcurrentHashMap<>();

    /**
     * Data syncer for cluster mode (null when standalone)
     */
    private volatile DataSyncer dataSyncer;

    private ClientRegistry() {
    }

    public static ClientRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Set the data syncer (called when cluster mode is enabled).
     */
    public void setDataSyncer(DataSyncer dataSyncer) {
        this.dataSyncer = dataSyncer;
    }

    // ==================== Local Client Operations ====================

    /**
     * Register a locally connected client.
     */
    public void register(String appId, String instanceId, Channel channel, Collection<String> threadPoolIds) {
        ClientInfo info = new ClientInfo();
        info.setAppId(appId);
        info.setInstanceId(instanceId);
        info.setChannel(channel);
        info.setThreadPoolIds(new ArrayList<>(threadPoolIds));
        info.setConnectedAt(LocalDateTime.now());
        info.setLastHeartbeat(LocalDateTime.now());

        localClients.put(instanceId, info);
        localAppInstances.computeIfAbsent(appId, k -> ConcurrentHashMap.newKeySet()).add(instanceId);

        log.info("Client registered (local): appId={}, instanceId={}, threadPools={}",
                appId, instanceId, threadPoolIds.size());

        // Trigger cluster sync
        if (dataSyncer != null) {
            dataSyncer.syncRegister(appId, instanceId, new ArrayList<>(threadPoolIds));
        }
    }

    /**
     * Unregister a locally connected client.
     */
    public void unregister(String instanceId) {
        ClientInfo info = localClients.remove(instanceId);
        if (info != null) {
            Set<String> instances = localAppInstances.get(info.getAppId());
            if (instances != null) {
                instances.remove(instanceId);
                if (instances.isEmpty()) {
                    localAppInstances.remove(info.getAppId());
                }
            }
            localInstanceStates.remove(instanceId);
            log.info("Client unregistered (local): instanceId={}", instanceId);

            // Trigger cluster sync
            if (dataSyncer != null) {
                dataSyncer.syncUnregister(instanceId);
            }
        }
    }

    /**
     * Update heartbeat time for a local client.
     */
    public void updateHeartbeat(String instanceId) {
        ClientInfo info = localClients.get(instanceId);
        if (info != null) {
            info.setLastHeartbeat(LocalDateTime.now());
        }
    }

    /**
     * Update thread pool states for a local client.
     */
    public void updateStates(String instanceId, List<ThreadPoolState> states) {
        localInstanceStates.put(instanceId, states);
        ClientInfo info = localClients.get(instanceId);
        if (info != null) {
            info.setLastHeartbeat(LocalDateTime.now());
        }

        // Trigger cluster sync
        if (dataSyncer != null) {
            dataSyncer.syncStateUpdate(instanceId, states);
        }
    }

    // ==================== Remote Client Operations (for cluster sync) ====================

    /**
     * Register a remote client (from another cluster node).
     */
    public void registerRemote(String sourceNodeId, String appId, String instanceId,
                                List<String> threadPoolIds, long timestamp) {
        // Don't overwrite local clients
        if (localClients.containsKey(instanceId)) {
            return;
        }

        RemoteClientInfo info = remoteClients.get(instanceId);
        if (info != null && info.getTimestamp() >= timestamp) {
            return; // Our data is newer
        }

        info = new RemoteClientInfo();
        info.setAppId(appId);
        info.setInstanceId(instanceId);
        info.setSourceNodeId(sourceNodeId);
        info.setThreadPoolIds(threadPoolIds != null ? new ArrayList<>(threadPoolIds) : new ArrayList<>());
        info.setTimestamp(timestamp);
        info.setSyncedAt(LocalDateTime.now());

        remoteClients.put(instanceId, info);
        remoteAppInstances.computeIfAbsent(appId, k -> ConcurrentHashMap.newKeySet()).add(instanceId);

        log.debug("Client registered (remote): appId={}, instanceId={}, sourceNode={}",
                appId, instanceId, sourceNodeId);
    }

    /**
     * Unregister a remote client.
     */
    public void unregisterRemote(String instanceId) {
        RemoteClientInfo info = remoteClients.remove(instanceId);
        if (info != null) {
            Set<String> instances = remoteAppInstances.get(info.getAppId());
            if (instances != null) {
                instances.remove(instanceId);
                if (instances.isEmpty()) {
                    remoteAppInstances.remove(info.getAppId());
                }
            }
            remoteInstanceStates.remove(instanceId);
            log.debug("Client unregistered (remote): instanceId={}", instanceId);
        }
    }

    /**
     * Update thread pool states for a remote client.
     */
    public void updateRemoteStates(String instanceId, List<ThreadPoolState> states, long timestamp) {
        // Don't overwrite local data
        if (localClients.containsKey(instanceId)) {
            return;
        }

        RemoteClientInfo info = remoteClients.get(instanceId);
        if (info != null) {
            info.setTimestamp(timestamp);
        }
        if (states != null) {
            remoteInstanceStates.put(instanceId, states);
        }
    }

    /**
     * Remove all remote data originating from a specific node (when node goes down).
     */
    public void removeRemoteDataByNode(String nodeId) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, RemoteClientInfo> entry : remoteClients.entrySet()) {
            if (nodeId.equals(entry.getValue().getSourceNodeId())) {
                toRemove.add(entry.getKey());
            }
        }
        for (String instanceId : toRemove) {
            unregisterRemote(instanceId);
        }
        log.info("Removed {} remote clients from node: {}", toRemove.size(), nodeId);
    }

    /**
     * Get local client data as SyncData list for full sync responses.
     */
    public List<SyncData> getLocalSyncData(String selfNodeId) {
        List<SyncData> result = new ArrayList<>();
        for (Map.Entry<String, ClientInfo> entry : localClients.entrySet()) {
            String instanceId = entry.getKey();
            ClientInfo info = entry.getValue();
            List<ThreadPoolState> states = localInstanceStates.get(instanceId);

            result.add(SyncData.builder()
                    .sourceNodeId(selfNodeId)
                    .instanceId(instanceId)
                    .appId(info.getAppId())
                    .threadPoolIds(info.getThreadPoolIds())
                    .states(states)
                    .timestamp(System.currentTimeMillis())
                    .type(SyncData.SyncType.REGISTER)
                    .build());
        }
        return result;
    }

    /**
     * Get a local client's Channel.
     */
    public Channel getLocalChannel(String instanceId) {
        ClientInfo info = localClients.get(instanceId);
        return info != null ? info.getChannel() : null;
    }

    /**
     * Get a local client info.
     */
    public ClientInfo getLocalClient(String instanceId) {
        return localClients.get(instanceId);
    }

    /**
     * Check if an instance is connected locally.
     */
    public boolean isLocal(String instanceId) {
        return localClients.containsKey(instanceId);
    }

    /**
     * Get the source node ID for a remote client.
     */
    public String getRemoteNodeId(String instanceId) {
        RemoteClientInfo info = remoteClients.get(instanceId);
        return info != null ? info.getSourceNodeId() : null;
    }

    /**
     * Get count of local clients.
     */
    public int getLocalClientCount() {
        return localClients.size();
    }

    // ==================== Unified Query Methods (merge local + remote) ====================

    /**
     * Get all registered apps (local + remote).
     */
    public Set<String> listApps() {
        Set<String> apps = new HashSet<>(localAppInstances.keySet());
        apps.addAll(remoteAppInstances.keySet());
        return apps;
    }

    /**
     * Get instances for an app (local + remote).
     */
    public Set<String> listInstances(String appId) {
        Set<String> instances = new HashSet<>();
        Set<String> local = localAppInstances.get(appId);
        if (local != null) instances.addAll(local);
        Set<String> remote = remoteAppInstances.get(appId);
        if (remote != null) instances.addAll(remote);
        return instances;
    }

    /**
     * Get client info (local first, then remote as ClientInfo wrapper).
     */
    public ClientInfo getClient(String instanceId) {
        ClientInfo local = localClients.get(instanceId);
        if (local != null) return local;

        // Wrap remote info as ClientInfo for API compatibility
        RemoteClientInfo remote = remoteClients.get(instanceId);
        if (remote != null) {
            ClientInfo wrapper = new ClientInfo();
            wrapper.setAppId(remote.getAppId());
            wrapper.setInstanceId(remote.getInstanceId());
            wrapper.setThreadPoolIds(remote.getThreadPoolIds());
            wrapper.setConnectedAt(remote.getSyncedAt());
            wrapper.setLastHeartbeat(remote.getSyncedAt());
            wrapper.setRemote(true);
            wrapper.setSourceNodeId(remote.getSourceNodeId());
            return wrapper;
        }
        return null;
    }

    /**
     * Get all clients (local + remote).
     */
    public Collection<ClientInfo> listClients() {
        List<ClientInfo> result = new ArrayList<>(localClients.values());
        // Add remote clients as ClientInfo wrappers
        for (RemoteClientInfo remote : remoteClients.values()) {
            ClientInfo wrapper = new ClientInfo();
            wrapper.setAppId(remote.getAppId());
            wrapper.setInstanceId(remote.getInstanceId());
            wrapper.setThreadPoolIds(remote.getThreadPoolIds());
            wrapper.setConnectedAt(remote.getSyncedAt());
            wrapper.setLastHeartbeat(remote.getSyncedAt());
            wrapper.setRemote(true);
            wrapper.setSourceNodeId(remote.getSourceNodeId());
            result.add(wrapper);
        }
        return result;
    }

    /**
     * Get thread pool states for an instance (excludes web container pools).
     */
    public List<ThreadPoolState> getStates(String instanceId) {
        List<ThreadPoolState> allStates = localInstanceStates.getOrDefault(instanceId,
                remoteInstanceStates.getOrDefault(instanceId, Collections.emptyList()));
        return allStates.stream()
                .filter(state -> !isWebContainerPool(state.getThreadPoolId()))
                .toList();
    }

    /**
     * Get all thread pool states grouped by app and instance (excludes web container pools).
     */
    public Map<String, Map<String, List<ThreadPoolState>>> getAllStates() {
        Map<String, Map<String, List<ThreadPoolState>>> result = new HashMap<>();

        // Process local states
        addStatesToResult(result, localAppInstances, localInstanceStates);
        // Process remote states
        addStatesToResult(result, remoteAppInstances, remoteInstanceStates);

        return result;
    }

    private void addStatesToResult(Map<String, Map<String, List<ThreadPoolState>>> result,
                                   Map<String, Set<String>> appInstances,
                                   Map<String, List<ThreadPoolState>> instanceStates) {
        for (Map.Entry<String, Set<String>> entry : appInstances.entrySet()) {
            String appId = entry.getKey();
            Map<String, List<ThreadPoolState>> instanceMap =
                    result.computeIfAbsent(appId, k -> new HashMap<>());

            for (String instanceId : entry.getValue()) {
                List<ThreadPoolState> states = instanceStates.get(instanceId);
                if (states != null && !states.isEmpty()) {
                    List<ThreadPoolState> businessPools = states.stream()
                            .filter(state -> !isWebContainerPool(state.getThreadPoolId()))
                            .toList();
                    if (!businessPools.isEmpty()) {
                        instanceMap.put(instanceId, businessPools);
                    }
                }
            }
        }
    }

    /**
     * Get channel by instanceId (local only).
     */
    public Channel getChannel(String instanceId) {
        ClientInfo info = localClients.get(instanceId);
        return info != null ? info.getChannel() : null;
    }

    /**
     * Get all channels for instances that have a specific threadPoolId (local only for sending).
     */
    public Map<String, Channel> getChannelsForPool(String threadPoolId) {
        Map<String, Channel> channels = new HashMap<>();
        for (Map.Entry<String, List<ThreadPoolState>> entry : localInstanceStates.entrySet()) {
            String instanceId = entry.getKey();
            List<ThreadPoolState> states = entry.getValue();
            boolean hasPool = states.stream()
                    .anyMatch(s -> threadPoolId.equals(s.getThreadPoolId()));
            if (hasPool) {
                ClientInfo info = localClients.get(instanceId);
                if (info != null && info.isOnline()) {
                    channels.put(instanceId, info.getChannel());
                }
            }
        }
        return channels;
    }

    /**
     * Get all online channels (local only).
     */
    public Map<String, Channel> getAllOnlineChannels() {
        Map<String, Channel> channels = new HashMap<>();
        for (ClientInfo info : localClients.values()) {
            if (info.isOnline()) {
                channels.put(info.getInstanceId(), info.getChannel());
            }
        }
        return channels;
    }

    /**
     * Reset rejection count for a specific pool in local cache.
     */
    public void resetPoolRejectionStats(String threadPoolId) {
        resetPoolRejectionStatsInMap(threadPoolId, localInstanceStates);
        resetPoolRejectionStatsInMap(threadPoolId, remoteInstanceStates);
        log.info("Reset local cache for pool: {}", threadPoolId);
    }

    /**
     * Reset all rejection counts in local cache.
     */
    public void resetAllRejectionStats() {
        resetAllRejectionStatsInMap(localInstanceStates);
        resetAllRejectionStatsInMap(remoteInstanceStates);
        log.info("Reset all local cache rejection stats");
    }

    private void resetPoolRejectionStatsInMap(String threadPoolId,
                                               Map<String, List<ThreadPoolState>> statesMap) {
        for (List<ThreadPoolState> states : statesMap.values()) {
            for (ThreadPoolState state : states) {
                if (threadPoolId.equals(state.getThreadPoolId())) {
                    state.setRejectedCount(0L);
                }
            }
        }
    }

    private void resetAllRejectionStatsInMap(Map<String, List<ThreadPoolState>> statesMap) {
        for (List<ThreadPoolState> states : statesMap.values()) {
            for (ThreadPoolState state : states) {
                state.setRejectedCount(0L);
            }
        }
    }

    // ==================== Web Container Thread Pool Methods ====================

    /**
     * Get all web container thread pool states across all instances.
     */
    public List<WebContainerState> getAllWebContainerStates() {
        List<WebContainerState> result = new ArrayList<>();
        collectWebContainerStates(result, localClients, localInstanceStates, false);
        collectRemoteWebContainerStates(result);
        return result;
    }

    /**
     * Get web container thread pool states for a specific app.
     */
    public List<WebContainerState> getWebContainerStatesByApp(String appId) {
        List<WebContainerState> all = getAllWebContainerStates();
        return all.stream()
                .filter(s -> appId.equals(s.getAppId()))
                .toList();
    }

    /**
     * Get web container thread pool state for a specific instance.
     */
    public WebContainerState getWebContainerState(String instanceId) {
        // Check local first
        ClientInfo localInfo = localClients.get(instanceId);
        List<ThreadPoolState> localStates = localInstanceStates.get(instanceId);
        if (localInfo != null && localStates != null) {
            for (ThreadPoolState state : localStates) {
                if (isWebContainerPool(state.getThreadPoolId())) {
                    WebContainerState webState = new WebContainerState();
                    webState.setAppId(localInfo.getAppId());
                    webState.setInstanceId(instanceId);
                    webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                    webState.setState(state);
                    webState.setOnline(localInfo.isOnline());
                    return webState;
                }
            }
        }

        // Check remote
        RemoteClientInfo remoteInfo = remoteClients.get(instanceId);
        List<ThreadPoolState> remoteStates = remoteInstanceStates.get(instanceId);
        if (remoteInfo != null && remoteStates != null) {
            for (ThreadPoolState state : remoteStates) {
                if (isWebContainerPool(state.getThreadPoolId())) {
                    WebContainerState webState = new WebContainerState();
                    webState.setAppId(remoteInfo.getAppId());
                    webState.setInstanceId(instanceId);
                    webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                    webState.setState(state);
                    webState.setOnline(true); // Remote clients are assumed online if synced
                    return webState;
                }
            }
        }

        return null;
    }

    private void collectWebContainerStates(List<WebContainerState> result,
                                            Map<String, ClientInfo> clients,
                                            Map<String, List<ThreadPoolState>> statesMap,
                                            boolean isRemote) {
        for (Map.Entry<String, List<ThreadPoolState>> entry : statesMap.entrySet()) {
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
                    webState.setOnline(isRemote || clientInfo.isOnline());
                    result.add(webState);
                }
            }
        }
    }

    private void collectRemoteWebContainerStates(List<WebContainerState> result) {
        for (Map.Entry<String, List<ThreadPoolState>> entry : remoteInstanceStates.entrySet()) {
            String instanceId = entry.getKey();
            RemoteClientInfo remoteInfo = remoteClients.get(instanceId);
            if (remoteInfo == null) continue;

            for (ThreadPoolState state : entry.getValue()) {
                if (isWebContainerPool(state.getThreadPoolId())) {
                    WebContainerState webState = new WebContainerState();
                    webState.setAppId(remoteInfo.getAppId());
                    webState.setInstanceId(instanceId);
                    webState.setContainerType(extractContainerType(state.getThreadPoolId()));
                    webState.setState(state);
                    webState.setOnline(true);
                    result.add(webState);
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Check if a thread pool is a web container pool.
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
     * Extract container type from thread pool id.
     */
    private String extractContainerType(String threadPoolId) {
        if (threadPoolId.startsWith("tomcat")) return "Tomcat";
        if (threadPoolId.startsWith("jetty")) return "Jetty";
        if (threadPoolId.startsWith("undertow")) return "Undertow";
        return "Unknown";
    }

    // ==================== Data Models ====================

    /**
     * Web container state with app and instance info.
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
     * Client information for locally connected agents.
     */
    @Data
    public static class ClientInfo {
        private String appId;
        private String instanceId;
        private Channel channel;
        private List<String> threadPoolIds;
        private LocalDateTime connectedAt;
        private LocalDateTime lastHeartbeat;
        /**
         * Whether this client is on a remote node (for API compatibility)
         */
        private boolean remote = false;
        /**
         * Source node ID if this is a remote client wrapper
         */
        private String sourceNodeId;

        public boolean isOnline() {
            if (remote) return true; // Remote clients are assumed online if present
            return channel != null && channel.isActive();
        }
    }

    /**
     * Client information for remotely connected agents (synced from other nodes).
     */
    @Data
    public static class RemoteClientInfo {
        private String appId;
        private String instanceId;
        private String sourceNodeId;
        private List<String> threadPoolIds;
        private long timestamp;
        private LocalDateTime syncedAt;
    }
}
