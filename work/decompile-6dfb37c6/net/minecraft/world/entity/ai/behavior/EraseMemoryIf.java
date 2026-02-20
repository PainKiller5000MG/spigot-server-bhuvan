package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class EraseMemoryIf {

    public EraseMemoryIf() {}

    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> predicate, MemoryModuleType<?> memoryType) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(memoryType)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, livingentity, i) -> {
                    if (predicate.test(livingentity)) {
                        memoryaccessor.erase();
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
