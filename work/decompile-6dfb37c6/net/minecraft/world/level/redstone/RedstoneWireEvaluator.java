package net.minecraft.world.level.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class RedstoneWireEvaluator {

    protected final RedStoneWireBlock wireBlock;

    protected RedstoneWireEvaluator(RedStoneWireBlock wireBlock) {
        this.wireBlock = wireBlock;
    }

    public abstract void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean skipShapeUpdates);

    protected int getBlockSignal(Level level, BlockPos pos) {
        return this.wireBlock.getBlockSignal(level, pos);
    }

    protected int getWireSignal(BlockPos pos, BlockState state) {
        return state.is(this.wireBlock) ? (Integer) state.getValue(RedStoneWireBlock.POWER) : 0;
    }

    protected int getIncomingWireSignal(Level level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);
            BlockState blockstate = level.getBlockState(blockpos1);

            i = Math.max(i, this.getWireSignal(blockpos1, blockstate));
            BlockPos blockpos2 = pos.above();

            if (blockstate.isRedstoneConductor(level, blockpos1) && !level.getBlockState(blockpos2).isRedstoneConductor(level, blockpos2)) {
                BlockPos blockpos3 = blockpos1.above();

                i = Math.max(i, this.getWireSignal(blockpos3, level.getBlockState(blockpos3)));
            } else if (!blockstate.isRedstoneConductor(level, blockpos1)) {
                BlockPos blockpos4 = blockpos1.below();

                i = Math.max(i, this.getWireSignal(blockpos4, level.getBlockState(blockpos4)));
            }
        }

        return Math.max(0, i - 1);
    }
}
