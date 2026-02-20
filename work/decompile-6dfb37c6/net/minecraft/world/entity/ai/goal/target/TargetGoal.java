package net.minecraft.world.entity.ai.goal.target;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

public abstract class TargetGoal extends Goal {

    private static final int EMPTY_REACH_CACHE = 0;
    private static final int CAN_REACH_CACHE = 1;
    private static final int CANT_REACH_CACHE = 2;
    protected final Mob mob;
    protected final boolean mustSee;
    private final boolean mustReach;
    private int reachCache;
    private int reachCacheTime;
    private int unseenTicks;
    protected @Nullable LivingEntity targetMob;
    protected int unseenMemoryTicks;

    public TargetGoal(Mob mob, boolean mustSee) {
        this(mob, mustSee, false);
    }

    public TargetGoal(Mob mob, boolean mustSee, boolean mustReach) {
        this.unseenMemoryTicks = 60;
        this.mob = mob;
        this.mustSee = mustSee;
        this.mustReach = mustReach;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity livingentity = this.mob.getTarget();

        if (livingentity == null) {
            livingentity = this.targetMob;
        }

        if (livingentity == null) {
            return false;
        } else if (!this.mob.canAttack(livingentity)) {
            return false;
        } else {
            Team team = this.mob.getTeam();
            Team team1 = livingentity.getTeam();

            if (team != null && team1 == team) {
                return false;
            } else {
                double d0 = this.getFollowDistance();

                if (this.mob.distanceToSqr((Entity) livingentity) > d0 * d0) {
                    return false;
                } else {
                    if (this.mustSee) {
                        if (this.mob.getSensing().hasLineOfSight(livingentity)) {
                            this.unseenTicks = 0;
                        } else if (++this.unseenTicks > reducedTickDelay(this.unseenMemoryTicks)) {
                            return false;
                        }
                    }

                    this.mob.setTarget(livingentity);
                    return true;
                }
            }
        }
    }

    protected double getFollowDistance() {
        return this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public void start() {
        this.reachCache = 0;
        this.reachCacheTime = 0;
        this.unseenTicks = 0;
    }

    @Override
    public void stop() {
        this.mob.setTarget((LivingEntity) null);
        this.targetMob = null;
    }

    protected boolean canAttack(@Nullable LivingEntity target, TargetingConditions targetConditions) {
        if (target == null) {
            return false;
        } else if (!targetConditions.test(getServerLevel((Entity) this.mob), this.mob, target)) {
            return false;
        } else if (!this.mob.isWithinHome(target.blockPosition())) {
            return false;
        } else {
            if (this.mustReach) {
                if (--this.reachCacheTime <= 0) {
                    this.reachCache = 0;
                }

                if (this.reachCache == 0) {
                    this.reachCache = this.canReach(target) ? 1 : 2;
                }

                if (this.reachCache == 2) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean canReach(LivingEntity target) {
        this.reachCacheTime = reducedTickDelay(10 + this.mob.getRandom().nextInt(5));
        Path path = this.mob.getNavigation().createPath(target, 0);

        if (path == null) {
            return false;
        } else {
            Node node = path.getEndNode();

            if (node == null) {
                return false;
            } else {
                int i = node.x - target.getBlockX();
                int j = node.z - target.getBlockZ();

                return (double) (i * i + j * j) <= 2.25D;
            }
        }
    }

    public TargetGoal setUnseenMemoryTicks(int unseenMemoryTicks) {
        this.unseenMemoryTicks = unseenMemoryTicks;
        return this;
    }
}
