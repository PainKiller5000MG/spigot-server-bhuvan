package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public class SwimNodeEvaluator extends NodeEvaluator {

    private final boolean allowBreaching;
    private final Long2ObjectMap<PathType> pathTypesByPosCache = new Long2ObjectOpenHashMap();

    public SwimNodeEvaluator(boolean allowBreaching) {
        this.allowBreaching = allowBreaching;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob entity) {
        super.prepare(level, entity);
        this.pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        return this.getNode(Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5D), Mth.floor(this.mob.getBoundingBox().minZ));
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node pos) {
        int i = 0;
        Map<Direction, Node> map = Maps.newEnumMap(Direction.class);

        for (Direction direction : Direction.values()) {
            Node node1 = this.findAcceptedNode(pos.x + direction.getStepX(), pos.y + direction.getStepY(), pos.z + direction.getStepZ());

            map.put(direction, node1);
            if (this.isNodeValid(node1)) {
                neighbors[i++] = node1;
            }
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction1.getClockWise();

            if (hasMalus((Node) map.get(direction1)) && hasMalus((Node) map.get(direction2))) {
                Node node2 = this.findAcceptedNode(pos.x + direction1.getStepX() + direction2.getStepX(), pos.y, pos.z + direction1.getStepZ() + direction2.getStepZ());

                if (this.isNodeValid(node2)) {
                    neighbors[i++] = node2;
                }
            }
        }

        return i;
    }

    protected boolean isNodeValid(@Nullable Node node) {
        return node != null && !node.closed;
    }

    private static boolean hasMalus(@Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType pathtype = this.getCachedBlockType(x, y, z);

        if (this.allowBreaching && pathtype == PathType.BREACH || pathtype == PathType.WATER) {
            float f = this.mob.getPathfindingMalus(pathtype);

            if (f >= 0.0F) {
                node = this.getNode(x, y, z);
                node.type = pathtype;
                node.costMalus = Math.max(node.costMalus, f);
                if (this.currentContext.level().getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    node.costMalus += 8.0F;
                }
            }
        }

        return node;
    }

    protected PathType getCachedBlockType(int x, int y, int z) {
        return (PathType) this.pathTypesByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), (l) -> {
            return this.getPathType(this.currentContext, x, y, z);
        });
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return this.getPathTypeOfMob(context, x, y, z, this.mob);
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int l = x; l < x + this.entityWidth; ++l) {
            for (int i1 = y; i1 < y + this.entityHeight; ++i1) {
                for (int j1 = z; j1 < z + this.entityDepth; ++j1) {
                    BlockState blockstate = context.getBlockState(blockpos_mutableblockpos.set(l, i1, j1));
                    FluidState fluidstate = blockstate.getFluidState();

                    if (fluidstate.isEmpty() && blockstate.isPathfindable(PathComputationType.WATER) && blockstate.isAir()) {
                        return PathType.BREACH;
                    }

                    if (!fluidstate.is(FluidTags.WATER)) {
                        return PathType.BLOCKED;
                    }
                }
            }
        }

        BlockState blockstate1 = context.getBlockState(blockpos_mutableblockpos);

        if (blockstate1.isPathfindable(PathComputationType.WATER)) {
            return PathType.WATER;
        } else {
            return PathType.BLOCKED;
        }
    }
}
