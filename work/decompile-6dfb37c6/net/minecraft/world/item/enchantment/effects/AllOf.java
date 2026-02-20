package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {

    static <T, A extends T> MapCodec<A> codec(Codec<T> topLevelCodec, Function<List<T>, A> constructor, Function<A, List<T>> accessor) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(topLevelCodec.listOf().fieldOf("effects").forGetter(accessor)).apply(instance, constructor);
        });
    }

    static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... effects) {
        return new AllOf.EntityEffects(List.of(effects));
    }

    static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... effects) {
        return new AllOf.LocationBasedEffects(List.of(effects));
    }

    static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... effects) {
        return new AllOf.ValueEffects(List.of(effects));
    }

    public static record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {

        public static final MapCodec<AllOf.EntityEffects> CODEC = AllOf.codec(EnchantmentEntityEffect.CODEC, AllOf.EntityEffects::new, AllOf.EntityEffects::effects);

        @Override
        public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
            for (EnchantmentEntityEffect enchantmententityeffect : this.effects) {
                enchantmententityeffect.apply(serverLevel, enchantmentLevel, item, entity, position);
            }

        }

        @Override
        public MapCodec<AllOf.EntityEffects> codec() {
            return AllOf.EntityEffects.CODEC;
        }
    }

    public static record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {

        public static final MapCodec<AllOf.LocationBasedEffects> CODEC = AllOf.codec(EnchantmentLocationBasedEffect.CODEC, AllOf.LocationBasedEffects::new, AllOf.LocationBasedEffects::effects);

        @Override
        public void onChangedBlock(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position, boolean becameActive) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onChangedBlock(serverLevel, enchantmentLevel, item, entity, position, becameActive);
            }

        }

        @Override
        public void onDeactivated(EnchantedItemInUse item, Entity entity, Vec3 position, int level) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onDeactivated(item, entity, position, level);
            }

        }

        @Override
        public MapCodec<AllOf.LocationBasedEffects> codec() {
            return AllOf.LocationBasedEffects.CODEC;
        }
    }

    public static record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {

        public static final MapCodec<AllOf.ValueEffects> CODEC = AllOf.codec(EnchantmentValueEffect.CODEC, AllOf.ValueEffects::new, AllOf.ValueEffects::effects);

        @Override
        public float process(int enchantmentLevel, RandomSource random, float value) {
            for (EnchantmentValueEffect enchantmentvalueeffect : this.effects) {
                value = enchantmentvalueeffect.process(enchantmentLevel, random, value);
            }

            return value;
        }

        @Override
        public MapCodec<AllOf.ValueEffects> codec() {
            return AllOf.ValueEffects.CODEC;
        }
    }
}
