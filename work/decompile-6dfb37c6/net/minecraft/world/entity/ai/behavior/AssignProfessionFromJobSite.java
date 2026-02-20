package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class AssignProfessionFromJobSite {

    public AssignProfessionFromJobSite() {}

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.POTENTIAL_JOB_SITE), behaviorbuilder_instance.registered(MemoryModuleType.JOB_SITE)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, villager, i) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor);

                    if (!globalpos.pos().closerToCenterThan(villager.position(), 2.0D) && !villager.assignProfessionWhenSpawned()) {
                        return false;
                    } else {
                        memoryaccessor.erase();
                        memoryaccessor1.set(globalpos);
                        serverlevel.broadcastEntityEvent(villager, (byte) 14);
                        if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
                            return true;
                        } else {
                            MinecraftServer minecraftserver = serverlevel.getServer();

                            Optional.ofNullable(minecraftserver.getLevel(globalpos.dimension())).flatMap((serverlevel1) -> {
                                return serverlevel1.getPoiManager().getType(globalpos.pos());
                            }).flatMap((holder) -> {
                                return BuiltInRegistries.VILLAGER_PROFESSION.listElements().filter((holder_reference) -> {
                                    return ((VillagerProfession) holder_reference.value()).heldJobSite().test(holder);
                                }).findFirst();
                            }).ifPresent((holder_reference) -> {
                                villager.setVillagerData(villager.getVillagerData().withProfession(holder_reference));
                                villager.refreshBrain(serverlevel);
                            });
                            return true;
                        }
                    }
                };
            });
        });
    }
}
