package com.dynamic.thread.server.cluster.transport;

import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageType;
import com.dynamic.thread.server.cluster.ClusterMemberManager;
import com.dynamic.thread.server.cluster.model.ClusterNode;
import com.dynamic.thread.server.cluster.sync.DataSyncer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Channel handler for cluster inter-node communication.
 * Processes cluster-specific message types (heartbeat, sync, config forward, etc.)
 */
@Slf4j
@ChannelHandler.Sharable
public class ClusterChannelHandler extends SimpleChannelInboundHandler<Message> {

    private final ClusterMemberManager memberManager;
    private DataSyncer dataSyncer;
    private ClusterTransportClient transportClient;

    public ClusterChannelHandler(ClusterMemberManager memberManager) {
        this.memberManager = memberManager;
    }

    public void setDataSyncer(DataSyncer dataSyncer) {
        this.dataSyncer = dataSyncer;
    }

    public void setTransportClient(ClusterTransportClient transportClient) {
        this.transportClient = transportClient;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Cluster peer connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (msg.getType() == null) {
            return;
        }

        switch (msg.getType()) {
            case CLUSTER_HEARTBEAT:
                handleClusterHeartbeat(ctx, msg);
                break;
            case CLUSTER_FULL_SYNC:
                handleFullSync(ctx, msg);
                break;
            case CLUSTER_INCREMENTAL_SYNC:
                handleIncrementalSync(ctx, msg);
                break;
            case CLUSTER_CONFIG_FORWARD:
                handleConfigForward(ctx, msg);
                break;
            case CLUSTER_NODE_JOIN:
                handleNodeJoin(ctx, msg);
                break;
            case CLUSTER_NODE_LEAVE:
                handleNodeLeave(ctx, msg);
                break;
            case CLUSTER_ALARM_SYNC:
                handleAlarmSync(ctx, msg);
                break;
            default:
                log.warn("Unexpected message type on cluster channel: {}", msg.getType());
                break;
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Cluster peer idle timeout, closing: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Cluster peer disconnected: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Cluster channel exception: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * Handle cluster heartbeat: update member manager heartbeat timestamp.
     */
    private void handleClusterHeartbeat(ChannelHandlerContext ctx, Message msg) {
        String nodeId = msg.getAppId(); // Reuse appId field for nodeId
        String nodeAddress = msg.getInstanceId(); // Node address for remapping
        int agentCount = 0;
        if (msg.getBody() != null) {
            try {
                agentCount = Integer.parseInt(msg.getBody());
            } catch (NumberFormatException ignored) {
            }
        }
        // Ensure nodeId is remapped (handles first heartbeat before NODE_JOIN processed)
        if (nodeAddress != null && !nodeAddress.trim().isEmpty()) {
            if (transportClient != null) {
                ClusterNode existingNode = memberManager.findNodeByAddress(nodeAddress);
                if (existingNode != null && !existingNode.getNodeId().equals(nodeId)) {
                    transportClient.remapPeerChannel(existingNode.getNodeId(), nodeId);
                }
            }
            memberManager.onNodeJoin(nodeId, nodeAddress);
        }
        memberManager.onHeartbeatReceived(nodeId, agentCount);
        log.debug("Cluster heartbeat from node: {}", nodeId);

        // Send heartbeat response
        Message response = Message.response(msg.getMessageId(), true, null);
        ctx.writeAndFlush(response);
    }

    /**
     * Handle full sync request/response.
     */
    private void handleFullSync(ChannelHandlerContext ctx, Message msg) {
        if (dataSyncer == null) return;
        String sourceNodeId = msg.getAppId();
        String body = msg.getBody();

        if (body == null || body.trim().isEmpty()) {
            // This is a full sync request - respond with our local data
            log.info("Full sync request from node: {}", sourceNodeId);
            dataSyncer.handleFullSyncRequest(ctx, sourceNodeId);
        } else {
            // This is a full sync response - apply the data
            log.info("Full sync response from node: {}", sourceNodeId);
            dataSyncer.handleFullSyncResponse(sourceNodeId, body);
        }
    }

    /**
     * Handle incremental sync (register/unregister/state update).
     */
    private void handleIncrementalSync(ChannelHandlerContext ctx, Message msg) {
        if (dataSyncer == null) return;
        String sourceNodeId = msg.getAppId();
        String body = msg.getBody();
        log.debug("Incremental sync from node: {}", sourceNodeId);
        dataSyncer.handleIncrementalSync(sourceNodeId, body);
    }

    /**
     * Handle config forward: relay config update to a local agent.
     */
    private void handleConfigForward(ChannelHandlerContext ctx, Message msg) {
        if (dataSyncer == null) return;
        String instanceId = msg.getInstanceId();
        String configBody = msg.getBody();
        // Resolve the original message type from errorMsg field
        MessageType forwardType = MessageType.CONFIG_UPDATE;
        if (msg.getErrorMsg() != null) {
            try {
                byte code = Byte.parseByte(msg.getErrorMsg());
                MessageType resolved = MessageType.fromCode(code);
                if (resolved != null) {
                    forwardType = resolved;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        log.info("Config forward request for instance: {}, type={}", instanceId, forwardType);
        dataSyncer.handleConfigForward(instanceId, configBody, forwardType);
    }

    /**
     * Handle node join notification: remap nodeId and mark node as UP.
     */
    private void handleNodeJoin(ChannelHandlerContext ctx, Message msg) {
        String nodeId = msg.getAppId();
        String nodeAddress = msg.getInstanceId();
        log.info("Cluster node join notification: nodeId={}, address={}", nodeId, nodeAddress);
        // Remap peer channel if nodeId changed
        if (transportClient != null && nodeAddress != null) {
            ClusterNode existingNode = memberManager.findNodeByAddress(nodeAddress);
            if (existingNode != null && !existingNode.getNodeId().equals(nodeId)) {
                transportClient.remapPeerChannel(existingNode.getNodeId(), nodeId);
            }
        }
        memberManager.onNodeJoin(nodeId, nodeAddress);
    }

    /**
     * Handle node leave notification.
     */
    private void handleNodeLeave(ChannelHandlerContext ctx, Message msg) {
        String nodeId = msg.getAppId();
        log.info("Cluster node leave notification: {}", nodeId);
        if (dataSyncer != null) {
            dataSyncer.handleNodeLeave(nodeId);
        }
    }

    /**
     * Handle alarm rule sync.
     */
    private void handleAlarmSync(ChannelHandlerContext ctx, Message msg) {
        if (dataSyncer == null) return;
        String body = msg.getBody();
        log.info("Alarm rule sync received from node: {}", msg.getAppId());
        dataSyncer.handleAlarmSync(body);
    }
}
