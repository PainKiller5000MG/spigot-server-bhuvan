package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class VillagerHostilesSensor extends NearestVisibleLivingEntitySensor {

    private static final ImmutableMap<EntityType<?>, Float> ACCEPTABLE_DISTANCE_FROM_HOSTILES = ImmutableMap.builder().put(EntityType.DROWNED, 8.0F).put(EntityType.EVOKER, 12.0F).put(EntityType.HUSK, 8.0F).put(EntityType.ILLUSIONER, 12.0F).put(EntityType.PILLAGER, 15.0F).put(EntityType.RAVAGER, 12.0F).put(EntityType.VEX, 8.0F).put(EntityType.VINDICATOR, 10.0F).put(EntityType.ZOGLIN, 10.0F).put(EntityType.ZOMBIE, 8.0F).put(EntityType.ZOMBIE_VILLAGER, 8.0F).build();

    public VillagerHostilesSensor() {}

    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity mob) {
        return this.isHostile(mob) && this.isClose(body, mob);
    }

    private boolean isClose(LivingEntity body, LivingEntity mob) {
        float f = (Float) VillagerHostilesSensor.ACCEPTABLE_DISTANCE_FROM_HOSTILES.get(mob.getType());

        return mob.distanceToSqr((Entity) body) <= (double) (f * f);
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_HOSTILE;
    }

    private boolean isHostile(LivingEntity entity) {
        return VillagerHostilesSensor.ACCEPTABLE_DISTANCE_FROM_HOSTILES.containsKey(entity.getType());
    }
}
