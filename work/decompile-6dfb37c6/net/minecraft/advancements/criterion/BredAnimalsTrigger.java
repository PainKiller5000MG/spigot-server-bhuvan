package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;

public class BredAnimalsTrigger extends SimpleCriterionTrigger<BredAnimalsTrigger.TriggerInstance> {

    public BredAnimalsTrigger() {}

    @Override
    public Codec<BredAnimalsTrigger.TriggerInstance> codec() {
        return BredAnimalsTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Animal parent, Animal partner, @Nullable AgeableMob child) {
        LootContext lootcontext = EntityPredicate.createContext(player, parent);
        LootContext lootcontext1 = EntityPredicate.createContext(player, partner);
        LootContext lootcontext2 = child != null ? EntityPredicate.createContext(player, child) : null;

        this.trigger(player, (bredanimalstrigger_triggerinstance) -> {
            return bredanimalstrigger_triggerinstance.matches(lootcontext, lootcontext1, lootcontext2);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> parent, Optional<ContextAwarePredicate> partner, Optional<ContextAwarePredicate> child) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<BredAnimalsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BredAnimalsTrigger.TriggerInstance::player), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("parent").forGetter(BredAnimalsTrigger.TriggerInstance::parent), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("partner").forGetter(BredAnimalsTrigger.TriggerInstance::partner), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("child").forGetter(BredAnimalsTrigger.TriggerInstance::child)).apply(instance, BredAnimalsTrigger.TriggerInstance::new);
        });

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals() {
            return CriteriaTriggers.BRED_ANIMALS.createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(EntityPredicate.Builder child) {
            return CriteriaTriggers.BRED_ANIMALS.createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(child))));
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(Optional<EntityPredicate> parent1, Optional<EntityPredicate> parent2, Optional<EntityPredicate> child) {
            return CriteriaTriggers.BRED_ANIMALS.createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(parent1), EntityPredicate.wrap(parent2), EntityPredicate.wrap(child)));
        }

        public boolean matches(LootContext parent, LootContext partner, @Nullable LootContext child) {
            return !this.child.isPresent() || child != null && ((ContextAwarePredicate) this.child.get()).matches(child) ? matches(this.parent, parent) && matches(this.partner, partner) || matches(this.parent, partner) && matches(this.partner, parent) : false;
        }

        private static boolean matches(Optional<ContextAwarePredicate> predicate, LootContext context) {
            return predicate.isEmpty() || ((ContextAwarePredicate) predicate.get()).matches(context);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.parent, "parent");
            validator.validateEntity(this.partner, "partner");
            validator.validateEntity(this.child, "child");
        }
    }
}
