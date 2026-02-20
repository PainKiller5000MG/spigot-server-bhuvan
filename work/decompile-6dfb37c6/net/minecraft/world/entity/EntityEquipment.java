package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public class EntityEquipment {

    public static final Codec<EntityEquipment> CODEC = Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).xmap((map) -> {
        EnumMap<EquipmentSlot, ItemStack> enummap = new EnumMap(EquipmentSlot.class);

        enummap.putAll(map);
        return new EntityEquipment(enummap);
    }, (entityequipment) -> {
        Map<EquipmentSlot, ItemStack> map = new EnumMap(entityequipment.items);

        map.values().removeIf(ItemStack::isEmpty);
        return map;
    });
    private final EnumMap<EquipmentSlot, ItemStack> items;

    private EntityEquipment(EnumMap<EquipmentSlot, ItemStack> items) {
        this.items = items;
    }

    public EntityEquipment() {
        this(new EnumMap(EquipmentSlot.class));
    }

    public ItemStack set(EquipmentSlot slot, ItemStack itemStack) {
        return (ItemStack) Objects.requireNonNullElse((ItemStack) this.items.put(slot, itemStack), ItemStack.EMPTY);
    }

    public ItemStack get(EquipmentSlot slot) {
        return (ItemStack) this.items.getOrDefault(slot, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        for (ItemStack itemstack : this.items.values()) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public void tick(Entity owner) {
        for (Map.Entry<EquipmentSlot, ItemStack> map_entry : this.items.entrySet()) {
            ItemStack itemstack = (ItemStack) map_entry.getValue();

            if (!itemstack.isEmpty()) {
                itemstack.inventoryTick(owner.level(), owner, (EquipmentSlot) map_entry.getKey());
            }
        }

    }

    public void setAll(EntityEquipment equipment) {
        this.items.clear();
        this.items.putAll(equipment.items);
    }

    public void dropAll(LivingEntity dropper) {
        for (ItemStack itemstack : this.items.values()) {
            dropper.drop(itemstack, true, false);
        }

        this.clear();
    }

    public void clear() {
        this.items.replaceAll((equipmentslot, itemstack) -> {
            return ItemStack.EMPTY;
        });
    }
}
