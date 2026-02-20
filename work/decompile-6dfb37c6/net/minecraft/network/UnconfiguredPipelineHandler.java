package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;

public class UnconfiguredPipelineHandler {

    public UnconfiguredPipelineHandler() {}

    public static <T extends PacketListener> UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundProtocol(ProtocolInfo<T> protocolInfo) {
        return setupInboundHandler(new PacketDecoder(protocolInfo));
    }

    private static UnconfiguredPipelineHandler.InboundConfigurationTask setupInboundHandler(ChannelInboundHandler newHandler) {
        return (channelhandlercontext) -> {
            channelhandlercontext.pipeline().replace(channelhandlercontext.name(), "decoder", newHandler);
            channelhandlercontext.channel().config().setAutoRead(true);
        };
    }

    public static <T extends PacketListener> UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundProtocol(ProtocolInfo<T> codecData) {
        return setupOutboundHandler(new PacketEncoder(codecData));
    }

    private static UnconfiguredPipelineHandler.OutboundConfigurationTask setupOutboundHandler(ChannelOutboundHandler newHandler) {
        return (channelhandlercontext) -> {
            channelhandlercontext.pipeline().replace(channelhandlercontext.name(), "encoder", newHandler);
        };
    }

    public static class Inbound extends ChannelDuplexHandler {

        public Inbound() {}

        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf) && !(msg instanceof Packet)) {
                ctx.fireChannelRead(msg);
            } else {
                ReferenceCountUtil.release(msg);
                throw new DecoderException("Pipeline has no inbound protocol configured, can't process packet " + String.valueOf(msg));
            }
        }

        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler_inboundconfigurationtask) {
                try {
                    unconfiguredpipelinehandler_inboundconfigurationtask.run(ctx);
                } finally {
                    ReferenceCountUtil.release(msg);
                }

                promise.setSuccess();
            } else {
                ctx.write(msg, promise);
            }

        }
    }

    public static class Outbound extends ChannelOutboundHandlerAdapter {

        public Outbound() {}

        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Packet) {
                ReferenceCountUtil.release(msg);
                throw new EncoderException("Pipeline has no outbound protocol configured, can't process packet " + String.valueOf(msg));
            } else {
                if (msg instanceof UnconfiguredPipelineHandler.OutboundConfigurationTask) {
                    UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler_outboundconfigurationtask = (UnconfiguredPipelineHandler.OutboundConfigurationTask) msg;

                    try {
                        unconfiguredpipelinehandler_outboundconfigurationtask.run(ctx);
                    } finally {
                        ReferenceCountUtil.release(msg);
                    }

                    promise.setSuccess();
                } else {
                    ctx.write(msg, promise);
                }

            }
        }
    }

    @FunctionalInterface
    public interface InboundConfigurationTask {

        void run(ChannelHandlerContext ctx);

        default UnconfiguredPipelineHandler.InboundConfigurationTask andThen(UnconfiguredPipelineHandler.InboundConfigurationTask otherTask) {
            return (channelhandlercontext) -> {
                this.run(channelhandlercontext);
                otherTask.run(channelhandlercontext);
            };
        }
    }

    @FunctionalInterface
    public interface OutboundConfigurationTask {

        void run(ChannelHandlerContext ctx);

        default UnconfiguredPipelineHandler.OutboundConfigurationTask andThen(UnconfiguredPipelineHandler.OutboundConfigurationTask otherTask) {
            return (channelhandlercontext) -> {
                this.run(channelhandlercontext);
                otherTask.run(channelhandlercontext);
            };
        }
    }
}
