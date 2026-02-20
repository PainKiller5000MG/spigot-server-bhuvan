package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.jsonrpc.IncomingRpcMethod;
import net.minecraft.server.jsonrpc.OutgoingRpcMethod;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.api.SchemaComponent;

public class DiscoveryService {

    public DiscoveryService() {}

    public static DiscoveryService.DiscoverResponse discover(List<SchemaComponent<?>> schemaRegistry) {
        List<MethodInfo.Named<?, ?>> list1 = new ArrayList(BuiltInRegistries.INCOMING_RPC_METHOD.size() + BuiltInRegistries.OUTGOING_RPC_METHOD.size());

        BuiltInRegistries.INCOMING_RPC_METHOD.listElements().forEach((holder_reference) -> {
            if (((IncomingRpcMethod) holder_reference.value()).attributes().discoverable()) {
                list1.add(((IncomingRpcMethod) holder_reference.value()).info().named(holder_reference.key().identifier()));
            }

        });
        BuiltInRegistries.OUTGOING_RPC_METHOD.listElements().forEach((holder_reference) -> {
            if (((OutgoingRpcMethod) holder_reference.value()).attributes().discoverable()) {
                list1.add(((OutgoingRpcMethod) holder_reference.value()).info().named(holder_reference.key().identifier()));
            }

        });
        Map<String, Schema<?>> map = new HashMap();

        for (SchemaComponent<?> schemacomponent : schemaRegistry) {
            map.put(schemacomponent.name(), schemacomponent.schema().info());
        }

        DiscoveryService.DiscoverInfo discoveryservice_discoverinfo = new DiscoveryService.DiscoverInfo("Minecraft Server JSON-RPC", "2.0.0");

        return new DiscoveryService.DiscoverResponse("1.3.2", discoveryservice_discoverinfo, list1, new DiscoveryService.DiscoverComponents(map));
    }

    public static record DiscoverResponse(String jsonRpcProtocolVersion, DiscoveryService.DiscoverInfo discoverInfo, List<MethodInfo.Named<?, ?>> methods, DiscoveryService.DiscoverComponents components) {

        public static final MapCodec<DiscoveryService.DiscoverResponse> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.fieldOf("openrpc").forGetter(DiscoveryService.DiscoverResponse::jsonRpcProtocolVersion), DiscoveryService.DiscoverInfo.CODEC.codec().fieldOf("info").forGetter(DiscoveryService.DiscoverResponse::discoverInfo), Codec.list(MethodInfo.Named.CODEC).fieldOf("methods").forGetter(DiscoveryService.DiscoverResponse::methods), DiscoveryService.DiscoverComponents.CODEC.codec().fieldOf("components").forGetter(DiscoveryService.DiscoverResponse::components)).apply(instance, DiscoveryService.DiscoverResponse::new);
        });
    }

    public static record DiscoverComponents(Map<String, Schema<?>> schemas) {

        public static final MapCodec<DiscoveryService.DiscoverComponents> CODEC = typedSchema();

        private static MapCodec<DiscoveryService.DiscoverComponents> typedSchema() {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(Codec.unboundedMap(Codec.STRING, Schema.CODEC).fieldOf("schemas").forGetter(DiscoveryService.DiscoverComponents::schemas)).apply(instance, DiscoveryService.DiscoverComponents::new);
            });
        }
    }

    public static record DiscoverInfo(String title, String version) {

        public static final MapCodec<DiscoveryService.DiscoverInfo> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.STRING.fieldOf("title").forGetter(DiscoveryService.DiscoverInfo::title), Codec.STRING.fieldOf("version").forGetter(DiscoveryService.DiscoverInfo::version)).apply(instance, DiscoveryService.DiscoverInfo::new);
        });
    }
}
