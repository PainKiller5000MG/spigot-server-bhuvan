package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class ResetProfession {

    public ResetProfession() {}

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.JOB_SITE)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, villager, i) -> {
                    VillagerData villagerdata = villager.getVillagerData();
                    boolean flag = !villagerdata.profession().is(VillagerProfession.NONE) && !villagerdata.profession().is(VillagerProfession.NITWIT);

                    if (flag && villager.getVillagerXp() == 0 && villagerdata.level() <= 1) {
                        villager.setVillagerData(villager.getVillagerData().withProfession(serverlevel.registryAccess(), VillagerProfession.NONE));
                        villager.refreshBrain(serverlevel);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
