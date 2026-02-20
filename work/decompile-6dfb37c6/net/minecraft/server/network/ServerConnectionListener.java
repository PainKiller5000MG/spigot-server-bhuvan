package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerConnectionListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    public volatile boolean running;
    private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
    private final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

    public ServerConnectionListener(MinecraftServer server) {
        this.server = server;
        this.running = true;
    }

    public void startTcpServerListener(@Nullable InetAddress address, int port) throws IOException {
        synchronized (this.channels) {
            EventLoopGroupHolder eventloopgroupholder = EventLoopGroupHolder.remote(this.server.useNativeTransport());

            this.channels.add(((ServerBootstrap) ((ServerBootstrap) (new ServerBootstrap()).channel(eventloopgroupholder.serverChannelCls())).childHandler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel channel) {
                    try {
                        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                    } catch (ChannelException channelexception) {
                        ;
                    }

                    ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                    if (ServerConnectionListener.this.server.repliesToStatus()) {
                        channelpipeline.addLast("legacy_query", new LegacyQueryHandler(ServerConnectionListener.this.getServer()));
                    }

                    Connection.configureSerialization(channelpipeline, PacketFlow.SERVERBOUND, false, (BandwidthDebugMonitor) null);
                    int j = ServerConnectionListener.this.server.getRateLimitPacketsPerSecond();
                    Connection connection = (Connection) (j > 0 ? new RateKickingConnection(j) : new Connection(PacketFlow.SERVERBOUND));

                    ServerConnectionListener.this.connections.add(connection);
                    connection.configurePacketHandler(channelpipeline);
                    connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection));
                }
            }).group(eventloopgroupholder.eventLoopGroup()).localAddress(address, port)).bind().syncUninterruptibly());
        }
    }

    public SocketAddress startMemoryChannel() {
        ChannelFuture channelfuture;

        synchronized (this.channels) {
            channelfuture = ((ServerBootstrap) ((ServerBootstrap) (new ServerBootstrap()).channel(EventLoopGroupHolder.local().serverChannelCls())).childHandler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel channel) {
                    Connection connection = new Connection(PacketFlow.SERVERBOUND);

                    connection.setListenerForServerboundHandshake(new MemoryServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection));
                    ServerConnectionListener.this.connections.add(connection);
                    ChannelPipeline channelpipeline = channel.pipeline();

                    Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.SERVERBOUND);
                    if (SharedConstants.DEBUG_FAKE_LATENCY_MS > 0) {
                        channelpipeline.addLast("latency", new ServerConnectionListener.LatencySimulator(SharedConstants.DEBUG_FAKE_LATENCY_MS, SharedConstants.DEBUG_FAKE_JITTER_MS));
                    }

                    connection.configurePacketHandler(channelpipeline);
                }
            }).group(EventLoopGroupHolder.local().eventLoopGroup()).localAddress(LocalAddress.ANY)).bind().syncUninterruptibly();
            this.channels.add(channelfuture);
        }

        return channelfuture.channel().localAddress();
    }

    public void stop() {
        this.running = false;

        for (ChannelFuture channelfuture : this.channels) {
            try {
                channelfuture.channel().close().sync();
            } catch (InterruptedException interruptedexception) {
                ServerConnectionListener.LOGGER.error("Interrupted whilst closing channel");
            }
        }

    }

    public void tick() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = (Connection) iterator.next();

                if (!connection.isConnecting()) {
                    if (connection.isConnected()) {
                        try {
                            connection.tick();
                        } catch (Exception exception) {
                            if (connection.isMemoryConnection()) {
                                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
                            }

                            ServerConnectionListener.LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(this.server.logIPs()), exception);
                            Component component = Component.literal("Internal server error");

                            connection.send(new ClientboundDisconnectPacket(component), PacketSendListener.thenRun(() -> {
                                connection.disconnect(component);
                            }));
                            connection.setReadOnly();
                        }
                    } else {
                        iterator.remove();
                        connection.handleDisconnection();
                    }
                }
            }

        }
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public List<Connection> getConnections() {
        return this.connections;
    }

    private static class LatencySimulator extends ChannelInboundHandlerAdapter {

        private static final Timer TIMER = new HashedWheelTimer();
        private final int delay;
        private final int jitter;
        private final List<ServerConnectionListener.LatencySimulator.DelayedMessage> queuedMessages = Lists.newArrayList();

        public LatencySimulator(int delay, int jitter) {
            this.delay = delay;
            this.jitter = jitter;
        }

        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            this.delayDownstream(ctx, msg);
        }

        private void delayDownstream(ChannelHandlerContext ctx, Object msg) {
            int i = this.delay + (int) (Math.random() * (double) this.jitter);

            this.queuedMessages.add(new ServerConnectionListener.LatencySimulator.DelayedMessage(ctx, msg));
            ServerConnectionListener.LatencySimulator.TIMER.newTimeout(this::onTimeout, (long) i, TimeUnit.MILLISECONDS);
        }

        private void onTimeout(Timeout timeout) {
            ServerConnectionListener.LatencySimulator.DelayedMessage serverconnectionlistener_latencysimulator_delayedmessage = (ServerConnectionListener.LatencySimulator.DelayedMessage) this.queuedMessages.remove(0);

            serverconnectionlistener_latencysimulator_delayedmessage.ctx.fireChannelRead(serverconnectionlistener_latencysimulator_delayedmessage.msg);
        }

        private static class DelayedMessage {

            public final ChannelHandlerContext ctx;
            public final Object msg;

            public DelayedMessage(ChannelHandlerContext ctx, Object msg) {
                this.ctx = ctx;
                this.msg = msg;
            }
        }
    }
}
