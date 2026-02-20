package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpearRetreat extends Behavior<PathfinderMob> {

    public static final int MIN_COOLDOWN_DISTANCE = 9;
    public static final int MAX_COOLDOWN_DISTANCE = 11;
    public static final int MAX_FLEEING_TIME = 100;
    double speedModifierWhenRepositioning;

    public SpearRetreat(double speedModifierWhenRepositioning) {
        super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_PRESENT), 100);
        this.speedModifierWhenRepositioning = speedModifierWhenRepositioning;
    }

    private @Nullable LivingEntity getTarget(PathfinderMob mob) {
        return (LivingEntity) mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse((Object) null);
    }

    private boolean ableToAttack(PathfinderMob mob) {
        return this.getTarget(mob) != null && mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob body) {
        if (this.ableToAttack(body) && !body.isUsingItem()) {
            if (body.getBrain().getMemory(MemoryModuleType.SPEAR_STATUS).orElse(SpearAttack.SpearStatus.APPROACH) != SpearAttack.SpearStatus.RETREAT) {
                return false;
            } else {
                LivingEntity livingentity = this.getTarget(body);
                double d0 = body.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
                int i = body.isPassenger() ? 2 : 0;
                double d1 = Math.sqrt(d0);
                Vec3 vec3 = LandRandomPos.getPosAway(body, Math.max(0.0D, (double) (9 + i) - d1), Math.max(1.0D, (double) (11 + i) - d1), 7, livingentity.position());

                if (vec3 == null) {
                    return false;
                } else {
                    body.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_POSITION, vec3);
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    protected void start(ServerLevel level, PathfinderMob body, long timestamp) {
        body.setAggressive(true);
        body.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_TIME, 0);
        super.start(level, body, timestamp);
    }

    protected boolean canStillUse(ServerLevel level, PathfinderMob body, long timestamp) {
        return (Integer) body.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_TIME).orElse(100) < 100 && body.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_POSITION).isPresent() && !body.getNavigation().isDone() && this.ableToAttack(body);
    }

    protected void tick(ServerLevel level, PathfinderMob mob, long timestamp) {
        LivingEntity livingentity = this.getTarget(mob);
        Entity entity = mob.getRootVehicle();
        float f;

        if (entity instanceof Mob mob1) {
            f = mob1.chargeSpeedModifier();
        } else {
            f = 1.0F;
        }

        float f1 = f;

        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(livingentity, true));
        mob.getBrain().setMemory(MemoryModuleType.SPEAR_FLEEING_TIME, (Integer) mob.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_TIME).orElse(0) + 1);
        mob.getBrain().getMemory(MemoryModuleType.SPEAR_FLEEING_POSITION).ifPresent((vec3) -> {
            mob.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, (double) f1 * this.speedModifierWhenRepositioning);
        });
    }

    protected void stop(ServerLevel level, PathfinderMob body, long timestamp) {
        body.getNavigation().stop();
        body.setAggressive(false);
        body.stopUsingItem();
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_FLEEING_TIME);
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_FLEEING_POSITION);
        body.getBrain().eraseMemory(MemoryModuleType.SPEAR_STATUS);
    }
}
