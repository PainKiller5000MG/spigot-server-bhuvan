package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

public interface SignalGetter extends BlockGetter {

    Direction[] DIRECTIONS = Direction.values();

    default int getDirectSignal(BlockPos pos, Direction direction) {
        return this.getBlockState(pos).getDirectSignal(this, pos, direction);
    }

    default int getDirectSignalTo(BlockPos pos) {
        int i = 0;

        i = Math.max(i, this.getDirectSignal(pos.below(), Direction.DOWN));
        if (i >= 15) {
            return i;
        } else {
            i = Math.max(i, this.getDirectSignal(pos.above(), Direction.UP));
            if (i >= 15) {
                return i;
            } else {
                i = Math.max(i, this.getDirectSignal(pos.north(), Direction.NORTH));
                if (i >= 15) {
                    return i;
                } else {
                    i = Math.max(i, this.getDirectSignal(pos.south(), Direction.SOUTH));
                    if (i >= 15) {
                        return i;
                    } else {
                        i = Math.max(i, this.getDirectSignal(pos.west(), Direction.WEST));
                        if (i >= 15) {
                            return i;
                        } else {
                            i = Math.max(i, this.getDirectSignal(pos.east(), Direction.EAST));
                            return i >= 15 ? i : i;
                        }
                    }
                }
            }
        }
    }

    default int getControlInputSignal(BlockPos pos, Direction direction, boolean onlyDiodes) {
        BlockState blockstate = this.getBlockState(pos);

        return onlyDiodes ? (DiodeBlock.isDiode(blockstate) ? this.getDirectSignal(pos, direction) : 0) : (blockstate.is(Blocks.REDSTONE_BLOCK) ? 15 : (blockstate.is(Blocks.REDSTONE_WIRE) ? (Integer) blockstate.getValue(RedStoneWireBlock.POWER) : (blockstate.isSignalSource() ? this.getDirectSignal(pos, direction) : 0)));
    }

    default boolean hasSignal(BlockPos pos, Direction direction) {
        return this.getSignal(pos, direction) > 0;
    }

    default int getSignal(BlockPos pos, Direction direction) {
        BlockState blockstate = this.getBlockState(pos);
        int i = blockstate.getSignal(this, pos, direction);

        return blockstate.isRedstoneConductor(this, pos) ? Math.max(i, this.getDirectSignalTo(pos)) : i;
    }

    default boolean hasNeighborSignal(BlockPos blockPos) {
        return this.getSignal(blockPos.below(), Direction.DOWN) > 0 ? true : (this.getSignal(blockPos.above(), Direction.UP) > 0 ? true : (this.getSignal(blockPos.north(), Direction.NORTH) > 0 ? true : (this.getSignal(blockPos.south(), Direction.SOUTH) > 0 ? true : (this.getSignal(blockPos.west(), Direction.WEST) > 0 ? true : this.getSignal(blockPos.east(), Direction.EAST) > 0))));
    }

    default int getBestNeighborSignal(BlockPos pos) {
        int i = 0;

        for (Direction direction : SignalGetter.DIRECTIONS) {
            int j = this.getSignal(pos.relative(direction), direction);

            if (j >= 15) {
                return 15;
            }

            if (j > i) {
                i = j;
            }
        }

        return i;
    }
}
