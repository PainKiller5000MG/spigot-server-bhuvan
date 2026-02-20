package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

public class DeltaFeatureConfiguration implements FeatureConfiguration {

    public static final Codec<DeltaFeatureConfiguration> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BlockState.CODEC.fieldOf("contents").forGetter((deltafeatureconfiguration) -> {
            return deltafeatureconfiguration.contents;
        }), BlockState.CODEC.fieldOf("rim").forGetter((deltafeatureconfiguration) -> {
            return deltafeatureconfiguration.rim;
        }), IntProvider.codec(0, 16).fieldOf("size").forGetter((deltafeatureconfiguration) -> {
            return deltafeatureconfiguration.size;
        }), IntProvider.codec(0, 16).fieldOf("rim_size").forGetter((deltafeatureconfiguration) -> {
            return deltafeatureconfiguration.rimSize;
        })).apply(instance, DeltaFeatureConfiguration::new);
    });
    private final BlockState contents;
    private final BlockState rim;
    private final IntProvider size;
    private final IntProvider rimSize;

    public DeltaFeatureConfiguration(BlockState contents, BlockState rim, IntProvider size, IntProvider rimSize) {
        this.contents = contents;
        this.rim = rim;
        this.size = size;
        this.rimSize = rimSize;
    }

    public BlockState contents() {
        return this.contents;
    }

    public BlockState rim() {
        return this.rim;
    }

    public IntProvider size() {
        return this.size;
    }

    public IntProvider rimSize() {
        return this.rimSize;
    }
}
