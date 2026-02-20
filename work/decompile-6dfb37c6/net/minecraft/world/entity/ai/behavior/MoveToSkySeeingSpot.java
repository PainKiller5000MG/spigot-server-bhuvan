package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MoveToSkySeeingSpot {

    public MoveToSkySeeingSpot() {}

    public static OneShot<LivingEntity> create(float speedModifier) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.absent(MemoryModuleType.WALK_TARGET)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, livingentity, i) -> {
                    if (serverlevel.canSeeSky(livingentity.blockPosition())) {
                        return false;
                    } else {
                        Optional<Vec3> optional = Optional.ofNullable(getOutdoorPosition(serverlevel, livingentity));

                        optional.ifPresent((vec3) -> {
                            memoryaccessor.set(new WalkTarget(vec3, speedModifier, 0));
                        });
                        return true;
                    }
                };
            });
        });
    }

    private static @Nullable Vec3 getOutdoorPosition(ServerLevel level, LivingEntity body) {
        RandomSource randomsource = body.getRandom();
        BlockPos blockpos = body.blockPosition();

        for (int i = 0; i < 10; ++i) {
            BlockPos blockpos1 = blockpos.offset(randomsource.nextInt(20) - 10, randomsource.nextInt(6) - 3, randomsource.nextInt(20) - 10);

            if (hasNoBlocksAbove(level, body, blockpos1)) {
                return Vec3.atBottomCenterOf(blockpos1);
            }
        }

        return null;
    }

    public static boolean hasNoBlocksAbove(ServerLevel level, LivingEntity body, BlockPos target) {
        return level.canSeeSky(target) && (double) level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target).getY() <= body.getY();
    }
}
