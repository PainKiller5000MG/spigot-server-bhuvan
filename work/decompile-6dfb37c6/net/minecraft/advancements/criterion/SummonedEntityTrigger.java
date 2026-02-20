package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;

public class SummonedEntityTrigger extends SimpleCriterionTrigger<SummonedEntityTrigger.TriggerInstance> {

    public SummonedEntityTrigger() {}

    @Override
    public Codec<SummonedEntityTrigger.TriggerInstance> codec() {
        return SummonedEntityTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Entity entity) {
        LootContext lootcontext = EntityPredicate.createContext(player, entity);

        this.trigger(player, (summonedentitytrigger_triggerinstance) -> {
            return summonedentitytrigger_triggerinstance.matches(lootcontext);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> entity) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<SummonedEntityTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SummonedEntityTrigger.TriggerInstance::player), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(SummonedEntityTrigger.TriggerInstance::entity)).apply(instance, SummonedEntityTrigger.TriggerInstance::new);
        });

        public static Criterion<SummonedEntityTrigger.TriggerInstance> summonedEntity(EntityPredicate.Builder predicate) {
            return CriteriaTriggers.SUMMONED_ENTITY.createCriterion(new SummonedEntityTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(predicate))));
        }

        public boolean matches(LootContext entity) {
            return this.entity.isEmpty() || ((ContextAwarePredicate) this.entity.get()).matches(entity);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, "entity");
        }
    }
}
