package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SlabBlock extends Block implements SimpleWaterloggedBlock {

    public static final MapCodec<SlabBlock> CODEC = simpleCodec(SlabBlock::new);
    public static final EnumProperty<SlabType> TYPE = BlockStateProperties.SLAB_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE_BOTTOM = Block.column(16.0D, 0.0D, 8.0D);
    private static final VoxelShape SHAPE_TOP = Block.column(16.0D, 8.0D, 16.0D);

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return SlabBlock.CODEC;
    }

    public SlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) this.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)).setValue(SlabBlock.WATERLOGGED, false));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SlabBlock.TYPE, SlabBlock.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape;

        switch ((SlabType) state.getValue(SlabBlock.TYPE)) {
            case TOP:
                voxelshape = SlabBlock.SHAPE_TOP;
                break;
            case BOTTOM:
                voxelshape = SlabBlock.SHAPE_BOTTOM;
                break;
            case DOUBLE:
                voxelshape = Shapes.block();
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return voxelshape;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = context.getLevel().getBlockState(blockpos);

        if (blockstate.is(this)) {
            return (BlockState) ((BlockState) blockstate.setValue(SlabBlock.TYPE, SlabType.DOUBLE)).setValue(SlabBlock.WATERLOGGED, false);
        } else {
            FluidState fluidstate = context.getLevel().getFluidState(blockpos);
            BlockState blockstate1 = (BlockState) ((BlockState) this.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)).setValue(SlabBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
            Direction direction = context.getClickedFace();

            return direction != Direction.DOWN && (direction == Direction.UP || context.getClickLocation().y - (double) blockpos.getY() <= 0.5D) ? blockstate1 : (BlockState) blockstate1.setValue(SlabBlock.TYPE, SlabType.TOP);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        ItemStack itemstack = context.getItemInHand();
        SlabType slabtype = (SlabType) state.getValue(SlabBlock.TYPE);

        if (slabtype != SlabType.DOUBLE && itemstack.is(this.asItem())) {
            if (context.replacingClickedOnBlock()) {
                boolean flag = context.getClickLocation().y - (double) context.getClickedPos().getY() > 0.5D;
                Direction direction = context.getClickedFace();

                return slabtype == SlabType.BOTTOM ? direction == Direction.UP || flag && direction.getAxis().isHorizontal() : direction == Direction.DOWN || !flag && direction.getAxis().isHorizontal();
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(SlabBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE ? SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluidState) : false;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
        return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE ? SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, type) : false;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(SlabBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        switch (type) {
            case LAND:
                return false;
            case WATER:
                return state.getFluidState().is(FluidTags.WATER);
            case AIR:
                return false;
            default:
                return false;
        }
    }
}
