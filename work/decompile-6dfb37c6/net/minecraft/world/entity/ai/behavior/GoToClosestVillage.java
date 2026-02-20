package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

public class GoToClosestVillage {

    public GoToClosestVillage() {}

    public static BehaviorControl<Villager> create(float speedModifier, int closeEnoughDistance) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, villager, j) -> {
                    if (serverlevel.isVillage(villager.blockPosition())) {
                        return false;
                    } else {
                        PoiManager poimanager = serverlevel.getPoiManager();
                        int k = poimanager.sectionsToVillage(SectionPos.of(villager.blockPosition()));
                        Vec3 vec3 = null;

                        for (int l = 0; l < 5; ++l) {
                            Vec3 vec31 = LandRandomPos.getPos(villager, 15, 7, (blockpos) -> {
                                return (double) (-poimanager.sectionsToVillage(SectionPos.of(blockpos)));
                            });

                            if (vec31 != null) {
                                int i1 = poimanager.sectionsToVillage(SectionPos.of(BlockPos.containing(vec31)));

                                if (i1 < k) {
                                    vec3 = vec31;
                                    break;
                                }

                                if (i1 == k) {
                                    vec3 = vec31;
                                }
                            }
                        }

                        if (vec3 != null) {
                            memoryaccessor.set(new WalkTarget(vec3, speedModifier, closeEnoughDistance));
                        }

                        return true;
                    }
                };
            });
        });
    }
}
