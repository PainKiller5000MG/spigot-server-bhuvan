package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class InventoryChangeTrigger extends SimpleCriterionTrigger<InventoryChangeTrigger.TriggerInstance> {

    public InventoryChangeTrigger() {}

    @Override
    public Codec<InventoryChangeTrigger.TriggerInstance> codec() {
        return InventoryChangeTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Inventory inventory, ItemStack changedItem) {
        int i = 0;
        int j = 0;
        int k = 0;

        for (int l = 0; l < inventory.getContainerSize(); ++l) {
            ItemStack itemstack1 = inventory.getItem(l);

            if (itemstack1.isEmpty()) {
                ++j;
            } else {
                ++k;
                if (itemstack1.getCount() >= itemstack1.getMaxStackSize()) {
                    ++i;
                }
            }
        }

        this.trigger(player, inventory, changedItem, i, j, k);
    }

    private void trigger(ServerPlayer player, Inventory inventory, ItemStack changedItem, int slotsFull, int slotsEmpty, int slotsOccupied) {
        this.trigger(player, (inventorychangetrigger_triggerinstance) -> {
            return inventorychangetrigger_triggerinstance.matches(inventory, changedItem, slotsFull, slotsEmpty, slotsOccupied);
        });
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, InventoryChangeTrigger.TriggerInstance.Slots slots, List<ItemPredicate> items) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<InventoryChangeTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(InventoryChangeTrigger.TriggerInstance::player), InventoryChangeTrigger.TriggerInstance.Slots.CODEC.optionalFieldOf("slots", InventoryChangeTrigger.TriggerInstance.Slots.ANY).forGetter(InventoryChangeTrigger.TriggerInstance::slots), ItemPredicate.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(InventoryChangeTrigger.TriggerInstance::items)).apply(instance, InventoryChangeTrigger.TriggerInstance::new);
        });

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemPredicate.Builder... items) {
            return hasItems((ItemPredicate[]) Stream.of(items).map(ItemPredicate.Builder::build).toArray((i) -> {
                return new ItemPredicate[i];
            }));
        }

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemPredicate... items) {
            return CriteriaTriggers.INVENTORY_CHANGED.createCriterion(new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(items)));
        }

        public static Criterion<InventoryChangeTrigger.TriggerInstance> hasItems(ItemLike... items) {
            ItemPredicate[] aitempredicate = new ItemPredicate[items.length];

            for (int i = 0; i < items.length; ++i) {
                aitempredicate[i] = new ItemPredicate(Optional.of(HolderSet.direct(items[i].asItem().builtInRegistryHolder())), MinMaxBounds.Ints.ANY, DataComponentMatchers.ANY);
            }

            return hasItems(aitempredicate);
        }

        public boolean matches(Inventory inventory, ItemStack changedItem, int slotsFull, int slotsEmpty, int slotsOccupied) {
            if (!this.slots.matches(slotsFull, slotsEmpty, slotsOccupied)) {
                return false;
            } else if (this.items.isEmpty()) {
                return true;
            } else if (this.items.size() != 1) {
                List<ItemPredicate> list = new ObjectArrayList(this.items);
                int l = inventory.getContainerSize();

                for (int i1 = 0; i1 < l; ++i1) {
                    if (list.isEmpty()) {
                        return true;
                    }

                    ItemStack itemstack1 = inventory.getItem(i1);

                    if (!itemstack1.isEmpty()) {
                        list.removeIf((itempredicate) -> {
                            return itempredicate.test(itemstack1);
                        });
                    }
                }

                return list.isEmpty();
            } else {
                return !changedItem.isEmpty() && ((ItemPredicate) this.items.get(0)).test(changedItem);
            }
        }

        public static record Slots(MinMaxBounds.Ints occupied, MinMaxBounds.Ints full, MinMaxBounds.Ints empty) {

            public static final Codec<InventoryChangeTrigger.TriggerInstance.Slots> CODEC = RecordCodecBuilder.create((instance) -> {
                return instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("occupied", MinMaxBounds.Ints.ANY).forGetter(InventoryChangeTrigger.TriggerInstance.Slots::occupied), MinMaxBounds.Ints.CODEC.optionalFieldOf("full", MinMaxBounds.Ints.ANY).forGetter(InventoryChangeTrigger.TriggerInstance.Slots::full), MinMaxBounds.Ints.CODEC.optionalFieldOf("empty", MinMaxBounds.Ints.ANY).forGetter(InventoryChangeTrigger.TriggerInstance.Slots::empty)).apply(instance, InventoryChangeTrigger.TriggerInstance.Slots::new);
            });
            public static final InventoryChangeTrigger.TriggerInstance.Slots ANY = new InventoryChangeTrigger.TriggerInstance.Slots(MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.ANY);

            public boolean matches(int slotsFull, int slotsEmpty, int slotsOccupied) {
                return !this.full.matches(slotsFull) ? false : (!this.empty.matches(slotsEmpty) ? false : this.occupied.matches(slotsOccupied));
            }
        }
    }
}
