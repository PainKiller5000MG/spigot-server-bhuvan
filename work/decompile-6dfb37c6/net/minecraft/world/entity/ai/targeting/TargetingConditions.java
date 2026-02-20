package net.minecraft.world.entity.ai.targeting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

public class TargetingConditions {

    public static final TargetingConditions DEFAULT = forCombat();
    private static final double MIN_VISIBILITY_DISTANCE_FOR_INVISIBLE_TARGET = 2.0D;
    private final boolean isCombat;
    private double range = -1.0D;
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    private TargetingConditions.@Nullable Selector selector;

    private TargetingConditions(boolean isCombat) {
        this.isCombat = isCombat;
    }

    public static TargetingConditions forCombat() {
        return new TargetingConditions(true);
    }

    public static TargetingConditions forNonCombat() {
        return new TargetingConditions(false);
    }

    public TargetingConditions copy() {
        TargetingConditions targetingconditions = this.isCombat ? forCombat() : forNonCombat();

        targetingconditions.range = this.range;
        targetingconditions.checkLineOfSight = this.checkLineOfSight;
        targetingconditions.testInvisible = this.testInvisible;
        targetingconditions.selector = this.selector;
        return targetingconditions;
    }

    public TargetingConditions range(double range) {
        this.range = range;
        return this;
    }

    public TargetingConditions ignoreLineOfSight() {
        this.checkLineOfSight = false;
        return this;
    }

    public TargetingConditions ignoreInvisibilityTesting() {
        this.testInvisible = false;
        return this;
    }

    public TargetingConditions selector(TargetingConditions.@Nullable Selector selector) {
        this.selector = selector;
        return this;
    }

    public boolean test(ServerLevel level, @Nullable LivingEntity targeter, LivingEntity target) {
        if (targeter == target) {
            return false;
        } else if (!target.canBeSeenByAnyone()) {
            return false;
        } else if (this.selector != null && !this.selector.test(target, level)) {
            return false;
        } else {
            if (targeter == null) {
                if (this.isCombat && (!target.canBeSeenAsEnemy() || level.getDifficulty() == Difficulty.PEACEFUL)) {
                    return false;
                }
            } else {
                if (this.isCombat && (!targeter.canAttack(target) || !targeter.canAttackType(target.getType()) || targeter.isAlliedTo((Entity) target))) {
                    return false;
                }

                if (this.range > 0.0D) {
                    double d0 = this.testInvisible ? target.getVisibilityPercent(targeter) : 1.0D;
                    double d1 = Math.max(this.range * d0, 2.0D);
                    double d2 = targeter.distanceToSqr(target.getX(), target.getY(), target.getZ());

                    if (d2 > d1 * d1) {
                        return false;
                    }
                }

                if (this.checkLineOfSight && targeter instanceof Mob) {
                    Mob mob = (Mob) targeter;

                    if (!mob.getSensing().hasLineOfSight(target)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    @FunctionalInterface
    public interface Selector {

        boolean test(LivingEntity target, ServerLevel level);
    }
}
