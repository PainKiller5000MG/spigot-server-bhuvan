package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.kinds.K1;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;

public class GoToWantedItem {

    public GoToWantedItem() {}

    public static BehaviorControl<LivingEntity> create(float speedModifier, boolean interruptOngoingWalk, int maxDistToWalk) {
        return create((livingentity) -> {
            return true;
        }, speedModifier, interruptOngoingWalk, maxDistToWalk);
    }

    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> predicate, float speedModifier, boolean interruptOngoingWalk, int maxDistToWalk) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            BehaviorBuilder<E, ? extends MemoryAccessor<? extends K1, WalkTarget>> behaviorbuilder = interruptOngoingWalk ? behaviorbuilder_instance.registered(MemoryModuleType.WALK_TARGET) : behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET);

            return behaviorbuilder_instance.group(behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET), behaviorbuilder, behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM), behaviorbuilder_instance.registered(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, livingentity, j) -> {
                    ItemEntity itementity = (ItemEntity) behaviorbuilder_instance.get(memoryaccessor2);

                    if (behaviorbuilder_instance.tryGet(memoryaccessor3).isEmpty() && predicate.test(livingentity) && itementity.closerThan(livingentity, (double) maxDistToWalk) && livingentity.level().getWorldBorder().isWithinBounds(itementity.blockPosition()) && livingentity.canPickUpLoot()) {
                        WalkTarget walktarget = new WalkTarget(new EntityTracker(itementity, false), speedModifier, 0);

                        memoryaccessor.set(new EntityTracker(itementity, true));
                        memoryaccessor1.set(walktarget);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
