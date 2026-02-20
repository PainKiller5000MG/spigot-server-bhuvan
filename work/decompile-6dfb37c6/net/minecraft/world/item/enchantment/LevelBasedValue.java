package net.minecraft.world.item.enchantment;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;

public interface LevelBasedValue {

    Codec<LevelBasedValue> DISPATCH_CODEC = BuiltInRegistries.ENCHANTMENT_LEVEL_BASED_VALUE_TYPE.byNameCodec().dispatch(LevelBasedValue::codec, (mapcodec) -> {
        return mapcodec;
    });
    Codec<LevelBasedValue> CODEC = Codec.either(LevelBasedValue.Constant.CODEC, LevelBasedValue.DISPATCH_CODEC).xmap((either) -> {
        return (LevelBasedValue) either.map((levelbasedvalue_constant) -> {
            return levelbasedvalue_constant;
        }, (levelbasedvalue) -> {
            return levelbasedvalue;
        });
    }, (levelbasedvalue) -> {
        Either either;

        if (levelbasedvalue instanceof LevelBasedValue.Constant levelbasedvalue_constant) {
            either = Either.left(levelbasedvalue_constant);
        } else {
            either = Either.right(levelbasedvalue);
        }

        return either;
    });

    static MapCodec<? extends LevelBasedValue> bootstrap(Registry<MapCodec<? extends LevelBasedValue>> registry) {
        Registry.register(registry, "clamped", LevelBasedValue.Clamped.CODEC);
        Registry.register(registry, "fraction", LevelBasedValue.Fraction.CODEC);
        Registry.register(registry, "levels_squared", LevelBasedValue.LevelsSquared.CODEC);
        Registry.register(registry, "linear", LevelBasedValue.Linear.CODEC);
        Registry.register(registry, "exponent", LevelBasedValue.Exponent.CODEC);
        return (MapCodec) Registry.register(registry, "lookup", LevelBasedValue.Lookup.CODEC);
    }

    static LevelBasedValue.Constant constant(float value) {
        return new LevelBasedValue.Constant(value);
    }

    static LevelBasedValue.Linear perLevel(float base, float perLevelAboveFirst) {
        return new LevelBasedValue.Linear(base, perLevelAboveFirst);
    }

    static LevelBasedValue.Linear perLevel(float perLevel) {
        return perLevel(perLevel, perLevel);
    }

    static LevelBasedValue.Lookup lookup(List<Float> values, LevelBasedValue fallback) {
        return new LevelBasedValue.Lookup(values, fallback);
    }

    float calculate(int level);

    MapCodec<? extends LevelBasedValue> codec();

    public static record Constant(float value) implements LevelBasedValue {

        public static final Codec<LevelBasedValue.Constant> CODEC = Codec.FLOAT.xmap(LevelBasedValue.Constant::new, LevelBasedValue.Constant::value);
        public static final MapCodec<LevelBasedValue.Constant> TYPED_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.fieldOf("value").forGetter(LevelBasedValue.Constant::value)).apply(instance, LevelBasedValue.Constant::new);
        });

        @Override
        public float calculate(int level) {
            return this.value;
        }

        @Override
        public MapCodec<LevelBasedValue.Constant> codec() {
            return LevelBasedValue.Constant.TYPED_CODEC;
        }
    }

    public static record Lookup(List<Float> values, LevelBasedValue fallback) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.Lookup> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.listOf().fieldOf("values").forGetter(LevelBasedValue.Lookup::values), LevelBasedValue.CODEC.fieldOf("fallback").forGetter(LevelBasedValue.Lookup::fallback)).apply(instance, LevelBasedValue.Lookup::new);
        });

        @Override
        public float calculate(int level) {
            return level <= this.values.size() ? (Float) this.values.get(level - 1) : this.fallback.calculate(level);
        }

        @Override
        public MapCodec<LevelBasedValue.Lookup> codec() {
            return LevelBasedValue.Lookup.CODEC;
        }
    }

    public static record Linear(float base, float perLevelAboveFirst) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.Linear> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.fieldOf("base").forGetter(LevelBasedValue.Linear::base), Codec.FLOAT.fieldOf("per_level_above_first").forGetter(LevelBasedValue.Linear::perLevelAboveFirst)).apply(instance, LevelBasedValue.Linear::new);
        });

        @Override
        public float calculate(int level) {
            return this.base + this.perLevelAboveFirst * (float) (level - 1);
        }

        @Override
        public MapCodec<LevelBasedValue.Linear> codec() {
            return LevelBasedValue.Linear.CODEC;
        }
    }

    public static record Clamped(LevelBasedValue value, float min, float max) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.Clamped> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(LevelBasedValue.CODEC.fieldOf("value").forGetter(LevelBasedValue.Clamped::value), Codec.FLOAT.fieldOf("min").forGetter(LevelBasedValue.Clamped::min), Codec.FLOAT.fieldOf("max").forGetter(LevelBasedValue.Clamped::max)).apply(instance, LevelBasedValue.Clamped::new);
        }).validate((levelbasedvalue_clamped) -> {
            return levelbasedvalue_clamped.max <= levelbasedvalue_clamped.min ? DataResult.error(() -> {
                return "Max must be larger than min, min: " + levelbasedvalue_clamped.min + ", max: " + levelbasedvalue_clamped.max;
            }) : DataResult.success(levelbasedvalue_clamped);
        });

        @Override
        public float calculate(int level) {
            return Mth.clamp(this.value.calculate(level), this.min, this.max);
        }

        @Override
        public MapCodec<LevelBasedValue.Clamped> codec() {
            return LevelBasedValue.Clamped.CODEC;
        }
    }

    public static record Fraction(LevelBasedValue numerator, LevelBasedValue denominator) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.Fraction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(LevelBasedValue.CODEC.fieldOf("numerator").forGetter(LevelBasedValue.Fraction::numerator), LevelBasedValue.CODEC.fieldOf("denominator").forGetter(LevelBasedValue.Fraction::denominator)).apply(instance, LevelBasedValue.Fraction::new);
        });

        @Override
        public float calculate(int level) {
            float f = this.denominator.calculate(level);

            return f == 0.0F ? 0.0F : this.numerator.calculate(level) / f;
        }

        @Override
        public MapCodec<LevelBasedValue.Fraction> codec() {
            return LevelBasedValue.Fraction.CODEC;
        }
    }

    public static record Exponent(LevelBasedValue base, LevelBasedValue power) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.Exponent> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(LevelBasedValue.CODEC.fieldOf("base").forGetter(LevelBasedValue.Exponent::base), LevelBasedValue.CODEC.fieldOf("power").forGetter(LevelBasedValue.Exponent::power)).apply(instance, LevelBasedValue.Exponent::new);
        });

        @Override
        public float calculate(int level) {
            return (float) Math.pow((double) this.base.calculate(level), (double) this.power.calculate(level));
        }

        @Override
        public MapCodec<LevelBasedValue.Exponent> codec() {
            return LevelBasedValue.Exponent.CODEC;
        }
    }

    public static record LevelsSquared(float added) implements LevelBasedValue {

        public static final MapCodec<LevelBasedValue.LevelsSquared> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Codec.FLOAT.fieldOf("added").forGetter(LevelBasedValue.LevelsSquared::added)).apply(instance, LevelBasedValue.LevelsSquared::new);
        });

        @Override
        public float calculate(int level) {
            return (float) Mth.square(level) + this.added;
        }

        @Override
        public MapCodec<LevelBasedValue.LevelsSquared> codec() {
            return LevelBasedValue.LevelsSquared.CODEC;
        }
    }
}
