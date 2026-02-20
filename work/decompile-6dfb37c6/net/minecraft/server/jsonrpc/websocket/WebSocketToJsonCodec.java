package net.minecraft.server.jsonrpc.websocket;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

public class WebSocketToJsonCodec extends MessageToMessageDecoder<TextWebSocketFrame> {

    public WebSocketToJsonCodec() {}

    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) {
        JsonElement jsonelement = JsonParser.parseString(msg.text());

        out.add(jsonelement);
    }
}
