package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TemptingSensor extends Sensor<PathfinderMob> {

    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private final BiPredicate<PathfinderMob, ItemStack> temptations;

    public TemptingSensor(Predicate<ItemStack> tt) {
        this((pathfindermob, itemstack) -> {
            return tt.test(itemstack);
        });
    }

    public static TemptingSensor forAnimal() {
        return new TemptingSensor((pathfindermob, itemstack) -> {
            if (pathfindermob instanceof Animal animal) {
                return animal.isFood(itemstack);
            } else {
                return false;
            }
        });
    }

    private TemptingSensor(BiPredicate<PathfinderMob, ItemStack> temptations) {
        this.temptations = temptations;
    }

    protected void doTick(ServerLevel level, PathfinderMob body) {
        Brain<?> brain = body.getBrain();
        TargetingConditions targetingconditions = TemptingSensor.TEMPT_TARGETING.copy().range((double) ((float) body.getAttributeValue(Attributes.TEMPT_RANGE)));
        Stream stream = level.players().stream().filter(EntitySelector.NO_SPECTATORS).filter((serverplayer) -> {
            return targetingconditions.test(level, body, serverplayer);
        }).filter((serverplayer) -> {
            return this.playerHoldingTemptation(body, serverplayer);
        }).filter((serverplayer) -> {
            return !body.hasPassenger(serverplayer);
        });

        Objects.requireNonNull(body);
        List<Player> list = (List) stream.sorted(Comparator.comparingDouble(body::distanceToSqr)).collect(Collectors.toList());

        if (!list.isEmpty()) {
            Player player = (Player) list.get(0);

            brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, player);
        } else {
            brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }

    }

    private boolean playerHoldingTemptation(PathfinderMob mob, Player player) {
        return this.isTemptation(mob, player.getMainHandItem()) || this.isTemptation(mob, player.getOffhandItem());
    }

    private boolean isTemptation(PathfinderMob mob, ItemStack itemStack) {
        return this.temptations.test(mob, itemStack);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
    }
}
