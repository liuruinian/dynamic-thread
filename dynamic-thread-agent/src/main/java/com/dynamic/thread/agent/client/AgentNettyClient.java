package com.dynamic.thread.agent.client;

import com.dynamic.thread.agent.config.AgentProperties;
import com.dynamic.thread.agent.handler.AgentChannelHandler;
import com.dynamic.thread.core.protocol.Message;
import com.dynamic.thread.core.protocol.MessageCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Netty client for connecting to Dashboard Server.
 */
@Slf4j
public class AgentNettyClient {

    private final AgentProperties properties;
    private final AgentChannelHandler channelHandler;
    
    private EventLoopGroup workerGroup;
    private Channel channel;
    private Bootstrap bootstrap;
    private volatile boolean running = false;

    public AgentNettyClient(AgentProperties properties, AgentChannelHandler channelHandler) {
        this.properties = properties;
        this.channelHandler = channelHandler;
    }

    /**
     * Start the client and connect to server
     */
    public void start() {
        if (running) {
            log.warn("Agent client already running");
            return;
        }

        running = true;
        workerGroup = new NioEventLoopGroup();

        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getServer().getConnectTimeout())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // Idle state handler for heartbeat
                        pipeline.addLast(new IdleStateHandler(
                                properties.getHeartbeat().getReadIdleTimeout(),
                                properties.getHeartbeat().getInterval(),
                                0,
                                TimeUnit.SECONDS
                        ));
                        
                        // Message codec
                        pipeline.addLast(new MessageCodec());
                        
                        // Business handler
                        pipeline.addLast(channelHandler);
                    }
                });

        connect();
    }

    /**
     * Connect to server
     */
    public void connect() {
        if (!running) {
            return;
        }

        String host = properties.getServer().getHost();
        int port = properties.getServer().getPort();

        log.info("Connecting to Dashboard Server at {}:{}", host, port);

        bootstrap.connect(new InetSocketAddress(host, port))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        channel = future.channel();
                        log.info("Connected to Dashboard Server successfully");
                    } else {
                        log.warn("Failed to connect to Dashboard Server, will retry in {}ms",
                                properties.getServer().getReconnectDelay());
                        scheduleReconnect();
                    }
                });
    }

    /**
     * Schedule reconnection
     */
    public void scheduleReconnect() {
        if (!running) {
            return;
        }

        workerGroup.schedule(this::connect,
                properties.getServer().getReconnectDelay(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Send message to server
     */
    public void send(Message message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        } else {
            log.warn("Channel is not active, cannot send message");
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * Stop the client
     */
    @PreDestroy
    public void stop() {
        running = false;
        
        if (channel != null) {
            channel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        log.info("Agent client stopped");
    }
}
