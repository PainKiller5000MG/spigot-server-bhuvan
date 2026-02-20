package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;

public interface HolderGetter<T> {

    Optional<Holder.Reference<T>> get(ResourceKey<T> id);

    default Holder.Reference<T> getOrThrow(ResourceKey<T> id) {
        return (Holder.Reference) this.get(id).orElseThrow(() -> {
            return new IllegalStateException("Missing element " + String.valueOf(id));
        });
    }

    Optional<HolderSet.Named<T>> get(TagKey<T> id);

    default HolderSet.Named<T> getOrThrow(TagKey<T> id) {
        return (HolderSet.Named) this.get(id).orElseThrow(() -> {
            return new IllegalStateException("Missing tag " + String.valueOf(id));
        });
    }

    default Optional<Holder<T>> getRandomElementOf(TagKey<T> tag, RandomSource random) {
        return this.get(tag).flatMap((holderset_named) -> {
            return holderset_named.getRandomElement(random);
        });
    }

    public interface Provider {

        <T> Optional<? extends HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> key);

        default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key) {
            return (HolderGetter) this.lookup(key).orElseThrow(() -> {
                return new IllegalStateException("Registry " + String.valueOf(key.identifier()) + " not found");
            });
        }

        default <T> Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
            return this.lookup(id.registryKey()).flatMap((holdergetter) -> {
                return holdergetter.get(id);
            });
        }

        default <T> Holder.Reference<T> getOrThrow(ResourceKey<T> id) {
            return (Holder.Reference) this.lookup(id.registryKey()).flatMap((holdergetter) -> {
                return holdergetter.get(id);
            }).orElseThrow(() -> {
                return new IllegalStateException("Missing element " + String.valueOf(id));
            });
        }
    }
}
