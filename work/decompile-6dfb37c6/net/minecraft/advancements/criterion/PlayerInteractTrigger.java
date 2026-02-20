package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

public class PlayerInteractTrigger extends SimpleCriterionTrigger<PlayerInteractTrigger.TriggerInstance> {

    public PlayerInteractTrigger() {}

    @Override
    public Codec<PlayerInteractTrigger.TriggerInstance> codec() {
        return PlayerInteractTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack itemStack, Entity interactedWith) {
        LootContext lootcontext = EntityPredicate.createContext(player, interactedWith);

        this.trigger(player, (playerinteracttrigger_triggerinstance) -> {
            return playerinteracttrigger_triggerinstance.matches(itemStack, lootcontext);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item, Optional<ContextAwarePredicate> entity) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<PlayerInteractTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(PlayerInteractTrigger.TriggerInstance::player), ItemPredicate.CODEC.optionalFieldOf("item").forGetter(PlayerInteractTrigger.TriggerInstance::item), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(PlayerInteractTrigger.TriggerInstance::entity)).apply(instance, PlayerInteractTrigger.TriggerInstance::new);
        });

        public static Criterion<PlayerInteractTrigger.TriggerInstance> itemUsedOnEntity(Optional<ContextAwarePredicate> player, ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.createCriterion(new PlayerInteractTrigger.TriggerInstance(player, Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> equipmentSheared(Optional<ContextAwarePredicate> player, ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT.createCriterion(new PlayerInteractTrigger.TriggerInstance(player, Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> equipmentSheared(ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT.createCriterion(new PlayerInteractTrigger.TriggerInstance(Optional.empty(), Optional.of(item.build()), entity));
        }

        public static Criterion<PlayerInteractTrigger.TriggerInstance> itemUsedOnEntity(ItemPredicate.Builder item, Optional<ContextAwarePredicate> entity) {
            return itemUsedOnEntity(Optional.empty(), item, entity);
        }

        public boolean matches(ItemStack itemStack, LootContext interactedWith) {
            return this.item.isPresent() && !((ItemPredicate) this.item.get()).test(itemStack) ? false : this.entity.isEmpty() || ((ContextAwarePredicate) this.entity.get()).matches(interactedWith);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, "entity");
        }
    }
}
