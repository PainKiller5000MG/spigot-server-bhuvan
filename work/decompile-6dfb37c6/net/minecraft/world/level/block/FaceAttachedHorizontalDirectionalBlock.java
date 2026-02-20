package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

public abstract class FaceAttachedHorizontalDirectionalBlock extends HorizontalDirectionalBlock {

    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    protected FaceAttachedHorizontalDirectionalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec();

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttach(level, pos, getConnectedDirection(state).getOpposite());
    }

    public static boolean canAttach(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos blockpos1 = pos.relative(direction);

        return level.getBlockState(blockpos1).isFaceSturdy(level, blockpos1, direction.getOpposite());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState blockstate;

            if (direction.getAxis() == Direction.Axis.Y) {
                blockstate = (BlockState) ((BlockState) this.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR)).setValue(FaceAttachedHorizontalDirectionalBlock.FACING, context.getHorizontalDirection());
            } else {
                blockstate = (BlockState) ((BlockState) this.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)).setValue(FaceAttachedHorizontalDirectionalBlock.FACING, direction.getOpposite());
            }

            if (blockstate.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockstate;
            }
        }

        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return getConnectedDirection(state).getOpposite() == directionToNeighbour && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    protected static Direction getConnectedDirection(BlockState state) {
        switch ((AttachFace) state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
            case CEILING:
                return Direction.DOWN;
            case FLOOR:
                return Direction.UP;
            default:
                return (Direction) state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);
        }
    }
}
