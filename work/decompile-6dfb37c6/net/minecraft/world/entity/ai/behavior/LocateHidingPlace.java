package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public class LocateHidingPlace {

    public LocateHidingPlace() {}

    public static OneShot<LivingEntity> create(int radius, float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.HOME), behaviorbuilder_instance.registered(MemoryModuleType.HIDING_PLACE), behaviorbuilder_instance.registered(MemoryModuleType.PATH), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.BREED_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.INTERACTION_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3, memoryaccessor4, memoryaccessor5, memoryaccessor6) -> {
                return (serverlevel, livingentity, k) -> {
                    serverlevel.getPoiManager().find((holder) -> {
                        return holder.is(PoiTypes.HOME);
                    }, (blockpos) -> {
                        return true;
                    }, livingentity.blockPosition(), closeEnoughDist + 1, PoiManager.Occupancy.ANY).filter((blockpos) -> {
                        return blockpos.closerToCenterThan(livingentity.position(), (double) closeEnoughDist);
                    }).or(() -> {
                        return serverlevel.getPoiManager().getRandom((holder) -> {
                            return holder.is(PoiTypes.HOME);
                        }, (blockpos) -> {
                            return true;
                        }, PoiManager.Occupancy.ANY, livingentity.blockPosition(), radius, livingentity.getRandom());
                    }).or(() -> {
                        return behaviorbuilder_instance.tryGet(memoryaccessor1).map(GlobalPos::pos);
                    }).ifPresent((blockpos) -> {
                        memoryaccessor3.erase();
                        memoryaccessor4.erase();
                        memoryaccessor5.erase();
                        memoryaccessor6.erase();
                        memoryaccessor2.set(GlobalPos.of(serverlevel.dimension(), blockpos));
                        if (!blockpos.closerToCenterThan(livingentity.position(), (double) closeEnoughDist)) {
                            memoryaccessor.set(new WalkTarget(blockpos, speedModifier, closeEnoughDist));
                        }

                    });
                    return true;
                };
            });
        });
    }
}
