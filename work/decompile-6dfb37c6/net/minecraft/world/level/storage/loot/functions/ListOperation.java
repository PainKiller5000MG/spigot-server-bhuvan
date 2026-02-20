package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.slf4j.Logger;

public interface ListOperation {

    MapCodec<ListOperation> UNLIMITED_CODEC = codec(Integer.MAX_VALUE);

    static MapCodec<ListOperation> codec(int maxSize) {
        return ListOperation.Type.CODEC.dispatchMap("mode", ListOperation::mode, (listoperation_type) -> {
            return listoperation_type.mapCodec;
        }).validate((listoperation) -> {
            if (listoperation instanceof ListOperation.ReplaceSection listoperation_replacesection) {
                if (listoperation_replacesection.size().isPresent()) {
                    int j = (Integer) listoperation_replacesection.size().get();

                    if (j > maxSize) {
                        return DataResult.error(() -> {
                            return "Size value too large: " + j + ", max size is " + maxSize;
                        });
                    }
                }
            }

            return DataResult.success(listoperation);
        });
    }

    ListOperation.Type mode();

    default <T> List<T> apply(List<T> original, List<T> replacement) {
        return this.<T>apply(original, replacement, Integer.MAX_VALUE);
    }

    <T> List<T> apply(List<T> original, List<T> replacement, int maxSize);

    public static enum Type implements StringRepresentable {

        REPLACE_ALL("replace_all", ListOperation.ReplaceAll.MAP_CODEC), REPLACE_SECTION("replace_section", ListOperation.ReplaceSection.MAP_CODEC), INSERT("insert", ListOperation.Insert.MAP_CODEC), APPEND("append", ListOperation.Append.MAP_CODEC);

        public static final Codec<ListOperation.Type> CODEC = StringRepresentable.<ListOperation.Type>fromEnum(ListOperation.Type::values);
        private final String id;
        private final MapCodec<? extends ListOperation> mapCodec;

        private Type(String id, MapCodec<? extends ListOperation> mapCodec) {
            this.id = id;
            this.mapCodec = mapCodec;
        }

        public MapCodec<? extends ListOperation> mapCodec() {
            return this.mapCodec;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    public static class ReplaceAll implements ListOperation {

        public static final ListOperation.ReplaceAll INSTANCE = new ListOperation.ReplaceAll();
        public static final MapCodec<ListOperation.ReplaceAll> MAP_CODEC = MapCodec.unit(() -> {
            return ListOperation.ReplaceAll.INSTANCE;
        });

        private ReplaceAll() {}

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.REPLACE_ALL;
        }

        @Override
        public <T> List<T> apply(List<T> original, List<T> replacement, int maxSize) {
            return replacement;
        }
    }

    public static record ReplaceSection(int offset, Optional<Integer> size) implements ListOperation {

        private static final Logger LOGGER = LogUtils.getLogger();
        public static final MapCodec<ListOperation.ReplaceSection> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(ListOperation.ReplaceSection::offset), ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("size").forGetter(ListOperation.ReplaceSection::size)).apply(instance, ListOperation.ReplaceSection::new);
        });

        public ReplaceSection(int offset) {
            this(offset, Optional.empty());
        }

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.REPLACE_SECTION;
        }

        @Override
        public <T> List<T> apply(List<T> original, List<T> replacement, int maxSize) {
            int j = original.size();

            if (this.offset > j) {
                ListOperation.ReplaceSection.LOGGER.error("Cannot replace when offset is out of bounds");
                return original;
            } else {
                ImmutableList.Builder<T> immutablelist_builder = ImmutableList.builder();

                immutablelist_builder.addAll(original.subList(0, this.offset));
                immutablelist_builder.addAll(replacement);
                int k = this.offset + (Integer) this.size.orElse(replacement.size());

                if (k < j) {
                    immutablelist_builder.addAll(original.subList(k, j));
                }

                List<T> list2 = immutablelist_builder.build();

                if (list2.size() > maxSize) {
                    ListOperation.ReplaceSection.LOGGER.error("Contents overflow in section replacement");
                    return original;
                } else {
                    return list2;
                }
            }
        }
    }

    public static record Insert(int offset) implements ListOperation {

        private static final Logger LOGGER = LogUtils.getLogger();
        public static final MapCodec<ListOperation.Insert> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(ListOperation.Insert::offset)).apply(instance, ListOperation.Insert::new);
        });

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.INSERT;
        }

        @Override
        public <T> List<T> apply(List<T> original, List<T> replacement, int maxSize) {
            int j = original.size();

            if (this.offset > j) {
                ListOperation.Insert.LOGGER.error("Cannot insert when offset is out of bounds");
                return original;
            } else if (j + replacement.size() > maxSize) {
                ListOperation.Insert.LOGGER.error("Contents overflow in section insertion");
                return original;
            } else {
                ImmutableList.Builder<T> immutablelist_builder = ImmutableList.builder();

                immutablelist_builder.addAll(original.subList(0, this.offset));
                immutablelist_builder.addAll(replacement);
                immutablelist_builder.addAll(original.subList(this.offset, j));
                return immutablelist_builder.build();
            }
        }
    }

    public static class Append implements ListOperation {

        private static final Logger LOGGER = LogUtils.getLogger();
        public static final ListOperation.Append INSTANCE = new ListOperation.Append();
        public static final MapCodec<ListOperation.Append> MAP_CODEC = MapCodec.unit(() -> {
            return ListOperation.Append.INSTANCE;
        });

        private Append() {}

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.APPEND;
        }

        @Override
        public <T> List<T> apply(List<T> original, List<T> replacement, int maxSize) {
            if (original.size() + replacement.size() > maxSize) {
                ListOperation.Append.LOGGER.error("Contents overflow in section append");
                return original;
            } else {
                return Stream.concat(original.stream(), replacement.stream()).toList();
            }
        }
    }

    public static record StandAlone<T>(List<T> value, ListOperation operation) {

        public static <T> Codec<ListOperation.StandAlone<T>> codec(Codec<T> valueCodec, int maxSize) {
            return RecordCodecBuilder.create((instance) -> {
                return instance.group(valueCodec.sizeLimitedListOf(maxSize).fieldOf("values").forGetter((listoperation_standalone) -> {
                    return listoperation_standalone.value;
                }), ListOperation.codec(maxSize).forGetter((listoperation_standalone) -> {
                    return listoperation_standalone.operation;
                })).apply(instance, ListOperation.StandAlone::new);
            });
        }

        public List<T> apply(List<T> input) {
            return this.operation.<T>apply(input, this.value);
        }
    }
}
