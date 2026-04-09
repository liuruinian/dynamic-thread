package com.dynamic.thread.server.cluster.transport;

import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageCodec;
import com.dynamic.thread.core.protocol.MessageType;
import com.dynamic.thread.server.cluster.ClusterMemberManager;
import com.dynamic.thread.server.cluster.model.ClusterNode;
import com.dynamic.thread.server.config.ServerProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Netty client for connecting to other cluster nodes.
 * Maintains persistent connections to all peer nodes with automatic reconnection.
 */
@Slf4j
public class ClusterTransportClient {

    private final ServerProperties.ClusterConfig clusterConfig;
    private final ClusterMemberManager memberManager;
    private final ClusterChannelHandler channelHandler;

    /**
     * Active channels to peer nodes: nodeId -> Channel
     */
    private final Map<String, Channel> peerChannels = new ConcurrentHashMap<>();

    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;

    /**
     * Scheduler for reconnection and periodic heartbeat
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "cluster-client-" + Thread.currentThread().getId());
        t.setDaemon(true);
        return t;
    });

    public ClusterTransportClient(ServerProperties.ClusterConfig clusterConfig,
                                  ClusterMemberManager memberManager,
                                  ClusterChannelHandler channelHandler) {
        this.clusterConfig = clusterConfig;
        this.memberManager = memberManager;
        this.channelHandler = channelHandler;
    }

    /**
     * Initialize and connect to all peer nodes.
     */
    public void start() {
        workerGroup = new NioEventLoopGroup(2);
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        int writeIdleSeconds = clusterConfig.getHeartbeatIntervalMs() / 1000;
                        pipeline.addLast(new IdleStateHandler(0, writeIdleSeconds, 0, TimeUnit.SECONDS));
                        pipeline.addLast(new MessageCodec());
                        pipeline.addLast(channelHandler);
                    }
                });

        // Connect to all other members
        for (ClusterNode node : memberManager.getOtherMembers()) {
            connectToNode(node);
        }

        // Start periodic heartbeat to all peers
        scheduler.scheduleAtFixedRate(this::sendHeartbeatToAll,
                clusterConfig.getHeartbeatIntervalMs(),
                clusterConfig.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS);

        // Start periodic reconnection check
        scheduler.scheduleAtFixedRate(this::checkAndReconnect,
                clusterConfig.getHeartbeatIntervalMs() * 2L,
                clusterConfig.getHeartbeatIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info("Cluster transport client started");
    }

    /**
     * Connect to a specific peer node.
     */
    private void connectToNode(ClusterNode node) {
        if (node.isSelf()) return;

        String nodeId = node.getNodeId();
        Channel existingChannel = peerChannels.get(nodeId);
        if (existingChannel != null && existingChannel.isActive()) {
            return; // Already connected
        }

        try {
            ChannelFuture future = bootstrap.connect(node.getIp(), node.getClusterPort());
            future.addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    peerChannels.put(nodeId, f.channel());
                    log.info("Connected to cluster peer: nodeId={}, address={}", nodeId, node.getAddress());

                    // Send join notification
                    sendNodeJoin(nodeId, f.channel());
                } else {
                    log.warn("Failed to connect to cluster peer: nodeId={}, address={}, reason={}",
                            nodeId, node.getAddress(),
                            f.cause() != null ? f.cause().getMessage() : "unknown");
                }
            });
        } catch (Exception e) {
            log.error("Error connecting to cluster peer: nodeId={}, error={}", nodeId, e.getMessage());
        }
    }

    /**
     * Send node join notification after connecting.
     */
    private void sendNodeJoin(String targetNodeId, Channel channel) {
        ClusterNode self = memberManager.getSelfNode();
        Message msg = Message.builder()
                .type(MessageType.CLUSTER_NODE_JOIN)
                .messageId(java.util.UUID.randomUUID().toString().replace("-", ""))
                .appId(self.getNodeId())
                .instanceId(self.getAddress())
                .timestamp(System.currentTimeMillis())
                .build();
        channel.writeAndFlush(msg);
    }

    /**
     * Send heartbeat to all connected peers.
     */
    private void sendHeartbeatToAll() {
        ClusterNode self = memberManager.getSelfNode();
        int localAgentCount = self.getAgentCount();

        for (Map.Entry<String, Channel> entry : peerChannels.entrySet()) {
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                Message heartbeat = Message.builder()
                        .type(MessageType.CLUSTER_HEARTBEAT)
                        .messageId(java.util.UUID.randomUUID().toString().replace("-", ""))
                        .appId(self.getNodeId())
                        .instanceId(self.getAddress())
                        .timestamp(System.currentTimeMillis())
                        .body(String.valueOf(localAgentCount))
                        .build();
                channel.writeAndFlush(heartbeat);
            }
        }
    }

    /**
     * Check for disconnected peers and reconnect.
     */
    private void checkAndReconnect() {
        for (ClusterNode node : memberManager.getOtherMembers()) {
            Channel channel = peerChannels.get(node.getNodeId());
            if (channel == null || !channel.isActive()) {
                log.debug("Reconnecting to cluster peer: nodeId={}", node.getNodeId());
                connectToNode(node);
            }
        }
    }

    /**
     * Send a message to a specific peer node.
     *
     * @return true if message was sent
     */
    public boolean sendToNode(String nodeId, Message message) {
        Channel channel = peerChannels.get(nodeId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            return true;
        }
        log.warn("Cannot send to node {}: not connected", nodeId);
        return false;
    }

    /**
     * Broadcast a message to all healthy peer nodes.
     */
    public void broadcastToAll(Message message) {
        for (ClusterNode node : memberManager.getHealthyMembers()) {
            sendToNode(node.getNodeId(), message);
        }
    }

    /**
     * Get the channel for a specific peer node.
     */
    public Channel getChannel(String nodeId) {
        return peerChannels.get(nodeId);
    }

    /**
     * Check if connected to a specific peer.
     */
    public boolean isConnected(String nodeId) {
        Channel channel = peerChannels.get(nodeId);
        return channel != null && channel.isActive();
    }

    /**
     * Get count of active peer connections.
     */
    public int getActivePeerCount() {
        return (int) peerChannels.values().stream()
                .filter(ch -> ch != null && ch.isActive())
                .count();
    }

    /**
     * Remap a peer channel from old nodeId to new nodeId.
     * Called when a remote node's actual configured nodeId is discovered.
     */
    public void remapPeerChannel(String oldNodeId, String newNodeId) {
        if (oldNodeId.equals(newNodeId)) return;
        Channel channel = peerChannels.remove(oldNodeId);
        if (channel != null && channel.isActive()) {
            peerChannels.put(newNodeId, channel);
            log.info("Remapped peer channel: {} -> {}", oldNodeId, newNodeId);
        }
    }

    /**
     * Stop the transport client.
     */
    public void stop() {
        scheduler.shutdown();
        for (Channel channel : peerChannels.values()) {
            if (channel != null) {
                channel.close();
            }
        }
        peerChannels.clear();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Cluster transport client stopped");
    }
}
