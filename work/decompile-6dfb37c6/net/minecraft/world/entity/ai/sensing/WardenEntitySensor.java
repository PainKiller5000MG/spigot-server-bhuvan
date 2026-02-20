package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;

public class WardenEntitySensor extends NearestLivingEntitySensor<Warden> {

    public WardenEntitySensor() {}

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.copyOf(Iterables.concat(super.requires(), List.of(MemoryModuleType.NEAREST_ATTACKABLE)));
    }

    protected void doTick(ServerLevel level, Warden body) {
        super.doTick(level, body);
        getClosest(body, (livingentity) -> {
            return livingentity.getType() == EntityType.PLAYER;
        }).or(() -> {
            return getClosest(body, (livingentity) -> {
                return livingentity.getType() != EntityType.PLAYER;
            });
        }).ifPresentOrElse((livingentity) -> {
            body.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, livingentity);
        }, () -> {
            body.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
        });
    }

    private static Optional<LivingEntity> getClosest(Warden body, Predicate<LivingEntity> test) {
        Stream stream = body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).stream().flatMap(Collection::stream);

        Objects.requireNonNull(body);
        return stream.filter(body::canTargetEntity).filter(test).findFirst();
    }
}
