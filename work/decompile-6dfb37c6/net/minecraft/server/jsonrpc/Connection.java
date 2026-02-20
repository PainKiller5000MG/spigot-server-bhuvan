package net.minecraft.server.jsonrpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidRequestJsonRpcException;
import net.minecraft.server.jsonrpc.methods.MethodNotFoundJsonRpcException;
import net.minecraft.server.jsonrpc.methods.RemoteRpcErrorException;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Connection extends SimpleChannelInboundHandler<JsonElement> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger CONNECTION_ID_COUNTER = new AtomicInteger(0);
    private final JsonRpcLogger jsonRpcLogger;
    private final ClientInfo clientInfo;
    private final ManagementServer managementServer;
    private final Channel channel;
    private final MinecraftApi minecraftApi;
    private final AtomicInteger transactionId = new AtomicInteger();
    private final Int2ObjectMap<PendingRpcRequest<?>> pendingRequests = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap());

    public Connection(Channel channel, ManagementServer managementServer, MinecraftApi minecraftApi, JsonRpcLogger jsonrpcLogger) {
        this.clientInfo = ClientInfo.of(Connection.CONNECTION_ID_COUNTER.incrementAndGet());
        this.managementServer = managementServer;
        this.minecraftApi = minecraftApi;
        this.channel = channel;
        this.jsonRpcLogger = jsonrpcLogger;
    }

    public void tick() {
        long i = Util.getMillis();

        this.pendingRequests.int2ObjectEntrySet().removeIf((entry) -> {
            boolean flag = ((PendingRpcRequest) entry.getValue()).timedOut(i);

            if (flag) {
                ((PendingRpcRequest) entry.getValue()).resultFuture().completeExceptionally(new ReadTimeoutException("RPC method " + String.valueOf(((PendingRpcRequest) entry.getValue()).method().key().identifier()) + " timed out waiting for response"));
            }

            return flag;
        });
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.jsonRpcLogger.log(this.clientInfo, "Management connection opened for {}", this.channel.remoteAddress());
        super.channelActive(ctx);
        this.managementServer.onConnected(this);
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.jsonRpcLogger.log(this.clientInfo, "Management connection closed for {}", this.channel.remoteAddress());
        super.channelInactive(ctx);
        this.managementServer.onDisconnected(this);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause.getCause() instanceof JsonParseException) {
            this.channel.writeAndFlush(JsonRPCErrors.PARSE_ERROR.createWithUnknownId(cause.getMessage()));
        } else {
            super.exceptionCaught(ctx, cause);
            this.channel.close().awaitUninterruptibly();
        }
    }

    protected void channelRead0(ChannelHandlerContext channelHandlerContext, JsonElement jsonElement) {
        if (jsonElement.isJsonObject()) {
            JsonObject jsonobject = this.handleJsonObject(jsonElement.getAsJsonObject());

            if (jsonobject != null) {
                this.channel.writeAndFlush(jsonobject);
            }
        } else if (jsonElement.isJsonArray()) {
            this.channel.writeAndFlush(this.handleBatchRequest(jsonElement.getAsJsonArray().asList()));
        } else {
            this.channel.writeAndFlush(JsonRPCErrors.INVALID_REQUEST.createWithUnknownId((String) null));
        }

    }

    private JsonArray handleBatchRequest(List<JsonElement> batchRequests) {
        JsonArray jsonarray = new JsonArray();
        Stream stream = batchRequests.stream().map((jsonelement) -> {
            return this.handleJsonObject(jsonelement.getAsJsonObject());
        }).filter(Objects::nonNull);

        Objects.requireNonNull(jsonarray);
        stream.forEach(jsonarray::add);
        return jsonarray;
    }

    public void sendNotification(Holder.Reference<? extends OutgoingRpcMethod<Void, ?>> method) {
        this.sendRequest(method, (Object) null, false);
    }

    public <Params> void sendNotification(Holder.Reference<? extends OutgoingRpcMethod<Params, ?>> method, Params params) {
        this.sendRequest(method, params, false);
    }

    public <Result> CompletableFuture<Result> sendRequest(Holder.Reference<? extends OutgoingRpcMethod<Void, Result>> method) {
        return this.sendRequest(method, (Object) null, true);
    }

    public <Params, Result> CompletableFuture<Result> sendRequest(Holder.Reference<? extends OutgoingRpcMethod<Params, Result>> method, Params params) {
        return this.sendRequest(method, params, true);
    }

    @Contract("_,_,false->null;_,_,true->!null")
    private <Params, Result> @Nullable CompletableFuture<Result> sendRequest(Holder.Reference<? extends OutgoingRpcMethod<Params, ? extends Result>> method, @Nullable Params params, boolean expectReply) {
        List<JsonElement> list = params != null ? List.of((JsonElement) Objects.requireNonNull(((OutgoingRpcMethod) method.value()).encodeParams(params))) : List.of();

        if (expectReply) {
            CompletableFuture<Result> completablefuture = new CompletableFuture();
            int i = this.transactionId.incrementAndGet();
            long j = Util.timeSource.get(TimeUnit.MILLISECONDS);

            this.pendingRequests.put(i, new PendingRpcRequest(method, completablefuture, j + 5000L));
            this.channel.writeAndFlush(JsonRPCUtils.createRequest(i, method.key().identifier(), list));
            return completablefuture;
        } else {
            this.channel.writeAndFlush(JsonRPCUtils.createRequest((Integer) null, method.key().identifier(), list));
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    JsonObject handleJsonObject(JsonObject jsonObject) {
        try {
            JsonElement jsonelement = JsonRPCUtils.getRequestId(jsonObject);
            String s = JsonRPCUtils.getMethodName(jsonObject);
            JsonElement jsonelement1 = JsonRPCUtils.getResult(jsonObject);
            JsonElement jsonelement2 = JsonRPCUtils.getParams(jsonObject);
            JsonObject jsonobject1 = JsonRPCUtils.getError(jsonObject);

            if (s != null && jsonelement1 == null && jsonobject1 == null) {
                return jsonelement != null && !isValidRequestId(jsonelement) ? JsonRPCErrors.INVALID_REQUEST.createWithUnknownId("Invalid request id - only String, Number and NULL supported") : this.handleIncomingRequest(jsonelement, s, jsonelement2);
            } else if (s == null && jsonelement1 != null && jsonobject1 == null && jsonelement != null) {
                if (isValidResponseId(jsonelement)) {
                    this.handleRequestResponse(jsonelement.getAsInt(), jsonelement1);
                } else {
                    Connection.LOGGER.warn("Received respose {} with id {} we did not request", jsonelement1, jsonelement);
                }

                return null;
            } else {
                return s == null && jsonelement1 == null && jsonobject1 != null ? this.handleError(jsonelement, jsonobject1) : JsonRPCErrors.INVALID_REQUEST.createWithoutData((JsonElement) Objects.requireNonNullElse(jsonelement, JsonNull.INSTANCE));
            }
        } catch (Exception exception) {
            Connection.LOGGER.error("Error while handling rpc request", exception);
            return JsonRPCErrors.INTERNAL_ERROR.createWithUnknownId("Unknown error handling request - check server logs for stack trace");
        }
    }

    private static boolean isValidRequestId(JsonElement id) {
        return id.isJsonNull() || GsonHelper.isNumberValue(id) || GsonHelper.isStringValue(id);
    }

    private static boolean isValidResponseId(JsonElement id) {
        return GsonHelper.isNumberValue(id);
    }

    private @Nullable JsonObject handleIncomingRequest(@Nullable JsonElement id, String method, @Nullable JsonElement params) {
        boolean flag = id != null;

        try {
            JsonElement jsonelement2 = this.dispatchIncomingRequest(method, params);

            return jsonelement2 != null && flag ? JsonRPCUtils.createSuccessResult(id, jsonelement2) : null;
        } catch (InvalidParameterJsonRpcException invalidparameterjsonrpcexception) {
            Connection.LOGGER.debug("Invalid parameter invocation {}: {}, {}", new Object[]{method, params, invalidparameterjsonrpcexception.getMessage()});
            return flag ? JsonRPCErrors.INVALID_PARAMS.create(id, invalidparameterjsonrpcexception.getMessage()) : null;
        } catch (EncodeJsonRpcException encodejsonrpcexception) {
            Connection.LOGGER.error("Failed to encode json rpc response {}: {}", method, encodejsonrpcexception.getMessage());
            return flag ? JsonRPCErrors.INTERNAL_ERROR.create(id, encodejsonrpcexception.getMessage()) : null;
        } catch (InvalidRequestJsonRpcException invalidrequestjsonrpcexception) {
            return flag ? JsonRPCErrors.INVALID_REQUEST.create(id, invalidrequestjsonrpcexception.getMessage()) : null;
        } catch (MethodNotFoundJsonRpcException methodnotfoundjsonrpcexception) {
            return flag ? JsonRPCErrors.METHOD_NOT_FOUND.create(id, methodnotfoundjsonrpcexception.getMessage()) : null;
        } catch (Exception exception) {
            Connection.LOGGER.error("Error while dispatching rpc method {}", method, exception);
            return flag ? JsonRPCErrors.INTERNAL_ERROR.createWithoutData(id) : null;
        }
    }

    public @Nullable JsonElement dispatchIncomingRequest(String method, @Nullable JsonElement params) {
        Identifier identifier = Identifier.tryParse(method);

        if (identifier == null) {
            throw new InvalidRequestJsonRpcException("Failed to parse method value: " + method);
        } else {
            Optional<IncomingRpcMethod<?, ?>> optional = BuiltInRegistries.INCOMING_RPC_METHOD.getOptional(identifier);

            if (optional.isEmpty()) {
                throw new MethodNotFoundJsonRpcException("Method not found: " + method);
            } else if (((IncomingRpcMethod) optional.get()).attributes().runOnMainThread()) {
                try {
                    return (JsonElement) this.minecraftApi.submit(() -> {
                        return ((IncomingRpcMethod) optional.get()).apply(this.minecraftApi, params, this.clientInfo);
                    }).join();
                } catch (CompletionException completionexception) {
                    Throwable throwable = completionexception.getCause();

                    if (throwable instanceof RuntimeException) {
                        RuntimeException runtimeexception = (RuntimeException) throwable;

                        throw runtimeexception;
                    } else {
                        throw completionexception;
                    }
                }
            } else {
                return ((IncomingRpcMethod) optional.get()).apply(this.minecraftApi, params, this.clientInfo);
            }
        }
    }

    private void handleRequestResponse(int id, JsonElement result) {
        PendingRpcRequest<?> pendingrpcrequest = (PendingRpcRequest) this.pendingRequests.remove(id);

        if (pendingrpcrequest == null) {
            Connection.LOGGER.warn("Received unknown response (id: {}): {}", id, result);
        } else {
            pendingrpcrequest.accept(result);
        }

    }

    private @Nullable JsonObject handleError(@Nullable JsonElement id, JsonObject error) {
        if (id != null && isValidResponseId(id)) {
            PendingRpcRequest<?> pendingrpcrequest = (PendingRpcRequest) this.pendingRequests.remove(id.getAsInt());

            if (pendingrpcrequest != null) {
                pendingrpcrequest.resultFuture().completeExceptionally(new RemoteRpcErrorException(id, error));
            }
        }

        Connection.LOGGER.error("Received error (id: {}): {}", id, error);
        return null;
    }
}
