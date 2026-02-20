package net.minecraft.world.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record EitherHolder<T>(Either<Holder<T>, ResourceKey<T>> contents) {

    public EitherHolder(Holder<T> holder) {
        this(Either.left(holder));
    }

    public EitherHolder(ResourceKey<T> key) {
        this(Either.right(key));
    }

    public static <T> Codec<EitherHolder<T>> codec(ResourceKey<Registry<T>> registry, Codec<Holder<T>> holderCodec) {
        return Codec.either(holderCodec, ResourceKey.codec(registry).comapFlatMap((resourcekey1) -> {
            return DataResult.error(() -> {
                return "Cannot parse as key without registry";
            });
        }, Function.identity())).xmap(EitherHolder::new, EitherHolder::contents);
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, EitherHolder<T>> streamCodec(ResourceKey<Registry<T>> registry, StreamCodec<RegistryFriendlyByteBuf, Holder<T>> streamHolderCodec) {
        return StreamCodec.composite(ByteBufCodecs.either(streamHolderCodec, ResourceKey.streamCodec(registry)), EitherHolder::contents, EitherHolder::new);
    }

    public Optional<T> unwrap(Registry<T> registry) {
        Either either = this.contents;
        Function function = (holder) -> {
            return Optional.of(holder.value());
        };

        Objects.requireNonNull(registry);
        return (Optional) either.map(function, registry::getOptional);
    }

    public Optional<Holder<T>> unwrap(HolderLookup.Provider provider) {
        return (Optional) this.contents.map(Optional::of, (resourcekey) -> {
            return provider.get(resourcekey).map((holder_reference) -> {
                return holder_reference;
            });
        });
    }

    public Optional<ResourceKey<T>> key() {
        return (Optional) this.contents.map(Holder::unwrapKey, Optional::of);
    }
}
