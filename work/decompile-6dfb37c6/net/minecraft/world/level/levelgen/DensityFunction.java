package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public interface DensityFunction {

    Codec<DensityFunction> DIRECT_CODEC = DensityFunctions.DIRECT_CODEC;
    Codec<Holder<DensityFunction>> CODEC = RegistryFileCodec.<Holder<DensityFunction>>create(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC);
    Codec<DensityFunction> HOLDER_HELPER_CODEC = DensityFunction.CODEC.xmap(DensityFunctions.HolderHolder::new, (densityfunction) -> {
        if (densityfunction instanceof DensityFunctions.HolderHolder densityfunctions_holderholder) {
            return densityfunctions_holderholder.function();
        } else {
            return new Holder.Direct(densityfunction);
        }
    });

    double compute(DensityFunction.FunctionContext context);

    void fillArray(double[] output, DensityFunction.ContextProvider contextProvider);

    DensityFunction mapAll(DensityFunction.Visitor visitor);

    double minValue();

    double maxValue();

    KeyDispatchDataCodec<? extends DensityFunction> codec();

    default DensityFunction clamp(double min, double max) {
        return new DensityFunctions.Clamp(this, min, max);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction invert() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.INVERT);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    public static record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {

        public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC.xmap((holder) -> {
            return new DensityFunction.NoiseHolder(holder, (NormalNoise) null);
        }, DensityFunction.NoiseHolder::noiseData);

        public NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData) {
            this(noiseData, (NormalNoise) null);
        }

        public double getValue(double x, double y, double z) {
            return this.noise == null ? 0.0D : this.noise.getValue(x, y, z);
        }

        public double maxValue() {
            return this.noise == null ? 2.0D : this.noise.maxValue();
        }
    }

    public interface Visitor {

        DensityFunction apply(DensityFunction input);

        default DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            return noise;
        }
    }

    public interface SimpleFunction extends DensityFunction {

        @Override
        default void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(this);
        }
    }

    public interface FunctionContext {

        int blockX();

        int blockY();

        int blockZ();

        default Blender getBlender() {
            return Blender.empty();
        }
    }

    public static record SinglePointContext(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {

    }

    public interface ContextProvider {

        DensityFunction.FunctionContext forIndex(int index);

        void fillAllDirectly(double[] output, DensityFunction function);
    }
}
