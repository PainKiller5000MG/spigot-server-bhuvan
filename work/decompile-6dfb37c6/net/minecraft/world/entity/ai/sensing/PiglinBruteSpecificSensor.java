package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;

public class PiglinBruteSpecificSensor extends Sensor<LivingEntity> {

    public PiglinBruteSpecificSensor() {}

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.NEARBY_ADULT_PIGLINS);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity body) {
        Brain<?> brain = body.getBrain();
        List<AbstractPiglin> list = Lists.newArrayList();
        NearestVisibleLivingEntities nearestvisiblelivingentities = (NearestVisibleLivingEntities) brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
        Optional optional = nearestvisiblelivingentities.findClosest((livingentity1) -> {
            return livingentity1 instanceof WitherSkeleton || livingentity1 instanceof WitherBoss;
        });

        Objects.requireNonNull(Mob.class);
        Optional<Mob> optional1 = optional.map(Mob.class::cast);

        for (LivingEntity livingentity1 : (List) brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(ImmutableList.of())) {
            if (livingentity1 instanceof AbstractPiglin && ((AbstractPiglin) livingentity1).isAdult()) {
                list.add((AbstractPiglin) livingentity1);
            }
        }

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS, optional1);
        brain.setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, list);
    }
}
