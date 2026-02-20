package net.minecraft.world.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.frog.Frog;

public class FrogAttackablesSensor extends NearestVisibleLivingEntitySensor {

    public static final float TARGET_DETECTION_DISTANCE = 10.0F;

    public FrogAttackablesSensor() {}

    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity mob) {
        return !body.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && Sensor.isEntityAttackable(level, body, mob) && Frog.canEat(mob) && !this.isUnreachableAttackTarget(body, mob) ? mob.closerThan(body, 10.0D) : false;
    }

    private boolean isUnreachableAttackTarget(LivingEntity body, LivingEntity mob) {
        List<UUID> list = (List) body.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);

        return list.contains(mob.getUUID());
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
