package com.dynamic.thread.agent.handler;

import com.dynamic.thread.agent.config.AgentProperties;
import com.dynamic.thread.core.config.ThreadPoolConfig;
import com.dynamic.thread.core.model.ConfigChangeResult;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageType;
import com.dynamic.thread.core.registry.ThreadPoolRegistry;
import com.dynamic.thread.core.util.CommonComponents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.function.Consumer;

/**
 * Channel handler for Agent.
 * Handles incoming messages and idle events.
 */
@Slf4j
@ChannelHandler.Sharable
public class AgentChannelHandler extends SimpleChannelInboundHandler<Message> {

    private final AgentProperties properties;
    private final ThreadPoolRegistry registry;
    private final ObjectMapper objectMapper;
    
    private String instanceId;
    private Runnable reconnectCallback;
    private Consumer<String> webContainerConfigHandler;
    private Channel channel;

    public AgentChannelHandler(AgentProperties properties, ThreadPoolRegistry registry) {
        this.properties = properties;
        this.registry = registry;
        this.objectMapper = CommonComponents.objectMapper();
        this.instanceId = generateInstanceId();
    }

    public void setReconnectCallback(Runnable callback) {
        this.reconnectCallback = callback;
    }

    /**
     * Set the handler for web container configuration updates
     * @param handler receives JSON config string with corePoolSize, maximumPoolSize, keepAliveTime
     */
    public void setWebContainerConfigHandler(Consumer<String> handler) {
        this.webContainerConfigHandler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connected to Dashboard Server, sending registration...");
        this.channel = ctx.channel();
        
        // Build registration info
        String registrationInfo = objectMapper.writeValueAsString(new RegistrationInfo(
                properties.getAppId(),
                instanceId,
                registry.listThreadPoolIds()
        ));
        
        // Send register message
        Message registerMsg = Message.register(properties.getAppId(), instanceId, registrationInfo);
        ctx.writeAndFlush(registerMsg);
        
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        log.debug("Received message: type={}, messageId={}", msg.getType(), msg.getMessageId());
        
        if (msg.getType() == null) {
            return;
        }
        
        if (msg.getType() == MessageType.CONFIG_UPDATE) {
            handleConfigUpdate(ctx, msg);
        } else if (msg.getType() == MessageType.RESET_REJECT_STATS) {
            handleResetRejectStats(ctx, msg);
        } else if (msg.getType() == MessageType.WEB_CONTAINER_CONFIG_UPDATE) {
            handleWebContainerConfigUpdate(ctx, msg);
        } else if (msg.getType() == MessageType.RESPONSE) {
            handleResponse(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // Send heartbeat on write idle
                log.debug("Sending heartbeat");
                Message heartbeat = Message.heartbeat(properties.getAppId(), instanceId);
                ctx.writeAndFlush(heartbeat);
            } else if (event.state() == IdleState.READER_IDLE) {
                // No response from server, close and reconnect
                log.warn("No response from server, reconnecting...");
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("Disconnected from Dashboard Server");
        this.channel = null;
        if (reconnectCallback != null) {
            reconnectCallback.run();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception: {}", cause.getMessage(), cause);
        ctx.close();
    }

    private void handleConfigUpdate(ChannelHandlerContext ctx, Message msg) {
        String configJson = msg.getBody();
        log.info("Received config update: {}", configJson);
        
        try {
            // Parse config JSON
            JsonNode root = objectMapper.readTree(configJson);
            String threadPoolId = root.has("threadPoolId") ? root.get("threadPoolId").asText() : null;
            
            if (threadPoolId == null || threadPoolId.isEmpty()) {
                log.warn("Config update missing threadPoolId");
                sendConfigResponse(ctx, msg.getMessageId(), false, "Missing threadPoolId");
                return;
            }
            
            // Build ThreadPoolConfig
            ThreadPoolConfig.ThreadPoolConfigBuilder builder = ThreadPoolConfig.builder()
                    .threadPoolId(threadPoolId);
            
            if (root.has("corePoolSize") && !root.get("corePoolSize").isNull()) {
                builder.corePoolSize(root.get("corePoolSize").asInt());
            }
            if (root.has("maximumPoolSize") && !root.get("maximumPoolSize").isNull()) {
                builder.maximumPoolSize(root.get("maximumPoolSize").asInt());
            }
            if (root.has("queueCapacity") && !root.get("queueCapacity").isNull()) {
                builder.queueCapacity(root.get("queueCapacity").asInt());
            }
            if (root.has("keepAliveTime") && !root.get("keepAliveTime").isNull()) {
                builder.keepAliveTime(root.get("keepAliveTime").asLong());
            }
            if (root.has("rejectedHandler") && !root.get("rejectedHandler").isNull()) {
                String policy = root.get("rejectedHandler").asText();
                builder.rejectedHandler(policy);
            }
            
            ThreadPoolConfig config = builder.build();
            
            // Apply config update
            ConfigChangeResult result = registry.updateConfig(threadPoolId, config);
            
            if (result == null) {
                log.warn("Thread pool [{}] not found", threadPoolId);
                sendConfigResponse(ctx, msg.getMessageId(), false, "Thread pool not found: " + threadPoolId);
            } else if (result.isChanged()) {
                log.info("Config updated for [{}]: {}", threadPoolId, result.getChanges());
                sendConfigResponse(ctx, msg.getMessageId(), true, "Config updated: " + result.getChanges());
            } else {
                log.debug("No config changes for [{}]", threadPoolId);
                sendConfigResponse(ctx, msg.getMessageId(), true, "No changes needed");
            }
            
        } catch (Exception e) {
            log.error("Failed to apply config update: {}", e.getMessage(), e);
            sendConfigResponse(ctx, msg.getMessageId(), false, "Failed: " + e.getMessage());
        }
    }
    
    private void sendConfigResponse(ChannelHandlerContext ctx, String messageId, boolean success, String message) {
        Message response = Message.response(messageId, success, message);
        ctx.writeAndFlush(response);
    }

    private void handleResponse(Message msg) {
        if (Boolean.TRUE.equals(msg.getSuccess())) {
            log.debug("Received success response for message: {}", msg.getMessageId());
        } else {
            log.warn("Received error response: {}", msg.getErrorMsg());
        }
    }

    /**
     * Handle reset rejection statistics command from server
     */
    private void handleResetRejectStats(ChannelHandlerContext ctx, Message msg) {
        String threadPoolId = msg.getBody(); // null means all pools
        log.info("Received reset reject stats command, threadPoolId={}", threadPoolId);
        
        try {
            if (threadPoolId == null || threadPoolId.isEmpty()) {
                // Reset all pools
                registry.resetAllRejectedCounts();
                log.info("Reset rejection statistics for all thread pools");
                sendResponse(ctx, msg.getMessageId(), true, "All rejection stats reset");
            } else {
                // Reset specific pool
                boolean success = registry.resetRejectedCount(threadPoolId);
                if (success) {
                    log.info("Reset rejection statistics for thread pool: {}", threadPoolId);
                    sendResponse(ctx, msg.getMessageId(), true, "Rejection stats reset for " + threadPoolId);
                } else {
                    log.warn("Thread pool not found: {}", threadPoolId);
                    sendResponse(ctx, msg.getMessageId(), false, "Thread pool not found: " + threadPoolId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to reset rejection stats: {}", e.getMessage(), e);
            sendResponse(ctx, msg.getMessageId(), false, "Failed: " + e.getMessage());
        }
    }
    
    private void sendResponse(ChannelHandlerContext ctx, String messageId, boolean success, String message) {
        Message response = Message.response(messageId, success, message);
        ctx.writeAndFlush(response);
    }

    /**
     * Handle web container configuration update from server
     */
    private void handleWebContainerConfigUpdate(ChannelHandlerContext ctx, Message msg) {
        String configJson = msg.getBody();
        log.info("Received web container config update: {}", configJson);
        
        try {
            if (webContainerConfigHandler != null) {
                webContainerConfigHandler.accept(configJson);
                log.info("Web container config update applied successfully");
                sendResponse(ctx, msg.getMessageId(), true, "Web container config updated");
            } else {
                log.warn("No web container config handler registered");
                sendResponse(ctx, msg.getMessageId(), false, "Web container not available");
            }
        } catch (Exception e) {
            log.error("Failed to apply web container config update: {}", e.getMessage(), e);
            sendResponse(ctx, msg.getMessageId(), false, "Failed: " + e.getMessage());
        }
    }

    public String getAppId() {
        return properties.getAppId();
    }

    public String getInstanceId() {
        return instanceId;
    }
    
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    private String generateInstanceId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return hostname + ":" + System.currentTimeMillis() % 100000;
        } catch (Exception e) {
            return "instance-" + System.currentTimeMillis() % 100000;
        }
    }

    /**
     * Registration info sent to server
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RegistrationInfo {
        private final String appId;
        private final String instanceId;
        private final java.util.Collection<String> threadPoolIds;
    }
}
