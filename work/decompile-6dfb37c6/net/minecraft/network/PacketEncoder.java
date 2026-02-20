package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

public class PacketEncoder<T extends PacketListener> extends MessageToByteEncoder<Packet<T>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProtocolInfo<T> protocolInfo;

    public PacketEncoder(ProtocolInfo<T> protocolInfo) {
        this.protocolInfo = protocolInfo;
    }

    protected void encode(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output) throws Exception {
        PacketType<? extends Packet<? super T>> packettype = packet.type();

        try {
            this.protocolInfo.codec().encode(output, packet);
            int i = output.readableBytes();

            if (PacketEncoder.LOGGER.isDebugEnabled()) {
                PacketEncoder.LOGGER.debug(Connection.PACKET_SENT_MARKER, "OUT: [{}:{}] {} -> {} bytes", new Object[]{this.protocolInfo.id().id(), packettype, packet.getClass().getName(), i});
            }

            JvmProfiler.INSTANCE.onPacketSent(this.protocolInfo.id(), packettype, ctx.channel().remoteAddress(), i);
        } catch (Throwable throwable) {
            PacketEncoder.LOGGER.error("Error sending packet {}", packettype, throwable);
            if (packet.isSkippable()) {
                throw new SkipPacketEncoderException(throwable);
            }

            throw throwable;
        } finally {
            ProtocolSwapHandler.handleOutboundTerminalPacket(ctx, packet);
        }

    }
}
