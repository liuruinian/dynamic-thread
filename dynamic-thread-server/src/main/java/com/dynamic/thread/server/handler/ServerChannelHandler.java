package com.dynamic.thread.server.handler;

import com.dynamic.thread.core.alarm.AlarmManager;
import com.dynamic.thread.core.model.AlarmRecord;
import com.dynamic.thread.core.model.ThreadPoolState;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageType;
import com.dynamic.thread.core.util.CommonComponents;
import com.dynamic.thread.server.cluster.sync.DataSyncer;
import com.dynamic.thread.server.registry.ClientRegistry;
import com.dynamic.thread.server.security.ConnectionRateLimiter;
import com.dynamic.thread.server.security.InputValidator;
import com.dynamic.thread.server.security.InputValidator.ValidationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel handler for Dashboard Server.
 * Handles incoming messages from Agents.
 */
@Slf4j
@ChannelHandler.Sharable
public class ServerChannelHandler extends SimpleChannelInboundHandler<Message> {

    private static final AttributeKey<String> INSTANCE_ID_KEY = AttributeKey.valueOf("instanceId");
    private static final AttributeKey<String> APP_ID_KEY = AttributeKey.valueOf("appId");
    
    private final ClientRegistry clientRegistry;
    private final ObjectMapper objectMapper;
    private final ConnectionRateLimiter rateLimiter;
    private final InputValidator inputValidator;

    /**
     * Data syncer for cluster mode (null when standalone)
     */
    private volatile DataSyncer dataSyncer;
    
