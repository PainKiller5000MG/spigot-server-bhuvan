package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StopAdmiringIfTiredOfTryingToReachItem {

    public StopAdmiringIfTiredOfTryingToReachItem() {}

    public static BehaviorControl<LivingEntity> create(int maxTimeToReachItem, int disableTime) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.ADMIRING_ITEM), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM), behaviorbuilder_instance.registered(MemoryModuleType.TIME_TRYING_TO_REACH_ADMIRE_ITEM), behaviorbuilder_instance.registered(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, livingentity, k) -> {
                    if (!livingentity.getOffhandItem().isEmpty()) {
                        return false;
                    } else {
                        Optional<Integer> optional = behaviorbuilder_instance.<Integer>tryGet(memoryaccessor2);

                        if (optional.isEmpty()) {
                            memoryaccessor2.set(0);
                        } else {
                            int l = (Integer) optional.get();

                            if (l > maxTimeToReachItem) {
                                memoryaccessor.erase();
                                memoryaccessor2.erase();
                                memoryaccessor3.setWithExpiry(true, (long) disableTime);
                            } else {
                                memoryaccessor2.set(l + 1);
                            }
                        }

                        return true;
                    }
                };
            });
        });
    }
}
