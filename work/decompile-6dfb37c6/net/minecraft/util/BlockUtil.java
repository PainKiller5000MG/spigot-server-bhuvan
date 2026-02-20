package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockUtil {

    public BlockUtil() {}

    public static BlockUtil.FoundRectangle getLargestRectangleAround(BlockPos center, Direction.Axis axis1, int limit1, Direction.Axis axis2, int limit2, Predicate<BlockPos> test) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = center.mutable();
        Direction direction = Direction.get(Direction.AxisDirection.NEGATIVE, axis1);
        Direction direction1 = direction.getOpposite();
        Direction direction2 = Direction.get(Direction.AxisDirection.NEGATIVE, axis2);
        Direction direction3 = direction2.getOpposite();
        int k = getLimit(test, blockpos_mutableblockpos.set(center), direction, limit1);
        int l = getLimit(test, blockpos_mutableblockpos.set(center), direction1, limit1);
        int i1 = k;
        BlockUtil.IntBounds[] ablockutil_intbounds = new BlockUtil.IntBounds[k + 1 + l];

        ablockutil_intbounds[k] = new BlockUtil.IntBounds(getLimit(test, blockpos_mutableblockpos.set(center), direction2, limit2), getLimit(test, blockpos_mutableblockpos.set(center), direction3, limit2));
        int j1 = ablockutil_intbounds[k].min;

        for (int k1 = 1; k1 <= k; ++k1) {
            BlockUtil.IntBounds blockutil_intbounds = ablockutil_intbounds[i1 - (k1 - 1)];

            ablockutil_intbounds[i1 - k1] = new BlockUtil.IntBounds(getLimit(test, blockpos_mutableblockpos.set(center).move(direction, k1), direction2, blockutil_intbounds.min), getLimit(test, blockpos_mutableblockpos.set(center).move(direction, k1), direction3, blockutil_intbounds.max));
        }

        for (int l1 = 1; l1 <= l; ++l1) {
            BlockUtil.IntBounds blockutil_intbounds1 = ablockutil_intbounds[i1 + l1 - 1];

            ablockutil_intbounds[i1 + l1] = new BlockUtil.IntBounds(getLimit(test, blockpos_mutableblockpos.set(center).move(direction1, l1), direction2, blockutil_intbounds1.min), getLimit(test, blockpos_mutableblockpos.set(center).move(direction1, l1), direction3, blockutil_intbounds1.max));
        }

        int i2 = 0;
        int j2 = 0;
        int k2 = 0;
        int l2 = 0;
        int[] aint = new int[ablockutil_intbounds.length];

        for (int i3 = j1; i3 >= 0; --i3) {
            for (int j3 = 0; j3 < ablockutil_intbounds.length; ++j3) {
                BlockUtil.IntBounds blockutil_intbounds2 = ablockutil_intbounds[j3];
                int k3 = j1 - blockutil_intbounds2.min;
                int l3 = j1 + blockutil_intbounds2.max;

                aint[j3] = i3 >= k3 && i3 <= l3 ? l3 + 1 - i3 : 0;
            }

            Pair<BlockUtil.IntBounds, Integer> pair = getMaxRectangleLocation(aint);
            BlockUtil.IntBounds blockutil_intbounds3 = (BlockUtil.IntBounds) pair.getFirst();
            int i4 = 1 + blockutil_intbounds3.max - blockutil_intbounds3.min;
            int j4 = (Integer) pair.getSecond();

            if (i4 * j4 > k2 * l2) {
                i2 = blockutil_intbounds3.min;
                j2 = i3;
                k2 = i4;
                l2 = j4;
            }
        }

        return new BlockUtil.FoundRectangle(center.relative(axis1, i2 - i1).relative(axis2, j2 - j1), k2, l2);
    }

    private static int getLimit(Predicate<BlockPos> test, BlockPos.MutableBlockPos pos, Direction direction, int limit) {
        int j;

        for (j = 0; j < limit && test.test(pos.move(direction)); ++j) {
            ;
        }

        return j;
    }

    @VisibleForTesting
    static Pair<BlockUtil.IntBounds, Integer> getMaxRectangleLocation(int[] columns) {
        int i = 0;
        int j = 0;
        int k = 0;
        IntStack intstack = new IntArrayList();

        intstack.push(0);

        for (int l = 1; l <= columns.length; ++l) {
            int i1 = l == columns.length ? 0 : columns[l];

            while (!((IntStack) intstack).isEmpty()) {
                int j1 = columns[intstack.topInt()];

                if (i1 >= j1) {
                    intstack.push(l);
                    break;
                }

                intstack.popInt();
                int k1 = intstack.isEmpty() ? 0 : intstack.topInt() + 1;

                if (j1 * (l - k1) > k * (j - i)) {
                    j = l;
                    i = k1;
                    k = j1;
                }
            }

            if (intstack.isEmpty()) {
                intstack.push(l);
            }
        }

        return new Pair(new BlockUtil.IntBounds(i, j - 1), k);
    }

    public static Optional<BlockPos> getTopConnectedBlock(BlockGetter level, BlockPos pos, Block bodyBlock, Direction growthDirection, Block headBlock) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        BlockState blockstate;

        do {
            blockpos_mutableblockpos.move(growthDirection);
            blockstate = level.getBlockState(blockpos_mutableblockpos);
        } while (blockstate.is(bodyBlock));

        return blockstate.is(headBlock) ? Optional.of(blockpos_mutableblockpos) : Optional.empty();
    }

    public static class IntBounds {

        public final int min;
        public final int max;

        public IntBounds(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public String toString() {
            return "IntBounds{min=" + this.min + ", max=" + this.max + "}";
        }
    }

    public static class FoundRectangle {

        public final BlockPos minCorner;
        public final int axis1Size;
        public final int axis2Size;

        public FoundRectangle(BlockPos minCorner, int axis1Size, int axis2Size) {
            this.minCorner = minCorner;
            this.axis1Size = axis1Size;
            this.axis2Size = axis2Size;
        }
    }
}
