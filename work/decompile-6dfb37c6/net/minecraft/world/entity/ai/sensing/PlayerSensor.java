package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

public class PlayerSensor extends Sensor<LivingEntity> {

    public PlayerSensor() {}

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS);
    }

    @Override
    protected void doTick(ServerLevel level, LivingEntity body) {
        Stream stream = level.players().stream().filter(EntitySelector.NO_SPECTATORS).filter((serverplayer) -> {
            return body.closerThan(serverplayer, this.getFollowDistance(body));
        });

        Objects.requireNonNull(body);
        List<Player> list = (List) stream.sorted(Comparator.comparingDouble(body::distanceToSqr)).collect(Collectors.toList());
        Brain<?> brain = body.getBrain();

        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, list);
        List<Player> list1 = (List) list.stream().filter((player) -> {
            return isEntityTargetable(level, body, player);
        }).collect(Collectors.toList());

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, list1.isEmpty() ? null : (Player) list1.get(0));
        List<Player> list2 = list1.stream().filter((player) -> {
            return isEntityAttackable(level, body, player);
        }).toList();

        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYERS, list2);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, list2.isEmpty() ? null : (Player) list2.get(0));
    }

    protected double getFollowDistance(LivingEntity body) {
        return body.getAttributeValue(Attributes.FOLLOW_RANGE);
    }
}
