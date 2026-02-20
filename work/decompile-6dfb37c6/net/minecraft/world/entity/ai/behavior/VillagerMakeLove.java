package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;

public class VillagerMakeLove extends Behavior<Villager> {

    private long birthTimestamp;

    public VillagerMakeLove() {
        super(ImmutableMap.of(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT), 350, 350);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        return this.isBreedingPossible(body);
    }

    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return timestamp <= this.birthTimestamp && this.isBreedingPossible(body);
    }

    protected void start(ServerLevel level, Villager body, long timestamp) {
        AgeableMob ageablemob = (AgeableMob) body.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();

        BehaviorUtils.lockGazeAndWalkToEachOther(body, ageablemob, 0.5F, 2);
        level.broadcastEntityEvent(ageablemob, (byte) 18);
        level.broadcastEntityEvent(body, (byte) 18);
        int j = 275 + body.getRandom().nextInt(50);

        this.birthTimestamp = timestamp + (long) j;
    }

    protected void tick(ServerLevel level, Villager body, long timestamp) {
        Villager villager1 = (Villager) body.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();

        if (body.distanceToSqr((Entity) villager1) <= 5.0D) {
            BehaviorUtils.lockGazeAndWalkToEachOther(body, villager1, 0.5F, 2);
            if (timestamp >= this.birthTimestamp) {
                body.eatAndDigestFood();
                villager1.eatAndDigestFood();
                this.tryToGiveBirth(level, body, villager1);
            } else if (body.getRandom().nextInt(35) == 0) {
                level.broadcastEntityEvent(villager1, (byte) 12);
                level.broadcastEntityEvent(body, (byte) 12);
            }

        }
    }

    private void tryToGiveBirth(ServerLevel level, Villager body, Villager target) {
        Optional<BlockPos> optional = this.takeVacantBed(level, body);

        if (optional.isEmpty()) {
            level.broadcastEntityEvent(target, (byte) 13);
            level.broadcastEntityEvent(body, (byte) 13);
        } else {
            Optional<Villager> optional1 = this.breed(level, body, target);

            if (optional1.isPresent()) {
                this.giveBedToChild(level, (Villager) optional1.get(), (BlockPos) optional.get());
            } else {
                level.getPoiManager().release((BlockPos) optional.get());
                level.debugSynchronizers().updatePoi((BlockPos) optional.get());
            }
        }

    }

    protected void stop(ServerLevel level, Villager body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
    }

    private boolean isBreedingPossible(Villager myBody) {
        Brain<Villager> brain = myBody.getBrain();
        Optional<AgeableMob> optional = brain.getMemory(MemoryModuleType.BREED_TARGET).filter((ageablemob) -> {
            return ageablemob.getType() == EntityType.VILLAGER;
        });

        return optional.isEmpty() ? false : BehaviorUtils.targetIsValid(brain, MemoryModuleType.BREED_TARGET, EntityType.VILLAGER) && myBody.canBreed() && ((AgeableMob) optional.get()).canBreed();
    }

    private Optional<BlockPos> takeVacantBed(ServerLevel level, Villager body) {
        return level.getPoiManager().take((holder) -> {
            return holder.is(PoiTypes.HOME);
        }, (holder, blockpos) -> {
            return this.canReach(body, blockpos, holder);
        }, body.blockPosition(), 48);
    }

    private boolean canReach(Villager body, BlockPos poiPos, Holder<PoiType> poiType) {
        Path path = body.getNavigation().createPath(poiPos, ((PoiType) poiType.value()).validRange());

        return path != null && path.canReach();
    }

    private Optional<Villager> breed(ServerLevel level, Villager source, Villager target) {
        Villager villager2 = source.getBreedOffspring(level, target);

        if (villager2 == null) {
            return Optional.empty();
        } else {
            source.setAge(6000);
            target.setAge(6000);
            villager2.setAge(-24000);
            villager2.snapTo(source.getX(), source.getY(), source.getZ(), 0.0F, 0.0F);
            level.addFreshEntityWithPassengers(villager2);
            level.broadcastEntityEvent(villager2, (byte) 12);
            return Optional.of(villager2);
        }
    }

    private void giveBedToChild(ServerLevel level, Villager child, BlockPos bedPos) {
        GlobalPos globalpos = GlobalPos.of(level.dimension(), bedPos);

        child.getBrain().setMemory(MemoryModuleType.HOME, globalpos);
    }
}
