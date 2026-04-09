package com.dynamic.thread.server.cluster;

import com.dynamic.thread.server.cluster.sync.DataSyncer;
import com.dynamic.thread.server.cluster.transport.ClusterChannelHandler;
import com.dynamic.thread.server.cluster.transport.ClusterTransportClient;
import com.dynamic.thread.server.cluster.transport.ClusterTransportServer;
import com.dynamic.thread.server.config.ServerProperties;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import com.dynamic.thread.server.registry.ClientRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Auto-configuration for cluster mode.
 * Only activated when dynamic-thread.server.cluster.enabled=true.
 * When cluster is not enabled, the system behaves exactly as before (backward compatible).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "dynamic-thread.server.cluster", name = "enabled", havingValue = "true")
public class ClusterAutoConfiguration {

    private ClusterMemberManager memberManager;
    private ClusterTransportServer transportServer;
    private ClusterTransportClient transportClient;
    private DataSyncer dataSyncer;
    private ClusterChannelHandler clusterChannelHandler;
    private ServerChannelHandler serverChannelHandler;

    @Bean
    public ClusterMemberManager clusterMemberManager(ServerProperties properties) {
        this.memberManager = new ClusterMemberManager(properties.getCluster());
        memberManager.init();
        return memberManager;
    }

    @Bean
    public ClusterChannelHandler clusterChannelHandler(ClusterMemberManager memberManager) {
        this.clusterChannelHandler = new ClusterChannelHandler(memberManager);
        return clusterChannelHandler;
    }

    @Bean
    public ClusterTransportServer clusterTransportServer(ServerProperties properties,
                                                         ClusterChannelHandler channelHandler) {
        this.transportServer = new ClusterTransportServer(properties.getCluster(), channelHandler);
        return transportServer;
    }

    @Bean
    public ClusterTransportClient clusterTransportClient(ServerProperties properties,
                                                         ClusterMemberManager memberManager,
                                                         ClusterChannelHandler channelHandler) {
        this.transportClient = new ClusterTransportClient(properties.getCluster(), memberManager, channelHandler);
        return transportClient;
    }

    @Bean
    public DataSyncer dataSyncer(ClusterMemberManager memberManager,
                                 ClusterTransportClient transportClient,
                                 ClientRegistry clientRegistry,
                                 ServerProperties properties) {
        this.dataSyncer = new DataSyncer(memberManager, transportClient, clientRegistry, properties.getCluster());
        // Wire DataSyncer into ClientRegistry
        clientRegistry.setDataSyncer(dataSyncer);
        return dataSyncer;
    }

    /**
     * Start cluster components after application is fully ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting cluster components...");

        // Wire circular dependencies
        if (clusterChannelHandler != null && dataSyncer != null) {
            clusterChannelHandler.setDataSyncer(dataSyncer);
        }
        if (clusterChannelHandler != null && transportClient != null) {
            clusterChannelHandler.setTransportClient(transportClient);
        }

        // Ensure bidirectional wiring between DataSyncer and ServerChannelHandler
        if (dataSyncer != null && serverChannelHandler != null) {
            dataSyncer.setServerChannelHandler(serverChannelHandler);
            serverChannelHandler.setDataSyncer(dataSyncer);
        }

        // Start transport server
        if (transportServer != null) {
            transportServer.start();
        }

        // Start transport client (connects to peers)
        if (transportClient != null) {
            transportClient.start();
        }

        // Start data syncer (triggers startup sync)
        if (dataSyncer != null) {
            dataSyncer.start();
        }

        // Update self node agent count reference
        if (memberManager != null) {
            updateSelfAgentCount();
        }

        log.info("Cluster mode activated: nodeId={}, members={}",
                memberManager != null ? memberManager.getSelfNode().getNodeId() : "?",
                memberManager != null ? memberManager.getMemberCount() : 0);
    }

    /**
     * Wire ServerChannelHandler into DataSyncer for config forwarding.
     * Called from DashboardServerApplication after both beans are available.
     */
    public void wireServerChannelHandler(ServerChannelHandler handler) {
        this.serverChannelHandler = handler;
        if (dataSyncer != null) {
            dataSyncer.setServerChannelHandler(handler);
            handler.setDataSyncer(dataSyncer);
        }
    }

    private void updateSelfAgentCount() {
        // This will be called periodically or on demand
        ClientRegistry registry = ClientRegistry.getInstance();
        memberManager.getSelfNode().setAgentCount(registry.getLocalClientCount());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down cluster components...");
        if (dataSyncer != null) dataSyncer.shutdown();
        if (transportClient != null) transportClient.stop();
        if (transportServer != null) transportServer.stop();
        if (memberManager != null) memberManager.shutdown();
        log.info("Cluster components shutdown complete");
    }
}
