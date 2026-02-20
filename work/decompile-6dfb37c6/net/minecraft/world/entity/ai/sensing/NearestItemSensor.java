package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class NearestItemSensor extends Sensor<Mob> {

    private static final long XZ_RANGE = 32L;
    private static final long Y_RANGE = 16L;
    public static final int MAX_DISTANCE_TO_WANTED_ITEM = 32;

    public NearestItemSensor() {}

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
    }

    protected void doTick(ServerLevel level, Mob body) {
        Brain<?> brain = body.getBrain();
        List<ItemEntity> list = level.<ItemEntity>getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(32.0D, 16.0D, 32.0D), (itementity) -> {
            return true;
        });

        Objects.requireNonNull(body);
        list.sort(Comparator.comparingDouble(body::distanceToSqr));
        Stream stream = list.stream().filter((itementity) -> {
            return body.wantsToPickUp(level, itementity.getItem());
        }).filter((itementity) -> {
            return itementity.closerThan(body, 32.0D);
        });

        Objects.requireNonNull(body);
        Optional<ItemEntity> optional = stream.filter(body::hasLineOfSight).findFirst();

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, optional);
    }
}
