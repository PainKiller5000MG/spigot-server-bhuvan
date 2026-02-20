package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;

public class LightningRodBlock extends RodBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<LightningRodBlock> CODEC = simpleCodec(LightningRodBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int ACTIVATION_TICKS = 8;
    public static final int RANGE = 128;
    private static final int SPARK_CYCLE = 200;

    @Override
    public MapCodec<? extends LightningRodBlock> codec() {
        return LightningRodBlock.CODEC;
    }

    public LightningRodBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(LightningRodBlock.FACING, Direction.UP)).setValue(LightningRodBlock.WATERLOGGED, false)).setValue(LightningRodBlock.POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(LightningRodBlock.FACING, context.getClickedFace())).setValue(LightningRodBlock.WATERLOGGED, flag);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(LightningRodBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(LightningRodBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LightningRodBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LightningRodBlock.POWERED) && state.getValue(LightningRodBlock.FACING) == direction ? 15 : 0;
    }

    public void onLightningStrike(BlockState state, Level level, BlockPos pos) {
        level.setBlock(pos, (BlockState) state.setValue(LightningRodBlock.POWERED, true), 3);
        this.updateNeighbours(state, level, pos);
        level.scheduleTick(pos, (Block) this, 8);
        level.levelEvent(3002, pos, ((Direction) state.getValue(LightningRodBlock.FACING)).getAxis().ordinal());
    }

    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction direction = ((Direction) state.getValue(LightningRodBlock.FACING)).getOpposite();

        level.updateNeighborsAt(pos.relative(direction), this, ExperimentalRedstoneUtils.initialOrientation(level, direction, (Direction) null));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, (BlockState) state.setValue(LightningRodBlock.POWERED, false), 3);
        this.updateNeighbours(state, level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.isThundering() && (long) level.random.nextInt(200) <= level.getGameTime() % 200L && pos.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1) {
            ParticleUtils.spawnParticlesAlongAxis(((Direction) state.getValue(LightningRodBlock.FACING)).getAxis(), level, pos, 0.125D, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(1, 2));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if ((Boolean) state.getValue(LightningRodBlock.POWERED)) {
            this.updateNeighbours(state, level, pos);
        }

    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!state.is(oldState.getBlock())) {
            if ((Boolean) state.getValue(LightningRodBlock.POWERED) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, (Block) this, 8);
            }

        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LightningRodBlock.FACING, LightningRodBlock.POWERED, LightningRodBlock.WATERLOGGED);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }
}
