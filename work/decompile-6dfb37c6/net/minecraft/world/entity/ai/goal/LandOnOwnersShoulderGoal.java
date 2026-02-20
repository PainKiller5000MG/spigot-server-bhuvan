package net.minecraft.world.entity.ai.goal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;

public class LandOnOwnersShoulderGoal extends Goal {

    private final ShoulderRidingEntity entity;
    private boolean isSittingOnShoulder;

    public LandOnOwnersShoulderGoal(ShoulderRidingEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        LivingEntity livingentity = this.entity.getOwner();

        if (!(livingentity instanceof ServerPlayer serverplayer)) {
            return false;
        } else {
            boolean flag = !serverplayer.isSpectator() && !serverplayer.getAbilities().flying && !serverplayer.isInWater() && !serverplayer.isInPowderSnow;

            return !this.entity.isOrderedToSit() && flag && this.entity.canSitOnShoulder();
        }
    }

    @Override
    public boolean isInterruptable() {
        return !this.isSittingOnShoulder;
    }

    @Override
    public void start() {
        this.isSittingOnShoulder = false;
    }

    @Override
    public void tick() {
        if (!this.isSittingOnShoulder && !this.entity.isInSittingPose() && !this.entity.isLeashed()) {
            LivingEntity livingentity = this.entity.getOwner();

            if (livingentity instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) livingentity;

                if (this.entity.getBoundingBox().intersects(serverplayer.getBoundingBox())) {
                    this.isSittingOnShoulder = this.entity.setEntityOnShoulder(serverplayer);
                }
            }

        }
    }
}
