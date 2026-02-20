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

public final class RegistryFixedCodec<E> implements Codec<Holder<E>> {

    private final ResourceKey<? extends Registry<E>> registryKey;

    public static <E> RegistryFixedCodec<E> create(ResourceKey<? extends Registry<E>> registryKey) {
        return new RegistryFixedCodec<E>(registryKey);
    }

    private RegistryFixedCodec(ResourceKey<? extends Registry<E>> registryKey) {
        this.registryKey = registryKey;
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
                    return DataResult.error(() -> {
                        return "Elements from registry " + String.valueOf(this.registryKey) + " can't be serialized to a value";
                    });
                });
            }
        }

        return DataResult.error(() -> {
            return "Can't access registry " + String.valueOf(this.registryKey);
        });
    }

    public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<?> registryops) {
            Optional<HolderGetter<E>> optional = registryops.getter(this.registryKey);

            if (optional.isPresent()) {
                return Identifier.CODEC.decode(ops, input).flatMap((pair) -> {
                    Identifier identifier = (Identifier) pair.getFirst();

                    return ((DataResult) ((HolderGetter) optional.get()).get(ResourceKey.create(this.registryKey, identifier)).map(DataResult::success).orElseGet(() -> {
                        return DataResult.error(() -> {
                            return "Failed to get element " + String.valueOf(identifier);
                        });
                    })).map((holder_reference) -> {
                        return Pair.of(holder_reference, pair.getSecond());
                    }).setLifecycle(Lifecycle.stable());
                });
            }
        }

        return DataResult.error(() -> {
            return "Can't access registry " + String.valueOf(this.registryKey);
        });
    }

    public String toString() {
        return "RegistryFixedCodec[" + String.valueOf(this.registryKey) + "]";
    }
}
