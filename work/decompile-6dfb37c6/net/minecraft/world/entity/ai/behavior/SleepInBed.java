package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;

public class SleepInBed extends Behavior<LivingEntity> {

    public static final int COOLDOWN_AFTER_BEING_WOKEN = 100;
    private long nextOkStartTime;

    public SleepInBed() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LAST_WOKEN, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, LivingEntity body) {
        if (body.isPassenger()) {
            return false;
        } else {
            Brain<?> brain = body.getBrain();
            GlobalPos globalpos = (GlobalPos) brain.getMemory(MemoryModuleType.HOME).get();

            if (level.dimension() != globalpos.dimension()) {
                return false;
            } else {
                Optional<Long> optional = brain.<Long>getMemory(MemoryModuleType.LAST_WOKEN);

                if (optional.isPresent()) {
                    long i = level.getGameTime() - (Long) optional.get();

                    if (i > 0L && i < 100L) {
                        return false;
                    }
                }

                BlockState blockstate = level.getBlockState(globalpos.pos());

                return globalpos.pos().closerToCenterThan(body.position(), 2.0D) && blockstate.is(BlockTags.BEDS) && !(Boolean) blockstate.getValue(BedBlock.OCCUPIED);
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, LivingEntity body, long timestamp) {
        Optional<GlobalPos> optional = body.getBrain().<GlobalPos>getMemory(MemoryModuleType.HOME);

        if (optional.isEmpty()) {
            return false;
        } else {
            BlockPos blockpos = ((GlobalPos) optional.get()).pos();

            return body.getBrain().isActive(Activity.REST) && body.getY() > (double) blockpos.getY() + 0.4D && blockpos.closerToCenterThan(body.position(), 1.14D);
        }
    }

    @Override
    protected void start(ServerLevel level, LivingEntity body, long timestamp) {
        if (timestamp > this.nextOkStartTime) {
            Brain<?> brain = body.getBrain();

            if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
                Set<GlobalPos> set = (Set) brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).get();
                Optional<List<LivingEntity>> optional;

                if (brain.hasMemoryValue(MemoryModuleType.NEAREST_LIVING_ENTITIES)) {
                    optional = brain.<List<LivingEntity>>getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
                } else {
                    optional = Optional.empty();
                }

                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, body, (Node) null, (Node) null, set, optional);
            }

            body.startSleeping(((GlobalPos) body.getBrain().getMemory(MemoryModuleType.HOME).get()).pos());
        }

    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    @Override
    protected void stop(ServerLevel level, LivingEntity body, long timestamp) {
        if (body.isSleeping()) {
            body.stopSleeping();
            this.nextOkStartTime = timestamp + 40L;
        }

    }
}
