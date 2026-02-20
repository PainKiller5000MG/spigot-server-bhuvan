package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class EndRodBlock extends RodBlock {

    public static final MapCodec<EndRodBlock> CODEC = simpleCodec(EndRodBlock::new);

    @Override
    public MapCodec<EndRodBlock> codec() {
        return EndRodBlock.CODEC;
    }

    protected EndRodBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(EndRodBlock.FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos().relative(direction.getOpposite()));

        return blockstate.is(this) && blockstate.getValue(EndRodBlock.FACING) == direction ? (BlockState) this.defaultBlockState().setValue(EndRodBlock.FACING, direction.getOpposite()) : (BlockState) this.defaultBlockState().setValue(EndRodBlock.FACING, direction);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction direction = (Direction) state.getValue(EndRodBlock.FACING);
        double d0 = (double) pos.getX() + 0.55D - (double) (random.nextFloat() * 0.1F);
        double d1 = (double) pos.getY() + 0.55D - (double) (random.nextFloat() * 0.1F);
        double d2 = (double) pos.getZ() + 0.55D - (double) (random.nextFloat() * 0.1F);
        double d3 = (double) (0.4F - (random.nextFloat() + random.nextFloat()) * 0.4F);

        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.END_ROD, d0 + (double) direction.getStepX() * d3, d1 + (double) direction.getStepY() * d3, d2 + (double) direction.getStepZ() * d3, random.nextGaussian() * 0.005D, random.nextGaussian() * 0.005D, random.nextGaussian() * 0.005D);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EndRodBlock.FACING);
    }
}
