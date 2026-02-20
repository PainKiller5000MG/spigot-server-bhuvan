package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> {

    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = (new MapMaker()).weakValues().makeMap();
    private final Identifier registryName;
    private final Identifier identifier;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registryName) {
        return Identifier.CODEC.xmap((identifier) -> {
            return create(registryName, identifier);
        }, ResourceKey::identifier);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryName) {
        return Identifier.STREAM_CODEC.map((identifier) -> {
            return create(registryName, identifier);
        }, ResourceKey::identifier);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryName, Identifier location) {
        return create(registryName.identifier, location);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier) {
        return create(Registries.ROOT_REGISTRY_NAME, identifier);
    }

    private static <T> ResourceKey<T> create(Identifier registryName, Identifier identifier) {
        return (ResourceKey) ResourceKey.VALUES.computeIfAbsent(new ResourceKey.InternKey(registryName, identifier), (resourcekey_internkey) -> {
            return new ResourceKey(resourcekey_internkey.registry, resourcekey_internkey.identifier);
        });
    }

    private ResourceKey(Identifier registryName, Identifier identifier) {
        this.registryName = registryName;
        this.identifier = identifier;
    }

    public String toString() {
        String s = String.valueOf(this.registryName);

        return "ResourceKey[" + s + " / " + String.valueOf(this.identifier) + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registry) {
        return this.registryName.equals(registry.identifier());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registry) {
        return this.isFor(registry) ? Optional.of(this) : Optional.empty();
    }

    public Identifier identifier() {
        return this.identifier;
    }

    public Identifier registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    private static record InternKey(Identifier registry, Identifier identifier) {

    }
}
