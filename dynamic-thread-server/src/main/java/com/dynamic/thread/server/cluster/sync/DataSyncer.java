package com.dynamic.thread.server.cluster.sync;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.model.AlarmRule;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageType;
import com.dynamic.thread.server.cluster.ClusterMemberManager;
import com.dynamic.thread.server.cluster.model.ClusterNode;
import com.dynamic.thread.server.cluster.model.SyncData;
import com.dynamic.thread.server.cluster.transport.ClusterTransportClient;
import com.dynamic.thread.server.config.ServerProperties;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.registry.ClientRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * Core data synchronization logic between cluster nodes.
 * Handles incremental sync (real-time), full sync (periodic), and startup sync.
 */
@Slf4j
public class DataSyncer {

    private final ClusterMemberManager memberManager;
    private final ClusterTransportClient transportClient;
    private final ClientRegistry clientRegistry;
    private final ServerProperties.ClusterConfig clusterConfig;
    private final ObjectMapper objectMapper;

    /**
     * Reference to ServerChannelHandler for config forwarding
     */
    private ServerChannelHandler serverChannelHandler;

    /**
     * Scheduler for periodic full sync
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "data-syncer");
        t.setDaemon(true);
        return t;
    });

    /**
     * Async executor for sync operations to avoid blocking Netty threads
     */
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sync-worker");
        t.setDaemon(true);
        return t;
    });

    public DataSyncer(ClusterMemberManager memberManager,
                      ClusterTransportClient transportClient,
                      ClientRegistry clientRegistry,
                      ServerProperties.ClusterConfig clusterConfig) {
        this.memberManager = memberManager;
        this.transportClient = transportClient;
        this.clientRegistry = clientRegistry;
        this.clusterConfig = clusterConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void setServerChannelHandler(ServerChannelHandler serverChannelHandler) {
        this.serverChannelHandler = serverChannelHandler;
    }

    /**
     * Start the data syncer: trigger startup sync and schedule periodic full sync.
     */
    public void start() {
        // Delay startup sync to allow connections to establish
        scheduler.schedule(this::startupSync, 3, TimeUnit.SECONDS);

        // Schedule periodic full sync
        scheduler.scheduleAtFixedRate(this::periodicFullSync,
                clusterConfig.getSyncIntervalMs() + 5000L,
                clusterConfig.getSyncIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info("Data syncer started: syncInterval={}ms", clusterConfig.getSyncIntervalMs());
    }

    // ==================== Outbound: Send sync to peers ====================

    /**
     * Broadcast incremental sync for a client registration event.
     */
    public void syncRegister(String appId, String instanceId, List<String> threadPoolIds) {
        SyncData syncData = SyncData.builder()
                .sourceNodeId(memberManager.getSelfNode().getNodeId())
                .instanceId(instanceId)
                .appId(appId)
                .threadPoolIds(threadPoolIds)
                .timestamp(System.currentTimeMillis())
                .type(SyncData.SyncType.REGISTER)
                .build();
        broadcastIncrementalSync(syncData);
    }

    /**
     * Broadcast incremental sync for a client unregistration event.
     */
    public void syncUnregister(String instanceId) {
        SyncData syncData = SyncData.builder()
                .sourceNodeId(memberManager.getSelfNode().getNodeId())
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .type(SyncData.SyncType.UNREGISTER)
                .build();
        broadcastIncrementalSync(syncData);
    }

    /**
     * Broadcast incremental sync for a state update event.
     */
    public void syncStateUpdate(String instanceId, List<ThreadPoolState> states) {
        SyncData syncData = SyncData.builder()
                .sourceNodeId(memberManager.getSelfNode().getNodeId())
                .instanceId(instanceId)
                .states(states)
                .timestamp(System.currentTimeMillis())
                .type(SyncData.SyncType.STATE_UPDATE)
                .build();
        broadcastIncrementalSync(syncData);
    }

    /**
     * Forward a config update to the node that owns the agent.
     *
     * @param targetNodeId target cluster node ID
     * @param instanceId   agent instance ID
     * @param configJson   config JSON body
     * @param originalType the original message type (CONFIG_UPDATE or WEB_CONTAINER_CONFIG_UPDATE)
     * @return true if forwarding was successful
     */
    public boolean forwardConfigUpdate(String targetNodeId, String instanceId, String configJson, MessageType originalType) {
        Message msg = Message.builder()
                .type(MessageType.CLUSTER_CONFIG_FORWARD)
                .messageId(UUID.randomUUID().toString().replace("-", ""))
                .appId(memberManager.getSelfNode().getNodeId())
                .instanceId(instanceId)
                .timestamp(System.currentTimeMillis())
                .body(configJson)
                .errorMsg(String.valueOf(originalType.getCode()))
                .build();
        return transportClient.sendToNode(targetNodeId, msg);
    }

    /**
     * Broadcast alarm rule sync to all peers.
     */
    public void syncAlarmRules(List<AlarmRule> rules) {
        try {
            String body = objectMapper.writeValueAsString(rules);
            Message msg = Message.builder()
                    .type(MessageType.CLUSTER_ALARM_SYNC)
                    .messageId(UUID.randomUUID().toString().replace("-", ""))
                    .appId(memberManager.getSelfNode().getNodeId())
                    .timestamp(System.currentTimeMillis())
                    .body(body)
                    .build();
            transportClient.broadcastToAll(msg);
            log.info("Alarm rules synced to cluster: {} rules", rules.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alarm rules for sync: {}", e.getMessage());
        }
    }

    // ==================== Inbound: Handle sync from peers ====================

    /**
     * Handle full sync request from a peer: respond with all local client data.
     */
    public void handleFullSyncRequest(ChannelHandlerContext ctx, String requestingNodeId) {
        syncExecutor.submit(() -> {
            try {
                List<SyncData> localData = clientRegistry.getLocalSyncData(
                        memberManager.getSelfNode().getNodeId());
                String body = objectMapper.writeValueAsString(localData);

                Message response = Message.builder()
                        .type(MessageType.CLUSTER_FULL_SYNC)
                        .messageId(UUID.randomUUID().toString().replace("-", ""))
                        .appId(memberManager.getSelfNode().getNodeId())
                        .timestamp(System.currentTimeMillis())
                        .body(body)
                        .build();
                ctx.writeAndFlush(response);
                log.info("Sent full sync response to node {}: {} clients", requestingNodeId, localData.size());
            } catch (Exception e) {
                log.error("Failed to send full sync response: {}", e.getMessage());
            }
        });
    }

    /**
     * Handle full sync response from a peer: apply the data.
     */
    public void handleFullSyncResponse(String sourceNodeId, String body) {
        syncExecutor.submit(() -> {
            try {
                List<SyncData> syncDataList = objectMapper.readValue(body,
                        new TypeReference<List<SyncData>>() {});
                for (SyncData data : syncDataList) {
                    applyRemoteSyncData(sourceNodeId, data);
                }
                log.info("Applied full sync from node {}: {} entries", sourceNodeId, syncDataList.size());
            } catch (Exception e) {
                log.error("Failed to apply full sync from node {}: {}", sourceNodeId, e.getMessage());
            }
        });
    }

    /**
     * Handle incremental sync from a peer.
     */
    public void handleIncrementalSync(String sourceNodeId, String body) {
        syncExecutor.submit(() -> {
            try {
                SyncData syncData = objectMapper.readValue(body, SyncData.class);
                applyRemoteSyncData(sourceNodeId, syncData);
            } catch (Exception e) {
                log.error("Failed to apply incremental sync from node {}: {}", sourceNodeId, e.getMessage());
            }
        });
    }

    /**
     * Handle config forward: find local agent and send config update.
     */
    public void handleConfigForward(String instanceId, String configBody, MessageType forwardType) {
        if (serverChannelHandler == null) {
            log.warn("ServerChannelHandler not set, cannot forward config to {}", instanceId);
            return;
        }

        // Check if this agent is connected locally
        Channel localChannel = clientRegistry.getLocalChannel(instanceId);
        if (localChannel != null && localChannel.isActive()) {
            ClientRegistry.ClientInfo info = clientRegistry.getLocalClient(instanceId);
            if (info != null) {
                Message configMsg = Message.builder()
                        .type(forwardType)
                        .messageId(UUID.randomUUID().toString().replace("-", ""))
                        .appId(info.getAppId())
                        .instanceId(instanceId)
                        .timestamp(System.currentTimeMillis())
                        .body(configBody)
                        .build();
                localChannel.writeAndFlush(configMsg);
                log.info("Config forwarded to local agent: {}, type={}", instanceId, forwardType);
            }
        } else {
            log.warn("Config forward target not found locally: {}", instanceId);
        }
    }

    /**
     * Handle node leave: clean up remote data from that node.
     */
    public void handleNodeLeave(String nodeId) {
        syncExecutor.submit(() -> {
            clientRegistry.removeRemoteDataByNode(nodeId);
            log.info("Cleaned remote data for departed node: {}", nodeId);
        });
    }

    /**
     * Handle alarm rule sync from a peer.
     */
    public void handleAlarmSync(String body) {
        syncExecutor.submit(() -> {
            try {
                List<AlarmRule> rules = objectMapper.readValue(body,
                        new TypeReference<List<AlarmRule>>() {});
                AlarmManager alarmManager = AlarmManager.getInstance();
                // Replace all rules with synced ones
                alarmManager.replaceAllRules(rules);
                log.info("Applied alarm rule sync: {} rules", rules.size());
            } catch (Exception e) {
                log.error("Failed to apply alarm rule sync: {}", e.getMessage());
            }
        });
    }

    // ==================== Internal sync logic ====================

    /**
     * Startup sync: request full data from the first available healthy peer.
     */
    private void startupSync() {
        List<ClusterNode> healthyMembers = memberManager.getHealthyMembers();
        if (healthyMembers.isEmpty()) {
            log.info("No healthy peers found for startup sync, starting fresh");
            return;
        }

        for (ClusterNode node : healthyMembers) {
            if (transportClient.isConnected(node.getNodeId())) {
                log.info("Requesting full sync from peer: {}", node.getNodeId());
                Message request = Message.builder()
                        .type(MessageType.CLUSTER_FULL_SYNC)
                        .messageId(UUID.randomUUID().toString().replace("-", ""))
                        .appId(memberManager.getSelfNode().getNodeId())
                        .timestamp(System.currentTimeMillis())
                        .build(); // Empty body = request
                transportClient.sendToNode(node.getNodeId(), request);
                return; // Only need sync from one peer
            }
        }
        log.warn("No connected peers available for startup sync");
    }

    /**
     * Periodic full sync: verify consistency with peers.
     */
    private void periodicFullSync() {
        try {
            List<ClusterNode> healthyMembers = memberManager.getHealthyMembers();
            for (ClusterNode node : healthyMembers) {
                if (transportClient.isConnected(node.getNodeId())) {
                    Message request = Message.builder()
                            .type(MessageType.CLUSTER_FULL_SYNC)
                            .messageId(UUID.randomUUID().toString().replace("-", ""))
                            .appId(memberManager.getSelfNode().getNodeId())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    transportClient.sendToNode(node.getNodeId(), request);
                }
            }
        } catch (Exception e) {
            log.error("Periodic full sync failed: {}", e.getMessage());
        }
    }

    /**
     * Broadcast an incremental sync data to all healthy peers.
     */
    private void broadcastIncrementalSync(SyncData syncData) {
        try {
            String body = objectMapper.writeValueAsString(syncData);
            Message msg = Message.builder()
                    .type(MessageType.CLUSTER_INCREMENTAL_SYNC)
                    .messageId(UUID.randomUUID().toString().replace("-", ""))
                    .appId(memberManager.getSelfNode().getNodeId())
                    .timestamp(System.currentTimeMillis())
                    .body(body)
                    .build();
            transportClient.broadcastToAll(msg);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize sync data: {}", e.getMessage());
        }
    }

    /**
     * Apply remote sync data to the local registry.
     */
    private void applyRemoteSyncData(String sourceNodeId, SyncData data) {
        if (data == null || data.getType() == null) return;

        switch (data.getType()) {
            case REGISTER -> {
                clientRegistry.registerRemote(sourceNodeId, data.getAppId(),
                        data.getInstanceId(), data.getThreadPoolIds(), data.getTimestamp());
            }
            case UNREGISTER -> {
                clientRegistry.unregisterRemote(data.getInstanceId());
            }
            case STATE_UPDATE -> {
                clientRegistry.updateRemoteStates(data.getInstanceId(), data.getStates(), data.getTimestamp());
            }
        }
    }

    /**
     * Shutdown the data syncer.
     */
    public void shutdown() {
        scheduler.shutdown();
        syncExecutor.shutdown();
        log.info("Data syncer shutdown");
    }
}
