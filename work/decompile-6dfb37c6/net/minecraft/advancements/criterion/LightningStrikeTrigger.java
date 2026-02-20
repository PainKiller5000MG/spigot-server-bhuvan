package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.storage.loot.LootContext;

public class LightningStrikeTrigger extends SimpleCriterionTrigger<LightningStrikeTrigger.TriggerInstance> {

    public LightningStrikeTrigger() {}

    @Override
    public Codec<LightningStrikeTrigger.TriggerInstance> codec() {
        return LightningStrikeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, LightningBolt lightning, List<Entity> entitiesAround) {
        List<LootContext> list1 = (List) entitiesAround.stream().map((entity) -> {
            return EntityPredicate.createContext(player, entity);
        }).collect(Collectors.toList());
        LootContext lootcontext = EntityPredicate.createContext(player, lightning);

        this.trigger(player, (lightningstriketrigger_triggerinstance) -> {
            return lightningstriketrigger_triggerinstance.matches(lootcontext, list1);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> lightning, Optional<ContextAwarePredicate> bystander) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<LightningStrikeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LightningStrikeTrigger.TriggerInstance::player), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("lightning").forGetter(LightningStrikeTrigger.TriggerInstance::lightning), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("bystander").forGetter(LightningStrikeTrigger.TriggerInstance::bystander)).apply(instance, LightningStrikeTrigger.TriggerInstance::new);
        });

        public static Criterion<LightningStrikeTrigger.TriggerInstance> lightningStrike(Optional<EntityPredicate> lightning, Optional<EntityPredicate> bystander) {
            return CriteriaTriggers.LIGHTNING_STRIKE.createCriterion(new LightningStrikeTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(lightning), EntityPredicate.wrap(bystander)));
        }

        public boolean matches(LootContext bolt, List<LootContext> entitiesAround) {
            if (this.lightning.isPresent() && !((ContextAwarePredicate) this.lightning.get()).matches(bolt)) {
                return false;
            } else {
                if (this.bystander.isPresent()) {
                    Stream stream = entitiesAround.stream();
                    ContextAwarePredicate contextawarepredicate = (ContextAwarePredicate) this.bystander.get();

                    Objects.requireNonNull(contextawarepredicate);
                    if (stream.noneMatch(contextawarepredicate::matches)) {
                        return false;
                    }
                }

                return true;
            }
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.lightning, "lightning");
            validator.validateEntity(this.bystander, "bystander");
        }
    }
}
