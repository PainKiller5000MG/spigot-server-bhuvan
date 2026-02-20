package net.minecraft.world.attribute;

import com.google.common.collect.ImmutableBiMap;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.modifier.AttributeModifier;

public record AttributeType<Value>(Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary, Codec<AttributeModifier<Value, ?>> modifierCodec, LerpFunction<Value> keyframeLerp, LerpFunction<Value> stateChangeLerp, LerpFunction<Value> spatialLerp, LerpFunction<Value> partialTickLerp) {

    public static <Value> AttributeType<Value> ofInterpolated(Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary, LerpFunction<Value> lerp) {
        return ofInterpolated(valueCodec, modifierLibrary, lerp, lerp);
    }

    public static <Value> AttributeType<Value> ofInterpolated(Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary, LerpFunction<Value> lerp, LerpFunction<Value> partialTickLerp) {
        return new AttributeType<Value>(valueCodec, modifierLibrary, createModifierCodec(modifierLibrary), lerp, lerp, lerp, partialTickLerp);
    }

    public static <Value> AttributeType<Value> ofNotInterpolated(Codec<Value> valueCodec, Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifierLibrary) {
        return new AttributeType<Value>(valueCodec, modifierLibrary, createModifierCodec(modifierLibrary), LerpFunction.ofStep(1.0F), LerpFunction.ofStep(0.0F), LerpFunction.ofStep(0.5F), LerpFunction.ofStep(0.0F));
    }

    public static <Value> AttributeType<Value> ofNotInterpolated(Codec<Value> valueCodec) {
        return ofNotInterpolated(valueCodec, Map.of());
    }

    private static <Value> Codec<AttributeModifier<Value, ?>> createModifierCodec(Map<AttributeModifier.OperationId, AttributeModifier<Value, ?>> modifiers) {
        ImmutableBiMap<AttributeModifier.OperationId, AttributeModifier<Value, ?>> immutablebimap = ImmutableBiMap.builder().put(AttributeModifier.OperationId.OVERRIDE, AttributeModifier.override()).putAll(modifiers).buildOrThrow();
        Codec codec = AttributeModifier.OperationId.CODEC;

        Objects.requireNonNull(immutablebimap);
        Function function = immutablebimap::get;
        ImmutableBiMap immutablebimap1 = immutablebimap.inverse();

        Objects.requireNonNull(immutablebimap1);
        return ExtraCodecs.idResolverCodec(codec, function, immutablebimap1::get);
    }

    public void checkAllowedModifier(AttributeModifier<Value, ?> modifier) {
        if (modifier != AttributeModifier.override() && !this.modifierLibrary.containsValue(modifier)) {
            String s = String.valueOf(modifier);

            throw new IllegalArgumentException("Modifier " + s + " is not valid for " + String.valueOf(this));
        }
    }

    public String toString() {
        return Util.getRegisteredName(BuiltInRegistries.ATTRIBUTE_TYPE, this);
    }
}
