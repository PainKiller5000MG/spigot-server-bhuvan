package net.minecraft.world.entity.ai.sensing;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class AxolotlAttackablesSensor extends NearestVisibleLivingEntitySensor {

    public static final float TARGET_DETECTION_DISTANCE = 8.0F;

    public AxolotlAttackablesSensor() {}

    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity mob) {
        return this.isClose(body, mob) && mob.isInWater() && (this.isHostileTarget(mob) || this.isHuntTarget(body, mob)) && Sensor.isEntityAttackable(level, body, mob);
    }

    private boolean isHuntTarget(LivingEntity body, LivingEntity mob) {
        return !body.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && mob.getType().is(EntityTypeTags.AXOLOTL_HUNT_TARGETS);
    }

    private boolean isHostileTarget(LivingEntity mob) {
        return mob.getType().is(EntityTypeTags.AXOLOTL_ALWAYS_HOSTILES);
    }

    private boolean isClose(LivingEntity body, LivingEntity mob) {
        return mob.distanceToSqr((Entity) body) <= 64.0D;
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
