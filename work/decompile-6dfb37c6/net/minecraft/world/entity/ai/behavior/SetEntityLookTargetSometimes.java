package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

/** @deprecated */
@Deprecated
public class SetEntityLookTargetSometimes {

    public SetEntityLookTargetSometimes() {}

    public static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval) {
        return create(maxDist, interval, (livingentity) -> {
            return true;
        });
    }

    public static BehaviorControl<LivingEntity> create(EntityType<?> type, float maxDist, UniformInt interval) {
        return create(maxDist, interval, (livingentity) -> {
            return type.equals(livingentity.getType());
        });
    }

    private static BehaviorControl<LivingEntity> create(float maxDist, UniformInt interval, Predicate<LivingEntity> predicate) {
        float f1 = maxDist * maxDist;
        SetEntityLookTargetSometimes.Ticker setentitylooktargetsometimes_ticker = new SetEntityLookTargetSometimes.Ticker(interval);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.LOOK_TARGET), behaviorbuilder_instance.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, i) -> {
                    Optional<LivingEntity> optional = ((NearestVisibleLivingEntities) behaviorbuilder_instance.get(memoryaccessor1)).findClosest(predicate.and((livingentity1) -> {
                        return livingentity1.distanceToSqr((Entity) livingentity) <= (double) f1;
                    }));

                    if (optional.isEmpty()) {
                        return false;
                    } else if (!setentitylooktargetsometimes_ticker.tickDownAndCheck(serverlevel.random)) {
                        return false;
                    } else {
                        memoryaccessor.set(new EntityTracker((Entity) optional.get(), true));
                        return true;
                    }
                };
            });
        });
    }

    public static final class Ticker {

        private final UniformInt interval;
        private int ticksUntilNextStart;

        public Ticker(UniformInt interval) {
            if (interval.getMinValue() <= 1) {
                throw new IllegalArgumentException();
            } else {
                this.interval = interval;
            }
        }

        public boolean tickDownAndCheck(RandomSource random) {
            if (this.ticksUntilNextStart == 0) {
                this.ticksUntilNextStart = this.interval.sample(random) - 1;
                return false;
            } else {
                return --this.ticksUntilNextStart == 0;
            }
        }
    }
}
