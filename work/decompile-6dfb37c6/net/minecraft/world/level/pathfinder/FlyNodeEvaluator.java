package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class FlyNodeEvaluator extends WalkNodeEvaluator {

    private final Long2ObjectMap<PathType> pathTypeByPosCache = new Long2ObjectOpenHashMap();
    private static final float SMALL_MOB_SIZE = 1.0F;
    private static final float SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX = 1.1F;
    private static final int MAX_START_NODE_CANDIDATES = 10;

    public FlyNodeEvaluator() {}

    @Override
    public void prepare(PathNavigationRegion level, Mob entity) {
        super.prepare(level, entity);
        this.pathTypeByPosCache.clear();
        entity.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypeByPosCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        int i;

        if (this.canFloat() && this.mob.isInWater()) {
            i = this.mob.getBlockY();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(this.mob.getX(), (double) i, this.mob.getZ());

            for (BlockState blockstate = this.currentContext.getBlockState(blockpos_mutableblockpos); blockstate.is(Blocks.WATER); blockstate = this.currentContext.getBlockState(blockpos_mutableblockpos)) {
                ++i;
                blockpos_mutableblockpos.set(this.mob.getX(), (double) i, this.mob.getZ());
            }
        } else {
            i = Mth.floor(this.mob.getY() + 0.5D);
        }

        BlockPos blockpos = BlockPos.containing(this.mob.getX(), (double) i, this.mob.getZ());

        if (!this.canStartAt(blockpos)) {
            for (BlockPos blockpos1 : this.iteratePathfindingStartNodeCandidatePositions(this.mob)) {
                if (this.canStartAt(blockpos1)) {
                    return super.getStartNode(blockpos1);
                }
            }
        }

        return super.getStartNode(blockpos);
    }

    @Override
    protected boolean canStartAt(BlockPos pos) {
        PathType pathtype = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());

        return this.mob.getPathfindingMalus(pathtype) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node pos) {
        int i = 0;
        Node node1 = this.findAcceptedNode(pos.x, pos.y, pos.z + 1);

        if (this.isOpen(node1)) {
            neighbors[i++] = node1;
        }

        Node node2 = this.findAcceptedNode(pos.x - 1, pos.y, pos.z);

        if (this.isOpen(node2)) {
            neighbors[i++] = node2;
        }

        Node node3 = this.findAcceptedNode(pos.x + 1, pos.y, pos.z);

        if (this.isOpen(node3)) {
            neighbors[i++] = node3;
        }

        Node node4 = this.findAcceptedNode(pos.x, pos.y, pos.z - 1);

        if (this.isOpen(node4)) {
            neighbors[i++] = node4;
        }

        Node node5 = this.findAcceptedNode(pos.x, pos.y + 1, pos.z);

        if (this.isOpen(node5)) {
            neighbors[i++] = node5;
        }

        Node node6 = this.findAcceptedNode(pos.x, pos.y - 1, pos.z);

        if (this.isOpen(node6)) {
            neighbors[i++] = node6;
        }

        Node node7 = this.findAcceptedNode(pos.x, pos.y + 1, pos.z + 1);

        if (this.isOpen(node7) && this.hasMalus(node1) && this.hasMalus(node5)) {
            neighbors[i++] = node7;
        }

        Node node8 = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z);

        if (this.isOpen(node8) && this.hasMalus(node2) && this.hasMalus(node5)) {
            neighbors[i++] = node8;
        }

        Node node9 = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z);

        if (this.isOpen(node9) && this.hasMalus(node3) && this.hasMalus(node5)) {
            neighbors[i++] = node9;
        }

        Node node10 = this.findAcceptedNode(pos.x, pos.y + 1, pos.z - 1);

        if (this.isOpen(node10) && this.hasMalus(node4) && this.hasMalus(node5)) {
            neighbors[i++] = node10;
        }

        Node node11 = this.findAcceptedNode(pos.x, pos.y - 1, pos.z + 1);

        if (this.isOpen(node11) && this.hasMalus(node1) && this.hasMalus(node6)) {
            neighbors[i++] = node11;
        }

        Node node12 = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z);

        if (this.isOpen(node12) && this.hasMalus(node2) && this.hasMalus(node6)) {
            neighbors[i++] = node12;
        }

        Node node13 = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z);

        if (this.isOpen(node13) && this.hasMalus(node3) && this.hasMalus(node6)) {
            neighbors[i++] = node13;
        }

        Node node14 = this.findAcceptedNode(pos.x, pos.y - 1, pos.z - 1);

        if (this.isOpen(node14) && this.hasMalus(node4) && this.hasMalus(node6)) {
            neighbors[i++] = node14;
        }

        Node node15 = this.findAcceptedNode(pos.x + 1, pos.y, pos.z - 1);

        if (this.isOpen(node15) && this.hasMalus(node4) && this.hasMalus(node3)) {
            neighbors[i++] = node15;
        }

        Node node16 = this.findAcceptedNode(pos.x + 1, pos.y, pos.z + 1);

        if (this.isOpen(node16) && this.hasMalus(node1) && this.hasMalus(node3)) {
            neighbors[i++] = node16;
        }

        Node node17 = this.findAcceptedNode(pos.x - 1, pos.y, pos.z - 1);

        if (this.isOpen(node17) && this.hasMalus(node4) && this.hasMalus(node2)) {
            neighbors[i++] = node17;
        }

        Node node18 = this.findAcceptedNode(pos.x - 1, pos.y, pos.z + 1);

        if (this.isOpen(node18) && this.hasMalus(node1) && this.hasMalus(node2)) {
            neighbors[i++] = node18;
        }

        Node node19 = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z - 1);

        if (this.isOpen(node19) && this.hasMalus(node15) && this.hasMalus(node4) && this.hasMalus(node3) && this.hasMalus(node5) && this.hasMalus(node10) && this.hasMalus(node9)) {
            neighbors[i++] = node19;
        }

        Node node20 = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z + 1);

        if (this.isOpen(node20) && this.hasMalus(node16) && this.hasMalus(node1) && this.hasMalus(node3) && this.hasMalus(node5) && this.hasMalus(node7) && this.hasMalus(node9)) {
            neighbors[i++] = node20;
        }

        Node node21 = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z - 1);

        if (this.isOpen(node21) && this.hasMalus(node17) && this.hasMalus(node4) && this.hasMalus(node2) && this.hasMalus(node5) && this.hasMalus(node10) && this.hasMalus(node8)) {
            neighbors[i++] = node21;
        }

        Node node22 = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z + 1);

        if (this.isOpen(node22) && this.hasMalus(node18) && this.hasMalus(node1) && this.hasMalus(node2) && this.hasMalus(node5) && this.hasMalus(node7) && this.hasMalus(node8)) {
            neighbors[i++] = node22;
        }

        Node node23 = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z - 1);

        if (this.isOpen(node23) && this.hasMalus(node15) && this.hasMalus(node4) && this.hasMalus(node3) && this.hasMalus(node6) && this.hasMalus(node14) && this.hasMalus(node13)) {
            neighbors[i++] = node23;
        }

        Node node24 = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z + 1);

        if (this.isOpen(node24) && this.hasMalus(node16) && this.hasMalus(node1) && this.hasMalus(node3) && this.hasMalus(node6) && this.hasMalus(node11) && this.hasMalus(node13)) {
            neighbors[i++] = node24;
        }

        Node node25 = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z - 1);

        if (this.isOpen(node25) && this.hasMalus(node17) && this.hasMalus(node4) && this.hasMalus(node2) && this.hasMalus(node6) && this.hasMalus(node14) && this.hasMalus(node12)) {
            neighbors[i++] = node25;
        }

        Node node26 = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z + 1);

        if (this.isOpen(node26) && this.hasMalus(node18) && this.hasMalus(node1) && this.hasMalus(node2) && this.hasMalus(node6) && this.hasMalus(node11) && this.hasMalus(node12)) {
            neighbors[i++] = node26;
        }

        return i;
    }

    private boolean hasMalus(@Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    private boolean isOpen(@Nullable Node node) {
        return node != null && !node.closed;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType pathtype = this.getCachedPathType(x, y, z);
        float f = this.mob.getPathfindingMalus(pathtype);

        if (f >= 0.0F) {
            node = this.getNode(x, y, z);
            node.type = pathtype;
            node.costMalus = Math.max(node.costMalus, f);
            if (pathtype == PathType.WALKABLE) {
                ++node.costMalus;
            }
        }

        return node;
    }

    @Override
    protected PathType getCachedPathType(int x, int y, int z) {
        return (PathType) this.pathTypeByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), (l) -> {
            return this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
        });
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathtype = context.getPathTypeFromState(x, y, z);

        if (pathtype == PathType.OPEN && y >= context.level().getMinY() + 1) {
            BlockPos blockpos = new BlockPos(x, y - 1, z);
            PathType pathtype1 = context.getPathTypeFromState(blockpos.getX(), blockpos.getY(), blockpos.getZ());

            if (pathtype1 != PathType.DAMAGE_FIRE && pathtype1 != PathType.LAVA) {
                if (pathtype1 == PathType.DAMAGE_OTHER) {
                    pathtype = PathType.DAMAGE_OTHER;
                } else if (pathtype1 == PathType.COCOA) {
                    pathtype = PathType.COCOA;
                } else if (pathtype1 == PathType.FENCE) {
                    if (!blockpos.equals(context.mobPosition())) {
                        pathtype = PathType.FENCE;
                    }
                } else {
                    pathtype = pathtype1 != PathType.WALKABLE && pathtype1 != PathType.OPEN && pathtype1 != PathType.WATER ? PathType.WALKABLE : PathType.OPEN;
                }
            } else {
                pathtype = PathType.DAMAGE_FIRE;
            }
        }

        if (pathtype == PathType.WALKABLE || pathtype == PathType.OPEN) {
            pathtype = checkNeighbourBlocks(context, x, y, z, pathtype);
        }

        return pathtype;
    }

    private Iterable<BlockPos> iteratePathfindingStartNodeCandidatePositions(Mob mob) {
        AABB aabb = mob.getBoundingBox();
        boolean flag = aabb.getSize() < 1.0D;

        if (!flag) {
            return List.of(BlockPos.containing(aabb.minX, (double) mob.getBlockY(), aabb.minZ), BlockPos.containing(aabb.minX, (double) mob.getBlockY(), aabb.maxZ), BlockPos.containing(aabb.maxX, (double) mob.getBlockY(), aabb.minZ), BlockPos.containing(aabb.maxX, (double) mob.getBlockY(), aabb.maxZ));
        } else {
            double d0 = Math.max(0.0D, (double) 1.1F - aabb.getZsize());
            double d1 = Math.max(0.0D, (double) 1.1F - aabb.getXsize());
            double d2 = Math.max(0.0D, (double) 1.1F - aabb.getYsize());
            AABB aabb1 = aabb.inflate(d1, d2, d0);

            return BlockPos.randomBetweenClosed(mob.getRandom(), 10, Mth.floor(aabb1.minX), Mth.floor(aabb1.minY), Mth.floor(aabb1.minZ), Mth.floor(aabb1.maxX), Mth.floor(aabb1.maxY), Mth.floor(aabb1.maxZ));
        }
    }
}
