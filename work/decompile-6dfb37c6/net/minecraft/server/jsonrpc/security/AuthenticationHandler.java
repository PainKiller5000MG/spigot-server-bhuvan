package net.minecraft.server.jsonrpc.security;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Sharable
public class AuthenticationHandler extends ChannelDuplexHandler {

    private final Logger LOGGER = LogUtils.getLogger();
    private static final AttributeKey<Boolean> AUTHENTICATED_KEY = AttributeKey.valueOf("authenticated");
    private static final AttributeKey<Boolean> ATTR_WEBSOCKET_ALLOWED = AttributeKey.valueOf("websocket_auth_allowed");
    private static final String SUBPROTOCOL_VALUE = "minecraft-v1";
    private static final String SUBPROTOCOL_HEADER_PREFIX = "minecraft-v1,";
    public static final String BEARER_PREFIX = "Bearer ";
    private final SecurityConfig securityConfig;
    private final Set<String> allowedOrigins;

    public AuthenticationHandler(SecurityConfig securityConfig, String allowedOrigins) {
        this.securityConfig = securityConfig;
        this.allowedOrigins = Sets.newHashSet(allowedOrigins.split(","));
    }

    public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
        String s = this.getClientIp(context);

        if (msg instanceof HttpRequest httprequest) {
            AuthenticationHandler.SecurityCheckResult authenticationhandler_securitycheckresult = this.performSecurityChecks(httprequest);

            if (!authenticationhandler_securitycheckresult.isAllowed()) {
                this.LOGGER.debug("Authentication rejected for connection with ip {}: {}", s, authenticationhandler_securitycheckresult.getReason());
                context.channel().attr(AuthenticationHandler.AUTHENTICATED_KEY).set(false);
                this.sendUnauthorizedResponse(context, authenticationhandler_securitycheckresult.getReason());
                return;
            }

            context.channel().attr(AuthenticationHandler.AUTHENTICATED_KEY).set(true);
            if (authenticationhandler_securitycheckresult.isTokenSentInSecWebsocketProtocol()) {
                context.channel().attr(AuthenticationHandler.ATTR_WEBSOCKET_ALLOWED).set(Boolean.TRUE);
            }
        }

        Boolean obool = (Boolean) context.channel().attr(AuthenticationHandler.AUTHENTICATED_KEY).get();

        if (Boolean.TRUE.equals(obool)) {
            super.channelRead(context, msg);
        } else {
            this.LOGGER.debug("Dropping unauthenticated connection with ip {}", s);
            context.close();
        }

    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse httpresponse) {
            if (httpresponse.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code() && ctx.channel().attr(AuthenticationHandler.ATTR_WEBSOCKET_ALLOWED).get() != null && ((Boolean) ctx.channel().attr(AuthenticationHandler.ATTR_WEBSOCKET_ALLOWED).get()).equals(Boolean.TRUE)) {
                httpresponse.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "minecraft-v1");
            }
        }

        super.write(ctx, msg, promise);
    }

    private AuthenticationHandler.SecurityCheckResult performSecurityChecks(HttpRequest request) {
        String s = this.parseTokenInAuthorizationHeader(request);

        if (s != null) {
            return this.isValidApiKey(s) ? AuthenticationHandler.SecurityCheckResult.allowed() : AuthenticationHandler.SecurityCheckResult.denied("Invalid API key");
        } else {
            String s1 = this.parseTokenInSecWebsocketProtocolHeader(request);

            return s1 != null ? (!this.isAllowedOriginHeader(request) ? AuthenticationHandler.SecurityCheckResult.denied("Origin Not Allowed") : (this.isValidApiKey(s1) ? AuthenticationHandler.SecurityCheckResult.allowed(true) : AuthenticationHandler.SecurityCheckResult.denied("Invalid API key"))) : AuthenticationHandler.SecurityCheckResult.denied("Missing API key");
        }
    }

    private boolean isAllowedOriginHeader(HttpRequest request) {
        String s = request.headers().get(HttpHeaderNames.ORIGIN);

        return s != null && !s.isEmpty() ? this.allowedOrigins.contains(s) : false;
    }

    private @Nullable String parseTokenInAuthorizationHeader(HttpRequest request) {
        String s = request.headers().get(HttpHeaderNames.AUTHORIZATION);

        return s != null && s.startsWith("Bearer ") ? s.substring("Bearer ".length()).trim() : null;
    }

    private @Nullable String parseTokenInSecWebsocketProtocolHeader(HttpRequest request) {
        String s = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);

        return s != null && s.startsWith("minecraft-v1,") ? s.substring("minecraft-v1,".length()).trim() : null;
    }

    public boolean isValidApiKey(String suppliedKey) {
        if (suppliedKey.isEmpty()) {
            return false;
        } else {
            byte[] abyte = suppliedKey.getBytes(StandardCharsets.UTF_8);
            byte[] abyte1 = this.securityConfig.secretKey().getBytes(StandardCharsets.UTF_8);

            return MessageDigest.isEqual(abyte, abyte1);
        }
    }

    private String getClientIp(ChannelHandlerContext context) {
        InetSocketAddress inetsocketaddress = (InetSocketAddress) context.channel().remoteAddress();

        return inetsocketaddress.getAddress().getHostAddress();
    }

    private void sendUnauthorizedResponse(ChannelHandlerContext context, String reason) {
        String s1 = "{\"error\":\"Unauthorized\",\"message\":\"" + reason + "\"}";
        byte[] abyte = s1.getBytes(StandardCharsets.UTF_8);
        DefaultFullHttpResponse defaultfullhttpresponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, Unpooled.wrappedBuffer(abyte));

        defaultfullhttpresponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        defaultfullhttpresponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, abyte.length);
        defaultfullhttpresponse.headers().set(HttpHeaderNames.CONNECTION, "close");
        context.writeAndFlush(defaultfullhttpresponse).addListener((future) -> {
            context.close();
        });
    }

    private static class SecurityCheckResult {

        private final boolean allowed;
        private final String reason;
        private final boolean tokenSentInSecWebsocketProtocol;

        private SecurityCheckResult(boolean allowed, String reason, boolean tokenSentInSecWebsocketProtocol) {
            this.allowed = allowed;
            this.reason = reason;
            this.tokenSentInSecWebsocketProtocol = tokenSentInSecWebsocketProtocol;
        }

        public static AuthenticationHandler.SecurityCheckResult allowed() {
            return new AuthenticationHandler.SecurityCheckResult(true, (String) null, false);
        }

        public static AuthenticationHandler.SecurityCheckResult allowed(boolean tokenSentInSecWebsocketProtocol) {
            return new AuthenticationHandler.SecurityCheckResult(true, (String) null, tokenSentInSecWebsocketProtocol);
        }

        public static AuthenticationHandler.SecurityCheckResult denied(String reason) {
            return new AuthenticationHandler.SecurityCheckResult(false, reason, false);
        }

        public boolean isAllowed() {
            return this.allowed;
        }

        public String getReason() {
            return this.reason;
        }

        public boolean isTokenSentInSecWebsocketProtocol() {
            return this.tokenSentInSecWebsocketProtocol;
        }
    }
}
