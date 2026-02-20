package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AnimalPanic<E extends PathfinderMob> extends Behavior<E> {

    private static final int PANIC_MIN_DURATION = 100;
    private static final int PANIC_MAX_DURATION = 120;
    private static final int PANIC_DISTANCE_HORIZONTAL = 5;
    private static final int PANIC_DISTANCE_VERTICAL = 4;
    private final float speedMultiplier;
    private final Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes;
    private final Function<E, Vec3> positionGetter;

    public AnimalPanic(float speedMultiplier) {
        this(speedMultiplier, (pathfindermob) -> {
            return DamageTypeTags.PANIC_CAUSES;
        }, (pathfindermob) -> {
            return LandRandomPos.getPos(pathfindermob, 5, 4);
        });
    }

    public AnimalPanic(float speedMultiplier, int flyHeight) {
        this(speedMultiplier, (pathfindermob) -> {
            return DamageTypeTags.PANIC_CAUSES;
        }, (pathfindermob) -> {
            return AirAndWaterRandomPos.getPos(pathfindermob, 5, 4, flyHeight, pathfindermob.getViewVector(0.0F).x, pathfindermob.getViewVector(0.0F).z, (double) ((float) Math.PI / 2F));
        });
    }

    public AnimalPanic(float speedMultiplier, Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes) {
        this(speedMultiplier, panicCausingDamageTypes, (pathfindermob) -> {
            return LandRandomPos.getPos(pathfindermob, 5, 4);
        });
    }

    public AnimalPanic(float speedMultiplier, Function<PathfinderMob, TagKey<DamageType>> panicCausingDamageTypes, Function<E, Vec3> positionGetter) {
        super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.REGISTERED, MemoryModuleType.HURT_BY, MemoryStatus.REGISTERED), 100, 120);
        this.speedMultiplier = speedMultiplier;
        this.panicCausingDamageTypes = panicCausingDamageTypes;
        this.positionGetter = positionGetter;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, E body) {
        return (Boolean) body.getBrain().getMemory(MemoryModuleType.HURT_BY).map((damagesource) -> {
            return damagesource.is((TagKey) this.panicCausingDamageTypes.apply(body));
        }).orElse(false) || body.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    protected boolean canStillUse(ServerLevel level, E body, long timestamp) {
        return true;
    }

    protected void start(ServerLevel level, E body, long timestamp) {
        body.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        ((PathfinderMob) body).getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getNavigation().stop();
    }

    protected void stop(ServerLevel level, E body, long timestamp) {
        Brain<?> brain = ((PathfinderMob) body).getBrain();

        brain.eraseMemory(MemoryModuleType.IS_PANICKING);
    }

    protected void tick(ServerLevel level, E body, long timestamp) {
        if (body.getNavigation().isDone()) {
            Vec3 vec3 = this.getPanicPos(body, level);

            if (vec3 != null) {
                body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(vec3, this.speedMultiplier, 0));
            }
        }

    }

    private @Nullable Vec3 getPanicPos(E body, ServerLevel level) {
        if (body.isOnFire()) {
            Optional<Vec3> optional = this.lookForWater(level, body).map(Vec3::atBottomCenterOf);

            if (optional.isPresent()) {
                return (Vec3) optional.get();
            }
        }

        return (Vec3) this.positionGetter.apply(body);
    }

    private Optional<BlockPos> lookForWater(BlockGetter level, Entity mob) {
        BlockPos blockpos = mob.blockPosition();

        if (!level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty()) {
            return Optional.empty();
        } else {
            Predicate<BlockPos> predicate;

            if (Mth.ceil(mob.getBbWidth()) == 2) {
                predicate = (blockpos1) -> {
                    return BlockPos.squareOutSouthEast(blockpos1).allMatch((blockpos2) -> {
                        return level.getFluidState(blockpos2).is(FluidTags.WATER);
                    });
                };
            } else {
                predicate = (blockpos1) -> {
                    return level.getFluidState(blockpos1).is(FluidTags.WATER);
                };
            }

            return BlockPos.findClosestMatch(blockpos, 5, 1, predicate);
        }
    }
}
