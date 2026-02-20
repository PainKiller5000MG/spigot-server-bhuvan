package net.minecraft.world.level.storage.loot.providers.number;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;

public record ConstantValue(float value) implements NumberProvider {

    public static final MapCodec<ConstantValue> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("value").forGetter(ConstantValue::value)).apply(instance, ConstantValue::new);
    });
    public static final Codec<ConstantValue> INLINE_CODEC = Codec.FLOAT.xmap(ConstantValue::new, ConstantValue::value);

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.CONSTANT;
    }

    @Override
    public float getFloat(LootContext random) {
        return this.value;
    }

    public static ConstantValue exactly(float value) {
        return new ConstantValue(value);
    }

    public boolean equals(Object o) {
        return this == o ? true : (o != null && this.getClass() == o.getClass() ? Float.compare(((ConstantValue) o).value, this.value) == 0 : false);
    }

    public int hashCode() {
        return this.value != 0.0F ? Float.floatToIntBits(this.value) : 0;
    }
}
