package net.minecraft.world.entity.ai.navigation;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.ServerDebugSubscribers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class PathNavigation {

    private static final int MAX_TIME_RECOMPUTE = 20;
    private static final int STUCK_CHECK_INTERVAL = 100;
    private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;
    protected final Mob mob;
    protected final Level level;
    protected @Nullable Path path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    protected Vec3 lastStuckCheckPos;
    protected Vec3i timeoutCachedNode;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint;
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected NodeEvaluator nodeEvaluator;
    private @Nullable BlockPos targetPos;
    private int reachRange;
    private float maxVisitedNodesMultiplier;
    private final PathFinder pathFinder;
    private boolean isStuck;
    private float requiredPathLength;

    public PathNavigation(Mob mob, Level level) {
        this.lastStuckCheckPos = Vec3.ZERO;
        this.timeoutCachedNode = Vec3i.ZERO;
        this.maxDistanceToWaypoint = 0.5F;
        this.maxVisitedNodesMultiplier = 1.0F;
        this.requiredPathLength = 16.0F;
        this.mob = mob;
        this.level = level;
        this.pathFinder = this.createPathFinder(Mth.floor(mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE) * 16.0D));
        if (level instanceof ServerLevel serverlevel) {
            ServerDebugSubscribers serverdebugsubscribers = serverlevel.getServer().debugSubscribers();

            this.pathFinder.setCaptureDebug(() -> {
                return serverdebugsubscribers.hasAnySubscriberFor(DebugSubscriptions.ENTITY_PATHS);
            });
        }

    }

    public void updatePathfinderMaxVisitedNodes() {
        int i = Mth.floor(this.getMaxPathLength() * 16.0F);

        this.pathFinder.setMaxVisitedNodes(i);
    }

    public void setRequiredPathLength(float length) {
        this.requiredPathLength = length;
        this.updatePathfinderMaxVisitedNodes();
    }

    private float getMaxPathLength() {
        return Math.max((float) this.mob.getAttributeValue(Attributes.FOLLOW_RANGE), this.requiredPathLength);
    }

    public void resetMaxVisitedNodesMultiplier() {
        this.maxVisitedNodesMultiplier = 1.0F;
    }

    public void setMaxVisitedNodesMultiplier(float maxVisitedNodesMultiplier) {
        this.maxVisitedNodesMultiplier = maxVisitedNodesMultiplier;
    }

    public @Nullable BlockPos getTargetPos() {
        return this.targetPos;
    }

    protected abstract PathFinder createPathFinder(int maxVisitedNodes);

    public void setSpeedModifier(double speedModifier) {
        this.speedModifier = speedModifier;
    }

    public void recomputePath() {
        if (this.level.getGameTime() - this.timeLastRecompute > 20L) {
            if (this.targetPos != null) {
                this.path = null;
                this.path = this.createPath(this.targetPos, this.reachRange);
                this.timeLastRecompute = this.level.getGameTime();
                this.hasDelayedRecomputation = false;
            }
        } else {
            this.hasDelayedRecomputation = true;
        }

    }

    public final @Nullable Path createPath(double x, double y, double z, int reachRange) {
        return this.createPath(BlockPos.containing(x, y, z), reachRange);
    }

    public @Nullable Path createPath(Stream<BlockPos> positions, int reachRange) {
        return this.createPath((Set) positions.collect(Collectors.toSet()), 8, false, reachRange);
    }

    public @Nullable Path createPath(Set<BlockPos> positions, int reachRange) {
        return this.createPath(positions, 8, false, reachRange);
    }

    public @Nullable Path createPath(BlockPos pos, int reachRange) {
        return this.createPath(ImmutableSet.of(pos), 8, false, reachRange);
    }

    public @Nullable Path createPath(BlockPos pos, int reachRange, int maxPathLength) {
        return this.createPath(ImmutableSet.of(pos), 8, false, reachRange, (float) maxPathLength);
    }

    public @Nullable Path createPath(Entity target, int reachRange) {
        return this.createPath(ImmutableSet.of(target.blockPosition()), 16, true, reachRange);
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange) {
        return this.createPath(targets, radiusOffset, above, reachRange, this.getMaxPathLength());
    }

    protected @Nullable Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength) {
        if (targets.isEmpty()) {
            return null;
        } else if (this.mob.getY() < (double) this.level.getMinY()) {
            return null;
        } else if (!this.canUpdatePath()) {
            return null;
        } else if (this.path != null && !this.path.isDone() && targets.contains(this.targetPos)) {
            return this.path;
        } else {
            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.push("pathfind");
            BlockPos blockpos = above ? this.mob.blockPosition().above() : this.mob.blockPosition();
            int k = (int) (maxPathLength + (float) radiusOffset);
            PathNavigationRegion pathnavigationregion = new PathNavigationRegion(this.level, blockpos.offset(-k, -k, -k), blockpos.offset(k, k, k));
            Path path = this.pathFinder.findPath(pathnavigationregion, this.mob, targets, maxPathLength, reachRange, this.maxVisitedNodesMultiplier);

            profilerfiller.pop();
            if (path != null && path.getTarget() != null) {
                this.targetPos = path.getTarget();
                this.reachRange = reachRange;
                this.resetStuckTimeout();
            }

            return path;
        }
    }

    public boolean moveTo(double x, double y, double z, double speedModifier) {
        return this.moveTo(this.createPath(x, y, z, 1), speedModifier);
    }

    public boolean moveTo(double x, double y, double z, int reachRange, double speedModifier) {
        return this.moveTo(this.createPath(x, y, z, reachRange), speedModifier);
    }

    public boolean moveTo(Entity target, double speedModifier) {
        Path path = this.createPath(target, 1);

        return path != null && this.moveTo(path, speedModifier);
    }

    public boolean moveTo(@Nullable Path newPath, double speedModifier) {
        if (newPath == null) {
            this.path = null;
            return false;
        } else {
            if (!newPath.sameAs(this.path)) {
                this.path = newPath;
            }

            if (this.isDone()) {
                return false;
            } else {
                this.trimPath();
                if (this.path.getNodeCount() <= 0) {
                    return false;
                } else {
                    this.speedModifier = speedModifier;
                    Vec3 vec3 = this.getTempMobPos();

                    this.lastStuckCheck = this.tick;
                    this.lastStuckCheckPos = vec3;
                    return true;
                }
            }
        }
    }

    public @Nullable Path getPath() {
        return this.path;
    }

    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 vec3 = this.getTempMobPos();
                Vec3 vec31 = this.path.getNextEntityPos(this.mob);

                if (vec3.y > vec31.y && !this.mob.onGround() && Mth.floor(vec3.x) == Mth.floor(vec31.x) && Mth.floor(vec3.z) == Mth.floor(vec31.z)) {
                    this.path.advance();
                }
            }

            if (!this.isDone()) {
                Vec3 vec32 = this.path.getNextEntityPos(this.mob);

                this.mob.getMoveControl().setWantedPosition(vec32.x, this.getGroundY(vec32), vec32.z, this.speedModifier);
            }
        }
    }

    protected double getGroundY(Vec3 target) {
        BlockPos blockpos = BlockPos.containing(target);

        return this.level.getBlockState(blockpos.below()).isAir() ? target.y : WalkNodeEvaluator.getFloorLevel(this.level, blockpos);
    }

    protected void followThePath() {
        Vec3 vec3 = this.getTempMobPos();

        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3i vec3i = this.path.getNextNodePos();
        double d0 = Math.abs(this.mob.getX() - ((double) vec3i.getX() + 0.5D));
        double d1 = Math.abs(this.mob.getY() - (double) vec3i.getY());
        double d2 = Math.abs(this.mob.getZ() - ((double) vec3i.getZ() + 0.5D));
        boolean flag = d0 < (double) this.maxDistanceToWaypoint && d2 < (double) this.maxDistanceToWaypoint && d1 < 1.0D;

        if (flag || this.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection(vec3)) {
            this.path.advance();
        }

        this.doStuckDetection(vec3);
    }

    private boolean shouldTargetNextNodeInDirection(Vec3 mobPosition) {
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        } else {
            Vec3 vec31 = Vec3.atBottomCenterOf(this.path.getNextNodePos());

            if (!mobPosition.closerThan(vec31, 2.0D)) {
                return false;
            } else if (this.canMoveDirectly(mobPosition, this.path.getNextEntityPos(this.mob))) {
                return true;
            } else {
                Vec3 vec32 = Vec3.atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + 1));
                Vec3 vec33 = vec31.subtract(mobPosition);
                Vec3 vec34 = vec32.subtract(mobPosition);
                double d0 = vec33.lengthSqr();
                double d1 = vec34.lengthSqr();
                boolean flag = d1 < d0;
                boolean flag1 = d0 < 0.5D;

                if (!flag && !flag1) {
                    return false;
                } else {
                    Vec3 vec35 = vec33.normalize();
                    Vec3 vec36 = vec34.normalize();

                    return vec36.dot(vec35) < 0.0D;
                }
            }
        }
    }

    protected void doStuckDetection(Vec3 mobPos) {
        if (this.tick - this.lastStuckCheck > 100) {
            float f = this.mob.getSpeed() >= 1.0F ? this.mob.getSpeed() : this.mob.getSpeed() * this.mob.getSpeed();
            float f1 = f * 100.0F * 0.25F;

            if (mobPos.distanceToSqr(this.lastStuckCheckPos) < (double) (f1 * f1)) {
                this.isStuck = true;
                this.stop();
            } else {
                this.isStuck = false;
            }

            this.lastStuckCheck = this.tick;
            this.lastStuckCheckPos = mobPos;
        }

        if (this.path != null && !this.path.isDone()) {
            Vec3i vec3i = this.path.getNextNodePos();
            long i = this.level.getGameTime();

            if (vec3i.equals(this.timeoutCachedNode)) {
                this.timeoutTimer += i - this.lastTimeoutCheck;
            } else {
                this.timeoutCachedNode = vec3i;
                double d0 = mobPos.distanceTo(Vec3.atBottomCenterOf(this.timeoutCachedNode));

                this.timeoutLimit = this.mob.getSpeed() > 0.0F ? d0 / (double) this.mob.getSpeed() * 20.0D : 0.0D;
            }

            if (this.timeoutLimit > 0.0D && (double) this.timeoutTimer > this.timeoutLimit * 3.0D) {
                this.timeoutPath();
            }

            this.lastTimeoutCheck = i;
        }

    }

    private void timeoutPath() {
        this.resetStuckTimeout();
        this.stop();
    }

    private void resetStuckTimeout() {
        this.timeoutCachedNode = Vec3i.ZERO;
        this.timeoutTimer = 0L;
        this.timeoutLimit = 0.0D;
        this.isStuck = false;
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public boolean isInProgress() {
        return !this.isDone();
    }

    public void stop() {
        this.path = null;
    }

    protected abstract Vec3 getTempMobPos();

    protected abstract boolean canUpdatePath();

    protected void trimPath() {
        if (this.path != null) {
            for (int i = 0; i < this.path.getNodeCount(); ++i) {
                Node node = this.path.getNode(i);
                Node node1 = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
                BlockState blockstate = this.level.getBlockState(new BlockPos(node.x, node.y, node.z));

                if (blockstate.is(BlockTags.CAULDRONS)) {
                    this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
                    if (node1 != null && node.y >= node1.y) {
                        this.path.replaceNode(i + 1, node.cloneAndMove(node1.x, node.y + 1, node1.z));
                    }
                }
            }

        }
    }

    protected boolean canMoveDirectly(Vec3 startPos, Vec3 stopPos) {
        return false;
    }

    public boolean canCutCorner(PathType pathType) {
        return pathType != PathType.DANGER_FIRE && pathType != PathType.DANGER_OTHER && pathType != PathType.WALKABLE_DOOR;
    }

    protected static boolean isClearForMovementBetween(Mob mob, Vec3 startPos, Vec3 stopPos, boolean blockedByFluids) {
        Vec3 vec32 = new Vec3(stopPos.x, stopPos.y + (double) mob.getBbHeight() * 0.5D, stopPos.z);

        return mob.level().clip(new ClipContext(startPos, vec32, ClipContext.Block.COLLIDER, blockedByFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pos) {
        BlockPos blockpos1 = pos.below();

        return this.level.getBlockState(blockpos1).isSolidRender();
    }

    public NodeEvaluator getNodeEvaluator() {
        return this.nodeEvaluator;
    }

    public void setCanFloat(boolean canFloat) {
        this.nodeEvaluator.setCanFloat(canFloat);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public boolean shouldRecomputePath(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
            Node node = this.path.getEndNode();
            Vec3 vec3 = new Vec3(((double) node.x + this.mob.getX()) / 2.0D, ((double) node.y + this.mob.getY()) / 2.0D, ((double) node.z + this.mob.getZ()) / 2.0D);

            return pos.closerToCenterThan(vec3, (double) (this.path.getNodeCount() - this.path.getNextNodeIndex()));
        } else {
            return false;
        }
    }

    public float getMaxDistanceToWaypoint() {
        return this.maxDistanceToWaypoint;
    }

    public boolean isStuck() {
        return this.isStuck;
    }

    public abstract boolean canNavigateGround();

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
    }
}
