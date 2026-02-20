package net.minecraft.world.level.block.state.pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

public class BlockPattern {

    private final Predicate<BlockInWorld>[][][] pattern;
    private final int depth;
    private final int height;
    private final int width;

    public BlockPattern(Predicate<BlockInWorld>[][][] pattern) {
        this.pattern = pattern;
        this.depth = pattern.length;
        if (this.depth > 0) {
            this.height = pattern[0].length;
            if (this.height > 0) {
                this.width = pattern[0][0].length;
            } else {
                this.width = 0;
            }
        } else {
            this.height = 0;
            this.width = 0;
        }

    }

    public int getDepth() {
        return this.depth;
    }

    public int getHeight() {
        return this.height;
    }

    public int getWidth() {
        return this.width;
    }

    @VisibleForTesting
    public Predicate<BlockInWorld>[][][] getPattern() {
        return this.pattern;
    }

    @VisibleForTesting
    public BlockPattern.@Nullable BlockPatternMatch matches(LevelReader level, BlockPos origin, Direction forwards, Direction up) {
        LoadingCache<BlockPos, BlockInWorld> loadingcache = createLevelCache(level, false);

        return this.matches(origin, forwards, up, loadingcache);
    }

    private BlockPattern.@Nullable BlockPatternMatch matches(BlockPos origin, Direction forwards, Direction up, LoadingCache<BlockPos, BlockInWorld> cache) {
        for (int i = 0; i < this.width; ++i) {
            for (int j = 0; j < this.height; ++j) {
                for (int k = 0; k < this.depth; ++k) {
                    if (!this.pattern[k][j][i].test((BlockInWorld) cache.getUnchecked(translateAndRotate(origin, forwards, up, i, j, k)))) {
                        return null;
                    }
                }
            }
        }

        return new BlockPattern.BlockPatternMatch(origin, forwards, up, cache, this.width, this.height, this.depth);
    }

    public BlockPattern.@Nullable BlockPatternMatch find(LevelReader level, BlockPos origin) {
        LoadingCache<BlockPos, BlockInWorld> loadingcache = createLevelCache(level, false);
        int i = Math.max(Math.max(this.width, this.height), this.depth);

        for (BlockPos blockpos1 : BlockPos.betweenClosed(origin, origin.offset(i - 1, i - 1, i - 1))) {
            for (Direction direction : Direction.values()) {
                for (Direction direction1 : Direction.values()) {
                    if (direction1 != direction && direction1 != direction.getOpposite()) {
                        BlockPattern.BlockPatternMatch blockpattern_blockpatternmatch = this.matches(blockpos1, direction, direction1, loadingcache);

                        if (blockpattern_blockpatternmatch != null) {
                            return blockpattern_blockpatternmatch;
                        }
                    }
                }
            }
        }

        return null;
    }

    public static LoadingCache<BlockPos, BlockInWorld> createLevelCache(LevelReader level, boolean loadChunks) {
        return CacheBuilder.newBuilder().build(new BlockPattern.BlockCacheLoader(level, loadChunks));
    }

    protected static BlockPos translateAndRotate(BlockPos origin, Direction forwardsDirection, Direction upDirection, int right, int down, int forwards) {
        if (forwardsDirection != upDirection && forwardsDirection != upDirection.getOpposite()) {
            Vec3i vec3i = new Vec3i(forwardsDirection.getStepX(), forwardsDirection.getStepY(), forwardsDirection.getStepZ());
            Vec3i vec3i1 = new Vec3i(upDirection.getStepX(), upDirection.getStepY(), upDirection.getStepZ());
            Vec3i vec3i2 = vec3i.cross(vec3i1);

            return origin.offset(vec3i1.getX() * -down + vec3i2.getX() * right + vec3i.getX() * forwards, vec3i1.getY() * -down + vec3i2.getY() * right + vec3i.getY() * forwards, vec3i1.getZ() * -down + vec3i2.getZ() * right + vec3i.getZ() * forwards);
        } else {
            throw new IllegalArgumentException("Invalid forwards & up combination");
        }
    }

    private static class BlockCacheLoader extends CacheLoader<BlockPos, BlockInWorld> {

        private final LevelReader level;
        private final boolean loadChunks;

        public BlockCacheLoader(LevelReader level, boolean loadChunks) {
            this.level = level;
            this.loadChunks = loadChunks;
        }

        public BlockInWorld load(BlockPos key) {
            return new BlockInWorld(this.level, key, this.loadChunks);
        }
    }

    public static class BlockPatternMatch {

        private final BlockPos frontTopLeft;
        private final Direction forwards;
        private final Direction up;
        private final LoadingCache<BlockPos, BlockInWorld> cache;
        private final int width;
        private final int height;
        private final int depth;

        public BlockPatternMatch(BlockPos frontTopLeft, Direction forwards, Direction up, LoadingCache<BlockPos, BlockInWorld> cache, int width, int height, int depth) {
            this.frontTopLeft = frontTopLeft;
            this.forwards = forwards;
            this.up = up;
            this.cache = cache;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        public BlockPos getFrontTopLeft() {
            return this.frontTopLeft;
        }

        public Direction getForwards() {
            return this.forwards;
        }

        public Direction getUp() {
            return this.up;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public int getDepth() {
            return this.depth;
        }

        public BlockInWorld getBlock(int right, int down, int forwards) {
            return (BlockInWorld) this.cache.getUnchecked(BlockPattern.translateAndRotate(this.frontTopLeft, this.getForwards(), this.getUp(), right, down, forwards));
        }

        public String toString() {
            return MoreObjects.toStringHelper(this).add("up", this.up).add("forwards", this.forwards).add("frontTopLeft", this.frontTopLeft).toString();
        }
    }
}
