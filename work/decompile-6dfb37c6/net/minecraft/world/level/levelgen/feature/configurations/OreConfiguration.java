package net.minecraft.world.level.levelgen.feature.configurations;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

public class OreConfiguration implements FeatureConfiguration {

    public static final Codec<OreConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Codec.list(OreConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter((oreconfiguration) -> {
            return oreconfiguration.targetStates;
        }), Codec.intRange(0, 64).fieldOf("size").forGetter((oreconfiguration) -> {
            return oreconfiguration.size;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("discard_chance_on_air_exposure").forGetter((oreconfiguration) -> {
            return oreconfiguration.discardChanceOnAirExposure;
        })).apply(instance, OreConfiguration::new);
    });
    public final List<OreConfiguration.TargetBlockState> targetStates;
    public final int size;
    public final float discardChanceOnAirExposure;

    public OreConfiguration(List<OreConfiguration.TargetBlockState> targetBlockStates, int size, float discardChanceOnAirExposure) {
        this.size = size;
        this.targetStates = targetBlockStates;
        this.discardChanceOnAirExposure = discardChanceOnAirExposure;
    }

    public OreConfiguration(List<OreConfiguration.TargetBlockState> targetBlockStates, int size) {
        this(targetBlockStates, size, 0.0F);
    }

    public OreConfiguration(RuleTest target, BlockState state, int size, float discardChanceOnAirExposure) {
        this(ImmutableList.of(new OreConfiguration.TargetBlockState(target, state)), size, discardChanceOnAirExposure);
    }

    public OreConfiguration(RuleTest target, BlockState state, int size) {
        this(ImmutableList.of(new OreConfiguration.TargetBlockState(target, state)), size, 0.0F);
    }

    public static OreConfiguration.TargetBlockState target(RuleTest rule, BlockState state) {
        return new OreConfiguration.TargetBlockState(rule, state);
    }

    public static class TargetBlockState {

        public static final Codec<OreConfiguration.TargetBlockState> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(RuleTest.CODEC.fieldOf("target").forGetter((oreconfiguration_targetblockstate) -> {
                return oreconfiguration_targetblockstate.target;
            }), BlockState.CODEC.fieldOf("state").forGetter((oreconfiguration_targetblockstate) -> {
                return oreconfiguration_targetblockstate.state;
            })).apply(instance, OreConfiguration.TargetBlockState::new);
        });
        public final RuleTest target;
        public final BlockState state;

        private TargetBlockState(RuleTest target, BlockState state) {
            this.target = target;
            this.state = state;
        }
    }
}
