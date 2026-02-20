package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WalkNodeEvaluator extends NodeEvaluator {

    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5D;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125D;
    private final Long2ObjectMap<PathType> pathTypesByPosCacheByMob = new Long2ObjectOpenHashMap();
    private final Object2BooleanMap<AABB> collisionCache = new Object2BooleanOpenHashMap();
    private final Node[] reusableNeighbors;

    public WalkNodeEvaluator() {
        this.reusableNeighbors = new Node[Direction.Plane.HORIZONTAL.length()];
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob entity) {
        super.prepare(level, entity);
        entity.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCacheByMob.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int i = this.mob.getBlockY();
        BlockState blockstate = this.currentContext.getBlockState(blockpos_mutableblockpos.set(this.mob.getX(), (double) i, this.mob.getZ()));

        if (!this.mob.canStandOnFluid(blockstate.getFluidState())) {
            if (this.canFloat() && this.mob.isInWater()) {
                while (true) {
                    if (!blockstate.is(Blocks.WATER) && blockstate.getFluidState() != Fluids.WATER.getSource(false)) {
                        --i;
                        break;
                    }

                    ++i;
                    blockstate = this.currentContext.getBlockState(blockpos_mutableblockpos.set(this.mob.getX(), (double) i, this.mob.getZ()));
                }
            } else if (this.mob.onGround()) {
                i = Mth.floor(this.mob.getY() + 0.5D);
            } else {
                blockpos_mutableblockpos.set(this.mob.getX(), this.mob.getY() + 1.0D, this.mob.getZ());

                while (blockpos_mutableblockpos.getY() > this.currentContext.level().getMinY()) {
                    i = blockpos_mutableblockpos.getY();
                    blockpos_mutableblockpos.setY(blockpos_mutableblockpos.getY() - 1);
                    BlockState blockstate1 = this.currentContext.getBlockState(blockpos_mutableblockpos);

                    if (!blockstate1.isAir() && !blockstate1.isPathfindable(PathComputationType.LAND)) {
                        break;
                    }
                }
            }
        } else {
            while (this.mob.canStandOnFluid(blockstate.getFluidState())) {
                ++i;
                blockstate = this.currentContext.getBlockState(blockpos_mutableblockpos.set(this.mob.getX(), (double) i, this.mob.getZ()));
            }

            --i;
        }

        BlockPos blockpos = this.mob.blockPosition();

        if (!this.canStartAt(blockpos_mutableblockpos.set(blockpos.getX(), i, blockpos.getZ()))) {
            AABB aabb = this.mob.getBoundingBox();

            if (this.canStartAt(blockpos_mutableblockpos.set(aabb.minX, (double) i, aabb.minZ)) || this.canStartAt(blockpos_mutableblockpos.set(aabb.minX, (double) i, aabb.maxZ)) || this.canStartAt(blockpos_mutableblockpos.set(aabb.maxX, (double) i, aabb.minZ)) || this.canStartAt(blockpos_mutableblockpos.set(aabb.maxX, (double) i, aabb.maxZ))) {
                return this.getStartNode(blockpos_mutableblockpos);
            }
        }

        return this.getStartNode(new BlockPos(blockpos.getX(), i, blockpos.getZ()));
    }

    protected Node getStartNode(BlockPos pos) {
        Node node = this.getNode(pos);

        node.type = this.getCachedPathType(node.x, node.y, node.z);
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(BlockPos pos) {
        PathType pathtype = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());

        return pathtype != PathType.OPEN && this.mob.getPathfindingMalus(pathtype) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node pos) {
        int i = 0;
        int j = 0;
        PathType pathtype = this.getCachedPathType(pos.x, pos.y + 1, pos.z);
        PathType pathtype1 = this.getCachedPathType(pos.x, pos.y, pos.z);

        if (this.mob.getPathfindingMalus(pathtype) >= 0.0F && pathtype1 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        }

        double d0 = this.getFloorLevel(new BlockPos(pos.x, pos.y, pos.z));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node1 = this.findAcceptedNode(pos.x + direction.getStepX(), pos.y, pos.z + direction.getStepZ(), j, d0, direction, pathtype1);

            this.reusableNeighbors[direction.get2DDataValue()] = node1;
            if (this.isNeighborValid(node1, pos)) {
                neighbors[i++] = node1;
            }
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction1.getClockWise();

            if (this.isDiagonalValid(pos, this.reusableNeighbors[direction1.get2DDataValue()], this.reusableNeighbors[direction2.get2DDataValue()])) {
                Node node2 = this.findAcceptedNode(pos.x + direction1.getStepX() + direction2.getStepX(), pos.y, pos.z + direction1.getStepZ() + direction2.getStepZ(), j, d0, direction1, pathtype1);

                if (this.isDiagonalValid(node2)) {
                    neighbors[i++] = node2;
                }
            }
        }

        return i;
    }

    protected boolean isNeighborValid(@Nullable Node neighbor, Node current) {
        return neighbor != null && !neighbor.closed && (neighbor.costMalus >= 0.0F || current.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(Node pos, @Nullable Node ew, @Nullable Node ns) {
        if (ns != null && ew != null && ns.y <= pos.y && ew.y <= pos.y) {
            if (ew.type != PathType.WALKABLE_DOOR && ns.type != PathType.WALKABLE_DOOR) {
                boolean flag = ns.type == PathType.FENCE && ew.type == PathType.FENCE && (double) this.mob.getBbWidth() < 0.5D;

                return (ns.y < pos.y || ns.costMalus >= 0.0F || flag) && (ew.y < pos.y || ew.costMalus >= 0.0F || flag);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean isDiagonalValid(@Nullable Node diagonal) {
        return diagonal != null && !diagonal.closed ? (diagonal.type == PathType.WALKABLE_DOOR ? false : diagonal.costMalus >= 0.0F) : false;
    }

    private static boolean doesBlockHavePartialCollision(PathType type) {
        return type == PathType.FENCE || type == PathType.DOOR_WOOD_CLOSED || type == PathType.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(Node posTo) {
        AABB aabb = this.mob.getBoundingBox();
        Vec3 vec3 = new Vec3((double) posTo.x - this.mob.getX() + aabb.getXsize() / 2.0D, (double) posTo.y - this.mob.getY() + aabb.getYsize() / 2.0D, (double) posTo.z - this.mob.getZ() + aabb.getZsize() / 2.0D);
        int i = Mth.ceil(vec3.length() / aabb.getSize());

        vec3 = vec3.scale((double) (1.0F / (float) i));

        for (int j = 1; j <= i; ++j) {
            aabb = aabb.move(vec3);
            if (this.hasCollisions(aabb)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPos pos) {
        BlockGetter blockgetter = this.currentContext.level();

        return (this.canFloat() || this.isAmphibious()) && blockgetter.getFluidState(pos).is(FluidTags.WATER) ? (double) pos.getY() + 0.5D : getFloorLevel(blockgetter, pos);
    }

    public static double getFloorLevel(BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        VoxelShape voxelshape = level.getBlockState(blockpos1).getCollisionShape(level, blockpos1);

        return (double) blockpos1.getY() + (voxelshape.isEmpty() ? 0.0D : voxelshape.max(Direction.Axis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    protected @Nullable Node findAcceptedNode(int x, int y, int z, int jumpSize, double nodeHeight, Direction travelDirection, PathType blockPathTypeCurrent) {
        Node node = null;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        double d1 = this.getFloorLevel(blockpos_mutableblockpos.set(x, y, z));

        if (d1 - nodeHeight > this.getMobJumpHeight()) {
            return null;
        } else {
            PathType pathtype1 = this.getCachedPathType(x, y, z);
            float f = this.mob.getPathfindingMalus(pathtype1);

            if (f >= 0.0F) {
                node = this.getNodeAndUpdateCostToMax(x, y, z, pathtype1, f);
            }

            if (doesBlockHavePartialCollision(blockPathTypeCurrent) && node != null && node.costMalus >= 0.0F && !this.canReachWithoutCollision(node)) {
                node = null;
            }

            if (pathtype1 != PathType.WALKABLE && (!this.isAmphibious() || pathtype1 != PathType.WATER)) {
                if ((node == null || node.costMalus < 0.0F) && jumpSize > 0 && (pathtype1 != PathType.FENCE || this.canWalkOverFences()) && pathtype1 != PathType.UNPASSABLE_RAIL && pathtype1 != PathType.TRAPDOOR && pathtype1 != PathType.POWDER_SNOW) {
                    node = this.tryJumpOn(x, y, z, jumpSize, nodeHeight, travelDirection, blockPathTypeCurrent, blockpos_mutableblockpos);
                } else if (!this.isAmphibious() && pathtype1 == PathType.WATER && !this.canFloat()) {
                    node = this.tryFindFirstNonWaterBelow(x, y, z, node);
                } else if (pathtype1 == PathType.OPEN) {
                    node = this.tryFindFirstGroundNodeBelow(x, y, z);
                } else if (doesBlockHavePartialCollision(pathtype1) && node == null) {
                    node = this.getClosedNode(x, y, z, pathtype1);
                }

                return node;
            } else {
                return node;
            }
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125D, (double) this.mob.maxUpStep());
    }

    private Node getNodeAndUpdateCostToMax(int x, int y, int z, PathType pathType, float cost) {
        Node node = this.getNode(x, y, z);

        node.type = pathType;
        node.costMalus = Math.max(node.costMalus, cost);
        return node;
    }

    private Node getBlockedNode(int x, int y, int z) {
        Node node = this.getNode(x, y, z);

        node.type = PathType.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private Node getClosedNode(int x, int y, int z, PathType pathType) {
        Node node = this.getNode(x, y, z);

        node.closed = true;
        node.type = pathType;
        node.costMalus = pathType.getMalus();
        return node;
    }

    private @Nullable Node tryJumpOn(int x, int y, int z, int jumpSize, double nodeHeight, Direction travelDirection, PathType blockPathTypeCurrent, BlockPos.MutableBlockPos reusablePos) {
        Node node = this.findAcceptedNode(x, y + 1, z, jumpSize - 1, nodeHeight, travelDirection, blockPathTypeCurrent);

        if (node == null) {
            return null;
        } else if (this.mob.getBbWidth() >= 1.0F) {
            return node;
        } else if (node.type != PathType.OPEN && node.type != PathType.WALKABLE) {
            return node;
        } else {
            double d1 = (double) (x - travelDirection.getStepX()) + 0.5D;
            double d2 = (double) (z - travelDirection.getStepZ()) + 0.5D;
            double d3 = (double) this.mob.getBbWidth() / 2.0D;
            AABB aabb = new AABB(d1 - d3, this.getFloorLevel(reusablePos.set(d1, (double) (y + 1), d2)) + 0.001D, d2 - d3, d1 + d3, (double) this.mob.getBbHeight() + this.getFloorLevel(reusablePos.set((double) node.x, (double) node.y, (double) node.z)) - 0.002D, d2 + d3);

            return this.hasCollisions(aabb) ? null : node;
        }
    }

    private @Nullable Node tryFindFirstNonWaterBelow(int x, int y, int z, @Nullable Node best) {
        --y;

        while (y > this.mob.level().getMinY()) {
            PathType pathtype = this.getCachedPathType(x, y, z);

            if (pathtype != PathType.WATER) {
                return best;
            }

            best = this.getNodeAndUpdateCostToMax(x, y, z, pathtype, this.mob.getPathfindingMalus(pathtype));
            --y;
        }

        return best;
    }

    private Node tryFindFirstGroundNodeBelow(int x, int y, int z) {
        for (int l = y - 1; l >= this.mob.level().getMinY(); --l) {
            if (y - l > this.mob.getMaxFallDistance()) {
                return this.getBlockedNode(x, l, z);
            }

            PathType pathtype = this.getCachedPathType(x, l, z);
            float f = this.mob.getPathfindingMalus(pathtype);

            if (pathtype != PathType.OPEN) {
                if (f >= 0.0F) {
                    return this.getNodeAndUpdateCostToMax(x, l, z, pathtype, f);
                }

                return this.getBlockedNode(x, l, z);
            }
        }

        return this.getBlockedNode(x, y, z);
    }

    private boolean hasCollisions(AABB aabb) {
        return this.collisionCache.computeIfAbsent(aabb, (object) -> {
            return !this.currentContext.level().noCollision(this.mob, aabb);
        });
    }

    protected PathType getCachedPathType(int x, int y, int z) {
        return (PathType) this.pathTypesByPosCacheByMob.computeIfAbsent(BlockPos.asLong(x, y, z), (l) -> {
            return this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob);
        });
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        Set<PathType> set = this.getPathTypeWithinMobBB(context, x, y, z);

        if (set.contains(PathType.FENCE)) {
            return PathType.FENCE;
        } else if (set.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        } else {
            PathType pathtype = PathType.BLOCKED;

            for (PathType pathtype1 : set) {
                if (mob.getPathfindingMalus(pathtype1) < 0.0F) {
                    return pathtype1;
                }

                if (mob.getPathfindingMalus(pathtype1) >= mob.getPathfindingMalus(pathtype)) {
                    pathtype = pathtype1;
                }
            }

            if (this.entityWidth <= 1 && pathtype != PathType.OPEN && mob.getPathfindingMalus(pathtype) == 0.0F && this.getPathType(context, x, y, z) == PathType.OPEN) {
                return PathType.OPEN;
            } else {
                return pathtype;
            }
        }
    }

    public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
        EnumSet<PathType> enumset = EnumSet.noneOf(PathType.class);

        for (int l = 0; l < this.entityWidth; ++l) {
            for (int i1 = 0; i1 < this.entityHeight; ++i1) {
                for (int j1 = 0; j1 < this.entityDepth; ++j1) {
                    int k1 = l + x;
                    int l1 = i1 + y;
                    int i2 = j1 + z;
                    PathType pathtype = this.getPathType(context, k1, l1, i2);
                    BlockPos blockpos = this.mob.blockPosition();
                    boolean flag = this.canPassDoors();

                    if (pathtype == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && flag) {
                        pathtype = PathType.WALKABLE_DOOR;
                    }

                    if (pathtype == PathType.DOOR_OPEN && !flag) {
                        pathtype = PathType.BLOCKED;
                    }

                    if (pathtype == PathType.RAIL && this.getPathType(context, blockpos.getX(), blockpos.getY(), blockpos.getZ()) != PathType.RAIL && this.getPathType(context, blockpos.getX(), blockpos.getY() - 1, blockpos.getZ()) != PathType.RAIL) {
                        pathtype = PathType.UNPASSABLE_RAIL;
                    }

                    enumset.add(pathtype);
                }
            }
        }

        return enumset;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return getPathTypeStatic(context, new BlockPos.MutableBlockPos(x, y, z));
    }

    public static PathType getPathTypeStatic(Mob mob, BlockPos pos) {
        return getPathTypeStatic(new PathfindingContext(mob.level(), mob), pos.mutable());
    }

    public static PathType getPathTypeStatic(PathfindingContext context, BlockPos.MutableBlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        PathType pathtype = context.getPathTypeFromState(i, j, k);

        if (pathtype == PathType.OPEN && j >= context.level().getMinY() + 1) {
            PathType pathtype1;

            switch (context.getPathTypeFromState(i, j - 1, k)) {
                case OPEN:
                case WATER:
                case LAVA:
                case WALKABLE:
                    pathtype1 = PathType.OPEN;
                    break;
                case DAMAGE_FIRE:
                    pathtype1 = PathType.DAMAGE_FIRE;
                    break;
                case DAMAGE_OTHER:
                    pathtype1 = PathType.DAMAGE_OTHER;
                    break;
                case STICKY_HONEY:
                    pathtype1 = PathType.STICKY_HONEY;
                    break;
                case POWDER_SNOW:
                    pathtype1 = PathType.DANGER_POWDER_SNOW;
                    break;
                case DAMAGE_CAUTIOUS:
                    pathtype1 = PathType.DAMAGE_CAUTIOUS;
                    break;
                case TRAPDOOR:
                    pathtype1 = PathType.DANGER_TRAPDOOR;
                    break;
                default:
                    pathtype1 = checkNeighbourBlocks(context, i, j, k, PathType.WALKABLE);
            }

            return pathtype1;
        } else {
            return pathtype;
        }
    }

    public static PathType checkNeighbourBlocks(PathfindingContext context, int x, int y, int z, PathType blockPathType) {
        for (int l = -1; l <= 1; ++l) {
            for (int i1 = -1; i1 <= 1; ++i1) {
                for (int j1 = -1; j1 <= 1; ++j1) {
                    if (l != 0 || j1 != 0) {
                        PathType pathtype1 = context.getPathTypeFromState(x + l, y + i1, z + j1);

                        if (pathtype1 == PathType.DAMAGE_OTHER) {
                            return PathType.DANGER_OTHER;
                        }

                        if (pathtype1 == PathType.DAMAGE_FIRE || pathtype1 == PathType.LAVA) {
                            return PathType.DANGER_FIRE;
                        }

                        if (pathtype1 == PathType.WATER) {
                            return PathType.WATER_BORDER;
                        }

                        if (pathtype1 == PathType.DAMAGE_CAUTIOUS) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return blockPathType;
    }

    protected static PathType getPathTypeFromState(BlockGetter level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);
        Block block = blockstate.getBlock();

        if (blockstate.isAir()) {
            return PathType.OPEN;
        } else if (!blockstate.is(BlockTags.TRAPDOORS) && !blockstate.is(Blocks.LILY_PAD) && !blockstate.is(Blocks.BIG_DRIPLEAF)) {
            if (blockstate.is(Blocks.POWDER_SNOW)) {
                return PathType.POWDER_SNOW;
            } else if (!blockstate.is(Blocks.CACTUS) && !blockstate.is(Blocks.SWEET_BERRY_BUSH)) {
                if (blockstate.is(Blocks.HONEY_BLOCK)) {
                    return PathType.STICKY_HONEY;
                } else if (blockstate.is(Blocks.COCOA)) {
                    return PathType.COCOA;
                } else if (!blockstate.is(Blocks.WITHER_ROSE) && !blockstate.is(Blocks.POINTED_DRIPSTONE)) {
                    FluidState fluidstate = blockstate.getFluidState();

                    if (fluidstate.is(FluidTags.LAVA)) {
                        return PathType.LAVA;
                    } else if (isBurningBlock(blockstate)) {
                        return PathType.DAMAGE_FIRE;
                    } else if (block instanceof DoorBlock) {
                        DoorBlock doorblock = (DoorBlock) block;

                        return (Boolean) blockstate.getValue(DoorBlock.OPEN) ? PathType.DOOR_OPEN : (doorblock.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED);
                    } else {
                        return block instanceof BaseRailBlock ? PathType.RAIL : (block instanceof LeavesBlock ? PathType.LEAVES : (!blockstate.is(BlockTags.FENCES) && !blockstate.is(BlockTags.WALLS) && (!(block instanceof FenceGateBlock) || (Boolean) blockstate.getValue(FenceGateBlock.OPEN)) ? (!blockstate.isPathfindable(PathComputationType.LAND) ? PathType.BLOCKED : (fluidstate.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN)) : PathType.FENCE));
                    }
                } else {
                    return PathType.DAMAGE_CAUTIOUS;
                }
            } else {
                return PathType.DAMAGE_OTHER;
            }
        } else {
            return PathType.TRAPDOOR;
        }
    }
}
