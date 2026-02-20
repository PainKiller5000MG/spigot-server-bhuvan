package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class StainedGlassPaneBlock extends IronBarsBlock implements BeaconBeamBlock {

    public static final MapCodec<StainedGlassPaneBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(DyeColor.CODEC.fieldOf("color").forGetter(StainedGlassPaneBlock::getColor), propertiesCodec()).apply(instance, StainedGlassPaneBlock::new);
    });
    private final DyeColor color;

    @Override
    public MapCodec<StainedGlassPaneBlock> codec() {
        return StainedGlassPaneBlock.CODEC;
    }

    public StainedGlassPaneBlock(DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(StainedGlassPaneBlock.NORTH, false)).setValue(StainedGlassPaneBlock.EAST, false)).setValue(StainedGlassPaneBlock.SOUTH, false)).setValue(StainedGlassPaneBlock.WEST, false)).setValue(StainedGlassPaneBlock.WATERLOGGED, false));
    }

    @Override
    public DyeColor getColor() {
        return this.color;
    }
}
