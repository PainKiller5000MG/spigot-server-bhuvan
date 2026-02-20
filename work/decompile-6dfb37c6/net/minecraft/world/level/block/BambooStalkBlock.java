package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BambooStalkBlock extends Block implements BonemealableBlock {

    public static final MapCodec<BambooStalkBlock> CODEC = simpleCodec(BambooStalkBlock::new);
    private static final VoxelShape SHAPE_SMALL = Block.column(6.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_LARGE = Block.column(10.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_COLLISION = Block.column(3.0D, 0.0D, 16.0D);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_1;
    public static final EnumProperty<BambooLeaves> LEAVES = BlockStateProperties.BAMBOO_LEAVES;
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;
    public static final int MAX_HEIGHT = 16;
    public static final int STAGE_GROWING = 0;
    public static final int STAGE_DONE_GROWING = 1;
    public static final int AGE_THIN_BAMBOO = 0;
    public static final int AGE_THICK_BAMBOO = 1;

    @Override
    public MapCodec<BambooStalkBlock> codec() {
        return BambooStalkBlock.CODEC;
    }

    public BambooStalkBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BambooStalkBlock.AGE, 0)).setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE)).setValue(BambooStalkBlock.STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BambooStalkBlock.AGE, BambooStalkBlock.LEAVES, BambooStalkBlock.STAGE);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape = state.getValue(BambooStalkBlock.LEAVES) == BambooLeaves.LARGE ? BambooStalkBlock.SHAPE_LARGE : BambooStalkBlock.SHAPE_SMALL;

        return voxelshape.move(state.getOffset(pos));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BambooStalkBlock.SHAPE_COLLISION.move(state.getOffset(pos));
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());

        if (!fluidstate.isEmpty()) {
            return null;
        } else {
            BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos().below());

            if (blockstate.is(BlockTags.BAMBOO_PLANTABLE_ON)) {
                if (blockstate.is(Blocks.BAMBOO_SAPLING)) {
                    return (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, 0);
                } else if (blockstate.is(Blocks.BAMBOO)) {
                    int i = (Integer) blockstate.getValue(BambooStalkBlock.AGE) > 0 ? 1 : 0;

                    return (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, i);
                } else {
                    BlockState blockstate1 = context.getLevel().getBlockState(context.getClickedPos().above());

                    return blockstate1.is(Blocks.BAMBOO) ? (BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, (Integer) blockstate1.getValue(BambooStalkBlock.AGE)) : Blocks.BAMBOO_SAPLING.defaultBlockState();
                }
            } else {
                return null;
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return (Integer) state.getValue(BambooStalkBlock.STAGE) == 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(BambooStalkBlock.STAGE) == 0) {
            if (random.nextInt(3) == 0 && level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) {
                int i = this.getHeightBelowUpToMax(level, pos) + 1;

                if (i < 16) {
                    this.growBamboo(state, level, pos, random, i);
                }
            }

        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.BAMBOO_PLANTABLE_ON);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return directionToNeighbour == Direction.UP && neighbourState.is(Blocks.BAMBOO) && (Integer) neighbourState.getValue(BambooStalkBlock.AGE) > (Integer) state.getValue(BambooStalkBlock.AGE) ? (BlockState) state.cycle(BambooStalkBlock.AGE) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        int i = this.getHeightAboveUpToMax(level, pos);
        int j = this.getHeightBelowUpToMax(level, pos);

        return i + j + 1 < 16 && (Integer) level.getBlockState(pos.above(i)).getValue(BambooStalkBlock.STAGE) != 1;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = this.getHeightAboveUpToMax(level, pos);
        int j = this.getHeightBelowUpToMax(level, pos);
        int k = i + j + 1;
        int l = 1 + random.nextInt(2);

        for (int i1 = 0; i1 < l; ++i1) {
            BlockPos blockpos1 = pos.above(i);
            BlockState blockstate1 = level.getBlockState(blockpos1);

            if (k >= 16 || (Integer) blockstate1.getValue(BambooStalkBlock.STAGE) == 1 || !level.isEmptyBlock(blockpos1.above())) {
                return;
            }

            this.growBamboo(blockstate1, level, blockpos1, random, k);
            ++i;
            ++k;
        }

    }

    protected void growBamboo(BlockState state, Level level, BlockPos pos, RandomSource random, int height) {
        BlockState blockstate1 = level.getBlockState(pos.below());
        BlockPos blockpos1 = pos.below(2);
        BlockState blockstate2 = level.getBlockState(blockpos1);
        BambooLeaves bambooleaves = BambooLeaves.NONE;

        if (height >= 1) {
            if (blockstate1.is(Blocks.BAMBOO) && blockstate1.getValue(BambooStalkBlock.LEAVES) != BambooLeaves.NONE) {
                if (blockstate1.is(Blocks.BAMBOO) && blockstate1.getValue(BambooStalkBlock.LEAVES) != BambooLeaves.NONE) {
                    bambooleaves = BambooLeaves.LARGE;
                    if (blockstate2.is(Blocks.BAMBOO)) {
                        level.setBlock(pos.below(), (BlockState) blockstate1.setValue(BambooStalkBlock.LEAVES, BambooLeaves.SMALL), 3);
                        level.setBlock(blockpos1, (BlockState) blockstate2.setValue(BambooStalkBlock.LEAVES, BambooLeaves.NONE), 3);
                    }
                }
            } else {
                bambooleaves = BambooLeaves.SMALL;
            }
        }

        int j = (Integer) state.getValue(BambooStalkBlock.AGE) != 1 && !blockstate2.is(Blocks.BAMBOO) ? 0 : 1;
        int k = (height < 11 || random.nextFloat() >= 0.25F) && height != 15 ? 0 : 1;

        level.setBlock(pos.above(), (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(BambooStalkBlock.AGE, j)).setValue(BambooStalkBlock.LEAVES, bambooleaves)).setValue(BambooStalkBlock.STAGE, k), 3);
    }

    protected int getHeightAboveUpToMax(BlockGetter level, BlockPos pos) {
        int i;

        for (i = 0; i < 16 && level.getBlockState(pos.above(i + 1)).is(Blocks.BAMBOO); ++i) {
            ;
        }

        return i;
    }

    protected int getHeightBelowUpToMax(BlockGetter level, BlockPos pos) {
        int i;

        for (i = 0; i < 16 && level.getBlockState(pos.below(i + 1)).is(Blocks.BAMBOO); ++i) {
            ;
        }

        return i;
    }
}
