package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class GoToTargetLocation {

    public GoToTargetLocation() {}

    private static BlockPos getNearbyPos(Mob body, BlockPos pos) {
        RandomSource randomsource = body.level().random;

        return pos.offset(getRandomOffset(randomsource), 0, getRandomOffset(randomsource));
    }

    private static int getRandomOffset(RandomSource random) {
        return random.nextInt(3) - 1;
    }

    public static <E extends Mob> OneShot<E> create(MemoryModuleType<BlockPos> locationMemory, int closeEnoughDist, float speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(locationMemory), behaviorbuilder_instance.absent(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2, memoryaccessor3) -> {
                return (serverlevel, mob, j) -> {
                    BlockPos blockpos = (BlockPos) behaviorbuilder_instance.get(memoryaccessor);
                    boolean flag = blockpos.closerThan(mob.blockPosition(), (double) closeEnoughDist);

                    if (!flag) {
                        BehaviorUtils.setWalkAndLookTargetMemories(mob, getNearbyPos(mob, blockpos), speedModifier, closeEnoughDist);
                    }

                    return true;
                };
            });
        });
    }
}
