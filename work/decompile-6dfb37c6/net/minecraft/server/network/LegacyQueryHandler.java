package net.minecraft.server.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import java.util.Locale;
import net.minecraft.server.ServerInfo;
import org.slf4j.Logger;

public class LegacyQueryHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerInfo server;

    public LegacyQueryHandler(ServerInfo server) {
        this.server = server;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf bytebuf = (ByteBuf) msg;

        bytebuf.markReaderIndex();
        boolean flag = true;

        try {
            if (bytebuf.readUnsignedByte() == 254) {
                SocketAddress socketaddress = ctx.channel().remoteAddress();
                int i = bytebuf.readableBytes();

                if (i == 0) {
                    LegacyQueryHandler.LOGGER.debug("Ping: (<1.3.x) from {}", socketaddress);
                    String s = createVersion0Response(this.server);

                    sendFlushAndClose(ctx, createLegacyDisconnectPacket(ctx.alloc(), s));
                } else {
                    if (bytebuf.readUnsignedByte() != 1) {
                        return;
                    }

                    if (bytebuf.isReadable()) {
                        if (!readCustomPayloadPacket(bytebuf)) {
                            return;
                        }

                        LegacyQueryHandler.LOGGER.debug("Ping: (1.6) from {}", socketaddress);
                    } else {
                        LegacyQueryHandler.LOGGER.debug("Ping: (1.4-1.5.x) from {}", socketaddress);
                    }

                    String s1 = createVersion1Response(this.server);

                    sendFlushAndClose(ctx, createLegacyDisconnectPacket(ctx.alloc(), s1));
                }

                bytebuf.release();
                flag = false;
                return;
            }
        } catch (RuntimeException runtimeexception) {
            return;
        } finally {
            if (flag) {
                bytebuf.resetReaderIndex();
                ctx.channel().pipeline().remove(this);
                ctx.fireChannelRead(msg);
            }

        }

    }

    private static boolean readCustomPayloadPacket(ByteBuf in) {
        short short0 = in.readUnsignedByte();

        if (short0 != 250) {
            return false;
        } else {
            String s = LegacyProtocolUtils.readLegacyString(in);

            if (!"MC|PingHost".equals(s)) {
                return false;
            } else {
                int i = in.readUnsignedShort();

                if (in.readableBytes() != i) {
                    return false;
                } else {
                    short short1 = in.readUnsignedByte();

                    if (short1 < 73) {
                        return false;
                    } else {
                        String s1 = LegacyProtocolUtils.readLegacyString(in);
                        int j = in.readInt();

                        return j <= 65535;
                    }
                }
            }
        }
    }

    private static String createVersion0Response(ServerInfo server) {
        return String.format(Locale.ROOT, "%s\u00a7%d\u00a7%d", server.getMotd(), server.getPlayerCount(), server.getMaxPlayers());
    }

    private static String createVersion1Response(ServerInfo server) {
        return String.format(Locale.ROOT, "\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", 127, server.getServerVersion(), server.getMotd(), server.getPlayerCount(), server.getMaxPlayers());
    }

    private static void sendFlushAndClose(ChannelHandlerContext ctx, ByteBuf out) {
        ctx.pipeline().firstContext().writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
    }

    private static ByteBuf createLegacyDisconnectPacket(ByteBufAllocator alloc, String reason) {
        ByteBuf bytebuf = alloc.buffer();

        bytebuf.writeByte(255);
        LegacyProtocolUtils.writeLegacyString(bytebuf, reason);
        return bytebuf;
    }
}
