package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class CopyMemoryWithExpiry {

    public CopyMemoryWithExpiry() {}

    public static <E extends LivingEntity, T> BehaviorControl<E> create(Predicate<E> copyIfTrue, MemoryModuleType<? extends T> sourceMemory, MemoryModuleType<T> targetMemory, UniformInt durationOfCopy) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(sourceMemory), behaviorbuilder_instance.absent(targetMemory)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, i) -> {
                    if (!copyIfTrue.test(livingentity)) {
                        return false;
                    } else {
                        memoryaccessor1.setWithExpiry(behaviorbuilder_instance.get(memoryaccessor), (long) durationOfCopy.sample(serverlevel.random));
                        return true;
                    }
                };
            });
        });
    }
}
