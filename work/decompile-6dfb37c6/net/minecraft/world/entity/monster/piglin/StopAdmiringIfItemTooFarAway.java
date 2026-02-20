package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class StopAdmiringIfItemTooFarAway<E extends Piglin> {

    public StopAdmiringIfItemTooFarAway() {}

    public static BehaviorControl<LivingEntity> create(int maxDistanceToItem) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ADMIRING_ITEM), behaviorbuilder_instance.registered(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, j) -> {
                    if (!livingentity.getOffhandItem().isEmpty()) {
                        return false;
                    } else {
                        Optional<ItemEntity> optional = behaviorbuilder_instance.<ItemEntity>tryGet(memoryaccessor1);

                        if (optional.isPresent() && ((ItemEntity) optional.get()).closerThan(livingentity, (double) maxDistanceToItem)) {
                            return false;
                        } else {
                            memoryaccessor.erase();
                            return true;
                        }
                    }
                };
            });
        });
    }
}
