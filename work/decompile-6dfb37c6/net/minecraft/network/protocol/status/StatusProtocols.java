package net.minecraft.network.protocol.status;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.ProtocolInfoBuilder;
import net.minecraft.network.protocol.SimpleUnboundProtocol;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.PingPacketTypes;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

public class StatusProtocols {

    public static final SimpleUnboundProtocol<ServerStatusPacketListener, ByteBuf> SERVERBOUND_TEMPLATE = ProtocolInfoBuilder.<ServerStatusPacketListener, ByteBuf>serverboundProtocol(ConnectionProtocol.STATUS, (protocolinfobuilder) -> {
        protocolinfobuilder.addPacket(StatusPacketTypes.SERVERBOUND_STATUS_REQUEST, ServerboundStatusRequestPacket.STREAM_CODEC).addPacket(PingPacketTypes.SERVERBOUND_PING_REQUEST, ServerboundPingRequestPacket.STREAM_CODEC);
    });
    public static final ProtocolInfo<ServerStatusPacketListener> SERVERBOUND = StatusProtocols.SERVERBOUND_TEMPLATE.bind((bytebuf) -> {
        return bytebuf;
    });
    public static final SimpleUnboundProtocol<ClientStatusPacketListener, FriendlyByteBuf> CLIENTBOUND_TEMPLATE = ProtocolInfoBuilder.<ClientStatusPacketListener, FriendlyByteBuf>clientboundProtocol(ConnectionProtocol.STATUS, (protocolinfobuilder) -> {
        protocolinfobuilder.addPacket(StatusPacketTypes.CLIENTBOUND_STATUS_RESPONSE, ClientboundStatusResponsePacket.STREAM_CODEC).addPacket(PingPacketTypes.CLIENTBOUND_PONG_RESPONSE, ClientboundPongResponsePacket.STREAM_CODEC);
    });
    public static final ProtocolInfo<ClientStatusPacketListener> CLIENTBOUND = StatusProtocols.CLIENTBOUND_TEMPLATE.bind(FriendlyByteBuf::new);

    public StatusProtocols() {}
}
