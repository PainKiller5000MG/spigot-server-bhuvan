package net.minecraft.resources;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;

public class HolderSetCodec<E> implements Codec<HolderSet<E>> {

    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    private final Codec<List<Holder<E>>> homogenousListCodec;
    private final Codec<Either<TagKey<E>, List<Holder<E>>>> registryAwareCodec;

    private static <E> Codec<List<Holder<E>>> homogenousList(Codec<Holder<E>> elementCodec, boolean alwaysUseList) {
        Codec<List<Holder<E>>> codec1 = elementCodec.listOf().validate(ExtraCodecs.ensureHomogenous(Holder::kind));

        return alwaysUseList ? codec1 : ExtraCodecs.compactListCodec(elementCodec, codec1);
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec, boolean alwaysUseList) {
        return new HolderSetCodec<HolderSet<E>>(registryKey, elementCodec, alwaysUseList);
    }

    private HolderSetCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<Holder<E>> elementCodec, boolean alwaysUseList) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.homogenousListCodec = homogenousList(elementCodec, alwaysUseList);
        this.registryAwareCodec = Codec.either(TagKey.hashedCodec(registryKey), this.homogenousListCodec);
    }

    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<T> registryops) {
            Optional<HolderGetter<E>> optional = registryops.getter(this.registryKey);

            if (optional.isPresent()) {
                HolderGetter<E> holdergetter = (HolderGetter) optional.get();

                return this.registryAwareCodec.decode(ops, input).flatMap((pair) -> {
                    DataResult<HolderSet<E>> dataresult = (DataResult) ((Either) pair.getFirst()).map((tagkey) -> {
                        return lookupTag(holdergetter, tagkey);
                    }, (list) -> {
                        return DataResult.success(HolderSet.direct(list));
                    });

                    return dataresult.map((holderset) -> {
                        return Pair.of(holderset, pair.getSecond());
                    });
                });
            }
        }

        return this.decodeWithoutRegistry(ops, input);
    }

    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> registry, TagKey<E> key) {
        return (DataResult) registry.get(key).map(DataResult::success).orElseGet(() -> {
            return DataResult.error(() -> {
                String s = String.valueOf(key.location());

                return "Missing tag: '" + s + "' in '" + String.valueOf(key.registry().identifier()) + "'";
            });
        });
    }

    public <T> DataResult<T> encode(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<T> registryops) {
            Optional<HolderOwner<E>> optional = registryops.owner(this.registryKey);

            if (optional.isPresent()) {
                if (!input.canSerializeIn((HolderOwner) optional.get())) {
                    return DataResult.error(() -> {
                        return "HolderSet " + String.valueOf(input) + " is not valid in current registry set";
                    });
                }

                return this.registryAwareCodec.encode(input.unwrap().mapRight(List::copyOf), ops, prefix);
            }
        }

        return this.<T>encodeWithoutRegistry(input, ops, prefix);
    }

    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.elementCodec.listOf().decode(ops, input).flatMap((pair) -> {
            List<Holder.Direct<E>> list = new ArrayList();

            for (Holder<E> holder : (List) pair.getFirst()) {
                if (!(holder instanceof Holder.Direct)) {
                    return DataResult.error(() -> {
                        return "Can't decode element " + String.valueOf(holder) + " without registry";
                    });
                }

                Holder.Direct<E> holder_direct = (Holder.Direct) holder;

                list.add(holder_direct);
            }

            return DataResult.success(new Pair(HolderSet.direct(list), pair.getSecond()));
        });
    }

    private <T> DataResult<T> encodeWithoutRegistry(HolderSet<E> input, DynamicOps<T> ops, T prefix) {
        return this.homogenousListCodec.encode(input.stream().toList(), ops, prefix);
    }
}
