package net.minecraft.core.component.predicates;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public interface DataComponentPredicate {

    Codec<Map<DataComponentPredicate.Type<?>, DataComponentPredicate>> CODEC = Codec.dispatchedMap(DataComponentPredicate.Type.CODEC, DataComponentPredicate.Type::codec);
    StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<?>> SINGLE_STREAM_CODEC = DataComponentPredicate.Type.STREAM_CODEC.dispatch(DataComponentPredicate.Single::type, DataComponentPredicate.Type::singleStreamCodec);
    StreamCodec<RegistryFriendlyByteBuf, Map<DataComponentPredicate.Type<?>, DataComponentPredicate>> STREAM_CODEC = DataComponentPredicate.SINGLE_STREAM_CODEC.apply(ByteBufCodecs.list(64)).map((list) -> {
        return (Map) list.stream().collect(Collectors.toMap(DataComponentPredicate.Single::type, DataComponentPredicate.Single::predicate));
    }, (map) -> {
        return map.entrySet().stream().map(DataComponentPredicate.Single::fromEntry).toList();
    });

    static MapCodec<DataComponentPredicate.Single<?>> singleCodec(String name) {
        return DataComponentPredicate.Type.CODEC.dispatchMap(name, DataComponentPredicate.Single::type, DataComponentPredicate.Type::wrappedCodec);
    }

    boolean matches(DataComponentGetter components);

    public interface Type<T extends DataComponentPredicate> {

        Codec<DataComponentPredicate.Type<?>> CODEC = Codec.either(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE.byNameCodec(), BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec()).xmap(DataComponentPredicate.Type::copyOrCreateType, DataComponentPredicate.Type::unpackType);
        StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Type<?>> STREAM_CODEC = ByteBufCodecs.either(ByteBufCodecs.registry(Registries.DATA_COMPONENT_PREDICATE_TYPE), ByteBufCodecs.registry(Registries.DATA_COMPONENT_TYPE)).map(DataComponentPredicate.Type::copyOrCreateType, DataComponentPredicate.Type::unpackType);

        private static <T extends DataComponentPredicate.Type<?>> Either<T, DataComponentType<?>> unpackType(T type) {
            Either either;

            if (type instanceof DataComponentPredicate.AnyValueType datacomponentpredicate_anyvaluetype) {
                either = Either.right(datacomponentpredicate_anyvaluetype.componentType());
            } else {
                either = Either.left(type);
            }

            return either;
        }

        private static DataComponentPredicate.Type<?> copyOrCreateType(Either<DataComponentPredicate.Type<?>, DataComponentType<?>> concreteTypeOrComponent) {
            return (DataComponentPredicate.Type) concreteTypeOrComponent.map((datacomponentpredicate_type) -> {
                return datacomponentpredicate_type;
            }, DataComponentPredicate.AnyValueType::create);
        }

        Codec<T> codec();

        MapCodec<DataComponentPredicate.Single<T>> wrappedCodec();

        StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<T>> singleStreamCodec();
    }

    public abstract static class TypeBase<T extends DataComponentPredicate> implements DataComponentPredicate.Type<T> {

        private final Codec<T> codec;
        private final MapCodec<DataComponentPredicate.Single<T>> wrappedCodec;
        private final StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<T>> singleStreamCodec;

        public TypeBase(Codec<T> codec) {
            this.codec = codec;
            this.wrappedCodec = DataComponentPredicate.Single.wrapCodec(this, codec);
            this.singleStreamCodec = ByteBufCodecs.fromCodecWithRegistries(codec).map((datacomponentpredicate) -> {
                return new DataComponentPredicate.Single(this, datacomponentpredicate);
            }, DataComponentPredicate.Single::predicate);
        }

        @Override
        public Codec<T> codec() {
            return this.codec;
        }

        @Override
        public MapCodec<DataComponentPredicate.Single<T>> wrappedCodec() {
            return this.wrappedCodec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate.Single<T>> singleStreamCodec() {
            return this.singleStreamCodec;
        }
    }

    public static final class ConcreteType<T extends DataComponentPredicate> extends DataComponentPredicate.TypeBase<T> {

        public ConcreteType(Codec<T> codec) {
            super(codec);
        }
    }

    public static final class AnyValueType extends DataComponentPredicate.TypeBase<AnyValue> {

        private final AnyValue predicate;

        public AnyValueType(AnyValue predicate) {
            super(MapCodec.unitCodec(predicate));
            this.predicate = predicate;
        }

        public AnyValue predicate() {
            return this.predicate;
        }

        public DataComponentType<?> componentType() {
            return this.predicate.type();
        }

        public static DataComponentPredicate.AnyValueType create(DataComponentType<?> componentType) {
            return new DataComponentPredicate.AnyValueType(new AnyValue(componentType));
        }
    }

    public static record Single<T extends DataComponentPredicate>(DataComponentPredicate.Type<T> type, T predicate) {

        private static <T extends DataComponentPredicate> MapCodec<DataComponentPredicate.Single<T>> wrapCodec(DataComponentPredicate.Type<T> type, Codec<T> codec) {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(codec.fieldOf("value").forGetter(DataComponentPredicate.Single::predicate)).apply(instance, (datacomponentpredicate) -> {
                    return new DataComponentPredicate.Single(type, datacomponentpredicate);
                });
            });
        }

        private static <T extends DataComponentPredicate> DataComponentPredicate.Single<T> fromEntry(Map.Entry<DataComponentPredicate.Type<?>, T> e) {
            return new DataComponentPredicate.Single<T>((DataComponentPredicate.Type) e.getKey(), (DataComponentPredicate) e.getValue());
        }
    }
}
