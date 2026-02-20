package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.apache.commons.lang3.mutable.MutableInt;

public class SetHiddenState {

    private static final int HIDE_TIMEOUT = 300;

    public SetHiddenState() {}

    public static BehaviorControl<LivingEntity> create(int seconds, int closeEnoughDist) {
        int k = seconds * 20;
        MutableInt mutableint = new MutableInt(0);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(MemoryModuleType.HIDING_PLACE), behaviorbuilder_instance.present(MemoryModuleType.HEARD_BELL_TIME)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1) -> {
                return (serverlevel, livingentity, l) -> {
                    long i1 = (Long) behaviorbuilder_instance.get(memoryaccessor1);
                    boolean flag = i1 + 300L <= l;

                    if (mutableint.intValue() <= k && !flag) {
                        BlockPos blockpos = ((GlobalPos) behaviorbuilder_instance.get(memoryaccessor)).pos();

                        if (blockpos.closerThan(livingentity.blockPosition(), (double) closeEnoughDist)) {
                            mutableint.increment();
                        }

                        return true;
                    } else {
                        memoryaccessor1.erase();
                        memoryaccessor.erase();
                        livingentity.getBrain().updateActivityFromSchedule(serverlevel.environmentAttributes(), serverlevel.getGameTime(), livingentity.position());
                        mutableint.setValue(0);
                        return true;
                    }
                };
            });
        });
    }
}
