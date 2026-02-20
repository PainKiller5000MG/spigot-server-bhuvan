package net.minecraft.world.entity.ai.goal;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;

public class RestrictSunGoal extends Goal {

    private final PathfinderMob mob;

    public RestrictSunGoal(PathfinderMob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return this.mob.level().isBrightOutside() && this.mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && GoalUtils.hasGroundPathNavigation(this.mob);
    }

    @Override
    public void start() {
        PathNavigation pathnavigation = this.mob.getNavigation();

        if (pathnavigation instanceof GroundPathNavigation groundpathnavigation) {
            groundpathnavigation.setAvoidSun(true);
        }

    }

    @Override
    public void stop() {
        if (GoalUtils.hasGroundPathNavigation(this.mob)) {
            PathNavigation pathnavigation = this.mob.getNavigation();

            if (pathnavigation instanceof GroundPathNavigation) {
                GroundPathNavigation groundpathnavigation = (GroundPathNavigation) pathnavigation;

                groundpathnavigation.setAvoidSun(false);
            }
        }

    }
}
