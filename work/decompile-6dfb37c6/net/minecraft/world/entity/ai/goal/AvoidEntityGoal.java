package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AvoidEntityGoal<T extends LivingEntity> extends Goal {

    protected final PathfinderMob mob;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    protected @Nullable T toAvoid;
    protected final float maxDist;
    protected @Nullable Path path;
    protected final PathNavigation pathNav;
    protected final Class<T> avoidClass;
    protected final Predicate<? super LivingEntity> avoidPredicate;
    protected final Predicate<? super LivingEntity> predicateOnAvoidEntity;
    private final TargetingConditions avoidEntityTargeting;

    public AvoidEntityGoal(PathfinderMob mob, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
        this(mob, avoidClass, (livingentity) -> {
            return true;
        }, maxDist, walkSpeedModifier, sprintSpeedModifier, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
    }

    public AvoidEntityGoal(PathfinderMob mob, Class<T> avoidClass, Predicate<LivingEntity> avoidPredicate, float maxDist, double walkSpeedModifier, double sprintSpeedModifier, Predicate<? super LivingEntity> predicateOnAvoidEntity) {
        this.mob = mob;
        this.avoidClass = avoidClass;
        this.avoidPredicate = avoidPredicate;
        this.maxDist = maxDist;
        this.walkSpeedModifier = walkSpeedModifier;
        this.sprintSpeedModifier = sprintSpeedModifier;
        this.predicateOnAvoidEntity = predicateOnAvoidEntity;
        this.pathNav = mob.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        this.avoidEntityTargeting = TargetingConditions.forCombat().range((double) maxDist).selector((livingentity, serverlevel) -> {
            return predicateOnAvoidEntity.test(livingentity) && avoidPredicate.test(livingentity);
        });
    }

    public AvoidEntityGoal(PathfinderMob mob, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier, Predicate<? super LivingEntity> predicateOnAvoidEntity) {
        this(mob, avoidClass, (livingentity) -> {
            return true;
        }, maxDist, walkSpeedModifier, sprintSpeedModifier, predicateOnAvoidEntity);
    }

    @Override
    public boolean canUse() {
        this.toAvoid = getServerLevel((Entity) this.mob).getNearestEntity(this.mob.level().getEntitiesOfClass(this.avoidClass, this.mob.getBoundingBox().inflate((double) this.maxDist, 3.0D, (double) this.maxDist), (livingentity) -> {
            return true;
        }), this.avoidEntityTargeting, this.mob, this.mob.getX(), this.mob.getY(), this.mob.getZ());
        if (this.toAvoid == null) {
            return false;
        } else {
            Vec3 vec3 = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.toAvoid.position());

            if (vec3 == null) {
                return false;
            } else if (this.toAvoid.distanceToSqr(vec3.x, vec3.y, vec3.z) < this.toAvoid.distanceToSqr((Entity) this.mob)) {
                return false;
            } else {
                this.path = this.pathNav.createPath(vec3.x, vec3.y, vec3.z, 0);
                return this.path != null;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.pathNav.isDone();
    }

    @Override
    public void start() {
        this.pathNav.moveTo(this.path, this.walkSpeedModifier);
    }

    @Override
    public void stop() {
        this.toAvoid = null;
    }

    @Override
    public void tick() {
        if (this.mob.distanceToSqr((Entity) this.toAvoid) < 49.0D) {
            this.mob.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.mob.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }

    }
}
