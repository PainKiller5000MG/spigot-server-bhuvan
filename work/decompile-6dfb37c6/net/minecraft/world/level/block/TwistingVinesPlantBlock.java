package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TwistingVinesPlantBlock extends GrowingPlantBodyBlock {

    public static final MapCodec<TwistingVinesPlantBlock> CODEC = simpleCodec(TwistingVinesPlantBlock::new);
    private static final VoxelShape SHAPE = Block.column(8.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<TwistingVinesPlantBlock> codec() {
        return TwistingVinesPlantBlock.CODEC;
    }

    public TwistingVinesPlantBlock(BlockBehaviour.Properties properties) {
        super(properties, Direction.UP, TwistingVinesPlantBlock.SHAPE, false);
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return (GrowingPlantHeadBlock) Blocks.TWISTING_VINES;
    }
}
