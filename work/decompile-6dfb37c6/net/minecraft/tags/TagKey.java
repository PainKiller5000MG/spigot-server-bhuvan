package net.minecraft.tags;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location) {

    private static final Interner<TagKey<?>> VALUES = Interners.newWeakInterner();

    public static <T> Codec<TagKey<T>> codec(ResourceKey<? extends Registry<T>> registryName) {
        return Identifier.CODEC.xmap((identifier) -> {
            return create(registryName, identifier);
        }, TagKey::location);
    }

    public static <T> Codec<TagKey<T>> hashedCodec(ResourceKey<? extends Registry<T>> registryName) {
        return Codec.STRING.comapFlatMap((s) -> {
            return s.startsWith("#") ? Identifier.read(s.substring(1)).map((identifier) -> {
                return create(registryName, identifier);
            }) : DataResult.error(() -> {
                return "Not a tag id";
            });
        }, (tagkey) -> {
            return "#" + String.valueOf(tagkey.location);
        });
    }

    public static <T> StreamCodec<ByteBuf, TagKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryName) {
        return Identifier.STREAM_CODEC.map((identifier) -> {
            return create(registryName, identifier);
        }, TagKey::location);
    }

    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return (TagKey) TagKey.VALUES.intern(new TagKey(registry, location));
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registry) {
        return this.registry == registry;
    }

    public <E> Optional<TagKey<E>> cast(ResourceKey<? extends Registry<E>> registry) {
        return this.isFor(registry) ? Optional.of(this) : Optional.empty();
    }

    public String toString() {
        String s = String.valueOf(this.registry.identifier());

        return "TagKey[" + s + " / " + String.valueOf(this.location) + "]";
    }
}
