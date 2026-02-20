package net.minecraft.world.entity.ai.behavior;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InsideBrownianWalk {

    public InsideBrownianWalk() {}

    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, pathfindermob, i) -> {
                    if (serverlevel.canSeeSky(pathfindermob.blockPosition())) {
                        return false;
                    } else {
                        BlockPos blockpos = pathfindermob.blockPosition();
                        List<BlockPos> list = (List) BlockPos.betweenClosedStream(blockpos.offset(-1, -1, -1), blockpos.offset(1, 1, 1)).map(BlockPos::immutable).collect(Util.toMutableList());

                        Collections.shuffle(list);
                        list.stream().filter((blockpos1) -> {
                            return !serverlevel.canSeeSky(blockpos1);
                        }).filter((blockpos1) -> {
                            return serverlevel.loadedAndEntityCanStandOn(blockpos1, pathfindermob);
                        }).filter((blockpos1) -> {
                            return serverlevel.noCollision((Entity) pathfindermob);
                        }).findFirst().ifPresent((blockpos1) -> {
                            memoryaccessor.set(new WalkTarget(blockpos1, speedModifier, 0));
                        });
                        return true;
                    }
                };
            });
        });
    }
}
