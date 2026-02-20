package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class MangrovePropaguleBlock extends SaplingBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<MangrovePropaguleBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(TreeGrower.CODEC.fieldOf("tree").forGetter((mangrovepropaguleblock) -> {
            return mangrovepropaguleblock.treeGrower;
        }), propertiesCodec()).apply(instance, MangrovePropaguleBlock::new);
    });
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;
    public static final int MAX_AGE = 4;
    private static final int[] SHAPE_MIN_Y = new int[]{13, 10, 7, 3, 0};
    private static final VoxelShape[] SHAPE_PER_AGE = Block.boxes(4, (i) -> {
        return Block.column(2.0D, (double) MangrovePropaguleBlock.SHAPE_MIN_Y[i], 16.0D);
    });
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;

    @Override
    public MapCodec<MangrovePropaguleBlock> codec() {
        return MangrovePropaguleBlock.CODEC;
    }

    public MangrovePropaguleBlock(TreeGrower treeGrower, BlockBehaviour.Properties properties) {
        super(treeGrower, properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(MangrovePropaguleBlock.STAGE, 0)).setValue(MangrovePropaguleBlock.AGE, 0)).setValue(MangrovePropaguleBlock.WATERLOGGED, false)).setValue(MangrovePropaguleBlock.HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MangrovePropaguleBlock.STAGE).add(MangrovePropaguleBlock.AGE).add(MangrovePropaguleBlock.WATERLOGGED).add(MangrovePropaguleBlock.HANGING);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.CLAY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidstate.getType() == Fluids.WATER;

        return (BlockState) ((BlockState) super.getStateForPlacement(context).setValue(MangrovePropaguleBlock.WATERLOGGED, flag)).setValue(MangrovePropaguleBlock.AGE, 4);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = (Boolean) state.getValue(MangrovePropaguleBlock.HANGING) ? (Integer) state.getValue(MangrovePropaguleBlock.AGE) : 4;

        return MangrovePropaguleBlock.SHAPE_PER_AGE[i].move(state.getOffset(pos));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isHanging(state) ? level.getBlockState(pos.above()).is(Blocks.MANGROVE_LEAVES) : super.canSurvive(state, level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(MangrovePropaguleBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(MangrovePropaguleBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHanging(state)) {
            if (random.nextInt(7) == 0) {
                this.advanceTree(level, pos, state, random);
            }

        } else {
            if (!isFullyGrown(state)) {
                level.setBlock(pos, (BlockState) state.cycle(MangrovePropaguleBlock.AGE), 2);
            }

        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return !isHanging(state) || !isFullyGrown(state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return isHanging(state) ? !isFullyGrown(state) : super.isBonemealSuccess(level, random, pos, state);
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (isHanging(state) && !isFullyGrown(state)) {
            level.setBlock(pos, (BlockState) state.cycle(MangrovePropaguleBlock.AGE), 2);
        } else {
            super.performBonemeal(level, random, pos, state);
        }

    }

    private static boolean isHanging(BlockState state) {
        return (Boolean) state.getValue(MangrovePropaguleBlock.HANGING);
    }

    private static boolean isFullyGrown(BlockState state) {
        return (Integer) state.getValue(MangrovePropaguleBlock.AGE) == 4;
    }

    public static BlockState createNewHangingPropagule() {
        return createNewHangingPropagule(0);
    }

    public static BlockState createNewHangingPropagule(int age) {
        return (BlockState) ((BlockState) Blocks.MANGROVE_PROPAGULE.defaultBlockState().setValue(MangrovePropaguleBlock.HANGING, true)).setValue(MangrovePropaguleBlock.AGE, age);
    }
}
