package net.minecraft.world.entity.monster.piglin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopHoldingItemIfNoLongerAdmiring {

    public StopHoldingItemIfNoLongerAdmiring() {}

    public static BehaviorControl<Piglin> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.ADMIRING_ITEM)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, piglin, i) -> {
                    if (!piglin.getOffhandItem().isEmpty() && !piglin.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
                        PiglinAi.stopHoldingOffHandItem(serverlevel, piglin, true);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
