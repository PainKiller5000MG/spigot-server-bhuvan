package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public class UsedEnderEyeTrigger extends SimpleCriterionTrigger<UsedEnderEyeTrigger.TriggerInstance> {

    public UsedEnderEyeTrigger() {}

    @Override
    public Codec<UsedEnderEyeTrigger.TriggerInstance> codec() {
        return UsedEnderEyeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockPos feature) {
        double d0 = player.getX() - (double) feature.getX();
        double d1 = player.getZ() - (double) feature.getZ();
        double d2 = d0 * d0 + d1 * d1;

        this.trigger(player, (usedendereyetrigger_triggerinstance) -> {
            return usedendereyetrigger_triggerinstance.matches(d2);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Doubles distance) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<UsedEnderEyeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(UsedEnderEyeTrigger.TriggerInstance::player), MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance", MinMaxBounds.Doubles.ANY).forGetter(UsedEnderEyeTrigger.TriggerInstance::distance)).apply(instance, UsedEnderEyeTrigger.TriggerInstance::new);
        });

        public boolean matches(double sqrDistance) {
            return this.distance.matchesSqr(sqrDistance);
        }
    }
}
