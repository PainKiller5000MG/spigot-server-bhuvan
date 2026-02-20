package net.minecraft.world.level.block;

import com.google.common.base.Predicates;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class EndPortalFrameBlock extends Block {

    public static final MapCodec<EndPortalFrameBlock> CODEC = simpleCodec(EndPortalFrameBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_EYE = BlockStateProperties.EYE;
    private static final VoxelShape SHAPE_EMPTY = Block.column(16.0D, 0.0D, 13.0D);
    private static final VoxelShape SHAPE_FULL = Shapes.or(EndPortalFrameBlock.SHAPE_EMPTY, Block.column(8.0D, 13.0D, 16.0D));
    private static @Nullable BlockPattern portalShape;

    @Override
    public MapCodec<EndPortalFrameBlock> codec() {
        return EndPortalFrameBlock.CODEC;
    }

    public EndPortalFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(EndPortalFrameBlock.FACING, Direction.NORTH)).setValue(EndPortalFrameBlock.HAS_EYE, false));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(EndPortalFrameBlock.HAS_EYE) ? EndPortalFrameBlock.SHAPE_FULL : EndPortalFrameBlock.SHAPE_EMPTY;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) ((BlockState) this.defaultBlockState().setValue(EndPortalFrameBlock.FACING, context.getHorizontalDirection().getOpposite())).setValue(EndPortalFrameBlock.HAS_EYE, false);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(EndPortalFrameBlock.HAS_EYE) ? 15 : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(EndPortalFrameBlock.FACING, rotation.rotate((Direction) state.getValue(EndPortalFrameBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(EndPortalFrameBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EndPortalFrameBlock.FACING, EndPortalFrameBlock.HAS_EYE);
    }

    public static BlockPattern getOrCreatePortalShape() {
        if (EndPortalFrameBlock.portalShape == null) {
            EndPortalFrameBlock.portalShape = BlockPatternBuilder.start().aisle("?vvv?", ">???<", ">???<", ">???<", "?^^^?").where('?', BlockInWorld.hasState(BlockStatePredicate.ANY)).where('^', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME).where(EndPortalFrameBlock.HAS_EYE, Predicates.equalTo(true)).where(EndPortalFrameBlock.FACING, Predicates.equalTo(Direction.SOUTH)))).where('>', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME).where(EndPortalFrameBlock.HAS_EYE, Predicates.equalTo(true)).where(EndPortalFrameBlock.FACING, Predicates.equalTo(Direction.WEST)))).where('v', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME).where(EndPortalFrameBlock.HAS_EYE, Predicates.equalTo(true)).where(EndPortalFrameBlock.FACING, Predicates.equalTo(Direction.NORTH)))).where('<', BlockInWorld.hasState(BlockStatePredicate.forBlock(Blocks.END_PORTAL_FRAME).where(EndPortalFrameBlock.HAS_EYE, Predicates.equalTo(true)).where(EndPortalFrameBlock.FACING, Predicates.equalTo(Direction.EAST)))).build();
        }

        return EndPortalFrameBlock.portalShape;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
