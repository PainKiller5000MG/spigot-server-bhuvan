package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {

    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> consumer) {}
    };

    static ChunkTrackingView of(ChunkPos center, int radius) {
        return new ChunkTrackingView.Positioned(center, radius);
    }

    static void difference(ChunkTrackingView from, ChunkTrackingView to, Consumer<ChunkPos> onEnter, Consumer<ChunkPos> onLeave) {
        if (!from.equals(to)) {
            if (from instanceof ChunkTrackingView.Positioned) {
                ChunkTrackingView.Positioned chunktrackingview_positioned = (ChunkTrackingView.Positioned) from;

                if (to instanceof ChunkTrackingView.Positioned) {
                    ChunkTrackingView.Positioned chunktrackingview_positioned1 = (ChunkTrackingView.Positioned) to;

                    if (chunktrackingview_positioned.squareIntersects(chunktrackingview_positioned1)) {
                        int i = Math.min(chunktrackingview_positioned.minX(), chunktrackingview_positioned1.minX());
                        int j = Math.min(chunktrackingview_positioned.minZ(), chunktrackingview_positioned1.minZ());
                        int k = Math.max(chunktrackingview_positioned.maxX(), chunktrackingview_positioned1.maxX());
                        int l = Math.max(chunktrackingview_positioned.maxZ(), chunktrackingview_positioned1.maxZ());

                        for (int i1 = i; i1 <= k; ++i1) {
                            for (int j1 = j; j1 <= l; ++j1) {
                                boolean flag = chunktrackingview_positioned.contains(i1, j1);
                                boolean flag1 = chunktrackingview_positioned1.contains(i1, j1);

                                if (flag != flag1) {
                                    if (flag1) {
                                        onEnter.accept(new ChunkPos(i1, j1));
                                    } else {
                                        onLeave.accept(new ChunkPos(i1, j1));
                                    }
                                }
                            }
                        }

                        return;
                    }
                }
            }

            from.forEach(onLeave);
            to.forEach(onEnter);
        }
    }

    default boolean contains(ChunkPos pos) {
        return this.contains(pos.x, pos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int chunkX, int chunkZ, boolean includeNeighbors);

    void forEach(Consumer<ChunkPos> consumer);

    default boolean isInViewDistance(int chunkX, int chunkZ) {
        return this.contains(chunkX, chunkZ, false);
    }

    static boolean isInViewDistance(int centerX, int centerZ, int viewDistance, int chunkX, int chunkZ) {
        return isWithinDistance(centerX, centerZ, viewDistance, chunkX, chunkZ, false);
    }

    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int chunkX, int chunkZ, boolean includeNeighbors) {
        int j1 = includeNeighbors ? 2 : 1;
        long k1 = (long) Math.max(0, Math.abs(chunkX - centerX) - j1);
        long l1 = (long) Math.max(0, Math.abs(chunkZ - centerZ) - j1);
        long i2 = k1 * k1 + l1 * l1;
        int j2 = viewDistance * viewDistance;

        return i2 < (long) j2;
    }

    public static record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {

        private int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        private int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        private int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        private int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX() && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, chunkX, chunkZ, includeNeighbors);
        }

        @Override
        public void forEach(Consumer<ChunkPos> consumer) {
            for (int i = this.minX(); i <= this.maxX(); ++i) {
                for (int j = this.minZ(); j <= this.maxZ(); ++j) {
                    if (this.contains(i, j)) {
                        consumer.accept(new ChunkPos(i, j));
                    }
                }
            }

        }
    }
}
