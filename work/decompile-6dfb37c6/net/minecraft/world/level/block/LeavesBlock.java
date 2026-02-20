package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class LeavesBlock extends Block implements SimpleWaterloggedBlock {

    public static final int DECAY_DISTANCE = 7;
    public static final IntegerProperty DISTANCE = BlockStateProperties.DISTANCE;
    public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected final float leafParticleChance;
    private static final int TICK_DELAY = 1;
    private static boolean cutoutLeaves = true;

    @Override
    public abstract MapCodec<? extends LeavesBlock> codec();

    public LeavesBlock(float leafParticleChance, BlockBehaviour.Properties properties) {
        super(properties);
        this.leafParticleChance = leafParticleChance;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(LeavesBlock.DISTANCE, 7)).setValue(LeavesBlock.PERSISTENT, false)).setValue(LeavesBlock.WATERLOGGED, false));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return !LeavesBlock.cutoutLeaves && neighborState.getBlock() instanceof LeavesBlock ? true : super.skipRendering(state, neighborState, direction);
    }

    public static void setCutoutLeaves(boolean cutoutLeaves) {
        LeavesBlock.cutoutLeaves = cutoutLeaves;
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(LeavesBlock.DISTANCE) == 7 && !(Boolean) state.getValue(LeavesBlock.PERSISTENT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.decaying(state)) {
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }

    }

    protected boolean decaying(BlockState state) {
        return !(Boolean) state.getValue(LeavesBlock.PERSISTENT) && (Integer) state.getValue(LeavesBlock.DISTANCE) == 7;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, updateDistance(state, level, pos), 3);
    }

    @Override
    protected int getLightBlock(BlockState state) {
        return 1;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(LeavesBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        int i = getDistanceAt(neighbourState) + 1;

        if (i != 1 || (Integer) state.getValue(LeavesBlock.DISTANCE) != i) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return state;
    }

    private static BlockState updateDistance(BlockState state, LevelAccessor level, BlockPos pos) {
        int i = 7;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.values()) {
            blockpos_mutableblockpos.setWithOffset(pos, direction);
            i = Math.min(i, getDistanceAt(level.getBlockState(blockpos_mutableblockpos)) + 1);
            if (i == 1) {
                break;
            }
        }

        return (BlockState) state.setValue(LeavesBlock.DISTANCE, i);
    }

    private static int getDistanceAt(BlockState state) {
        return getOptionalDistanceAt(state).orElse(7);
    }

    public static OptionalInt getOptionalDistanceAt(BlockState state) {
        return state.is(BlockTags.LOGS) ? OptionalInt.of(0) : (state.hasProperty(LeavesBlock.DISTANCE) ? OptionalInt.of((Integer) state.getValue(LeavesBlock.DISTANCE)) : OptionalInt.empty());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(LeavesBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        makeDrippingWaterParticles(level, pos, random, blockstate1, blockpos1);
        this.makeFallingLeavesParticles(level, pos, random, blockstate1, blockpos1);
    }

    private static void makeDrippingWaterParticles(Level level, BlockPos pos, RandomSource random, BlockState belowState, BlockPos below) {
        if (level.isRainingAt(pos.above())) {
            if (random.nextInt(15) == 1) {
                if (!belowState.canOcclude() || !belowState.isFaceSturdy(level, below, Direction.UP)) {
                    ParticleUtils.spawnParticleBelow(level, pos, random, ParticleTypes.DRIPPING_WATER);
                }
            }
        }
    }

    private void makeFallingLeavesParticles(Level level, BlockPos pos, RandomSource random, BlockState belowState, BlockPos below) {
        if (random.nextFloat() < this.leafParticleChance) {
            if (!isFaceFull(belowState.getCollisionShape(level, below), Direction.UP)) {
                this.spawnFallingLeavesParticle(level, pos, random);
            }
        }
    }

    protected abstract void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LeavesBlock.DISTANCE, LeavesBlock.PERSISTENT, LeavesBlock.WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        BlockState blockstate = (BlockState) ((BlockState) this.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)).setValue(LeavesBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);

        return updateDistance(blockstate, context.getLevel(), context.getClickedPos());
    }
}
