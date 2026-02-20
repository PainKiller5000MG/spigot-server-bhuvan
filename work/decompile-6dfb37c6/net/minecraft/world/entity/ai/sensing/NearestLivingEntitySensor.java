package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.phys.AABB;

public class NearestLivingEntitySensor<T extends LivingEntity> extends Sensor<T> {

    public NearestLivingEntitySensor() {}

    @Override
    protected void doTick(ServerLevel level, T body) {
        double d0 = ((LivingEntity) body).getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB aabb = body.getBoundingBox().inflate(d0, d0, d0);
        List<LivingEntity> list = level.<LivingEntity>getEntitiesOfClass(LivingEntity.class, aabb, (livingentity) -> {
            return livingentity != body && livingentity.isAlive();
        });

        Objects.requireNonNull(body);
        list.sort(Comparator.comparingDouble(body::distanceToSqr));
        Brain<?> brain = ((LivingEntity) body).getBrain();

        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, list);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, body, list));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }
}
