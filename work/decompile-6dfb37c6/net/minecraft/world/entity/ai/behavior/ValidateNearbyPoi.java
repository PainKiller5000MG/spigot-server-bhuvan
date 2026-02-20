package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ValidateNearbyPoi {

    private static final int MAX_DISTANCE = 16;

    public ValidateNearbyPoi() {}

    public static BehaviorControl<LivingEntity> create(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> memoryType) {
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.group(behaviorbuilder_instance.present(memoryType)).apply(behaviorbuilder_instance, (memoryaccessor) -> {
                return (serverlevel, livingentity, i) -> {
                    GlobalPos globalpos = (GlobalPos) behaviorbuilder_instance.get(memoryaccessor);
                    BlockPos blockpos = globalpos.pos();

                    if (serverlevel.dimension() == globalpos.dimension() && blockpos.closerToCenterThan(livingentity.position(), 16.0D)) {
                        ServerLevel serverlevel1 = serverlevel.getServer().getLevel(globalpos.dimension());

                        if (serverlevel1 != null && serverlevel1.getPoiManager().exists(blockpos, poiType)) {
                            if (bedIsOccupied(serverlevel1, blockpos, livingentity)) {
                                memoryaccessor.erase();
                                if (!bedIsOccupiedByVillager(serverlevel1, blockpos)) {
                                    serverlevel.getPoiManager().release(blockpos);
                                    serverlevel.debugSynchronizers().updatePoi(blockpos);
                                }
                            }
                        } else {
                            memoryaccessor.erase();
                        }

                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }

    private static boolean bedIsOccupied(ServerLevel poiLevel, BlockPos poiPos, LivingEntity body) {
        BlockState blockstate = poiLevel.getBlockState(poiPos);

        return blockstate.is(BlockTags.BEDS) && (Boolean) blockstate.getValue(BedBlock.OCCUPIED) && !body.isSleeping();
    }

    private static boolean bedIsOccupiedByVillager(ServerLevel poiLevel, BlockPos poiPos) {
        List<Villager> list = poiLevel.<Villager>getEntitiesOfClass(Villager.class, new AABB(poiPos), LivingEntity::isSleeping);

        return !list.isEmpty();
    }
}
