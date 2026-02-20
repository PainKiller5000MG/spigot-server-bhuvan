package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Function3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class BecomePassiveIfMemoryPresent {

    public BecomePassiveIfMemoryPresent() {}

    public static BehaviorControl<LivingEntity> create(MemoryModuleType<?> pacifyingMemory, int pacifyDuration) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.PACIFIED), behaviorbuilder_instance.present(pacifyingMemory)).apply(behaviorbuilder_instance, behaviorbuilder_instance.point(() -> {
                return "[BecomePassive if " + String.valueOf(pacifyingMemory) + " present]";
            }, (Function3) (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, livingentity, j) -> {
                    memoryaccessor1.setWithExpiry(true, (long) pacifyDuration);
                    memoryaccessor.erase();
                    return true;
                };
            }));
        });
    }
}
