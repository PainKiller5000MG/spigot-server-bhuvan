package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;

public final class RegistryFileCodec<E> implements Codec<Holder<E>> {

    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<E> elementCodec;
    private final boolean allowInline;

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec) {
        return create(registryKey, elementCodec, true);
    }

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        return new RegistryFileCodec<E>(registryKey, elementCodec, allowInline);
    }

    private RegistryFileCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.allowInline = allowInline;
    }

    public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<?> registryops) {
            Optional<HolderOwner<E>> optional = registryops.owner(this.registryKey);

            if (optional.isPresent()) {
                if (!input.canSerializeIn((HolderOwner) optional.get())) {
                    return DataResult.error(() -> {
                        return "Element " + String.valueOf(input) + " is not valid in current registry set";
                    });
                }

                return (DataResult) input.unwrap().map((resourcekey) -> {
                    return Identifier.CODEC.encode(resourcekey.identifier(), ops, prefix);
                }, (object) -> {
                    return this.elementCodec.encode(object, ops, prefix);
                });
            }
        }

        return this.elementCodec.encode(input.value(), ops, prefix);
    }

    public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<?> registryops) {
            Optional<HolderGetter<E>> optional = registryops.getter(this.registryKey);

            if (optional.isEmpty()) {
                return DataResult.error(() -> {
                    return "Registry does not exist: " + String.valueOf(this.registryKey);
                });
            } else {
                HolderGetter<E> holdergetter = (HolderGetter) optional.get();
                DataResult<Pair<Identifier, T>> dataresult = Identifier.CODEC.decode(ops, input);

                if (dataresult.result().isEmpty()) {
                    return !this.allowInline ? DataResult.error(() -> {
                        return "Inline definitions not allowed here";
                    }) : this.elementCodec.decode(ops, input).map((pair) -> {
                        return pair.mapFirst(Holder::direct);
                    });
                } else {
                    Pair<Identifier, T> pair = (Pair) dataresult.result().get();
                    ResourceKey<E> resourcekey = ResourceKey.create(this.registryKey, (Identifier) pair.getFirst());

                    return ((DataResult) holdergetter.get(resourcekey).map(DataResult::success).orElseGet(() -> {
                        return DataResult.error(() -> {
                            return "Failed to get element " + String.valueOf(resourcekey);
                        });
                    })).map((holder_reference) -> {
                        return Pair.of(holder_reference, pair.getSecond());
                    }).setLifecycle(Lifecycle.stable());
                }
            }
        } else {
            return this.elementCodec.decode(ops, input).map((pair1) -> {
                return pair1.mapFirst(Holder::direct);
            });
        }
    }

    public String toString() {
        String s = String.valueOf(this.registryKey);

        return "RegistryFileCodec[" + s + " " + String.valueOf(this.elementCodec) + "]";
    }
}
