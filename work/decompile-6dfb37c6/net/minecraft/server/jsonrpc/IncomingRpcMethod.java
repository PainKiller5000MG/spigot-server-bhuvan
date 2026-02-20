package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import org.jspecify.annotations.Nullable;

public interface IncomingRpcMethod<Params, Result> {

    MethodInfo<Params, Result> info();

    IncomingRpcMethod.Attributes attributes();

    JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement paramsJson, ClientInfo clientInfo);

    static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> function) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result>(function);
    }

    static <Params, Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> method(IncomingRpcMethod.RpcMethodFunction<Params, Result> function) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result>(function);
    }

    static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(Function<MinecraftApi, Result> supplier) {
        return new IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result>(supplier);
    }

    public static record Attributes(boolean runOnMainThread, boolean discoverable) {

    }

    public static record ParameterlessMethod<Params, Result>(MethodInfo<Params, Result> info, IncomingRpcMethod.Attributes attributes, IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> supplier) implements IncomingRpcMethod<Params, Result> {

        @Override
        public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement paramsJson, ClientInfo clientInfo) {
            if (paramsJson == null || paramsJson.isJsonArray() && paramsJson.getAsJsonArray().isEmpty()) {
                if (this.info.params().isPresent()) {
                    throw new IllegalArgumentException("Parameterless method unexpectedly has parameter description");
                } else {
                    Result result = this.supplier.apply(minecraftApi, clientInfo);

                    if (this.info.result().isEmpty()) {
                        throw new IllegalStateException("No result codec defined");
                    } else {
                        return (JsonElement) ((ResultInfo) this.info.result().get()).schema().codec().encodeStart(JsonOps.INSTANCE, result).getOrThrow(InvalidParameterJsonRpcException::new);
                    }
                }
            } else {
                throw new InvalidParameterJsonRpcException("Expected no params, or an empty array");
            }
        }
    }

    public static record Method<Params, Result>(MethodInfo<Params, Result> info, IncomingRpcMethod.Attributes attributes, IncomingRpcMethod.RpcMethodFunction<Params, Result> function) implements IncomingRpcMethod<Params, Result> {

        @Override
        public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement paramsJson, ClientInfo clientInfo) {
            if (paramsJson != null && (paramsJson.isJsonArray() || paramsJson.isJsonObject())) {
                if (this.info.params().isEmpty()) {
                    throw new IllegalArgumentException("Method defined as having parameters without describing them");
                } else {
                    JsonElement jsonelement1;

                    if (paramsJson.isJsonObject()) {
                        String s = ((ParamInfo) this.info.params().get()).name();
                        JsonElement jsonelement2 = paramsJson.getAsJsonObject().get(s);

                        if (jsonelement2 == null) {
                            throw new InvalidParameterJsonRpcException(String.format(Locale.ROOT, "Params passed by-name, but expected param [%s] does not exist", s));
                        }

                        jsonelement1 = jsonelement2;
                    } else {
                        JsonArray jsonarray = paramsJson.getAsJsonArray();

                        if (jsonarray.isEmpty() || jsonarray.size() > 1) {
                            throw new InvalidParameterJsonRpcException("Expected exactly one element in the params array");
                        }

                        jsonelement1 = jsonarray.get(0);
                    }

                    Params params = (Params) ((ParamInfo) this.info.params().get()).schema().codec().parse(JsonOps.INSTANCE, jsonelement1).getOrThrow(InvalidParameterJsonRpcException::new);
                    Result result = this.function.apply(minecraftApi, params, clientInfo);

                    if (this.info.result().isEmpty()) {
                        throw new IllegalStateException("No result codec defined");
                    } else {
                        return (JsonElement) ((ResultInfo) this.info.result().get()).schema().codec().encodeStart(JsonOps.INSTANCE, result).getOrThrow(EncodeJsonRpcException::new);
                    }
                }
            } else {
                throw new InvalidParameterJsonRpcException("Expected params as array or named");
            }
        }
    }

    public static class IncomingRpcMethodBuilder<Params, Result> {

        private String description = "";
        private @Nullable ParamInfo<Params> paramInfo;
        private @Nullable ResultInfo<Result> resultInfo;
        private boolean discoverable = true;
        private boolean runOnMainThread = true;
        private IncomingRpcMethod.@Nullable ParameterlessRpcMethodFunction<Result> parameterlessFunction;
        private IncomingRpcMethod.@Nullable RpcMethodFunction<Params, Result> parameterFunction;

        public IncomingRpcMethodBuilder(IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> function) {
            this.parameterlessFunction = function;
        }

        public IncomingRpcMethodBuilder(IncomingRpcMethod.RpcMethodFunction<Params, Result> function) {
            this.parameterFunction = function;
        }

        public IncomingRpcMethodBuilder(Function<MinecraftApi, Result> supplier) {
            this.parameterlessFunction = (minecraftapi, clientinfo) -> {
                return supplier.apply(minecraftapi);
            };
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> description(String description) {
            this.description = description;
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> response(String resultName, Schema<Result> resultSchema) {
            this.resultInfo = new ResultInfo<Result>(resultName, resultSchema.info());
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> param(String paramName, Schema<Params> paramSchema) {
            this.paramInfo = new ParamInfo<Params>(paramName, paramSchema.info());
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> undiscoverable() {
            this.discoverable = false;
            return this;
        }

        public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> notOnMainThread() {
            this.runOnMainThread = false;
            return this;
        }

        public IncomingRpcMethod<Params, Result> build() {
            if (this.resultInfo == null) {
                throw new IllegalStateException("No response defined");
            } else {
                IncomingRpcMethod.Attributes incomingrpcmethod_attributes = new IncomingRpcMethod.Attributes(this.runOnMainThread, this.discoverable);
                MethodInfo<Params, Result> methodinfo = new MethodInfo<Params, Result>(this.description, this.paramInfo, this.resultInfo);

                if (this.parameterlessFunction != null) {
                    return new IncomingRpcMethod.ParameterlessMethod<Params, Result>(methodinfo, incomingrpcmethod_attributes, this.parameterlessFunction);
                } else if (this.parameterFunction != null) {
                    if (this.paramInfo == null) {
                        throw new IllegalStateException("No param schema defined");
                    } else {
                        return new IncomingRpcMethod.Method<Params, Result>(methodinfo, incomingrpcmethod_attributes, this.parameterFunction);
                    }
                } else {
                    throw new IllegalStateException("No method defined");
                }
            }
        }

        public IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> methodRegistry, String key) {
            return this.register(methodRegistry, Identifier.withDefaultNamespace(key));
        }

        private IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> methodRegistry, Identifier id) {
            return (IncomingRpcMethod) Registry.register(methodRegistry, id, this.build());
        }
    }

    @FunctionalInterface
    public interface ParameterlessRpcMethodFunction<Result> {

        Result apply(MinecraftApi api, ClientInfo clientInfo);
    }

    @FunctionalInterface
    public interface RpcMethodFunction<Params, Result> {

        Result apply(MinecraftApi api, Params params, ClientInfo clientInfo);
    }
}
