package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {

    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    final float speedModifier;

    public GoToPotentialJobSite(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.speedModifier = speedModifier;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return (Boolean) body.getBrain().getActiveNonCoreActivity().map((activity) -> {
            return activity == Activity.IDLE || activity == Activity.WORK || activity == Activity.PLAY;
        }).orElse(true);
    }

    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return body.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    protected void tick(ServerLevel level, Villager body, long timestamp) {
        BehaviorUtils.setWalkAndLookTargetMemories(body, ((GlobalPos) body.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get()).pos(), this.speedModifier, 1);
    }

    protected void stop(ServerLevel level, Villager body, long timestamp) {
        Optional<GlobalPos> optional = body.getBrain().<GlobalPos>getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);

        optional.ifPresent((globalpos) -> {
            BlockPos blockpos = globalpos.pos();
            ServerLevel serverlevel1 = level.getServer().getLevel(globalpos.dimension());

            if (serverlevel1 != null) {
                PoiManager poimanager = serverlevel1.getPoiManager();

                if (poimanager.exists(blockpos, (holder) -> {
                    return true;
                })) {
                    poimanager.release(blockpos);
                }

                level.debugSynchronizers().updatePoi(blockpos);
            }
        });
        body.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
