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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty client for connecting to Dashboard Server.
 * Supports multi-address configuration with Round-Robin failover for cluster mode.
 */
@Slf4j
public class AgentNettyClient {

    private final AgentProperties properties;
    private final AgentChannelHandler channelHandler;

    private EventLoopGroup workerGroup;
    private Channel channel;
    private Bootstrap bootstrap;
    private volatile boolean running = false;

    /** Resolved server address list */
    private List<InetSocketAddress> serverAddresses;
    /** Current connection index in the address list */
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    /** Number of consecutive failures in current round */
    private volatile int consecutiveFailures = 0;

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
        initServerAddresses();
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
     * Initialize the server address list.
     * Priority: addresses list > host:port single address.
     */
    private void initServerAddresses() {
        serverAddresses = new ArrayList<>();
        AgentProperties.ServerConfig serverConfig = properties.getServer();

        if (serverConfig.getAddresses() != null && !serverConfig.getAddresses().isEmpty()) {
            for (String addr : serverConfig.getAddresses()) {
                String[] parts = addr.split(":");
                if (parts.length == 2) {
                    try {
                        serverAddresses.add(new InetSocketAddress(parts[0].trim(), Integer.parseInt(parts[1].trim())));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid server address format: {}, skipping", addr);
                    }
                } else {
                    log.warn("Invalid server address format: {}, expected host:port", addr);
                }
            }
        }

        // Fallback to single host:port if no valid addresses configured
        if (serverAddresses.isEmpty()) {
            serverAddresses.add(new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort()));
        }

        log.info("Initialized {} server address(es): {}", serverAddresses.size(), serverAddresses);
    }

    /**
     * Connect to the next available server address using Round-Robin.
     */
    public void connect() {
        if (!running) {
            return;
        }

        // If all addresses have been tried in this round, delay before retrying
        if (consecutiveFailures >= serverAddresses.size()) {
            log.warn("All {} server addresses failed, will retry after {}ms",
                    serverAddresses.size(), properties.getServer().getReconnectDelay());
            consecutiveFailures = 0;
            scheduleReconnect();
            return;
        }

        int index = currentIndex.get() % serverAddresses.size();
        InetSocketAddress address = serverAddresses.get(index);

        log.info("Connecting to Dashboard Server at {} (address {}/{})",
                address, index + 1, serverAddresses.size());

        bootstrap.connect(address)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        channel = future.channel();
                        consecutiveFailures = 0;
                        log.info("Connected to Dashboard Server at {} successfully", address);
                    } else {
                        log.warn("Failed to connect to Dashboard Server at {}", address);
                        consecutiveFailures++;
                        // Move to next address
                        currentIndex.set((index + 1) % serverAddresses.size());
                        // Try next address immediately (or delay if all failed - handled at top of connect())
                        connect();
                    }
                });
    }

    /**
     * Schedule reconnection after delay
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
