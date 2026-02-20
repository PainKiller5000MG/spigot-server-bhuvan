package net.minecraft.world.level.redstone;

import java.util.Locale;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface NeighborUpdater {

    Direction[] UPDATE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    void shapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, @Block.UpdateFlags int updateFlags, int updateLimit);

    void neighborChanged(BlockPos pos, Block changedBlock, @Nullable Orientation orientation);

    void neighborChanged(BlockState state, BlockPos pos, Block changedBlock, @Nullable Orientation orientation, boolean movedByPiston);

    default void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction skipDirection, @Nullable Orientation orientation) {
        for (Direction direction1 : NeighborUpdater.UPDATE_ORDER) {
            if (direction1 != skipDirection) {
                this.neighborChanged(pos.relative(direction1), block, (Orientation) null);
            }
        }

    }

    static void executeShapeUpdate(LevelAccessor level, Direction direction, BlockPos pos, BlockPos neighborPos, BlockState neighborState, @Block.UpdateFlags int updateFlags, int updateLimit) {
        BlockState blockstate1 = level.getBlockState(pos);

        if ((updateFlags & 128) == 0 || !blockstate1.is(Blocks.REDSTONE_WIRE)) {
            BlockState blockstate2 = blockstate1.updateShape(level, level, pos, direction, neighborPos, neighborState, level.getRandom());

            Block.updateOrDestroy(blockstate1, blockstate2, level, pos, updateFlags, updateLimit);
        }
    }

    static void executeUpdate(Level level, BlockState state, BlockPos pos, Block changedBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        try {
            state.handleNeighborChanged(level, pos, changedBlock, orientation, movedByPiston);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while updating neighbours");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being updated");

            crashreportcategory.setDetail("Source block type", () -> {
                try {
                    return String.format(Locale.ROOT, "ID #%s (%s // %s)", BuiltInRegistries.BLOCK.getKey(changedBlock), changedBlock.getDescriptionId(), changedBlock.getClass().getCanonicalName());
                } catch (Throwable throwable1) {
                    return "ID #" + String.valueOf(BuiltInRegistries.BLOCK.getKey(changedBlock));
                }
            });
            CrashReportCategory.populateBlockDetails(crashreportcategory, level, pos, state);
            throw new ReportedException(crashreport);
        }
    }
}
