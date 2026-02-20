package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TypedDataComponent<T>(DataComponentType<T> type, T value) {

    public static final StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>>() {
        public TypedDataComponent<?> decode(RegistryFriendlyByteBuf input) {
            DataComponentType<?> datacomponenttype = (DataComponentType) DataComponentType.STREAM_CODEC.decode(input);

            return decodeTyped(input, datacomponenttype);
        }

        private static <T> TypedDataComponent<T> decodeTyped(RegistryFriendlyByteBuf input, DataComponentType<T> type) {
            return new TypedDataComponent<T>(type, type.streamCodec().decode(input));
        }

        public void encode(RegistryFriendlyByteBuf output, TypedDataComponent<?> value) {
            encodeCap(output, value);
        }

        private static <T> void encodeCap(RegistryFriendlyByteBuf output, TypedDataComponent<T> component) {
            DataComponentType.STREAM_CODEC.encode(output, component.type());
            component.type().streamCodec().encode(output, component.value());
        }
    };

    static TypedDataComponent<?> fromEntryUnchecked(Map.Entry<DataComponentType<?>, Object> entry) {
        return createUnchecked((DataComponentType) entry.getKey(), entry.getValue());
    }

    public static <T> TypedDataComponent<T> createUnchecked(DataComponentType<T> type, Object value) {
        return new TypedDataComponent<T>(type, value);
    }

    public void applyTo(PatchedDataComponentMap components) {
        components.set(this.type, this.value);
    }

    public <D> DataResult<D> encodeValue(DynamicOps<D> ops) {
        Codec<T> codec = this.type.codec();

        return codec == null ? DataResult.error(() -> {
            return "Component of type " + String.valueOf(this.type) + " is not encodable";
        }) : codec.encodeStart(ops, this.value);
    }

    public String toString() {
        String s = String.valueOf(this.type);

        return s + "=>" + String.valueOf(this.value);
    }
}
