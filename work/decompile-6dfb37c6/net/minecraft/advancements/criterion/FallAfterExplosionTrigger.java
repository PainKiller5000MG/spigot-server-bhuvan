package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FallAfterExplosionTrigger extends SimpleCriterionTrigger<FallAfterExplosionTrigger.TriggerInstance> {

    public FallAfterExplosionTrigger() {}

    @Override
    public Codec<FallAfterExplosionTrigger.TriggerInstance> codec() {
        return FallAfterExplosionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Vec3 startPosition, @Nullable Entity cause) {
        Vec3 vec31 = player.position();
        LootContext lootcontext = cause != null ? EntityPredicate.createContext(player, cause) : null;

        this.trigger(player, (fallafterexplosiontrigger_triggerinstance) -> {
            return fallafterexplosiontrigger_triggerinstance.matches(player.level(), startPosition, vec31, lootcontext);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<LocationPredicate> startPosition, Optional<DistancePredicate> distance, Optional<ContextAwarePredicate> cause) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<FallAfterExplosionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FallAfterExplosionTrigger.TriggerInstance::player), LocationPredicate.CODEC.optionalFieldOf("start_position").forGetter(FallAfterExplosionTrigger.TriggerInstance::startPosition), DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(FallAfterExplosionTrigger.TriggerInstance::distance), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("cause").forGetter(FallAfterExplosionTrigger.TriggerInstance::cause)).apply(instance, FallAfterExplosionTrigger.TriggerInstance::new);
        });

        public static Criterion<FallAfterExplosionTrigger.TriggerInstance> fallAfterExplosion(DistancePredicate distance, EntityPredicate.Builder cause) {
            return CriteriaTriggers.FALL_AFTER_EXPLOSION.createCriterion(new FallAfterExplosionTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(distance), Optional.of(EntityPredicate.wrap(cause))));
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.cause(), "cause");
        }

        public boolean matches(ServerLevel level, Vec3 enteredPosition, Vec3 playerPosition, @Nullable LootContext cause) {
            return this.startPosition.isPresent() && !((LocationPredicate) this.startPosition.get()).matches(level, enteredPosition.x, enteredPosition.y, enteredPosition.z) ? false : (this.distance.isPresent() && !((DistancePredicate) this.distance.get()).matches(enteredPosition.x, enteredPosition.y, enteredPosition.z, playerPosition.x, playerPosition.y, playerPosition.z) ? false : !this.cause.isPresent() || cause != null && ((ContextAwarePredicate) this.cause.get()).matches(cause));
        }
    }
}
