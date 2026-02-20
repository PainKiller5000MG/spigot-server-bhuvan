package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class GeodeBlockSettings {

    public final BlockStateProvider fillingProvider;
    public final BlockStateProvider innerLayerProvider;
    public final BlockStateProvider alternateInnerLayerProvider;
    public final BlockStateProvider middleLayerProvider;
    public final BlockStateProvider outerLayerProvider;
    public final List<BlockState> innerPlacements;
    public final TagKey<Block> cannotReplace;
    public final TagKey<Block> invalidBlocks;
    public static final Codec<GeodeBlockSettings> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BlockStateProvider.CODEC.fieldOf("filling_provider").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.fillingProvider;
        }), BlockStateProvider.CODEC.fieldOf("inner_layer_provider").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.innerLayerProvider;
        }), BlockStateProvider.CODEC.fieldOf("alternate_inner_layer_provider").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.alternateInnerLayerProvider;
        }), BlockStateProvider.CODEC.fieldOf("middle_layer_provider").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.middleLayerProvider;
        }), BlockStateProvider.CODEC.fieldOf("outer_layer_provider").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.outerLayerProvider;
        }), ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("inner_placements").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.innerPlacements;
        }), TagKey.hashedCodec(Registries.BLOCK).fieldOf("cannot_replace").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.cannotReplace;
        }), TagKey.hashedCodec(Registries.BLOCK).fieldOf("invalid_blocks").forGetter((geodeblocksettings) -> {
            return geodeblocksettings.invalidBlocks;
        })).apply(instance, GeodeBlockSettings::new);
    });

    public GeodeBlockSettings(BlockStateProvider fillingProvider, BlockStateProvider innerLayerProvider, BlockStateProvider alternateInnerLayerProvider, BlockStateProvider middleLayerProvider, BlockStateProvider outerLayerProvider, List<BlockState> innerPlacements, TagKey<Block> cannotReplace, TagKey<Block> invalidBlocks) {
        this.fillingProvider = fillingProvider;
        this.innerLayerProvider = innerLayerProvider;
        this.alternateInnerLayerProvider = alternateInnerLayerProvider;
        this.middleLayerProvider = middleLayerProvider;
        this.outerLayerProvider = outerLayerProvider;
        this.innerPlacements = innerPlacements;
        this.cannotReplace = cannotReplace;
        this.invalidBlocks = invalidBlocks;
    }
}
