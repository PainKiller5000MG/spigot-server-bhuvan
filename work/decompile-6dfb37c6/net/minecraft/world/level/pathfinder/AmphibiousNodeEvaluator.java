package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.jspecify.annotations.Nullable;

public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {

    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(boolean prefersShallowSwimming) {
        this.prefersShallowSwimming = prefersShallowSwimming;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob entity) {
        super.prepare(level, entity);
        entity.setPathfindingMalus(PathType.WATER, 0.0F);
        this.oldWalkableCost = entity.getPathfindingMalus(PathType.WALKABLE);
        entity.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        this.oldWaterBorderCost = entity.getPathfindingMalus(PathType.WATER_BORDER);
        entity.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        this.mob.setPathfindingMalus(PathType.WALKABLE, this.oldWalkableCost);
        this.mob.setPathfindingMalus(PathType.WATER_BORDER, this.oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        return !this.mob.isInWater() ? super.getStart() : this.getStartNode(new BlockPos(Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5D), Mth.floor(this.mob.getBoundingBox().minZ)));
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y + 0.5D, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node pos) {
        int i = super.getNeighbors(neighbors, pos);
        PathType pathtype = this.getCachedPathType(pos.x, pos.y + 1, pos.z);
        PathType pathtype1 = this.getCachedPathType(pos.x, pos.y, pos.z);
        int j;

        if (this.mob.getPathfindingMalus(pathtype) >= 0.0F && pathtype1 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        } else {
            j = 0;
        }

        double d0 = this.getFloorLevel(new BlockPos(pos.x, pos.y, pos.z));
        Node node1 = this.findAcceptedNode(pos.x, pos.y + 1, pos.z, Math.max(0, j - 1), d0, Direction.UP, pathtype1);
        Node node2 = this.findAcceptedNode(pos.x, pos.y - 1, pos.z, j, d0, Direction.DOWN, pathtype1);

        if (this.isVerticalNeighborValid(node1, pos)) {
            neighbors[i++] = node1;
        }

        if (this.isVerticalNeighborValid(node2, pos) && pathtype1 != PathType.TRAPDOOR) {
            neighbors[i++] = node2;
        }

        for (int k = 0; k < i; ++k) {
            Node node3 = neighbors[k];

            if (node3.type == PathType.WATER && this.prefersShallowSwimming && node3.y < this.mob.level().getSeaLevel() - 10) {
                ++node3.costMalus;
            }
        }

        return i;
    }

    private boolean isVerticalNeighborValid(@Nullable Node verticalNode, Node pos) {
        return this.isNeighborValid(verticalNode, pos) && verticalNode.type == PathType.WATER;
    }

    @Override
    protected boolean isAmphibious() {
        return true;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathtype = context.getPathTypeFromState(x, y, z);

        if (pathtype == PathType.WATER) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                blockpos_mutableblockpos.set(x, y, z).move(direction);
                PathType pathtype1 = context.getPathTypeFromState(blockpos_mutableblockpos.getX(), blockpos_mutableblockpos.getY(), blockpos_mutableblockpos.getZ());

                if (pathtype1 == PathType.BLOCKED) {
                    return PathType.WATER_BORDER;
                }
            }

            return PathType.WATER;
        } else {
            return super.getPathType(context, x, y, z);
        }
    }
}
