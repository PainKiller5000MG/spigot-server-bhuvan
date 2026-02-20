package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class DistanceTrigger extends SimpleCriterionTrigger<DistanceTrigger.TriggerInstance> {

    public DistanceTrigger() {}

    @Override
    public Codec<DistanceTrigger.TriggerInstance> codec() {
        return DistanceTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Vec3 startPosition) {
        Vec3 vec31 = player.position();

        this.trigger(player, (distancetrigger_triggerinstance) -> {
            return distancetrigger_triggerinstance.matches(player.level(), startPosition, vec31);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<LocationPredicate> startPosition, Optional<DistancePredicate> distance) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<DistanceTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(DistanceTrigger.TriggerInstance::player), LocationPredicate.CODEC.optionalFieldOf("start_position").forGetter(DistanceTrigger.TriggerInstance::startPosition), DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(DistanceTrigger.TriggerInstance::distance)).apply(instance, DistanceTrigger.TriggerInstance::new);
        });

        public static Criterion<DistanceTrigger.TriggerInstance> fallFromHeight(EntityPredicate.Builder player, DistancePredicate distance, LocationPredicate.Builder startPosition) {
            return CriteriaTriggers.FALL_FROM_HEIGHT.createCriterion(new DistanceTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(player)), Optional.of(startPosition.build()), Optional.of(distance)));
        }

        public static Criterion<DistanceTrigger.TriggerInstance> rideEntityInLava(EntityPredicate.Builder player, DistancePredicate distance) {
            return CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.createCriterion(new DistanceTrigger.TriggerInstance(Optional.of(EntityPredicate.wrap(player)), Optional.empty(), Optional.of(distance)));
        }

        public static Criterion<DistanceTrigger.TriggerInstance> travelledThroughNether(DistancePredicate distance) {
            return CriteriaTriggers.NETHER_TRAVEL.createCriterion(new DistanceTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(distance)));
        }

        public boolean matches(ServerLevel level, Vec3 enteredPosition, Vec3 playerPosition) {
            return this.startPosition.isPresent() && !((LocationPredicate) this.startPosition.get()).matches(level, enteredPosition.x, enteredPosition.y, enteredPosition.z) ? false : !this.distance.isPresent() || ((DistancePredicate) this.distance.get()).matches(enteredPosition.x, enteredPosition.y, enteredPosition.z, playerPosition.x, playerPosition.y, playerPosition.z);
        }
    }
}
