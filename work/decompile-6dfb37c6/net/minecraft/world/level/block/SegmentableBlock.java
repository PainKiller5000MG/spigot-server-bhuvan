package net.minecraft.world.level.block;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface SegmentableBlock {

    int MIN_SEGMENT = 1;
    int MAX_SEGMENT = 4;
    IntegerProperty AMOUNT = BlockStateProperties.SEGMENT_AMOUNT;

    default Function<BlockState, VoxelShape> getShapeCalculator(EnumProperty<Direction> facing, IntegerProperty amount) {
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.box(0.0D, 0.0D, 0.0D, 8.0D, this.getShapeHeight(), 8.0D));

        return (blockstate) -> {
            VoxelShape voxelshape = Shapes.empty();
            Direction direction = (Direction) blockstate.getValue(facing);
            int i = (Integer) blockstate.getValue(amount);

            for (int j = 0; j < i; ++j) {
                voxelshape = Shapes.or(voxelshape, (VoxelShape) map.get(direction));
                direction = direction.getCounterClockWise();
            }

            return voxelshape.singleEncompassing();
        };
    }

    default IntegerProperty getSegmentAmountProperty() {
        return SegmentableBlock.AMOUNT;
    }

    default double getShapeHeight() {
        return 1.0D;
    }

    default boolean canBeReplaced(BlockState state, BlockPlaceContext context, IntegerProperty segment) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(state.getBlock().asItem()) && (Integer) state.getValue(segment) < 4;
    }

    default BlockState getStateForPlacement(BlockPlaceContext context, Block block, IntegerProperty segment, EnumProperty<Direction> facing) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());

        return blockstate.is(block) ? (BlockState) blockstate.setValue(segment, Math.min(4, (Integer) blockstate.getValue(segment) + 1)) : (BlockState) block.defaultBlockState().setValue(facing, context.getHorizontalDirection().getOpposite());
    }
}