    /**
     * Async executor for alarm processing to avoid blocking Netty threads
     */
    private final ExecutorService alarmExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "alarm-processor");
        t.setDaemon(true);
        return t;
    });

    public ServerChannelHandler(ClientRegistry clientRegistry,
                                ConnectionRateLimiter rateLimiter,
                                InputValidator inputValidator) {
        this.clientRegistry = clientRegistry;
        this.rateLimiter = rateLimiter;
        this.inputValidator = inputValidator;
        this.objectMapper = CommonComponents.objectMapper();
    }

    /**
     * Set the data syncer for cluster mode support.
     */
    public void setDataSyncer(DataSyncer dataSyncer) {
        this.dataSyncer = dataSyncer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Check rate limiting before accepting connection
        if (!rateLimiter.acceptConnection(ctx)) {
            log.warn("Connection rejected by rate limiter: {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        log.info("New client connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        log.debug("Received message: type={}, appId={}, instanceId={}", 
                msg.getType(), msg.getAppId(), msg.getInstanceId());

        if (msg.getType() == null) {
            return;
        }

        if (msg.getType() == MessageType.REGISTER) {
            handleRegister(ctx, msg);
        } else if (msg.getType() == MessageType.HEARTBEAT) {
            handleHeartbeat(ctx, msg);
        } else if (msg.getType() == MessageType.STATE_REPORT) {
            handleStateReport(ctx, msg);
        } else if (msg.getType() == MessageType.UNREGISTER) {
            handleUnregister(ctx, msg);
        } else {
            log.warn("Unknown message type: {}", msg.getType());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Client idle timeout, closing connection: {}", ctx.channel().remoteAddress());
                String instanceId = ctx.channel().attr(INSTANCE_ID_KEY).get();
                if (instanceId != null) {
                    clientRegistry.unregister(instanceId);
                }
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String instanceId = ctx.channel().attr(INSTANCE_ID_KEY).get();
        String appId = ctx.channel().attr(APP_ID_KEY).get();
        
        // Update rate limiter
        rateLimiter.onDisconnect(ctx.channel(), appId);
        
        if (instanceId != null) {
            clientRegistry.unregister(instanceId);
            log.info("Client disconnected: instanceId={}", instanceId);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception: {}", cause.getMessage(), cause);
        ctx.close();
    }

    private void handleRegister(ChannelHandlerContext ctx, Message msg) {
        try {
            // Input validation
            ValidationResult appIdResult = inputValidator.validateAppId(msg.getAppId());
            if (!appIdResult.isValid()) {
                log.warn("Registration rejected: invalid appId. {}", appIdResult.getMessage());
                Message response = Message.response(msg.getMessageId(), false, appIdResult.getMessage());
                ctx.writeAndFlush(response);
                return;
            }
            
            ValidationResult instanceIdResult = inputValidator.validateInstanceId(msg.getInstanceId());
            if (!instanceIdResult.isValid()) {
                log.warn("Registration rejected: invalid instanceId. {}", instanceIdResult.getMessage());
                Message response = Message.response(msg.getMessageId(), false, instanceIdResult.getMessage());
                ctx.writeAndFlush(response);
                return;
            }
            
            // Check app connection limit
            if (!rateLimiter.canAppConnect(msg.getAppId())) {
                log.warn("Registration rejected: app connection limit reached. appId={}", msg.getAppId());
                Message response = Message.response(msg.getMessageId(), false, "Connection limit reached for application");
                ctx.writeAndFlush(response);
                ctx.close();
                return;
            }
            
            RegistrationInfo info = objectMapper.readValue(msg.getBody(), RegistrationInfo.class);
            
            // Validate pool count
            ValidationResult poolCountResult = inputValidator.validatePoolCount(info.getThreadPoolIds().size());
            if (!poolCountResult.isValid()) {
                log.warn("Registration rejected: {}", poolCountResult.getMessage());
                Message response = Message.response(msg.getMessageId(), false, poolCountResult.getMessage());
                ctx.writeAndFlush(response);
                return;
            }
            
            // Store IDs in channel attributes
            ctx.channel().attr(INSTANCE_ID_KEY).set(msg.getInstanceId());
            ctx.channel().attr(APP_ID_KEY).set(msg.getAppId());
            
            // Record connection in rate limiter
            rateLimiter.onConnect(ctx.channel(), msg.getAppId());
            
            clientRegistry.register(
                    msg.getAppId(),
                    msg.getInstanceId(),
                    ctx.channel(),
                    info.getThreadPoolIds()
            );

            // Send success response
            Message response = Message.response(msg.getMessageId(), true, null);
            ctx.writeAndFlush(response);
            
            log.info("Client registered: appId={}, instanceId={}, threadPools={}",
                    msg.getAppId(), msg.getInstanceId(), info.getThreadPoolIds().size());
                    
        } catch (Exception e) {
            log.error("Failed to handle registration: {}", e.getMessage());
            Message response = Message.response(msg.getMessageId(), false, "Registration failed");
            ctx.writeAndFlush(response);
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, Message msg) {
        clientRegistry.updateHeartbeat(msg.getInstanceId());
        
        // Send heartbeat response
        Message response = Message.response(msg.getMessageId(), true, null);
        ctx.writeAndFlush(response);
    }

    private void handleStateReport(ChannelHandlerContext ctx, Message msg) {
        try {
            List<ThreadPoolState> states = objectMapper.readValue(
                    msg.getBody(),
                    new TypeReference<List<ThreadPoolState>>() {}
            );
            
            clientRegistry.updateStates(msg.getInstanceId(), states);
            
            // Check alarm rules asynchronously to avoid blocking Netty threads
            final String appId = msg.getAppId();
            final String instanceId = msg.getInstanceId();
            alarmExecutor.submit(() -> checkAndTriggerAlarms(states, appId, instanceId));
            
            // Send success response
            Message response = Message.response(msg.getMessageId(), true, null);
            ctx.writeAndFlush(response);
            
            log.debug("State report received: instanceId={}, pools={}", 
                    msg.getInstanceId(), states.size());
                    
        } catch (Exception e) {
            log.error("Failed to handle state report: {}", e.getMessage());
            Message response = Message.response(msg.getMessageId(), false, "State report processing failed");
            ctx.writeAndFlush(response);
        }
    }

    /**
     * Check alarm rules against thread pool states and trigger notifications
     */
    private void checkAndTriggerAlarms(List<ThreadPoolState> states, String appId, String instanceId) {
        if (states == null || states.isEmpty()) {
            return;
        }
        
        AlarmManager alarmManager = AlarmManager.getInstance();
        
        for (ThreadPoolState state : states) {
            try {
                List<AlarmRecord> triggered = alarmManager.checkAndTrigger(state);
                if (!triggered.isEmpty()) {
                    log.info("[ALARM] Triggered {} alarms for pool={} (app={}, instance={})", 
                            triggered.size(), state.getThreadPoolId(), appId, instanceId);
                    
                    for (AlarmRecord record : triggered) {
                        log.warn("[ALARM] {} - {} = {} (threshold: {})", 
                                record.getThreadPoolId(), 
                                record.getMetric(), 
                                record.getValue(), 
                                record.getThreshold());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to check alarms for pool {}: {}", 
                        state.getThreadPoolId(), e.getMessage());
            }
        }
    }

    private void handleUnregister(ChannelHandlerContext ctx, Message msg) {
        clientRegistry.unregister(msg.getInstanceId());
        ctx.close();
    }

    /**
     * Send config update to a specific instance.
     * If the instance is local, send directly via Channel.
     * If the instance is remote (cluster mode), forward via cluster transport.
     * @return true if message was sent or forwarded, false if client not found
     */
    public boolean sendConfigUpdate(String instanceId, String configJson) {
        // Try local first
        Channel channel = clientRegistry.getLocalChannel(instanceId);
        if (channel != null && channel.isActive()) {
            ClientRegistry.ClientInfo info = clientRegistry.getLocalClient(instanceId);
            Message message = Message.configUpdate(info.getAppId(), instanceId, configJson);
            channel.writeAndFlush(message);
            log.info("Config update sent to local instance: {}", instanceId);
            return true;
        }

        // Try remote forwarding (cluster mode)
        if (dataSyncer != null) {
            String remoteNodeId = clientRegistry.getRemoteNodeId(instanceId);
            if (remoteNodeId != null) {
                boolean forwarded = dataSyncer.forwardConfigUpdate(remoteNodeId, instanceId, configJson, MessageType.CONFIG_UPDATE);
                if (forwarded) {
                    log.info("Config update forwarded to node {} for instance: {}", remoteNodeId, instanceId);
                    return true;
                }
            }
        }

        log.warn("Cannot send config update, client not connected: {}", instanceId);
        return false;
    }

    /**
     * Send web container config update to a specific instance.
     * Supports cluster forwarding.
     * @return true if message was sent or forwarded, false if client not found
     */
    public boolean sendWebContainerConfigUpdate(String instanceId, String configJson) {
        // Try local first
        Channel channel = clientRegistry.getLocalChannel(instanceId);
        if (channel != null && channel.isActive()) {
            ClientRegistry.ClientInfo info = clientRegistry.getLocalClient(instanceId);
            Message message = Message.webContainerConfigUpdate(info.getAppId(), instanceId, configJson);
            channel.writeAndFlush(message);
            log.info("Web container config update sent to local instance: {}", instanceId);
            return true;
        }

        // Try remote forwarding (cluster mode)
        if (dataSyncer != null) {
            String remoteNodeId = clientRegistry.getRemoteNodeId(instanceId);
            if (remoteNodeId != null) {
                boolean forwarded = dataSyncer.forwardConfigUpdate(remoteNodeId, instanceId, configJson, MessageType.WEB_CONTAINER_CONFIG_UPDATE);
                if (forwarded) {
                    log.info("Web container config update forwarded to node {} for instance: {}", remoteNodeId, instanceId);
                    return true;
                }
            }
        }

        log.warn("Cannot send web container config update, client not connected: {}", instanceId);
        return false;
    }

    /**
     * Registration info from client
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RegistrationInfo {
        private final String appId;
        private final String instanceId;
        private final Collection<String> threadPoolIds;
    }
}
