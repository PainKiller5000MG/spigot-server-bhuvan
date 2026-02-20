package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

public final class DataComponentPatch {

    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec).xmap((map) -> {
        if (map.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(map.size());

            for (Map.Entry<DataComponentPatch.PatchKey, ?> map_entry : map.entrySet()) {
                DataComponentPatch.PatchKey datacomponentpatch_patchkey = (DataComponentPatch.PatchKey) map_entry.getKey();

                if (datacomponentpatch_patchkey.removed()) {
                    reference2objectmap.put(datacomponentpatch_patchkey.type(), Optional.empty());
                } else {
                    reference2objectmap.put(datacomponentpatch_patchkey.type(), Optional.of(map_entry.getValue()));
                }
            }

            return new DataComponentPatch(reference2objectmap);
        }
    }, (datacomponentpatch) -> {
        Reference2ObjectMap<DataComponentPatch.PatchKey, Object> reference2objectmap = new Reference2ObjectArrayMap(datacomponentpatch.map.size());
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();
            DataComponentType<?> datacomponenttype = (DataComponentType) map_entry.getKey();

            if (!datacomponenttype.isTransient()) {
                Optional<?> optional = (Optional) map_entry.getValue();

                if (optional.isPresent()) {
                    reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, false), optional.get());
                } else {
                    reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, true), Unit.INSTANCE);
                }
            }
        }

        return reference2objectmap;
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
        @Override
        public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> type) {
            return type.streamCodec().cast();
        }
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> DELIMITED_STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
        @Override
        public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> type) {
            StreamCodec<RegistryFriendlyByteBuf, T> streamcodec = type.streamCodec().cast();

            return streamcodec.apply(ByteBufCodecs.registryFriendlyLengthPrefixed(Integer.MAX_VALUE));
        }
    });
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    private static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> createStreamCodec(final DataComponentPatch.CodecGetter codecGetter) {
        return new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
            public DataComponentPatch decode(RegistryFriendlyByteBuf input) {
                int i = input.readVarInt();
                int j = input.readVarInt();

                if (i == 0 && j == 0) {
                    return DataComponentPatch.EMPTY;
                } else {
                    int k = i + j;
                    Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(Math.min(k, 65536));

                    for (int l = 0; l < i; ++l) {
                        DataComponentType<?> datacomponenttype = (DataComponentType) DataComponentType.STREAM_CODEC.decode(input);
                        Object object = codecGetter.apply(datacomponenttype).decode(input);

                        reference2objectmap.put(datacomponenttype, Optional.of(object));
                    }

                    for (int i1 = 0; i1 < j; ++i1) {
                        DataComponentType<?> datacomponenttype1 = (DataComponentType) DataComponentType.STREAM_CODEC.decode(input);

                        reference2objectmap.put(datacomponenttype1, Optional.empty());
                    }

                    return new DataComponentPatch(reference2objectmap);
                }
            }

            public void encode(RegistryFriendlyByteBuf output, DataComponentPatch patch) {
                if (patch.isEmpty()) {
                    output.writeVarInt(0);
                    output.writeVarInt(0);
                } else {
                    int i = 0;
                    int j = 0;
                    ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(patch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();

                        if (((Optional) reference2objectmap_entry.getValue()).isPresent()) {
                            ++i;
                        } else {
                            ++j;
                        }
                    }

                    output.writeVarInt(i);
                    output.writeVarInt(j);
                    objectiterator = Reference2ObjectMaps.fastIterable(patch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry1 = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                        Optional<?> optional = (Optional) reference2objectmap_entry1.getValue();

                        if (optional.isPresent()) {
                            DataComponentType<?> datacomponenttype = (DataComponentType) reference2objectmap_entry1.getKey();

                            DataComponentType.STREAM_CODEC.encode(output, datacomponenttype);
                            this.encodeComponent(output, datacomponenttype, optional.get());
                        }
                    }

                    objectiterator = Reference2ObjectMaps.fastIterable(patch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry2 = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();

                        if (((Optional) reference2objectmap_entry2.getValue()).isEmpty()) {
                            DataComponentType<?> datacomponenttype1 = (DataComponentType) reference2objectmap_entry2.getKey();

                            DataComponentType.STREAM_CODEC.encode(output, datacomponenttype1);
                        }
                    }

                }
            }

            private <T> void encodeComponent(RegistryFriendlyByteBuf output, DataComponentType<T> type, Object value) {
                codecGetter.apply(type).encode(output, value);
            }
        };
    }

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        this.map = map;
    }

    public static DataComponentPatch.Builder builder() {
        return new DataComponentPatch.Builder();
    }

    public <T> @Nullable Optional<? extends T> get(DataComponentType<? extends T> type) {
        return (Optional) this.map.get(type);
    }

    public Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> test) {
        if (this.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(this.map);

            reference2objectmap.keySet().removeIf(test);
            return reference2objectmap.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(reference2objectmap);
        }
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.SplitResult split() {
        if (this.isEmpty()) {
            return DataComponentPatch.SplitResult.EMPTY;
        } else {
            DataComponentMap.Builder datacomponentmap_builder = DataComponentMap.builder();
            Set<DataComponentType<?>> set = Sets.newIdentityHashSet();

            this.map.forEach((datacomponenttype, optional) -> {
                if (optional.isPresent()) {
                    datacomponentmap_builder.setUnchecked(datacomponenttype, optional.get());
                } else {
                    set.add(datacomponenttype);
                }

            });
            return new DataComponentPatch.SplitResult(datacomponentmap_builder.build(), set);
        }
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof DataComponentPatch) {
                DataComponentPatch datacomponentpatch = (DataComponentPatch) obj;

                if (this.map.equals(datacomponentpatch.map)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.map.hashCode();
    }

    public String toString() {
        return toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append('{');
        boolean flag = true;
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(map).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();

            if (flag) {
                flag = false;
            } else {
                stringbuilder.append(", ");
            }

            Optional<?> optional = (Optional) map_entry.getValue();

            if (optional.isPresent()) {
                stringbuilder.append(map_entry.getKey());
                stringbuilder.append("=>");
                stringbuilder.append(optional.get());
            } else {
                stringbuilder.append("!");
                stringbuilder.append(map_entry.getKey());
            }
        }

        stringbuilder.append('}');
        return stringbuilder.toString();
    }

    public static record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {

        public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
    }

    private static record PatchKey(DataComponentType<?> type, boolean removed) {

        public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING.flatXmap((s) -> {
            boolean flag = s.startsWith("!");

            if (flag) {
                s = s.substring("!".length());
            }

            Identifier identifier = Identifier.tryParse(s);
            DataComponentType<?> datacomponenttype = (DataComponentType) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);

            return datacomponenttype == null ? DataResult.error(() -> {
                return "No component with type: '" + String.valueOf(identifier) + "'";
            }) : (datacomponenttype.isTransient() ? DataResult.error(() -> {
                return "'" + String.valueOf(identifier) + "' is not a persistent component";
            }) : DataResult.success(new DataComponentPatch.PatchKey(datacomponenttype, flag)));
        }, (datacomponentpatch_patchkey) -> {
            DataComponentType<?> datacomponenttype = datacomponentpatch_patchkey.type();
            Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);

            return identifier == null ? DataResult.error(() -> {
                return "Unregistered component: " + String.valueOf(datacomponenttype);
            }) : DataResult.success(datacomponentpatch_patchkey.removed() ? "!" + String.valueOf(identifier) : identifier.toString());
        });

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }

    public static class Builder {

        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap();

        private Builder() {}

        public <T> DataComponentPatch.Builder set(DataComponentType<T> type, T value) {
            this.map.put(type, Optional.of(value));
            return this;
        }

        public <T> DataComponentPatch.Builder remove(DataComponentType<T> type) {
            this.map.put(type, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.Builder set(TypedDataComponent<T> component) {
            return this.set(component.type(), component.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    @FunctionalInterface
    private interface CodecGetter {

        <T> StreamCodec<? super RegistryFriendlyByteBuf, T> apply(DataComponentType<T> type);
    }
}
