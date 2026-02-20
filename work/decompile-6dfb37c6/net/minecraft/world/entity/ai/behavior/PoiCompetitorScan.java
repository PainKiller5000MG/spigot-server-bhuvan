package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class PoiCompetitorScan {

    public PoiCompetitorScan() {}

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.JOB_SITE), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, villager, i) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor);

                    serverlevel.getPoiManager().getType(globalpos.pos()).ifPresent((holder) -> {
                        ((List) behaviorbuilder_instance.get(memoryaccessor1)).stream().filter((livingentity) -> {
                            return livingentity instanceof Villager && livingentity != villager;
                        }).map((livingentity) -> {
                            return (Villager) livingentity;
                        }).filter(LivingEntity::isAlive).filter((villager1) -> {
                            return competesForSameJobsite(globalpos, holder, villager1);
                        }).reduce(villager, PoiCompetitorScan::selectWinner);
                    });
                    return true;
                };
            });
        });
    }

    private static Villager selectWinner(Villager first, Villager second) {
        Villager villager2;
        Villager villager3;

        if (first.getVillagerXp() > second.getVillagerXp()) {
            villager2 = first;
            villager3 = second;
        } else {
            villager2 = second;
            villager3 = first;
        }

        villager3.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        return villager2;
    }

    private static boolean competesForSameJobsite(GlobalPos pos, Holder<PoiType> poiType, Villager nearbyVillager) {
        Optional<GlobalPos> optional = nearbyVillager.getBrain().<GlobalPos>getMemory(MemoryModuleType.JOB_SITE);

        return optional.isPresent() && pos.equals(optional.get()) && hasMatchingProfession(poiType, nearbyVillager.getVillagerData().profession());
    }

    private static boolean hasMatchingProfession(Holder<PoiType> poiType, Holder<VillagerProfession> profession) {
        return ((VillagerProfession) profession.value()).heldJobSite().test(poiType);
    }
}
