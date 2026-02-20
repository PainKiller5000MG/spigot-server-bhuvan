package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseThresholdProvider extends NoiseBasedStateProvider {

    public static final MapCodec<NoiseThresholdProvider> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return noiseCodec(instance).and(instance.group(Codec.floatRange(-1.0F, 1.0F).fieldOf("threshold").forGetter((noisethresholdprovider) -> {
            return noisethresholdprovider.threshold;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("high_chance").forGetter((noisethresholdprovider) -> {
            return noisethresholdprovider.highChance;
        }), BlockState.CODEC.fieldOf("default_state").forGetter((noisethresholdprovider) -> {
            return noisethresholdprovider.defaultState;
        }), ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("low_states").forGetter((noisethresholdprovider) -> {
            return noisethresholdprovider.lowStates;
        }), ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("high_states").forGetter((noisethresholdprovider) -> {
            return noisethresholdprovider.highStates;
        }))).apply(instance, NoiseThresholdProvider::new);
    });
    private final float threshold;
    private final float highChance;
    private final BlockState defaultState;
    private final List<BlockState> lowStates;
    private final List<BlockState> highStates;

    public NoiseThresholdProvider(long seed, NormalNoise.NoiseParameters parameters, float scale, float threshold, float highChance, BlockState defaultState, List<BlockState> lowStates, List<BlockState> highStates) {
        super(seed, parameters, scale);
        this.threshold = threshold;
        this.highChance = highChance;
        this.defaultState = defaultState;
        this.lowStates = lowStates;
        this.highStates = highStates;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.NOISE_THRESHOLD_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        double d0 = this.getNoiseValue(pos, (double) this.scale);

        return d0 < (double) this.threshold ? (BlockState) Util.getRandom(this.lowStates, random) : (random.nextFloat() < this.highChance ? (BlockState) Util.getRandom(this.highStates, random) : this.defaultState);
    }
}
