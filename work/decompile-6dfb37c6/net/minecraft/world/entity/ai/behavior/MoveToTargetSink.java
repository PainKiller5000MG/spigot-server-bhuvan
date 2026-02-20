package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveToTargetSink extends Behavior<Mob> {

    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;
    private int remainingCooldown;
    private @Nullable Path path;
    private @Nullable BlockPos lastTargetPos;
    private float speedModifier;

    public MoveToTargetSink() {
        this(150, 250);
    }

    public MoveToTargetSink(int minTimeout, int maxTimeout) {
        super(ImmutableMap.of(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED, MemoryModuleType.PATH, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT), minTimeout, maxTimeout);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Mob body) {
        if (this.remainingCooldown > 0) {
            --this.remainingCooldown;
            return false;
        } else {
            Brain<?> brain = body.getBrain();
            WalkTarget walktarget = (WalkTarget) brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            boolean flag = this.reachedTarget(body, walktarget);

            if (!flag && this.tryComputePath(body, walktarget, level.getGameTime())) {
                this.lastTargetPos = walktarget.getTarget().currentBlockPosition();
                return true;
            } else {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                if (flag) {
                    brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                }

                return false;
            }
        }
    }

    protected boolean canStillUse(ServerLevel level, Mob body, long timestamp) {
        if (this.path != null && this.lastTargetPos != null) {
            Optional<WalkTarget> optional = body.getBrain().<WalkTarget>getMemory(MemoryModuleType.WALK_TARGET);
            boolean flag = (Boolean) optional.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);
            PathNavigation pathnavigation = body.getNavigation();

            return !pathnavigation.isDone() && optional.isPresent() && !this.reachedTarget(body, (WalkTarget) optional.get()) && !flag;
        } else {
            return false;
        }
    }

    protected void stop(ServerLevel level, Mob body, long timestamp) {
        if (body.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && !this.reachedTarget(body, (WalkTarget) body.getBrain().getMemory(MemoryModuleType.WALK_TARGET).get()) && body.getNavigation().isStuck()) {
            this.remainingCooldown = level.getRandom().nextInt(40);
        }

        body.getNavigation().stop();
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.PATH);
        this.path = null;
    }

    protected void start(ServerLevel level, Mob body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.PATH, this.path);
        body.getNavigation().moveTo(this.path, (double) this.speedModifier);
    }

    protected void tick(ServerLevel level, Mob body, long timestamp) {
        Path path = body.getNavigation().getPath();
        Brain<?> brain = body.getBrain();

        if (this.path != path) {
            this.path = path;
            brain.setMemory(MemoryModuleType.PATH, path);
        }

        if (path != null && this.lastTargetPos != null) {
            WalkTarget walktarget = (WalkTarget) brain.getMemory(MemoryModuleType.WALK_TARGET).get();

            if (walktarget.getTarget().currentBlockPosition().distSqr(this.lastTargetPos) > 4.0D && this.tryComputePath(body, walktarget, level.getGameTime())) {
                this.lastTargetPos = walktarget.getTarget().currentBlockPosition();
                this.start(level, body, timestamp);
            }

        }
    }

    private boolean tryComputePath(Mob body, WalkTarget walkTarget, long timestamp) {
        BlockPos blockpos = walkTarget.getTarget().currentBlockPosition();

        this.path = body.getNavigation().createPath(blockpos, 0);
        this.speedModifier = walkTarget.getSpeedModifier();
        Brain<?> brain = body.getBrain();

        if (this.reachedTarget(body, walkTarget)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            boolean flag = this.path != null && this.path.canReach();

            if (flag) {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
                brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, timestamp);
            }

            if (this.path != null) {
                return true;
            }

            Vec3 vec3 = DefaultRandomPos.getPosTowards((PathfinderMob) body, 10, 7, Vec3.atBottomCenterOf(blockpos), (double) ((float) Math.PI / 2F));

            if (vec3 != null) {
                this.path = body.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
                return this.path != null;
            }
        }

        return false;
    }

    private boolean reachedTarget(Mob body, WalkTarget walkTarget) {
        return walkTarget.getTarget().currentBlockPosition().distManhattan(body.blockPosition()) <= walkTarget.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(WalkTarget walkTarget) {
        PositionTracker positiontracker = walkTarget.getTarget();

        if (positiontracker instanceof EntityTracker entitytracker) {
            return entitytracker.getEntity().isSpectator();
        } else {
            return false;
        }
    }
}
