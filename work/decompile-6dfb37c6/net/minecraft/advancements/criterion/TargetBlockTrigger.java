package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

public class TargetBlockTrigger extends SimpleCriterionTrigger<TargetBlockTrigger.TriggerInstance> {

    public TargetBlockTrigger() {}

    @Override
    public Codec<TargetBlockTrigger.TriggerInstance> codec() {
        return TargetBlockTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity projectile, Vec3 hitPosition, int signalStrength) {
        LootContext lootcontext = EntityPredicate.createContext(player, projectile);

        this.trigger(player, (targetblocktrigger_triggerinstance) -> {
            return targetblocktrigger_triggerinstance.matches(lootcontext, hitPosition, signalStrength);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints signalStrength, Optional<ContextAwarePredicate> projectile) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TargetBlockTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TargetBlockTrigger.TriggerInstance::player), MinMaxBounds.Ints.CODEC.optionalFieldOf("signal_strength", MinMaxBounds.Ints.ANY).forGetter(TargetBlockTrigger.TriggerInstance::signalStrength), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("projectile").forGetter(TargetBlockTrigger.TriggerInstance::projectile)).apply(instance, TargetBlockTrigger.TriggerInstance::new);
        });

        public static Criterion<TargetBlockTrigger.TriggerInstance> targetHit(MinMaxBounds.Ints redstoneSignalStrength, Optional<ContextAwarePredicate> projectile) {
            return CriteriaTriggers.TARGET_BLOCK_HIT.createCriterion(new TargetBlockTrigger.TriggerInstance(Optional.empty(), redstoneSignalStrength, projectile));
        }

        public boolean matches(LootContext projectile, Vec3 hitPosition, int signalStrength) {
            return !this.signalStrength.matches(signalStrength) ? false : !this.projectile.isPresent() || ((ContextAwarePredicate) this.projectile.get()).matches(projectile);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.projectile, "projectile");
        }
    }
}
