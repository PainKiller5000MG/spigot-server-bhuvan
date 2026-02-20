package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class RepeaterBlock extends DiodeBlock {

    public static final MapCodec<RepeaterBlock> CODEC = simpleCodec(RepeaterBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final IntegerProperty DELAY = BlockStateProperties.DELAY;

    @Override
    public MapCodec<RepeaterBlock> codec() {
        return RepeaterBlock.CODEC;
    }

    protected RepeaterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(RepeaterBlock.FACING, Direction.NORTH)).setValue(RepeaterBlock.DELAY, 1)).setValue(RepeaterBlock.LOCKED, false)).setValue(RepeaterBlock.POWERED, false));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            level.setBlock(pos, (BlockState) state.cycle(RepeaterBlock.DELAY), 3);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected int getDelay(BlockState state) {
        return (Integer) state.getValue(RepeaterBlock.DELAY) * 2;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = super.getStateForPlacement(context);

        return (BlockState) blockstate.setValue(RepeaterBlock.LOCKED, this.isLocked(context.getLevel(), context.getClickedPos(), blockstate));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !this.canSurviveOn(level, neighbourPos, neighbourState) ? Blocks.AIR.defaultBlockState() : (!level.isClientSide() && directionToNeighbour.getAxis() != ((Direction) state.getValue(RepeaterBlock.FACING)).getAxis() ? (BlockState) state.setValue(RepeaterBlock.LOCKED, this.isLocked(level, pos, state)) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random));
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(level, pos, state) > 0;
    }

    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RepeaterBlock.POWERED)) {
            Direction direction = (Direction) state.getValue(RepeaterBlock.FACING);
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            double d1 = (double) pos.getY() + 0.4D + (random.nextDouble() - 0.5D) * 0.2D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            float f = -5.0F;

            if (random.nextBoolean()) {
                f = (float) ((Integer) state.getValue(RepeaterBlock.DELAY) * 2 - 1);
            }

            f /= 16.0F;
            double d3 = (double) (f * (float) direction.getStepX());
            double d4 = (double) (f * (float) direction.getStepZ());

            level.addParticle(DustParticleOptions.REDSTONE, d0 + d3, d1, d2 + d4, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RepeaterBlock.FACING, RepeaterBlock.DELAY, RepeaterBlock.LOCKED, RepeaterBlock.POWERED);
    }
}
