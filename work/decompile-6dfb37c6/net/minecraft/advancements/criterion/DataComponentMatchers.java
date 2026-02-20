package net.minecraft.advancements.criterion;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record DataComponentMatchers(DataComponentExactPredicate exact, Map<DataComponentPredicate.Type<?>, DataComponentPredicate> partial) implements Predicate<DataComponentGetter> {

    public static final DataComponentMatchers ANY = new DataComponentMatchers(DataComponentExactPredicate.EMPTY, Map.of());
    public static final MapCodec<DataComponentMatchers> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(DataComponentExactPredicate.CODEC.optionalFieldOf("components", DataComponentExactPredicate.EMPTY).forGetter(DataComponentMatchers::exact), DataComponentPredicate.CODEC.optionalFieldOf("predicates", Map.of()).forGetter(DataComponentMatchers::partial)).apply(instance, DataComponentMatchers::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentMatchers> STREAM_CODEC = StreamCodec.composite(DataComponentExactPredicate.STREAM_CODEC, DataComponentMatchers::exact, DataComponentPredicate.STREAM_CODEC, DataComponentMatchers::partial, DataComponentMatchers::new);

    public boolean test(DataComponentGetter values) {
        if (!this.exact.test(values)) {
            return false;
        } else {
            for (DataComponentPredicate datacomponentpredicate : this.partial.values()) {
                if (!datacomponentpredicate.matches(values)) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean isEmpty() {
        return this.exact.isEmpty() && this.partial.isEmpty();
    }

    public static class Builder {

        private DataComponentExactPredicate exact;
        private final ImmutableMap.Builder<DataComponentPredicate.Type<?>, DataComponentPredicate> partial;

        private Builder() {
            this.exact = DataComponentExactPredicate.EMPTY;
            this.partial = ImmutableMap.builder();
        }

        public static DataComponentMatchers.Builder components() {
            return new DataComponentMatchers.Builder();
        }

        public <T extends DataComponentType<?>> DataComponentMatchers.Builder any(DataComponentType<?> type) {
            DataComponentPredicate.AnyValueType datacomponentpredicate_anyvaluetype = DataComponentPredicate.AnyValueType.create(type);

            this.partial.put(datacomponentpredicate_anyvaluetype, datacomponentpredicate_anyvaluetype.predicate());
            return this;
        }

        public <T extends DataComponentPredicate> DataComponentMatchers.Builder partial(DataComponentPredicate.Type<T> type, T predicate) {
            this.partial.put(type, predicate);
            return this;
        }

        public DataComponentMatchers.Builder exact(DataComponentExactPredicate exact) {
            this.exact = exact;
            return this;
        }

        public DataComponentMatchers build() {
            return new DataComponentMatchers(this.exact, this.partial.buildOrThrow());
        }
    }
}
