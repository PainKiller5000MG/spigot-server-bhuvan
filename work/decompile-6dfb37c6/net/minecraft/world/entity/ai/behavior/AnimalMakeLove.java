package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.Animal;

public class AnimalMakeLove extends Behavior<Animal> {

    private static final int BREED_RANGE = 3;
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 110;
    private final EntityType<? extends Animal> partnerType;
    private final float speedModifier;
    private final int closeEnoughDistance;
    private static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;
    private long spawnChildAtTime;

    public AnimalMakeLove(EntityType<? extends Animal> partnerType) {
        this(partnerType, 1.0F, 2);
    }

    public AnimalMakeLove(EntityType<? extends Animal> partnerType, float speedModifier, int closeEnoughDistance) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), 110);
        this.partnerType = partnerType;
        this.speedModifier = speedModifier;
        this.closeEnoughDistance = closeEnoughDistance;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Animal body) {
        return body.isInLove() && this.findValidBreedPartner(body).isPresent();
    }

    protected void start(ServerLevel level, Animal body, long timestamp) {
        Animal animal1 = (Animal) this.findValidBreedPartner(body).get();

        body.getBrain().setMemory(MemoryModuleType.BREED_TARGET, animal1);
        animal1.getBrain().setMemory(MemoryModuleType.BREED_TARGET, body);
        BehaviorUtils.lockGazeAndWalkToEachOther(body, animal1, this.speedModifier, this.closeEnoughDistance);
        int j = 60 + body.getRandom().nextInt(50);

        this.spawnChildAtTime = timestamp + (long) j;
    }

    protected boolean canStillUse(ServerLevel level, Animal body, long timestamp) {
        if (!this.hasBreedTargetOfRightType(body)) {
            return false;
        } else {
            Animal animal1 = this.getBreedTarget(body);

            return animal1.isAlive() && body.canMate(animal1) && BehaviorUtils.entityIsVisible(body.getBrain(), animal1) && timestamp <= this.spawnChildAtTime && !body.isPanicking() && !animal1.isPanicking();
        }
    }

    protected void tick(ServerLevel level, Animal body, long timestamp) {
        Animal animal1 = this.getBreedTarget(body);

        BehaviorUtils.lockGazeAndWalkToEachOther(body, animal1, this.speedModifier, this.closeEnoughDistance);
        if (body.closerThan(animal1, 3.0D)) {
            if (timestamp >= this.spawnChildAtTime) {
                body.spawnChildFromBreeding(level, animal1);
                body.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
                animal1.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
            }

        }
    }

    protected void stop(ServerLevel level, Animal body, long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.spawnChildAtTime = 0L;
    }

    private Animal getBreedTarget(Animal body) {
        return (Animal) body.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
    }

    private boolean hasBreedTargetOfRightType(Animal body) {
        Brain<?> brain = body.getBrain();

        return brain.hasMemoryValue(MemoryModuleType.BREED_TARGET) && ((AgeableMob) brain.getMemory(MemoryModuleType.BREED_TARGET).get()).getType() == this.partnerType;
    }

    private Optional<? extends Animal> findValidBreedPartner(Animal body) {
        Optional optional = ((NearestVisibleLivingEntities) body.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()).findClosest((livingentity) -> {
            boolean flag;

            if (livingentity.getType() == this.partnerType && livingentity instanceof Animal animal1) {
                if (body.canMate(animal1) && !animal1.isPanicking()) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        });

        Objects.requireNonNull(Animal.class);
        return optional.map(Animal.class::cast);
    }
}
