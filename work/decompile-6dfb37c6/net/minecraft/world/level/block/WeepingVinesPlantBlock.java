package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WeepingVinesPlantBlock extends GrowingPlantBodyBlock {

    public static final MapCodec<WeepingVinesPlantBlock> CODEC = simpleCodec(WeepingVinesPlantBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<WeepingVinesPlantBlock> codec() {
        return WeepingVinesPlantBlock.CODEC;
    }

    public WeepingVinesPlantBlock(BlockBehaviour.Properties properties) {
        super(properties, Direction.DOWN, WeepingVinesPlantBlock.SHAPE, false);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return (GrowingPlantHeadBlock) Blocks.WEEPING_VINES;
    }
}
