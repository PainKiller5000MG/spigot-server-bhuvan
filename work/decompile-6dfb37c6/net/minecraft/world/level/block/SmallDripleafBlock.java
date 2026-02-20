package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SmallDripleafBlock extends DoublePlantBlock implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final MapCodec<SmallDripleafBlock> CODEC = simpleCodec(SmallDripleafBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.column(12.0D, 0.0D, 13.0D);

    @Override
    public MapCodec<SmallDripleafBlock> codec() {
        return SmallDripleafBlock.CODEC;
    }

    public SmallDripleafBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.LOWER)).setValue(SmallDripleafBlock.WATERLOGGED, false)).setValue(SmallDripleafBlock.FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SmallDripleafBlock.SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SMALL_DRIPLEAF_PLACEABLE) || level.getFluidState(pos.above()).isSourceOfType(Fluids.WATER) && super.mayPlaceOn(state, level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = super.getStateForPlacement(context);

        return blockstate != null ? copyWaterloggedFrom(context.getLevel(), context.getClickedPos(), (BlockState) blockstate.setValue(SmallDripleafBlock.FACING, context.getHorizontalDirection().getOpposite())) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!level.isClientSide()) {
            BlockPos blockpos1 = pos.above();
            BlockState blockstate1 = DoublePlantBlock.copyWaterloggedFrom(level, blockpos1, (BlockState) ((BlockState) this.defaultBlockState().setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.UPPER)).setValue(SmallDripleafBlock.FACING, (Direction) state.getValue(SmallDripleafBlock.FACING)));

            level.setBlock(blockpos1, blockstate1, 3);
        }

    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SmallDripleafBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(SmallDripleafBlock.HALF) == DoubleBlockHalf.UPPER) {
            return super.canSurvive(state, level, pos);
        } else {
            BlockPos blockpos1 = pos.below();
            BlockState blockstate1 = level.getBlockState(blockpos1);

            return this.mayPlaceOn(blockstate1, level, blockpos1);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(SmallDripleafBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SmallDripleafBlock.HALF, SmallDripleafBlock.WATERLOGGED, SmallDripleafBlock.FACING);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos blockpos1 = pos.above();

            level.setBlock(blockpos1, level.getFluidState(blockpos1).createLegacyBlock(), 18);
            BigDripleafBlock.placeWithRandomHeight(level, random, pos, (Direction) state.getValue(SmallDripleafBlock.FACING));
        } else {
            BlockPos blockpos2 = pos.below();

            this.performBonemeal(level, random, blockpos2, level.getBlockState(blockpos2));
        }

    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(SmallDripleafBlock.FACING, rotation.rotate((Direction) state.getValue(SmallDripleafBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(SmallDripleafBlock.FACING)));
    }

    @Override
    protected float getMaxVerticalOffset() {
        return 0.1F;
    }
}
