package net.minecraft.core.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

public interface DataComponentMap extends Iterable<TypedDataComponent<?>>, DataComponentGetter {

    DataComponentMap EMPTY = new DataComponentMap() {
        @Override
        public <T> @Nullable T get(DataComponentType<? extends T> type) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Set.of();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    Codec<DataComponentMap> CODEC = makeCodecFromMap(DataComponentType.VALUE_MAP_CODEC);

    static Codec<DataComponentMap> makeCodec(Codec<DataComponentType<?>> componentTypeCodec) {
        return makeCodecFromMap(Codec.dispatchedMap(componentTypeCodec, DataComponentType::codecOrThrow));
    }

    static Codec<DataComponentMap> makeCodecFromMap(Codec<Map<DataComponentType<?>, Object>> mapCodec) {
        return mapCodec.flatComapMap(DataComponentMap.Builder::buildFromMapTrusted, (datacomponentmap) -> {
            int i = datacomponentmap.size();

            if (i == 0) {
                return DataResult.success(Reference2ObjectMaps.emptyMap());
            } else {
                Reference2ObjectMap<DataComponentType<?>, Object> reference2objectmap = new Reference2ObjectArrayMap(i);

                for (TypedDataComponent<?> typeddatacomponent : datacomponentmap) {
                    if (!typeddatacomponent.type().isTransient()) {
                        reference2objectmap.put(typeddatacomponent.type(), typeddatacomponent.value());
                    }
                }

                return DataResult.success(reference2objectmap);
            }
        });
    }

    static DataComponentMap composite(final DataComponentMap prototype, final DataComponentMap overrides) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                T t0 = (T) overrides.get(type);

                return (T) (t0 != null ? t0 : prototype.get(type));
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.union(prototype.keySet(), overrides.keySet());
            }
        };
    }

    static DataComponentMap.Builder builder() {
        return new DataComponentMap.Builder();
    }

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }

    default Iterator<TypedDataComponent<?>> iterator() {
        return Iterators.transform(this.keySet().iterator(), (datacomponenttype) -> {
            return (TypedDataComponent) Objects.requireNonNull(this.getTyped(datacomponenttype));
        });
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), (long) this.size(), 1345), false);
    }

    default int size() {
        return this.keySet().size();
    }

    default boolean isEmpty() {
        return this.size() == 0;
    }

    default DataComponentMap filter(final Predicate<DataComponentType<?>> predicate) {
        return new DataComponentMap() {
            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                return (T) (predicate.test(type) ? DataComponentMap.this.get(type) : null);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                Set set = DataComponentMap.this.keySet();
                Predicate predicate1 = predicate;

                Objects.requireNonNull(predicate);
                return Sets.filter(set, predicate1::test);
            }
        };
    }

    public static class Builder {

        private final Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap();

        private Builder() {}

        public <T> DataComponentMap.Builder set(DataComponentType<T> type, @Nullable T value) {
            this.setUnchecked(type, value);
            return this;
        }

        <T> void setUnchecked(DataComponentType<T> type, @Nullable Object value) {
            if (value != null) {
                this.map.put(type, value);
            } else {
                this.map.remove(type);
            }

        }

        public DataComponentMap.Builder addAll(DataComponentMap map) {
            for (TypedDataComponent<?> typeddatacomponent : map) {
                this.map.put(typeddatacomponent.type(), typeddatacomponent.value());
            }

            return this;
        }

        public DataComponentMap build() {
            return buildFromMapTrusted(this.map);
        }

        private static DataComponentMap buildFromMapTrusted(Map<DataComponentType<?>, Object> map) {
            return (DataComponentMap) (map.isEmpty() ? DataComponentMap.EMPTY : (map.size() < 8 ? new DataComponentMap.Builder.SimpleMap(new Reference2ObjectArrayMap(map)) : new DataComponentMap.Builder.SimpleMap(new Reference2ObjectOpenHashMap(map))));
        }

        private static record SimpleMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements DataComponentMap {

            @Override
            public <T> @Nullable T get(DataComponentType<? extends T> type) {
                return (T) this.map.get(type);
            }

            @Override
            public boolean has(DataComponentType<?> type) {
                return this.map.containsKey(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return this.map.keySet();
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), TypedDataComponent::fromEntryUnchecked);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            public String toString() {
                return this.map.toString();
            }
        }
    }
}
