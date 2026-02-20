package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IronBarsBlock extends CrossCollisionBlock {

    public static final MapCodec<IronBarsBlock> CODEC = simpleCodec(IronBarsBlock::new);

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return IronBarsBlock.CODEC;
    }

    protected IronBarsBlock(BlockBehaviour.Properties properties) {
        super(2.0F, 16.0F, 2.0F, 16.0F, 16.0F, properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(IronBarsBlock.NORTH, false)).setValue(IronBarsBlock.EAST, false)).setValue(IronBarsBlock.SOUTH, false)).setValue(IronBarsBlock.WEST, false)).setValue(IronBarsBlock.WATERLOGGED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter blockgetter = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockpos1 = blockpos.north();
        BlockPos blockpos2 = blockpos.south();
        BlockPos blockpos3 = blockpos.west();
        BlockPos blockpos4 = blockpos.east();
        BlockState blockstate = blockgetter.getBlockState(blockpos1);
        BlockState blockstate1 = blockgetter.getBlockState(blockpos2);
        BlockState blockstate2 = blockgetter.getBlockState(blockpos3);
        BlockState blockstate3 = blockgetter.getBlockState(blockpos4);

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(IronBarsBlock.NORTH, this.attachsTo(blockstate, blockstate.isFaceSturdy(blockgetter, blockpos1, Direction.SOUTH)))).setValue(IronBarsBlock.SOUTH, this.attachsTo(blockstate1, blockstate1.isFaceSturdy(blockgetter, blockpos2, Direction.NORTH)))).setValue(IronBarsBlock.WEST, this.attachsTo(blockstate2, blockstate2.isFaceSturdy(blockgetter, blockpos3, Direction.EAST)))).setValue(IronBarsBlock.EAST, this.attachsTo(blockstate3, blockstate3.isFaceSturdy(blockgetter, blockpos4, Direction.WEST)))).setValue(IronBarsBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(IronBarsBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour.getAxis().isHorizontal() ? (BlockState) state.setValue((Property) IronBarsBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), this.attachsTo(neighbourState, neighbourState.isFaceSturdy(level, neighbourPos, directionToNeighbour.getOpposite()))) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        if (neighborState.is(this) || neighborState.is(BlockTags.BARS) && state.is(BlockTags.BARS) && neighborState.hasProperty((Property) IronBarsBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))) {
            if (!direction.getAxis().isHorizontal()) {
                return true;
            }

            if ((Boolean) state.getValue((Property) IronBarsBlock.PROPERTY_BY_DIRECTION.get(direction)) && (Boolean) neighborState.getValue((Property) IronBarsBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))) {
                return true;
            }
        }

        return super.skipRendering(state, neighborState, direction);
    }

    public final boolean attachsTo(BlockState state, boolean faceSolid) {
        return !isExceptionForConnection(state) && faceSolid || state.getBlock() instanceof IronBarsBlock || state.is(BlockTags.WALLS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IronBarsBlock.NORTH, IronBarsBlock.EAST, IronBarsBlock.WEST, IronBarsBlock.SOUTH, IronBarsBlock.WATERLOGGED);
    }
}
