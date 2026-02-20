package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public class UpdateActivityFromSchedule {

    public UpdateActivityFromSchedule() {}

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.point((Trigger) (serverlevel, livingentity, i) -> {
                livingentity.getBrain().updateActivityFromSchedule(serverlevel.environmentAttributes(), serverlevel.getGameTime(), livingentity.position());
                return true;
            });
        });
    }
}
