package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FishingRodHookedTrigger extends SimpleCriterionTrigger<FishingRodHookedTrigger.TriggerInstance> {

    public FishingRodHookedTrigger() {}

    @Override
    public Codec<FishingRodHookedTrigger.TriggerInstance> codec() {
        return FishingRodHookedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items) {
        LootContext lootcontext = EntityPredicate.createContext(player, (Entity) (hook.getHookedIn() != null ? hook.getHookedIn() : hook));

        this.trigger(player, (fishingrodhookedtrigger_triggerinstance) -> {
            return fishingrodhookedtrigger_triggerinstance.matches(rod, lootcontext, items);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> rod, Optional<ContextAwarePredicate> entity, Optional<ItemPredicate> item) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<FishingRodHookedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FishingRodHookedTrigger.TriggerInstance::player), ItemPredicate.CODEC.optionalFieldOf("rod").forGetter(FishingRodHookedTrigger.TriggerInstance::rod), EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(FishingRodHookedTrigger.TriggerInstance::entity), ItemPredicate.CODEC.optionalFieldOf("item").forGetter(FishingRodHookedTrigger.TriggerInstance::item)).apply(instance, FishingRodHookedTrigger.TriggerInstance::new);
        });

        public static Criterion<FishingRodHookedTrigger.TriggerInstance> fishedItem(Optional<ItemPredicate> rod, Optional<EntityPredicate> entity, Optional<ItemPredicate> item) {
            return CriteriaTriggers.FISHING_ROD_HOOKED.createCriterion(new FishingRodHookedTrigger.TriggerInstance(Optional.empty(), rod, EntityPredicate.wrap(entity), item));
        }

        public boolean matches(ItemStack rod, LootContext hookedIn, Collection<ItemStack> items) {
            if (this.rod.isPresent() && !((ItemPredicate) this.rod.get()).test(rod)) {
                return false;
            } else if (this.entity.isPresent() && !((ContextAwarePredicate) this.entity.get()).matches(hookedIn)) {
                return false;
            } else {
                if (this.item.isPresent()) {
                    boolean flag = false;
                    Entity entity = (Entity) hookedIn.getOptionalParameter(LootContextParams.THIS_ENTITY);

                    if (entity instanceof ItemEntity) {
                        ItemEntity itementity = (ItemEntity) entity;

                        if (((ItemPredicate) this.item.get()).test(itementity.getItem())) {
                            flag = true;
                        }
                    }

                    for (ItemStack itemstack1 : items) {
                        if (((ItemPredicate) this.item.get()).test(itemstack1)) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }

                return true;
            }
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntity(this.entity, "entity");
        }
    }
}
