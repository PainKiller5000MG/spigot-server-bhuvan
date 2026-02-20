package net.minecraft.world.entity.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public interface InventoryCarrier {

    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(ServerLevel level, Mob mob, InventoryCarrier inventoryCarrier, ItemEntity itemEntity) {
        ItemStack itemstack = itemEntity.getItem();

        if (mob.wantsToPickUp(level, itemstack)) {
            SimpleContainer simplecontainer = inventoryCarrier.getInventory();
            boolean flag = simplecontainer.canAddItem(itemstack);

            if (!flag) {
                return;
            }

            mob.onItemPickup(itemEntity);
            int i = itemstack.getCount();
            ItemStack itemstack1 = simplecontainer.addItem(itemstack);

            mob.take(itemEntity, i - itemstack1.getCount());
            if (itemstack1.isEmpty()) {
                itemEntity.discard();
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }

    }

    default void readInventoryFromTag(ValueInput input) {
        input.list("Inventory", ItemStack.CODEC).ifPresent((valueinput_typedinputlist) -> {
            this.getInventory().fromItemList(valueinput_typedinputlist);
        });
    }

    default void writeInventoryToTag(ValueOutput output) {
        this.getInventory().storeAsItemList(output.list("Inventory", ItemStack.CODEC));
    }
}
