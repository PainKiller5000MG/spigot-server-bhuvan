package net.minecraft.network;

import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {

    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = (Marker) Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), (marker) -> {
        marker.add(Connection.ROOT_MARKER);
    });
    public static final Marker PACKET_RECEIVED_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final Marker PACKET_SENT_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_SENT"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    private static final ProtocolInfo<ServerHandshakePacketListener> INITIAL_PROTOCOL = HandshakeProtocols.SERVERBOUND;
    private final PacketFlow receiving;
    private volatile boolean sendLoginDisconnect = true;
    private final Queue<Consumer<Connection>> pendingActions = Queues.newConcurrentLinkedQueue();
    public Channel channel;
    public SocketAddress address;
    private volatile @Nullable PacketListener disconnectListener;
    private volatile @Nullable PacketListener packetListener;
    private @Nullable DisconnectionDetails disconnectionDetails;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    private volatile @Nullable DisconnectionDetails delayedDisconnect;
    private @Nullable BandwidthDebugMonitor bandwidthDebugMonitor;

    public Connection(PacketFlow receiving) {
        this.receiving = receiving;
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.channel = ctx.channel();
        this.address = this.channel.remoteAddress();
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }

    }

    public void channelInactive(ChannelHandlerContext ctx) {
        this.disconnect((Component) Component.translatable("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof SkipPacketException) {
            Connection.LOGGER.debug("Skipping packet due to errors", cause.getCause());
        } else {
            boolean flag = !this.handlingFault;

            this.handlingFault = true;
            if (this.channel.isOpen()) {
                if (cause instanceof TimeoutException) {
                    Connection.LOGGER.debug("Timeout", cause);
                    this.disconnect((Component) Component.translatable("disconnect.timeout"));
                } else {
                    Component component = Component.translatable("disconnect.genericReason", "Internal Exception: " + String.valueOf(cause));
                    PacketListener packetlistener = this.packetListener;
                    DisconnectionDetails disconnectiondetails;

                    if (packetlistener != null) {
                        disconnectiondetails = packetlistener.createDisconnectionInfo(component, cause);
                    } else {
                        disconnectiondetails = new DisconnectionDetails(component);
                    }

                    if (flag) {
                        Connection.LOGGER.debug("Failed to sent packet", cause);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            Packet<?> packet = (Packet<?>) (this.sendLoginDisconnect ? new ClientboundLoginDisconnectPacket(component) : new ClientboundDisconnectPacket(component));

                            this.send(packet, PacketSendListener.thenRun(() -> {
                                this.disconnect(disconnectiondetails);
                            }));
                        } else {
                            this.disconnect(disconnectiondetails);
                        }

                        this.setReadOnly();
                    } else {
                        Connection.LOGGER.debug("Double fault", cause);
                        this.disconnect(disconnectiondetails);
                    }
                }

            }
        }
    }

    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                if (packetlistener.shouldHandleMessage(packet)) {
                    try {
                        genericsFtw(packet, packetlistener);
                    } catch (RunningOnDifferentThreadException runningondifferentthreadexception) {
                        ;
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect((Component) Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        Connection.LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classcastexception);
                        this.disconnect((Component) Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    ++this.receivedPackets;
                }

            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle(listener);
    }

    private void validateListener(ProtocolInfo<?> protocol, PacketListener packetListener) {
        Objects.requireNonNull(packetListener, "packetListener");
        PacketFlow packetflow = packetListener.flow();

        if (packetflow != this.receiving) {
            String s = String.valueOf(this.receiving);

            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + s + ", but listener is " + String.valueOf(packetflow));
        } else {
            ConnectionProtocol connectionprotocol = packetListener.protocol();

            if (protocol.id() != connectionprotocol) {
                String s1 = String.valueOf(connectionprotocol);

                throw new IllegalStateException("Listener protocol (" + s1 + ") does not match requested one " + String.valueOf(protocol));
            }
        }
    }

    private static void syncAfterConfigurationChange(ChannelFuture future) {
        try {
            future.syncUninterruptibly();
        } catch (Exception exception) {
            if (exception instanceof ClosedChannelException) {
                Connection.LOGGER.info("Connection closed during protocol change");
            } else {
                throw exception;
            }
        }
    }

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T packetListener) {
        this.validateListener(protocol, packetListener);
        if (protocol.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + String.valueOf(protocol.id()));
        } else {
            this.packetListener = packetListener;
            this.disconnectListener = null;
            UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler_inboundconfigurationtask = UnconfiguredPipelineHandler.setupInboundProtocol(protocol);
            BundlerInfo bundlerinfo = protocol.bundlerInfo();

            if (bundlerinfo != null) {
                PacketBundlePacker packetbundlepacker = new PacketBundlePacker(bundlerinfo);

                unconfiguredpipelinehandler_inboundconfigurationtask = unconfiguredpipelinehandler_inboundconfigurationtask.andThen((channelhandlercontext) -> {
                    channelhandlercontext.pipeline().addAfter("decoder", "bundler", packetbundlepacker);
                });
            }

            syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler_inboundconfigurationtask));
        }
    }

    public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
        if (protocol.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + String.valueOf(protocol.id()));
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler_outboundconfigurationtask = UnconfiguredPipelineHandler.setupOutboundProtocol(protocol);
            BundlerInfo bundlerinfo = protocol.bundlerInfo();

            if (bundlerinfo != null) {
                PacketBundleUnpacker packetbundleunpacker = new PacketBundleUnpacker(bundlerinfo);

                unconfiguredpipelinehandler_outboundconfigurationtask = unconfiguredpipelinehandler_outboundconfigurationtask.andThen((channelhandlercontext) -> {
                    channelhandlercontext.pipeline().addAfter("encoder", "unbundler", packetbundleunpacker);
                });
            }

            boolean flag = protocol.id() == ConnectionProtocol.LOGIN;

            syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler_outboundconfigurationtask.andThen((channelhandlercontext) -> {
                this.sendLoginDisconnect = flag;
            })));
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && packetListener.flow() == PacketFlow.SERVERBOUND && packetListener.protocol() == Connection.INITIAL_PROTOCOL.id()) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String hostName, int port, ClientStatusPacketListener listener) {
        this.initiateServerboundConnection(hostName, port, StatusProtocols.SERVERBOUND, StatusProtocols.CLIENTBOUND, listener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String hostName, int port, ClientLoginPacketListener listener) {
        this.initiateServerboundConnection(hostName, port, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND, listener, ClientIntent.LOGIN);
    }

    public <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundPlayConnection(String hostName, int port, ProtocolInfo<S> outbound, ProtocolInfo<C> inbound, C listener, boolean transfer) {
        this.initiateServerboundConnection(hostName, port, outbound, inbound, listener, transfer ? ClientIntent.TRANSFER : ClientIntent.LOGIN);
    }

    private <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundConnection(String hostName, int port, ProtocolInfo<S> outbound, ProtocolInfo<C> inbound, C listener, ClientIntent intent) {
        if (outbound.id() != inbound.id()) {
            throw new IllegalStateException("Mismatched initial protocols");
        } else {
            this.disconnectListener = listener;
            this.runOnceConnected((connection) -> {
                this.setupInboundProtocol(inbound, listener);
                connection.sendPacket(new ClientIntentionPacket(SharedConstants.getCurrentVersion().protocolVersion(), hostName, port, intent), (ChannelFutureListener) null, true);
                this.setupOutboundProtocol(outbound);
            });
        }
    }

    public void send(Packet<?> packet) {
        this.send(packet, (ChannelFutureListener) null);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        this.send(packet, listener, true);
    }

    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(packet, listener, flush);
        } else {
            this.pendingActions.add((Consumer) (connection) -> {
                connection.sendPacket(packet, listener, flush);
            });
        }

    }

    public void runOnceConnected(Consumer<Connection> action) {
        if (this.isConnected()) {
            this.flushQueue();
            action.accept(this);
        } else {
            this.pendingActions.add(action);
        }

    }

    private void sendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        ++this.sentPackets;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, listener, flush);
        } else {
            this.channel.eventLoop().execute(() -> {
                this.doSendPacket(packet, listener, flush);
            });
        }

    }

    private void doSendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
        if (listener != null) {
            ChannelFuture channelfuture = flush ? this.channel.writeAndFlush(packet) : this.channel.write(packet);

            channelfuture.addListener(listener);
        } else if (flush) {
            this.channel.writeAndFlush(packet, this.channel.voidPromise());
        } else {
            this.channel.write(packet, this.channel.voidPromise());
        }

    }

    public void flushChannel() {
        if (this.isConnected()) {
            this.flush();
        } else {
            this.pendingActions.add(Connection::flush);
        }

    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> {
                this.channel.flush();
            });
        }

    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            synchronized (this.pendingActions) {
                Consumer<Connection> consumer;

                while ((consumer = (Consumer) this.pendingActions.poll()) != null) {
                    consumer.accept(this);
                }

            }
        }
    }

    public void tick() {
        this.flushQueue();
        PacketListener packetlistener = this.packetListener;

        if (packetlistener instanceof TickablePacketListener tickablepacketlistener) {
            tickablepacketlistener.tick();
        }

        if (!this.isConnected() && !this.disconnectionHandled) {
            this.handleDisconnection();
        }

        if (this.channel != null) {
            this.channel.flush();
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }

    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float) this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float) this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIPs) {
        return this.address == null ? "local" : (logIPs ? this.address.toString() : "IP hidden");
    }

    public void disconnect(Component reason) {
        this.disconnect(new DisconnectionDetails(reason));
    }

    public void disconnect(DisconnectionDetails details) {
        if (this.channel == null) {
            this.delayedDisconnect = details;
        }

        if (this.isConnected()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectionDetails = details;
        }

    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, @Nullable LocalSampleLogger bandwidthLogger) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);

        if (bandwidthLogger != null) {
            connection.setBandwidthLogger(bandwidthLogger);
        }

        ChannelFuture channelfuture = connect(address, eventLoopGroupHolder, connection);

        channelfuture.syncUninterruptibly();
        return connection;
    }

    public static ChannelFuture connect(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, final Connection connection) {
        return ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group(eventLoopGroupHolder.eventLoopGroup())).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                    ;
                }

                ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, false, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelpipeline);
            }
        })).channel(eventLoopGroupHolder.channelCls())).connect(address.getAddress(), address.getPort());
    }

    private static String outboundHandlerName(boolean configureOutbound) {
        return configureOutbound ? "encoder" : "outbound_config";
    }

    private static String inboundHandlerName(boolean configureInbound) {
        return configureInbound ? "decoder" : "inbound_config";
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast("hackfix", new ChannelOutboundHandlerAdapter() {
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise);
            }
        }).addLast("packet_handler", this);
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor) {
        PacketFlow packetflow1 = inboundDirection.getOpposite();
        boolean flag1 = inboundDirection == PacketFlow.SERVERBOUND;
        boolean flag2 = packetflow1 == PacketFlow.SERVERBOUND;

        pipeline.addLast("splitter", createFrameDecoder(monitor, local)).addLast(new ChannelHandler[]{new FlowControlHandler()}).addLast(inboundHandlerName(flag1), (ChannelHandler) (flag1 ? new PacketDecoder(Connection.INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Inbound())).addLast("prepender", createFrameEncoder(local)).addLast(outboundHandlerName(flag2), (ChannelHandler) (flag2 ? new PacketEncoder(Connection.INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Outbound()));
    }

    private static ChannelOutboundHandler createFrameEncoder(boolean local) {
        return (ChannelOutboundHandler) (local ? new LocalFrameEncoder() : new Varint21LengthFieldPrepender());
    }

    private static ChannelInboundHandler createFrameDecoder(@Nullable BandwidthDebugMonitor monitor, boolean local) {
        return (ChannelInboundHandler) (!local ? new Varint21FrameDecoder(monitor) : (monitor != null ? new MonitoredLocalFrameDecoder(monitor) : new LocalFrameDecoder()));
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow packetFlow) {
        configureSerialization(pipeline, packetFlow, true, (BandwidthDebugMonitor) null);
    }

    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);

        ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group(EventLoopGroupHolder.local().eventLoopGroup())).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                ChannelPipeline channelpipeline = channel.pipeline();

                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                connection.configurePacketHandler(channelpipeline);
            }
        })).channel(EventLoopGroupHolder.local().channelCls())).connect(address).syncUninterruptibly();
        return connection;
    }

    public void setEncryptionKey(Cipher decryptCipher, Cipher encryptCipher) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptCipher));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptCipher));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    public @Nullable PacketListener getPacketListener() {
        return this.packetListener;
    }

    public @Nullable DisconnectionDetails getDisconnectionDetails() {
        return this.disconnectionDetails;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }

    }

    public void setupCompression(int threshold, boolean validateDecompressed) {
        if (threshold >= 0) {
            ChannelHandler channelhandler = this.channel.pipeline().get("decompress");

            if (channelhandler instanceof CompressionDecoder) {
                CompressionDecoder compressiondecoder = (CompressionDecoder) channelhandler;

                compressiondecoder.setThreshold(threshold, validateDecompressed);
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(threshold, validateDecompressed));
            }

            channelhandler = this.channel.pipeline().get("compress");
            if (channelhandler instanceof CompressionEncoder) {
                CompressionEncoder compressionencoder = (CompressionEncoder) channelhandler;

                compressionencoder.setThreshold(threshold);
            } else {
                this.channel.pipeline().addAfter("prepender", "compress", new CompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                Connection.LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;

                if (packetlistener1 != null) {
                    DisconnectionDetails disconnectiondetails = (DisconnectionDetails) Objects.requireNonNullElseGet(this.getDisconnectionDetails(), () -> {
                        return new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic"));
                    });

                    packetlistener1.onDisconnect(disconnectiondetails);
                }

            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(LocalSampleLogger bandwidthLogger) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(bandwidthLogger);
    }
}
