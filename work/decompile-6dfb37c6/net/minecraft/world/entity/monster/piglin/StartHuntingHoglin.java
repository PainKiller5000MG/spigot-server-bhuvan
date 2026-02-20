package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;

public class StartHuntingHoglin {

    public StartHuntingHoglin() {}

    public static OneShot<Piglin> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_HUNTABLE_HOGLIN), behaviorbuilder_instance.absent(MemoryModuleType.ANGRY_AT), behaviorbuilder_instance.absent(MemoryModuleType.HUNTED_RECENTLY), behaviorbuilder_instance.registered(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLINS)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, piglin, i) -> {
                    if (!piglin.isBaby() && !behaviorbuilder_instance.tryGet(memoryaccessor3).map((list) -> {
                        return list.stream().anyMatch(StartHuntingHoglin::hasHuntedRecently);
                    }).isPresent()) {
                        Hoglin hoglin = (Hoglin) behaviorbuilder_instance.get(memoryaccessor);

                        PiglinAi.setAngerTarget(serverlevel, piglin, hoglin);
                        PiglinAi.dontKillAnyMoreHoglinsForAWhile(piglin);
                        PiglinAi.broadcastAngerTarget(serverlevel, piglin, hoglin);
                        behaviorbuilder_instance.tryGet(memoryaccessor3).ifPresent((list) -> {
                            list.forEach(PiglinAi::dontKillAnyMoreHoglinsForAWhile);
                        });
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }

    private static boolean hasHuntedRecently(AbstractPiglin otherPiglin) {
        return otherPiglin.getBrain().hasMemoryValue(MemoryModuleType.HUNTED_RECENTLY);
    }
}
