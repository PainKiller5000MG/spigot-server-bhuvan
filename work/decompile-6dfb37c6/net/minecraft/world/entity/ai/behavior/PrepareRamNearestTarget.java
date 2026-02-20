package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class PrepareRamNearestTarget<E extends PathfinderMob> extends Behavior<E> {

    public static final int TIME_OUT_DURATION = 160;
    private final ToIntFunction<E> getCooldownOnFail;
    private final int minRamDistance;
    private final int maxRamDistance;
    private final float walkSpeed;
    private final TargetingConditions ramTargeting;
    private final int ramPrepareTime;
    private final Function<E, SoundEvent> getPrepareRamSound;
    private Optional<Long> reachedRamPositionTimestamp = Optional.empty();
    private Optional<PrepareRamNearestTarget.RamCandidate> ramCandidate = Optional.empty();

    public PrepareRamNearestTarget(ToIntFunction<E> getCooldownOnFail, int minRamDistance, int maxRamDistance, float walkSpeed, TargetingConditions ramTargeting, int ramPrepareTime, Function<E, SoundEvent> getPrepareRamSound) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT), 160);
        this.getCooldownOnFail = getCooldownOnFail;
        this.minRamDistance = minRamDistance;
        this.maxRamDistance = maxRamDistance;
        this.walkSpeed = walkSpeed;
        this.ramTargeting = ramTargeting;
        this.ramPrepareTime = ramPrepareTime;
        this.getPrepareRamSound = getPrepareRamSound;
    }

    protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
        Brain<?> brain = body.getBrain();

        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).flatMap((nearestvisiblelivingentities) -> {
            return nearestvisiblelivingentities.findClosest((livingentity) -> {
                return this.ramTargeting.test(level, body, livingentity);
            });
        }).ifPresent((livingentity) -> {
            this.chooseRamPosition(body, livingentity);
        });
    }

    protected void stop(ServerLevel level, E body, long timestamp) {
        Brain<?> brain = ((PathfinderMob) body).getBrain();

        if (!brain.hasMemoryValue(MemoryModuleType.RAM_TARGET)) {
            level.broadcastEntityEvent(body, (byte) 59);
            brain.setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, this.getCooldownOnFail.applyAsInt(body));
        }

    }

    protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
        return this.ramCandidate.isPresent() && ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget().isAlive();
    }

    protected void tick(ServerLevel level, E body, long timestamp) {
        if (!this.ramCandidate.isEmpty()) {
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getStartPosition(), this.walkSpeed, 0));
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget(), true));
            boolean flag = !((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTarget().blockPosition().equals(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTargetPosition());

            if (flag) {
                level.broadcastEntityEvent(body, (byte) 59);
                body.getNavigation().stop();
                this.chooseRamPosition(body, ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).target);
            } else {
                BlockPos blockpos = body.blockPosition();

                if (blockpos.equals(((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getStartPosition())) {
                    level.broadcastEntityEvent(body, (byte) 58);
                    if (this.reachedRamPositionTimestamp.isEmpty()) {
                        this.reachedRamPositionTimestamp = Optional.of(timestamp);
                    }

                    if (timestamp - (Long) this.reachedRamPositionTimestamp.get() >= (long) this.ramPrepareTime) {
                        body.getBrain().setMemory(MemoryModuleType.RAM_TARGET, this.getEdgeOfBlock(blockpos, ((PrepareRamNearestTarget.RamCandidate) this.ramCandidate.get()).getTargetPosition()));
                        level.playSound((Entity) null, (Entity) body, (SoundEvent) this.getPrepareRamSound.apply(body), SoundSource.NEUTRAL, 1.0F, body.getVoicePitch());
                        this.ramCandidate = Optional.empty();
                    }
                }
            }

        }
    }

    private Vec3 getEdgeOfBlock(BlockPos startRamPos, BlockPos targetPos) {
        double d0 = 0.5D;
        double d1 = 0.5D * (double) Mth.sign((double) (targetPos.getX() - startRamPos.getX()));
        double d2 = 0.5D * (double) Mth.sign((double) (targetPos.getZ() - startRamPos.getZ()));

        return Vec3.atBottomCenterOf(targetPos).add(d1, 0.0D, d2);
    }

    private Optional<BlockPos> calculateRammingStartPosition(PathfinderMob body, LivingEntity ramableTarget) {
        BlockPos blockpos = ramableTarget.blockPosition();

        if (!this.isWalkableBlock(body, blockpos)) {
            return Optional.empty();
        } else {
            List<BlockPos> list = Lists.newArrayList();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos.mutable();

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                blockpos_mutableblockpos.set(blockpos);

                for (int i = 0; i < this.maxRamDistance; ++i) {
                    if (!this.isWalkableBlock(body, blockpos_mutableblockpos.move(direction))) {
                        blockpos_mutableblockpos.move(direction.getOpposite());
                        break;
                    }
                }

                if (blockpos_mutableblockpos.distManhattan(blockpos) >= this.minRamDistance) {
                    list.add(blockpos_mutableblockpos.immutable());
                }
            }

            PathNavigation pathnavigation = body.getNavigation();
            Stream stream = list.stream();
            BlockPos blockpos1 = body.blockPosition();

            Objects.requireNonNull(blockpos1);
            return stream.sorted(Comparator.comparingDouble(blockpos1::distSqr)).filter((blockpos2) -> {
                Path path = pathnavigation.createPath(blockpos2, 0);

                return path != null && path.canReach();
            }).findFirst();
        }
    }

    private boolean isWalkableBlock(PathfinderMob body, BlockPos targetPos) {
        return body.getNavigation().isStableDestination(targetPos) && body.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic((Mob) body, targetPos)) == 0.0F;
    }

    private void chooseRamPosition(PathfinderMob body, LivingEntity ramableTarget) {
        this.reachedRamPositionTimestamp = Optional.empty();
        this.ramCandidate = this.calculateRammingStartPosition(body, ramableTarget).map((blockpos) -> {
            return new PrepareRamNearestTarget.RamCandidate(blockpos, ramableTarget.blockPosition(), ramableTarget);
        });
    }

    public static class RamCandidate {

        private final BlockPos startPosition;
        private final BlockPos targetPosition;
        private final LivingEntity target;

        public RamCandidate(BlockPos startPosition, BlockPos targetPosition, LivingEntity target) {
            this.startPosition = startPosition;
            this.targetPosition = targetPosition;
            this.target = target;
        }

        public BlockPos getStartPosition() {
            return this.startPosition;
        }

        public BlockPos getTargetPosition() {
            return this.targetPosition;
        }

        public LivingEntity getTarget() {
            return this.target;
        }
    }
}
