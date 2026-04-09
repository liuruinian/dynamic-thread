package com.dynamic.thread.server.cluster.transport;

import com.dynamic.thread.core.protocol.MessageCodec;
import com.dynamic.thread.server.config.ServerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Netty server for inter-node cluster communication.
 * Listens on the cluster port for connections from other nodes.
 */
@Slf4j
public class ClusterTransportServer {

    private final ServerProperties.ClusterConfig clusterConfig;
    private final ClusterChannelHandler channelHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ClusterTransportServer(ServerProperties.ClusterConfig clusterConfig,
                                  ClusterChannelHandler channelHandler) {
        this.clusterConfig = clusterConfig;
        this.channelHandler = channelHandler;
    }

    /**
     * Start the cluster transport server.
     */
    public void start() {
        int port = clusterConfig.getClusterPort();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 64)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // Idle state handler: read timeout = 3x heartbeat interval
                            int readIdleTimeout = (clusterConfig.getHeartbeatIntervalMs() * 3) / 1000;
                            pipeline.addLast(new IdleStateHandler(
                                    readIdleTimeout, 0, 0, TimeUnit.SECONDS
                            ));
                            pipeline.addLast(new MessageCodec());
                            pipeline.addLast(channelHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();

            log.info("Cluster transport server started on port {}", port);

        } catch (Exception e) {
            log.error("Failed to start cluster transport server", e);
            stop();
        }
    }

    /**
     * Stop the cluster transport server.
     */
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
        log.info("Cluster transport server stopped");
    }
}
