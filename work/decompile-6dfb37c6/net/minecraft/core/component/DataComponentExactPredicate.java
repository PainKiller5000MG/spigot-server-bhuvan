package net.minecraft.core.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class DataComponentExactPredicate implements Predicate<DataComponentGetter> {

    public static final Codec<DataComponentExactPredicate> CODEC = DataComponentType.VALUE_MAP_CODEC.xmap((map) -> {
        return new DataComponentExactPredicate((List) map.entrySet().stream().map(TypedDataComponent::fromEntryUnchecked).collect(Collectors.toList()));
    }, (datacomponentexactpredicate) -> {
        return (Map) datacomponentexactpredicate.expectedComponents.stream().filter((typeddatacomponent) -> {
            return !typeddatacomponent.type().isTransient();
        }).collect(Collectors.toMap(TypedDataComponent::type, TypedDataComponent::value));
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentExactPredicate> STREAM_CODEC = TypedDataComponent.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DataComponentExactPredicate::new, (datacomponentexactpredicate) -> {
        return datacomponentexactpredicate.expectedComponents;
    });
    public static final DataComponentExactPredicate EMPTY = new DataComponentExactPredicate(List.of());
    private final List<TypedDataComponent<?>> expectedComponents;

    private DataComponentExactPredicate(List<TypedDataComponent<?>> expectedComponents) {
        this.expectedComponents = expectedComponents;
    }

    public static DataComponentExactPredicate.Builder builder() {
        return new DataComponentExactPredicate.Builder();
    }

    public static <T> DataComponentExactPredicate expect(DataComponentType<T> type, T value) {
        return new DataComponentExactPredicate(List.of(new TypedDataComponent(type, value)));
    }

    public static DataComponentExactPredicate allOf(DataComponentMap components) {
        return new DataComponentExactPredicate(ImmutableList.copyOf(components));
    }

    public static DataComponentExactPredicate someOf(DataComponentMap components, DataComponentType<?>... types) {
        DataComponentExactPredicate.Builder datacomponentexactpredicate_builder = new DataComponentExactPredicate.Builder();

        for (DataComponentType<?> datacomponenttype : types) {
            TypedDataComponent<?> typeddatacomponent = components.getTyped(datacomponenttype);

            if (typeddatacomponent != null) {
                datacomponentexactpredicate_builder.expect(typeddatacomponent);
            }
        }

        return datacomponentexactpredicate_builder.build();
    }

    public boolean isEmpty() {
        return this.expectedComponents.isEmpty();
    }

    public boolean equals(Object obj) {
        boolean flag;

        if (obj instanceof DataComponentExactPredicate datacomponentexactpredicate) {
            if (this.expectedComponents.equals(datacomponentexactpredicate.expectedComponents)) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public int hashCode() {
        return this.expectedComponents.hashCode();
    }

    public String toString() {
        return this.expectedComponents.toString();
    }

    public boolean test(DataComponentGetter actualComponents) {
        for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
            Object object = actualComponents.get(typeddatacomponent.type());

            if (!Objects.equals(typeddatacomponent.value(), object)) {
                return false;
            }
        }

        return true;
    }

    public boolean alwaysMatches() {
        return this.expectedComponents.isEmpty();
    }

    public DataComponentPatch asPatch() {
        DataComponentPatch.Builder datacomponentpatch_builder = DataComponentPatch.builder();

        for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
            datacomponentpatch_builder.set(typeddatacomponent);
        }

        return datacomponentpatch_builder.build();
    }

    public static class Builder {

        private final List<TypedDataComponent<?>> expectedComponents = new ArrayList();

        private Builder() {}

        public <T> DataComponentExactPredicate.Builder expect(TypedDataComponent<T> value) {
            return this.expect(value.type(), value.value());
        }

        public <T> DataComponentExactPredicate.Builder expect(DataComponentType<? super T> type, T value) {
            for (TypedDataComponent<?> typeddatacomponent : this.expectedComponents) {
                if (typeddatacomponent.type() == type) {
                    throw new IllegalArgumentException("Predicate already has component of type: '" + String.valueOf(type) + "'");
                }
            }

            this.expectedComponents.add(new TypedDataComponent(type, value));
            return this;
        }

        public DataComponentExactPredicate build() {
            return new DataComponentExactPredicate(List.copyOf(this.expectedComponents));
        }
    }
}
