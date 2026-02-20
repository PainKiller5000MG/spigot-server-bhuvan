package net.minecraft.core;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.Lifecycle;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public interface Registry<T> extends IdMap<T>, Keyable, HolderLookup.RegistryLookup<T> {

    @Override
    ResourceKey<? extends Registry<T>> key();

    default Codec<T> byNameCodec() {
        return this.referenceHolderWithLifecycle().flatComapMap(Holder.Reference::value, (object) -> {
            return this.safeCastToReference(this.wrapAsHolder(object));
        });
    }

    default Codec<Holder<T>> holderByNameCodec() {
        return this.referenceHolderWithLifecycle().flatComapMap((holder_reference) -> {
            return holder_reference;
        }, this::safeCastToReference);
    }

    private Codec<Holder.Reference<T>> referenceHolderWithLifecycle() {
        Codec<Holder.Reference<T>> codec = Identifier.CODEC.comapFlatMap((identifier) -> {
            return (DataResult) this.get(identifier).map(DataResult::success).orElseGet(() -> {
                return DataResult.error(() -> {
                    String s = String.valueOf(this.key());

                    return "Unknown registry key in " + s + ": " + String.valueOf(identifier);
                });
            });
        }, (holder_reference) -> {
            return holder_reference.key().identifier();
        });

        return ExtraCodecs.<Holder.Reference<T>>overrideLifecycle(codec, (holder_reference) -> {
            return (Lifecycle) this.registrationInfo(holder_reference.key()).map(RegistrationInfo::lifecycle).orElse(Lifecycle.experimental());
        });
    }

    private DataResult<Holder.Reference<T>> safeCastToReference(Holder<T> holder) {
        DataResult dataresult;

        if (holder instanceof Holder.Reference<T> holder_reference) {
            dataresult = DataResult.success(holder_reference);
        } else {
            dataresult = DataResult.error(() -> {
                String s = String.valueOf(this.key());

                return "Unregistered holder in " + s + ": " + String.valueOf(holder);
            });
        }

        return dataresult;
    }

    default <U> Stream<U> keys(DynamicOps<U> ops) {
        return this.keySet().stream().map((identifier) -> {
            return ops.createString(identifier.toString());
        });
    }

    @Nullable
    Identifier getKey(T thing);

    Optional<ResourceKey<T>> getResourceKey(T thing);

    @Override
    int getId(@Nullable T thing);

    @Nullable
    T getValue(@Nullable ResourceKey<T> key);

    @Nullable
    T getValue(@Nullable Identifier key);

    Optional<RegistrationInfo> registrationInfo(ResourceKey<T> element);

    default Optional<T> getOptional(@Nullable Identifier key) {
        return Optional.ofNullable(this.getValue(key));
    }

    default Optional<T> getOptional(@Nullable ResourceKey<T> key) {
        return Optional.ofNullable(this.getValue(key));
    }

    Optional<Holder.Reference<T>> getAny();

    default T getValueOrThrow(ResourceKey<T> key) {
        T t0 = (T) this.getValue(key);

        if (t0 == null) {
            String s = String.valueOf(this.key());

            throw new IllegalStateException("Missing key in " + s + ": " + String.valueOf(key));
        } else {
            return t0;
        }
    }

    Set<Identifier> keySet();

    Set<Map.Entry<ResourceKey<T>, T>> entrySet();

    Set<ResourceKey<T>> registryKeySet();

    Optional<Holder.Reference<T>> getRandom(RandomSource random);

    default Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    boolean containsKey(Identifier key);

    boolean containsKey(ResourceKey<T> key);

    static <T> T register(Registry<? super T> registry, String name, T value) {
        return (T) register(registry, Identifier.parse(name), value);
    }

    static <V, T extends V> T register(Registry<V> registry, Identifier location, T value) {
        return (T) register(registry, ResourceKey.create(registry.key(), location), value);
    }

    static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value) {
        ((WritableRegistry) registry).register(key, value, RegistrationInfo.BUILT_IN);
        return value;
    }

    static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, ResourceKey<R> key, T value) {
        return ((WritableRegistry) registry).register(key, value, RegistrationInfo.BUILT_IN);
    }

    static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, Identifier location, T value) {
        return registerForHolder(registry, ResourceKey.create(registry.key(), location), value);
    }

    Registry<T> freeze();

    Holder.Reference<T> createIntrusiveHolder(T value);

    Optional<Holder.Reference<T>> get(int id);

    Optional<Holder.Reference<T>> get(Identifier id);

    Holder<T> wrapAsHolder(T value);

    default Iterable<Holder<T>> getTagOrEmpty(TagKey<T> id) {
        return (Iterable) DataFixUtils.orElse(this.get(id), List.of());
    }

    Stream<HolderSet.Named<T>> getTags();

    default IdMap<Holder<T>> asHolderIdMap() {
        return new IdMap<Holder<T>>() {
            public int getId(Holder<T> thing) {
                return Registry.this.getId(thing.value());
            }

            @Override
            public @Nullable Holder<T> byId(int id) {
                return (Holder) Registry.this.get(id).orElse((Object) null);
            }

            @Override
            public int size() {
                return Registry.this.size();
            }

            public Iterator<Holder<T>> iterator() {
                return Registry.this.listElements().map((holder_reference) -> {
                    return holder_reference;
                }).iterator();
            }
        };
    }

    Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> tags);

    public interface PendingTags<T> {

        ResourceKey<? extends Registry<? extends T>> key();

        HolderLookup.RegistryLookup<T> lookup();

        void apply();

        int size();
    }
}
