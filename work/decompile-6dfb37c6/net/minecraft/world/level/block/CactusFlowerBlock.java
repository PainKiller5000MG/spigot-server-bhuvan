package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusFlowerBlock extends VegetationBlock {

    public static final MapCodec<CactusFlowerBlock> CODEC = simpleCodec(CactusFlowerBlock::new);
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 12.0D);

    @Override
    public MapCodec<? extends CactusFlowerBlock> codec() {
        return CactusFlowerBlock.CODEC;
    }

    public CactusFlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CactusFlowerBlock.SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos);

        return blockstate1.is(Blocks.CACTUS) || blockstate1.is(Blocks.FARMLAND) || blockstate1.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER);
    }
}
