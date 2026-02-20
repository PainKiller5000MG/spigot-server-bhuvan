package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

public class ReplaceSphereConfiguration implements FeatureConfiguration {

    public static final Codec<ReplaceSphereConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BlockState.CODEC.fieldOf("target").forGetter((replacesphereconfiguration) -> {
            return replacesphereconfiguration.targetState;
        }), BlockState.CODEC.fieldOf("state").forGetter((replacesphereconfiguration) -> {
            return replacesphereconfiguration.replaceState;
        }), IntProvider.codec(0, 12).fieldOf("radius").forGetter((replacesphereconfiguration) -> {
            return replacesphereconfiguration.radius;
        })).apply(instance, ReplaceSphereConfiguration::new);
    });
    public final BlockState targetState;
    public final BlockState replaceState;
    private final IntProvider radius;

    public ReplaceSphereConfiguration(BlockState targetState, BlockState replaceState, IntProvider radius) {
        this.targetState = targetState;
        this.replaceState = replaceState;
        this.radius = radius;
    }

    public IntProvider radius() {
        return this.radius;
    }
}
