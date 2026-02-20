package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindWater {

    public TryFindWater() {}

    public static BehaviorControl<PathfinderMob> create(int range, float speedModifier) {
        MutableLong mutablelong = new MutableLong(0L);

        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET), behaviorbuilder_instance.registered(MemoryModuleType.LOOK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (serverlevel, pathfindermob, j) -> {
                    if (serverlevel.getFluidState(pathfindermob.blockPosition()).is(FluidTags.WATER)) {
                        return false;
                    } else if (j < mutablelong.longValue()) {
                        mutablelong.setValue(j + 20L + 2L);
                        return true;
                    } else {
                        BlockPos blockpos = null;
                        BlockPos blockpos1 = null;
                        BlockPos blockpos2 = pathfindermob.blockPosition();

                        for (BlockPos blockpos3 : BlockPos.withinManhattan(blockpos2, range, range, range)) {
                            if (blockpos3.getX() != blockpos2.getX() || blockpos3.getZ() != blockpos2.getZ()) {
                                BlockState blockstate = pathfindermob.level().getBlockState(blockpos3.above());
                                BlockState blockstate1 = pathfindermob.level().getBlockState(blockpos3);

                                if (blockstate1.is(Blocks.WATER)) {
                                    if (blockstate.isAir()) {
                                        blockpos = blockpos3.immutable();
                                        break;
                                    }

                                    if (blockpos1 == null && !blockpos3.closerToCenterThan(pathfindermob.position(), 1.5D)) {
                                        blockpos1 = blockpos3.immutable();
                                    }
                                }
                            }
                        }

                        if (blockpos == null) {
                            blockpos = blockpos1;
                        }

                        if (blockpos != null) {
                            memoryaccessor2.set(new BlockPosTracker(blockpos));
                            memoryaccessor1.set(new WalkTarget(new BlockPosTracker(blockpos), speedModifier, 0));
                        }

                        mutablelong.setValue(j + 40L);
                        return true;
                    }
                };
            });
        });
    }
}
