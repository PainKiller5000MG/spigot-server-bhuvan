package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DismountOrSkipMounting {

    public DismountOrSkipMounting() {}

    public static <E extends LivingEntity> BehaviorControl<E> create(int maxWalkDistToRideTarget, BiPredicate<E, Entity> dontRideIf) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.RIDE_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, livingentity, j) -> {
                    Entity entity = livingentity.getVehicle();
                    Entity entity1 = (Entity) behaviorbuilder_instance.tryGet(memoryaccessor).orElse((Object) null);

                    if (entity == null && entity1 == null) {
                        return false;
                    } else {
                        Entity entity2 = entity == null ? entity1 : entity;

                        if (isVehicleValid(livingentity, entity2, maxWalkDistToRideTarget) && !dontRideIf.test(livingentity, entity2)) {
                            return false;
                        } else {
                            livingentity.stopRiding();
                            memoryaccessor.erase();
                            return true;
                        }
                    }
                };
            });
        });
    }

    private static boolean isVehicleValid(LivingEntity body, Entity vehicle, int maxWalkDistToRideTarget) {
        return vehicle.isAlive() && vehicle.closerThan(body, (double) maxWalkDistToRideTarget) && vehicle.level() == body.level();
    }
}
