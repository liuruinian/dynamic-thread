package com.dynamic.thread.server.netty;

import com.dynamic.thread.core.protocol.MessageCodec;
import com.dynamic.thread.server.config.ServerProperties;
import com.dynamic.thread.server.handler.ServerChannelHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Netty server for receiving Agent connections.
 */
@Slf4j
public class DashboardNettyServer {

    private final ServerProperties properties;
    private final ServerChannelHandler channelHandler;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public DashboardNettyServer(ServerProperties properties, ServerChannelHandler channelHandler) {
        this.properties = properties;
        this.channelHandler = channelHandler;
    }

    /**
     * Start the Netty server
     */
    public void start() {
        int port = properties.getNetty().getPort();
        int bossThreads = properties.getNetty().getBossThreads();
        int workerThreads = properties.getNetty().getWorkerThreads();
        int readIdleTimeout = properties.getNetty().getReadIdleTimeout();

        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // Idle state handler for client timeout
                            pipeline.addLast(new IdleStateHandler(
                                    readIdleTimeout, 0, 0, TimeUnit.SECONDS
                            ));
                            
                            // Message codec
                            pipeline.addLast(new MessageCodec());
                            
                            // Business handler
                            pipeline.addLast(channelHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            log.info("Dashboard Netty Server started on port {}", port);
            
        } catch (Exception e) {
            log.error("Failed to start Netty server", e);
            stop();
        }
    }

    /**
     * Stop the Netty server
     */
    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Dashboard Netty Server stopped");
    }
}
