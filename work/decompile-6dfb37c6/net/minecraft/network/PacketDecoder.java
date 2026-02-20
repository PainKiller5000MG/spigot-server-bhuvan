package net.minecraft.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.slf4j.Logger;

public class PacketDecoder<T extends PacketListener> extends ByteToMessageDecoder implements ProtocolSwapHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ProtocolInfo<T> protocolInfo;

    public PacketDecoder(ProtocolInfo<T> protocolInfo) {
        this.protocolInfo = protocolInfo;
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
        int i = input.readableBytes();

        Packet<? super T> packet;

        try {
            packet = (Packet) this.protocolInfo.codec().decode(input);
        } catch (Exception exception) {
            if (exception instanceof SkipPacketException) {
                input.skipBytes(input.readableBytes());
            }

            throw exception;
        }

        PacketType<? extends Packet<? super T>> packettype = packet.type();

        JvmProfiler.INSTANCE.onPacketReceived(this.protocolInfo.id(), packettype, ctx.channel().remoteAddress(), i);
        if (input.readableBytes() > 0) {
            String s = this.protocolInfo.id().id();

            throw new IOException("Packet " + s + "/" + String.valueOf(packettype) + " (" + packet.getClass().getSimpleName() + ") was larger than I expected, found " + input.readableBytes() + " bytes extra whilst reading packet " + String.valueOf(packettype));
        } else {
            out.add(packet);
            if (PacketDecoder.LOGGER.isDebugEnabled()) {
                PacketDecoder.LOGGER.debug(Connection.PACKET_RECEIVED_MARKER, " IN: [{}:{}] {} -> {} bytes", new Object[]{this.protocolInfo.id().id(), packettype, packet.getClass().getName(), i});
            }

            ProtocolSwapHandler.handleInboundTerminalPacket(ctx, packet);
        }
    }
}
