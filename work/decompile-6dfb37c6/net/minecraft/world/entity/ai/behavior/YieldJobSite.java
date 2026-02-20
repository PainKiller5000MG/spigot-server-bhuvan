package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;

public class YieldJobSite {

    public YieldJobSite() {}

    public static BehaviorControl<Villager> create(float speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.POTENTIAL_JOB_SITE), behaviorbuilder_instance.absent(MemoryModuleType.JOB_SITE), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_LIVING_ENTITIES), behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3, memoryaccessor4) -> {
                return (serverlevel, villager, i) -> {
                    if (villager.isBaby()) {
                        return false;
                    } else if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
                        return false;
                    } else {
                        BlockPos blockpos = ((GlobalPos) behaviorbuilder_instance.get(memoryaccessor)).pos();
                        Optional<Holder<PoiType>> optional = serverlevel.getPoiManager().getType(blockpos);

                        if (optional.isEmpty()) {
                            return true;
                        } else {
                            ((List) behaviorbuilder_instance.get(memoryaccessor2)).stream().filter((livingentity) -> {
                                return livingentity instanceof Villager && livingentity != villager;
                            }).map((livingentity) -> {
                                return (Villager) livingentity;
                            }).filter(LivingEntity::isAlive).filter((villager1) -> {
                                return nearbyWantsJobsite((Holder) optional.get(), villager1, blockpos);
                            }).findFirst().ifPresent((villager1) -> {
                                memoryaccessor3.erase();
                                memoryaccessor4.erase();
                                memoryaccessor.erase();
                                if (villager1.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
                                    BehaviorUtils.setWalkAndLookTargetMemories(villager1, blockpos, speedModifier, 1);
                                    villager1.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(serverlevel.dimension(), blockpos));
                                    serverlevel.debugSynchronizers().updatePoi(blockpos);
                                }

                            });
                            return true;
                        }
                    }
                };
            });
        });
    }

    private static boolean nearbyWantsJobsite(Holder<PoiType> type, Villager nearbyVillager, BlockPos poiPos) {
        boolean flag = nearbyVillager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent();

        if (flag) {
            return false;
        } else {
            Optional<GlobalPos> optional = nearbyVillager.getBrain().<GlobalPos>getMemory(MemoryModuleType.JOB_SITE);
            Holder<VillagerProfession> holder1 = nearbyVillager.getVillagerData().profession();

            return ((VillagerProfession) holder1.value()).heldJobSite().test(type) ? (optional.isEmpty() ? canReachPos(nearbyVillager, poiPos, type.value()) : ((GlobalPos) optional.get()).pos().equals(poiPos)) : false;
        }
    }

    private static boolean canReachPos(PathfinderMob nearbyVillager, BlockPos poiPos, PoiType type) {
        Path path = nearbyVillager.getNavigation().createPath(poiPos, type.validRange());

        return path != null && path.canReach();
    }
}
