package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpearAttack extends Behavior<PathfinderMob> {

    public static final int MIN_REPOSITION_DISTANCE = 6;
    public static final int MAX_REPOSITION_DISTANCE = 7;
    double speedModifierWhenCharging;
    double speedModifierWhenRepositioning;
    float approachDistanceSq;
    float targetInRangeRadiusSq;

    public SpearAttack(double speedModifierWhenCharging, double speedModifierWhenRepositioning, float approachDistance, float targetInRangeRadius) {
        super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_PRESENT));
        this.speedModifierWhenCharging = speedModifierWhenCharging;
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
        this.approachDistanceSq = approachDistance * approachDistance;
        this.targetInRangeRadiusSq = targetInRangeRadius * targetInRangeRadius;
    }

    private @Nullable LivingEntity getTarget(PathfinderMob mob) {
        return (LivingEntity) mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse((Object) null);
    }

    private boolean ableToAttack(PathfinderMob mob) {
        return this.getTarget(mob) != null && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    private int getKineticWeaponUseDuration(PathfinderMob mob) {
        return (Integer) Optional.ofNullable((KineticWeapon) mob.getMainHandItem().get(DataComponents.KINETIC_WEAPON)).map(KineticWeapon::computeDamageUseDuration).orElse(0);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
        return body.getBrain().getMemory(MemoryModuleType.SPEAR_STATUS).orElse(SpearAttack.SpearStatus.APPROACH) == SpearAttack.SpearStatus.CHARGING && this.ableToAttack(body) && !body.isUsingItem();
    }

    protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
        body.setAggressive(true);
        body.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, this.getKineticWeaponUseDuration(body));
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
        body.startUsingItem(InteractionHand.MAIN_HAND);
        super.start(level, body, timestamp);
    }

    protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
        return (Integer) body.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) > 0 && this.ableToAttack(body);
    }

    protected void tick(ServerLevel level, PathfinderMob mob, long timestamp) {
        LivingEntity livingentity = this.getTarget(mob);
        double d0 = mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
        Entity entity = mob.getRootVehicle();
        float f = 1.0F;

        if (entity instanceof Mob mob1) {
            f = mob1.chargeSpeedModifier();
        }

        int j = mob.isPassenger() ? 2 : 0;

        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(livingentity, true));
        mob.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, (Integer) mob.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) - 1);
        Vec3 vec3 = (Vec3) mob.getBrain().getMemory(MemoryModuleType.SPEAR_CHARGE_POSITION).orElse((Object) null);

        if (vec3 != null) {
            mob.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, (double) f * this.speedModifierWhenRepositioning);
            if (mob.getNavigation().isDone()) {
                mob.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
            }
        } else {
            mob.getNavigation().moveTo((Entity) livingentity, (double) f * this.speedModifierWhenCharging);
            if (d0 < (double) this.targetInRangeRadiusSq || mob.getNavigation().isDone()) {
                double d1 = Math.sqrt(d0);
                Vec3 vec31 = LandRandomPos.getPosAway(mob, (double) (6 + j) - d1, (double) (7 + j) - d1, 7, livingentity.position());

                mob.getBrain().setMemory(MemoryModuleType.SPEAR_CHARGE_POSITION, vec31);
            }
        }

    }

    protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
        body.getNavigation().stop();
        body.stopUsingItem();
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_ENGAGE_TIME);
        body.getBrain().setMemory(MemoryModuleType.SPEAR_STATUS, SpearAttack.SpearStatus.RETREAT);
    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    public static enum SpearStatus {

        APPROACH, CHARGING, RETREAT;

        private SpearStatus() {}
    }
}
