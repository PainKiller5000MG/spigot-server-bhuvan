package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

public interface EquipmentUser {

    void setItemSlot(EquipmentSlot slot, ItemStack stack);

    ItemStack getItemBySlot(EquipmentSlot slot);

    void setDropChance(EquipmentSlot slot, float dropChance);

    default void equip(EquipmentTable equipment, LootParams lootParams) {
        this.equip(equipment.lootTable(), lootParams, equipment.slotDropChances());
    }

    default void equip(ResourceKey<LootTable> lootTable, LootParams lootParams, Map<EquipmentSlot, Float> dropChances) {
        this.equip(lootTable, lootParams, 0L, dropChances);
    }

    default void equip(ResourceKey<LootTable> lootTable, LootParams lootParams, long optionalLootTableSeed, Map<EquipmentSlot, Float> dropChances) {
        LootTable loottable = lootParams.getLevel().getServer().reloadableRegistries().getLootTable(lootTable);

        if (loottable != LootTable.EMPTY) {
            List<ItemStack> list = loottable.getRandomItems(lootParams, optionalLootTableSeed);
            List<EquipmentSlot> list1 = new ArrayList();

            for (ItemStack itemstack : list) {
                EquipmentSlot equipmentslot = this.resolveSlot(itemstack, list1);

                if (equipmentslot != null) {
                    ItemStack itemstack1 = equipmentslot.limit(itemstack);

                    this.setItemSlot(equipmentslot, itemstack1);
                    Float ofloat = (Float) dropChances.get(equipmentslot);

                    if (ofloat != null) {
                        this.setDropChance(equipmentslot, ofloat);
                    }

                    list1.add(equipmentslot);
                }
            }

        }
    }

    default @Nullable EquipmentSlot resolveSlot(ItemStack toEquip, List<EquipmentSlot> alreadyInsertedIntoSlots) {
        if (toEquip.isEmpty()) {
            return null;
        } else {
            Equippable equippable = (Equippable) toEquip.get(DataComponents.EQUIPPABLE);

            if (equippable != null) {
                EquipmentSlot equipmentslot = equippable.slot();

                if (!alreadyInsertedIntoSlots.contains(equipmentslot)) {
                    return equipmentslot;
                }
            } else if (!alreadyInsertedIntoSlots.contains(EquipmentSlot.MAINHAND)) {
                return EquipmentSlot.MAINHAND;
            }

            return null;
        }
    }
}
