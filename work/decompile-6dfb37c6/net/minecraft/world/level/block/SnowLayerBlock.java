package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SnowLayerBlock extends Block {

    public static final MapCodec<SnowLayerBlock> CODEC = simpleCodec(SnowLayerBlock::new);
    public static final int MAX_HEIGHT = 8;
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = Block.boxes(8, (i) -> {
        return Block.column(16.0D, 0.0D, (double) (i * 2));
    });
    public static final int HEIGHT_IMPASSABLE = 5;

    @Override
    public MapCodec<SnowLayerBlock> codec() {
        return SnowLayerBlock.CODEC;
    }

    protected SnowLayerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(SnowLayerBlock.LAYERS, 1));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND ? (Integer) state.getValue(SnowLayerBlock.LAYERS) < 5 : false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SnowLayerBlock.SHAPES[(Integer) state.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SnowLayerBlock.SHAPES[(Integer) state.getValue(SnowLayerBlock.LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SnowLayerBlock.SHAPES[(Integer) state.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SnowLayerBlock.SHAPES[(Integer) state.getValue(SnowLayerBlock.LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return (Integer) state.getValue(SnowLayerBlock.LAYERS) == 8 ? 0.2F : 1.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.below());

        return blockstate1.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON) ? false : (blockstate1.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON) ? true : Block.isFaceFull(blockstate1.getCollisionShape(level, pos.below()), Direction.UP) || blockstate1.is(this) && (Integer) blockstate1.getValue(SnowLayerBlock.LAYERS) == 8);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 11) {
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }

    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int i = (Integer) state.getValue(SnowLayerBlock.LAYERS);

        return context.getItemInHand().is(this.asItem()) && i < 8 ? (context.replacingClickedOnBlock() ? context.getClickedFace() == Direction.UP : true) : i == 1;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());

        if (blockstate.is(this)) {
            int i = (Integer) blockstate.getValue(SnowLayerBlock.LAYERS);

            return (BlockState) blockstate.setValue(SnowLayerBlock.LAYERS, Math.min(8, i + 1));
        } else {
            return super.getStateForPlacement(context);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SnowLayerBlock.LAYERS);
    }
}
