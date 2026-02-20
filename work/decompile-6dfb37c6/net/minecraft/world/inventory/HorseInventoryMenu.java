package net.minecraft.world.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Inventory;

public class HorseInventoryMenu extends AbstractMountInventoryMenu {

    private static final Identifier SADDLE_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/saddle");
    private static final Identifier LLAMA_ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/llama_armor");
    private static final Identifier ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/horse_armor");

    public HorseInventoryMenu(int containerId, Inventory playerInventory, Container horseInventory, final AbstractHorse horse, int inventoryColumns) {
        super(containerId, playerInventory, horseInventory, horse);
        Container container1 = horse.createEquipmentSlotContainer(EquipmentSlot.SADDLE);

        this.addSlot(new ArmorSlot(container1, horse, EquipmentSlot.SADDLE, 0, 8, 18, HorseInventoryMenu.SADDLE_SLOT_SPRITE) {
            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.SADDLE) && horse.getType().is(EntityTypeTags.CAN_EQUIP_SADDLE);
            }
        });
        final boolean flag = horse instanceof Llama;
        Identifier identifier = flag ? HorseInventoryMenu.LLAMA_ARMOR_SLOT_SPRITE : HorseInventoryMenu.ARMOR_SLOT_SPRITE;
        Container container2 = horse.createEquipmentSlotContainer(EquipmentSlot.BODY);

        this.addSlot(new ArmorSlot(container2, horse, EquipmentSlot.BODY, 0, 8, 36, identifier) {
            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.BODY) && (horse.getType().is(EntityTypeTags.CAN_WEAR_HORSE_ARMOR) || flag);
            }
        });
        if (inventoryColumns > 0) {
            for (int k = 0; k < 3; ++k) {
                for (int l = 0; l < inventoryColumns; ++l) {
                    this.addSlot(new Slot(horseInventory, l + k * inventoryColumns, 80 + l * 18, 18 + k * 18));
                }
            }
        }

        this.addStandardInventorySlots(playerInventory, 8, 84);
    }

    @Override
    protected boolean hasInventoryChanged(Container container) {
        return ((AbstractHorse) this.mount).hasInventoryChanged(container);
    }
}
