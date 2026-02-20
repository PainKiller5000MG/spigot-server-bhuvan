package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BigDripleafStemBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final MapCodec<BigDripleafStemBlock> CODEC = simpleCodec(BigDripleafStemBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.column(6.0D, 0.0D, 16.0D).move(0.0D, 0.0D, 0.25D).optimize());

    @Override
    public MapCodec<BigDripleafStemBlock> codec() {
        return BigDripleafStemBlock.CODEC;
    }

    protected BigDripleafStemBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BigDripleafStemBlock.WATERLOGGED, false)).setValue(BigDripleafStemBlock.FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) BigDripleafStemBlock.SHAPES.get(state.getValue(BigDripleafStemBlock.FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BigDripleafStemBlock.WATERLOGGED, BigDripleafStemBlock.FACING);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(BigDripleafStemBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);
        BlockState blockstate2 = level.getBlockState(pos.above());

        return (blockstate1.is(this) || blockstate1.is(BlockTags.BIG_DRIPLEAF_PLACEABLE)) && (blockstate2.is(this) || blockstate2.is(Blocks.BIG_DRIPLEAF));
    }

    protected static boolean place(LevelAccessor level, BlockPos pos, FluidState fluidState, Direction facing) {
        BlockState blockstate = (BlockState) ((BlockState) Blocks.BIG_DRIPLEAF_STEM.defaultBlockState().setValue(BigDripleafStemBlock.WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))).setValue(BigDripleafStemBlock.FACING, facing);

        return level.setBlock(pos, blockstate, 3);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((directionToNeighbour == Direction.DOWN || directionToNeighbour == Direction.UP) && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        if ((Boolean) state.getValue(BigDripleafStemBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = BlockUtil.getTopConnectedBlock(level, pos, state.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);

        if (optional.isEmpty()) {
            return false;
        } else {
            BlockPos blockpos1 = ((BlockPos) optional.get()).above();
            BlockState blockstate1 = level.getBlockState(blockpos1);

            return BigDripleafBlock.canPlaceAt(level, blockpos1, blockstate1);
        }
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        Optional<BlockPos> optional = BlockUtil.getTopConnectedBlock(level, pos, state.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);

        if (!optional.isEmpty()) {
            BlockPos blockpos1 = (BlockPos) optional.get();
            BlockPos blockpos2 = blockpos1.above();
            Direction direction = (Direction) state.getValue(BigDripleafStemBlock.FACING);

            place(level, blockpos1, level.getFluidState(blockpos1), direction);
            BigDripleafBlock.place(level, blockpos2, level.getFluidState(blockpos2), direction);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Blocks.BIG_DRIPLEAF);
    }
}
