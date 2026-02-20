package net.minecraft.world.entity.ai.sensing;

import java.util.Optional;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensorAnyType extends AdultSensor {

    public AdultSensorAnyType() {}

    @Override
    protected void setNearestVisibleAdult(LivingEntity body, NearestVisibleLivingEntities visibleLivingEntities) {
        Optional<LivingEntity> optional = visibleLivingEntities.findClosest((livingentity1) -> {
            return livingentity1.getType().is(EntityTypeTags.FOLLOWABLE_FRIENDLY_MOBS) && !livingentity1.isBaby();
        });

        body.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
    }
}
