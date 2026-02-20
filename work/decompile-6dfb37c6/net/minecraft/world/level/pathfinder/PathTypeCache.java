package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

public class PathTypeCache {

    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];

    public PathTypeCache() {}

    public PathType getOrCompute(BlockGetter level, BlockPos pos) {
        long i = pos.asLong();
        int j = index(i);
        PathType pathtype = this.get(j, i);

        return pathtype != null ? pathtype : this.compute(level, pos, j, i);
    }

    private @Nullable PathType get(int index, long key) {
        return this.positions[index] == key ? this.pathTypes[index] : null;
    }

    private PathType compute(BlockGetter level, BlockPos pos, int index, long key) {
        PathType pathtype = WalkNodeEvaluator.getPathTypeFromState(level, pos);

        this.positions[index] = key;
        this.pathTypes[index] = pathtype;
        return pathtype;
    }

    public void invalidate(BlockPos pos) {
        long i = pos.asLong();
        int j = index(i);

        if (this.positions[j] == i) {
            this.pathTypes[j] = null;
        }

    }

    private static int index(long pos) {
        return (int) HashCommon.mix(pos) & 4095;
    }
}
