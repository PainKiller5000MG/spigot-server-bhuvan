package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class AmethystClusterBlock extends AmethystBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<AmethystClusterBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.FLOAT.fieldOf("height").forGetter((amethystclusterblock) -> {
            return amethystclusterblock.height;
        }), Codec.FLOAT.fieldOf("width").forGetter((amethystclusterblock) -> {
            return amethystclusterblock.width;
        }), propertiesCodec()).apply(instance, AmethystClusterBlock::new);
    });
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private final float height;
    private final float width;
    private final Map<Direction, VoxelShape> shapes;

    @Override
    public MapCodec<AmethystClusterBlock> codec() {
        return AmethystClusterBlock.CODEC;
    }

    public AmethystClusterBlock(float height, float width, BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState((BlockState) ((BlockState) this.defaultBlockState().setValue(AmethystClusterBlock.WATERLOGGED, false)).setValue(AmethystClusterBlock.FACING, Direction.UP));
        this.shapes = Shapes.rotateAll(Block.boxZ((double) width, (double) (16.0F - height), 16.0D));
        this.height = height;
        this.width = width;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.get(state.getValue(AmethystClusterBlock.FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = (Direction) state.getValue(AmethystClusterBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction.getOpposite());

        return level.getBlockState(blockpos1).isFaceSturdy(level, blockpos1, direction);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(AmethystClusterBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return directionToNeighbour == ((Direction) state.getValue(AmethystClusterBlock.FACING)).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor levelaccessor = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(AmethystClusterBlock.WATERLOGGED, levelaccessor.getFluidState(blockpos).getType() == Fluids.WATER)).setValue(AmethystClusterBlock.FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(AmethystClusterBlock.FACING, rotation.rotate((Direction) state.getValue(AmethystClusterBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(AmethystClusterBlock.FACING)));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(AmethystClusterBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AmethystClusterBlock.WATERLOGGED, AmethystClusterBlock.FACING);
    }
}
