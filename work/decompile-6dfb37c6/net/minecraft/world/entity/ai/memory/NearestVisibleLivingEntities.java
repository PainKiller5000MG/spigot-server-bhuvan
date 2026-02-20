package net.minecraft.world.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class NearestVisibleLivingEntities {

    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    private final List<LivingEntity> nearbyEntities;
    private final Predicate<LivingEntity> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = (livingentity) -> {
            return false;
        };
    }

    public NearestVisibleLivingEntities(ServerLevel level, LivingEntity body, List<LivingEntity> livingEntities) {
        this.nearbyEntities = livingEntities;
        Object2BooleanOpenHashMap<LivingEntity> object2booleanopenhashmap = new Object2BooleanOpenHashMap(livingEntities.size());
        Predicate<LivingEntity> predicate = (livingentity1) -> {
            return Sensor.isEntityTargetable(level, body, livingentity1);
        };

        this.lineOfSightTest = (livingentity1) -> {
            return object2booleanopenhashmap.computeIfAbsent(livingentity1, predicate);
        };
    }

    public static NearestVisibleLivingEntities empty() {
        return NearestVisibleLivingEntities.EMPTY;
    }

    public Optional<LivingEntity> findClosest(Predicate<LivingEntity> filter) {
        for (LivingEntity livingentity : this.nearbyEntities) {
            if (filter.test(livingentity) && this.lineOfSightTest.test(livingentity)) {
                return Optional.of(livingentity);
            }
        }

        return Optional.empty();
    }

    public Iterable<LivingEntity> findAll(Predicate<LivingEntity> filter) {
        return Iterables.filter(this.nearbyEntities, (livingentity) -> {
            return filter.test(livingentity) && this.lineOfSightTest.test(livingentity);
        });
    }

    public Stream<LivingEntity> find(Predicate<LivingEntity> filter) {
        return this.nearbyEntities.stream().filter((livingentity) -> {
            return filter.test(livingentity) && this.lineOfSightTest.test(livingentity);
        });
    }

    public boolean contains(LivingEntity targetEntity) {
        return this.nearbyEntities.contains(targetEntity) && this.lineOfSightTest.test(targetEntity);
    }

    public boolean contains(Predicate<LivingEntity> filter) {
        for (LivingEntity livingentity : this.nearbyEntities) {
            if (filter.test(livingentity) && this.lineOfSightTest.test(livingentity)) {
                return true;
            }
        }

        return false;
    }
}
