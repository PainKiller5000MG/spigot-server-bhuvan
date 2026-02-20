package net.minecraft.world.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public class TriggerGate {

    public TriggerGate() {}

    public static <E extends LivingEntity> OneShot<E> triggerOneShuffled(List<Pair<? extends Trigger<? super E>, Integer>> weightedTriggers) {
        return triggerGate(weightedTriggers, GateBehavior.OrderPolicy.SHUFFLED, GateBehavior.RunningPolicy.RUN_ONE);
    }

    public static <E extends LivingEntity> OneShot<E> triggerGate(List<Pair<? extends Trigger<? super E>, Integer>> weightedBehaviors, GateBehavior.OrderPolicy orderPolicy, GateBehavior.RunningPolicy runningPolicy) {
        ShufflingList<Trigger<? super E>> shufflinglist = new ShufflingList<Trigger<? super E>>();

        weightedBehaviors.forEach((pair) -> {
            shufflinglist.add((Trigger) pair.getFirst(), (Integer) pair.getSecond());
        });
        return BehaviorBuilder.create((behaviorbuilder_instance) -> {
            return behaviorbuilder_instance.point((Trigger) (serverlevel, livingentity, i) -> {
                if (orderPolicy == GateBehavior.OrderPolicy.SHUFFLED) {
                    shufflinglist.shuffle();
                }

                for (Trigger<? super E> trigger : shufflinglist) {
                    if (trigger.trigger(serverlevel, livingentity, i) && runningPolicy == GateBehavior.RunningPolicy.RUN_ONE) {
                        break;
                    }
                }

                return true;
            });
        });
    }
}
