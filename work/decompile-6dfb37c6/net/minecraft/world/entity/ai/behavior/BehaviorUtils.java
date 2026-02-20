package net.minecraft.world.entity.ai.behavior;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class BehaviorUtils {

    private BehaviorUtils() {}

    public static void lockGazeAndWalkToEachOther(LivingEntity entity1, LivingEntity entity2, float speedModifier, int closeEnoughDistance) {
        lookAtEachOther(entity1, entity2);
        setWalkAndLookTargetMemoriesToEachOther(entity1, entity2, speedModifier, closeEnoughDistance);
    }

    public static boolean entityIsVisible(Brain<?> brain, LivingEntity targetEntity) {
        Optional<NearestVisibleLivingEntities> optional = brain.<NearestVisibleLivingEntities>getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);

        return optional.isPresent() && ((NearestVisibleLivingEntities) optional.get()).contains(targetEntity);
    }

    public static boolean targetIsValid(Brain<?> brain, MemoryModuleType<? extends LivingEntity> memory, EntityType<?> targetType) {
        return targetIsValid(brain, memory, (livingentity) -> {
            return livingentity.getType() == targetType;
        });
    }

    private static boolean targetIsValid(Brain<?> brain, MemoryModuleType<? extends LivingEntity> memory, Predicate<LivingEntity> targetPredicate) {
        return brain.getMemory(memory).filter(targetPredicate).filter(LivingEntity::isAlive).filter((livingentity) -> {
            return entityIsVisible(brain, livingentity);
        }).isPresent();
    }

    private static void lookAtEachOther(LivingEntity entity1, LivingEntity entity2) {
        lookAtEntity(entity1, entity2);
        lookAtEntity(entity2, entity1);
    }

    public static void lookAtEntity(LivingEntity looker, LivingEntity targetEntity) {
        looker.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(targetEntity, true));
    }

    private static void setWalkAndLookTargetMemoriesToEachOther(LivingEntity entity1, LivingEntity entity2, float speedModifier, int closeEnoughDistance) {
        setWalkAndLookTargetMemories(entity1, (Entity) entity2, speedModifier, closeEnoughDistance);
        setWalkAndLookTargetMemories(entity2, (Entity) entity1, speedModifier, closeEnoughDistance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity walker, Entity targetEntity, float speedModifier, int closeEnoughDistance) {
        setWalkAndLookTargetMemories(walker, (PositionTracker) (new EntityTracker(targetEntity, true)), speedModifier, closeEnoughDistance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity walker, BlockPos targetPos, float speedModifier, int closeEnoughDistance) {
        setWalkAndLookTargetMemories(walker, (PositionTracker) (new BlockPosTracker(targetPos)), speedModifier, closeEnoughDistance);
    }

    public static void setWalkAndLookTargetMemories(LivingEntity walker, PositionTracker target, float speedModifier, int closeEnoughDistance) {
        WalkTarget walktarget = new WalkTarget(target, speedModifier, closeEnoughDistance);

        walker.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, target);
        walker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walktarget);
    }

    public static void throwItem(LivingEntity thrower, ItemStack item, Vec3 targetPos) {
        Vec3 vec31 = new Vec3((double) 0.3F, (double) 0.3F, (double) 0.3F);

        throwItem(thrower, item, targetPos, vec31, 0.3F);
    }

    public static void throwItem(LivingEntity thrower, ItemStack item, Vec3 targetPos, Vec3 throwVelocity, float handYDistanceFromEye) {
        double d0 = thrower.getEyeY() - (double) handYDistanceFromEye;
        ItemEntity itementity = new ItemEntity(thrower.level(), thrower.getX(), d0, thrower.getZ(), item);

        itementity.setThrower(thrower);
        Vec3 vec32 = targetPos.subtract(thrower.position());

        vec32 = vec32.normalize().multiply(throwVelocity.x, throwVelocity.y, throwVelocity.z);
        itementity.setDeltaMovement(vec32);
        itementity.setDefaultPickUpDelay();
        thrower.level().addFreshEntity(itementity);
    }

    public static SectionPos findSectionClosestToVillage(ServerLevel level, SectionPos center, int radius) {
        int j = level.sectionsToVillage(center);
        Stream stream = SectionPos.cube(center, radius).filter((sectionpos1) -> {
            return level.sectionsToVillage(sectionpos1) < j;
        });

        Objects.requireNonNull(level);
        return (SectionPos) stream.min(Comparator.comparingInt(level::sectionsToVillage)).orElse(center);
    }

    public static boolean isWithinAttackRange(Mob body, LivingEntity target, int projectileAttackRangeMargin) {
        Item item = body.getMainHandItem().getItem();

        if (item instanceof ProjectileWeaponItem projectileweaponitem) {
            if (body.canUseNonMeleeWeapon(body.getMainHandItem())) {
                int j = projectileweaponitem.getDefaultProjectileRange() - projectileAttackRangeMargin;

                return body.closerThan(target, (double) j);
            }
        }

        return body.isWithinMeleeAttackRange(target);
    }

    public static boolean isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(LivingEntity body, LivingEntity otherTarget, double howMuchFurtherAway) {
        Optional<LivingEntity> optional = body.getBrain().<LivingEntity>getMemory(MemoryModuleType.ATTACK_TARGET);

        if (optional.isEmpty()) {
            return false;
        } else {
            double d1 = body.distanceToSqr(((LivingEntity) optional.get()).position());
            double d2 = body.distanceToSqr(otherTarget.position());

            return d2 > d1 + howMuchFurtherAway * howMuchFurtherAway;
        }
    }

    public static boolean canSee(LivingEntity body, LivingEntity target) {
        Brain<?> brain = body.getBrain();

        return !brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) ? false : ((NearestVisibleLivingEntities) brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()).contains(target);
    }

    public static LivingEntity getNearestTarget(LivingEntity body, Optional<LivingEntity> target1, LivingEntity target2) {
        return target1.isEmpty() ? target2 : getTargetNearestMe(body, (LivingEntity) target1.get(), target2);
    }

    public static LivingEntity getTargetNearestMe(LivingEntity body, LivingEntity target1, LivingEntity target2) {
        Vec3 vec3 = target1.position();
        Vec3 vec31 = target2.position();

        return body.distanceToSqr(vec3) < body.distanceToSqr(vec31) ? target1 : target2;
    }

    public static Optional<LivingEntity> getLivingEntityFromUUIDMemory(LivingEntity body, MemoryModuleType<UUID> memoryType) {
        Optional<UUID> optional = body.getBrain().<UUID>getMemory(memoryType);

        return optional.map((uuid) -> {
            return body.level().getEntity(uuid);
        }).map((entity) -> {
            LivingEntity livingentity1;

            if (entity instanceof LivingEntity livingentity2) {
                livingentity1 = livingentity2;
            } else {
                livingentity1 = null;
            }

            return livingentity1;
        });
    }

    public static @Nullable Vec3 getRandomSwimmablePos(PathfinderMob body, int maxHorizontalDistance, int maxVerticalDistance) {
        Vec3 vec3 = DefaultRandomPos.getPos(body, maxHorizontalDistance, maxVerticalDistance);

        for (int k = 0; vec3 != null && !body.level().getBlockState(BlockPos.containing(vec3)).isPathfindable(PathComputationType.WATER) && k++ < 10; vec3 = DefaultRandomPos.getPos(body, maxHorizontalDistance, maxVerticalDistance)) {
            ;
        }

        return vec3;
    }

    public static boolean isBreeding(LivingEntity body) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET);
    }
}
