package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class RedstoneWallTorchBlock extends RedstoneTorchBlock {

    public static final MapCodec<RedstoneWallTorchBlock> CODEC = simpleCodec(RedstoneWallTorchBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<RedstoneWallTorchBlock> codec() {
        return RedstoneWallTorchBlock.CODEC;
    }

    protected RedstoneWallTorchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(RedstoneWallTorchBlock.FACING, Direction.NORTH)).setValue(RedstoneWallTorchBlock.LIT, true));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WallTorchBlock.getShape(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return WallTorchBlock.canSurvive(level, pos, (Direction) state.getValue(RedstoneWallTorchBlock.FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getOpposite() == state.getValue(RedstoneWallTorchBlock.FACING) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = Blocks.WALL_TORCH.getStateForPlacement(context);

        return blockstate == null ? null : (BlockState) this.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, (Direction) blockstate.getValue(RedstoneWallTorchBlock.FACING));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedstoneWallTorchBlock.LIT)) {
            Direction direction = ((Direction) state.getValue(RedstoneWallTorchBlock.FACING)).getOpposite();
            double d0 = 0.27D;
            double d1 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D + 0.27D * (double) direction.getStepX();
            double d2 = (double) pos.getY() + 0.7D + (random.nextDouble() - 0.5D) * 0.2D + 0.22D;
            double d3 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D + 0.27D * (double) direction.getStepZ();

            level.addParticle(DustParticleOptions.REDSTONE, d1, d2, d3, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean hasNeighborSignal(Level level, BlockPos pos, BlockState state) {
        Direction direction = ((Direction) state.getValue(RedstoneWallTorchBlock.FACING)).getOpposite();

        return level.hasSignal(pos.relative(direction), direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(RedstoneWallTorchBlock.LIT) && state.getValue(RedstoneWallTorchBlock.FACING) != direction ? 15 : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(RedstoneWallTorchBlock.FACING, rotation.rotate((Direction) state.getValue(RedstoneWallTorchBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(RedstoneWallTorchBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedstoneWallTorchBlock.FACING, RedstoneWallTorchBlock.LIT);
    }

    @Override
    protected @Nullable Orientation randomOrientation(Level level, BlockState state) {
        return ExperimentalRedstoneUtils.initialOrientation(level, ((Direction) state.getValue(RedstoneWallTorchBlock.FACING)).getOpposite(), Direction.UP);
    }
}
