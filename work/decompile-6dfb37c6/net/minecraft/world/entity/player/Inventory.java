package net.minecraft.world.entity.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Inventory implements Container, Nameable {

    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    public static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int SLOT_BODY_ARMOR = 41;
    public static final int SLOT_SADDLE = 42;
    public static final int NOT_FOUND_INDEX = -1;
    public static final Int2ObjectMap<EquipmentSlot> EQUIPMENT_SLOT_MAPPING = new Int2ObjectArrayMap(Map.of(EquipmentSlot.FEET.getIndex(36), EquipmentSlot.FEET, EquipmentSlot.LEGS.getIndex(36), EquipmentSlot.LEGS, EquipmentSlot.CHEST.getIndex(36), EquipmentSlot.CHEST, EquipmentSlot.HEAD.getIndex(36), EquipmentSlot.HEAD, 40, EquipmentSlot.OFFHAND, 41, EquipmentSlot.BODY, 42, EquipmentSlot.SADDLE));
    private static final Component DEFAULT_NAME = Component.translatable("container.inventory");
    private final NonNullList<ItemStack> items;
    private int selected;
    public final Player player;
    private final EntityEquipment equipment;
    private int timesChanged;

    public Inventory(Player player, EntityEquipment equipment) {
        this.items = NonNullList.<ItemStack>withSize(36, ItemStack.EMPTY);
        this.player = player;
        this.equipment = equipment;
    }

    public int getSelectedSlot() {
        return this.selected;
    }

    public void setSelectedSlot(int selected) {
        if (!isHotbarSlot(selected)) {
            throw new IllegalArgumentException("Invalid selected slot");
        } else {
            this.selected = selected;
        }
    }

    public ItemStack getSelectedItem() {
        return this.items.get(this.selected);
    }

    public ItemStack setSelectedItem(ItemStack itemStack) {
        return this.items.set(this.selected, itemStack);
    }

    public static int getSelectionSize() {
        return 9;
    }

    public NonNullList<ItemStack> getNonEquipmentItems() {
        return this.items;
    }

    private boolean hasRemainingSpaceForItem(ItemStack slotItemStack, ItemStack newItemStack) {
        return !slotItemStack.isEmpty() && ItemStack.isSameItemSameComponents(slotItemStack, newItemStack) && slotItemStack.isStackable() && slotItemStack.getCount() < this.getMaxStackSize(slotItemStack);
    }

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void addAndPickItem(ItemStack itemStack) {
        this.setSelectedSlot(this.getSuitableHotbarSlot());
        if (!((ItemStack) this.items.get(this.selected)).isEmpty()) {
            int i = this.getFreeSlot();

            if (i != -1) {
                this.items.set(i, this.items.get(this.selected));
            }
        }

        this.items.set(this.selected, itemStack);
    }

    public void pickSlot(int slot) {
        this.setSelectedSlot(this.getSuitableHotbarSlot());
        ItemStack itemstack = this.items.get(this.selected);

        this.items.set(this.selected, this.items.get(slot));
        this.items.set(slot, itemstack);
    }

    public static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    public int findSlotMatchingItem(ItemStack itemStack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty() && ItemStack.isSameItemSameComponents(itemStack, this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean isUsableForCrafting(ItemStack item) {
        return !item.isDamaged() && !item.isEnchanted() && !item.has(DataComponents.CUSTOM_NAME);
    }

    public int findSlotMatchingCraftingIngredient(Holder<Item> item, ItemStack existingItem) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.items.get(i);

            if (!itemstack1.isEmpty() && itemstack1.is(item) && isUsableForCrafting(itemstack1) && (existingItem.isEmpty() || ItemStack.isSameItemSameComponents(existingItem, itemstack1))) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            int j = (this.selected + i) % 9;

            if (((ItemStack) this.items.get(j)).isEmpty()) {
                return j;
            }
        }

        for (int k = 0; k < 9; ++k) {
            int l = (this.selected + k) % 9;

            if (!((ItemStack) this.items.get(l)).isEnchanted()) {
                return l;
            }
        }

        return this.selected;
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int amountToRemove, Container craftSlots) {
        int j = 0;
        boolean flag = amountToRemove == 0;

        j += ContainerHelper.clearOrCountMatchingItems((Container) this, predicate, amountToRemove - j, flag);
        j += ContainerHelper.clearOrCountMatchingItems(craftSlots, predicate, amountToRemove - j, flag);
        ItemStack itemstack = this.player.containerMenu.getCarried();

        j += ContainerHelper.clearOrCountMatchingItems(itemstack, predicate, amountToRemove - j, flag);
        if (itemstack.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return j;
    }

    private int addResource(ItemStack itemStack) {
        int i = this.getSlotWithRemainingSpace(itemStack);

        if (i == -1) {
            i = this.getFreeSlot();
        }

        return i == -1 ? itemStack.getCount() : this.addResource(i, itemStack);
    }

    private int addResource(int slot, ItemStack itemStack) {
        int j = itemStack.getCount();
        ItemStack itemstack1 = this.getItem(slot);

        if (itemstack1.isEmpty()) {
            itemstack1 = itemStack.copyWithCount(0);
            this.setItem(slot, itemstack1);
        }

        int k = this.getMaxStackSize(itemstack1) - itemstack1.getCount();
        int l = Math.min(j, k);

        if (l == 0) {
            return j;
        } else {
            j -= l;
            itemstack1.grow(l);
            itemstack1.setPopTime(5);
            return j;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack newItemStack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), newItemStack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), newItemStack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); ++i) {
                if (this.hasRemainingSpaceForItem(this.items.get(i), newItemStack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                itemstack.inventoryTick(this.player.level(), this.player, i == this.selected ? EquipmentSlot.MAINHAND : null);
            }
        }

    }

    public boolean add(ItemStack itemStack) {
        return this.add(-1, itemStack);
    }

    public boolean add(int slot, ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        } else {
            try {
                if (itemStack.isDamaged()) {
                    if (slot == -1) {
                        slot = this.getFreeSlot();
                    }

                    if (slot >= 0) {
                        this.items.set(slot, itemStack.copyAndClear());
                        ((ItemStack) this.items.get(slot)).setPopTime(5);
                        return true;
                    } else if (this.player.hasInfiniteMaterials()) {
                        itemStack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int j;

                    do {
                        j = itemStack.getCount();
                        if (slot == -1) {
                            itemStack.setCount(this.addResource(itemStack));
                        } else {
                            itemStack.setCount(this.addResource(slot, itemStack));
                        }
                    } while (!itemStack.isEmpty() && itemStack.getCount() < j);

                    if (itemStack.getCount() == j && this.player.hasInfiniteMaterials()) {
                        itemStack.setCount(0);
                        return true;
                    } else {
                        return itemStack.getCount() < j;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being added");

                crashreportcategory.setDetail("Item ID", Item.getId(itemStack.getItem()));
                crashreportcategory.setDetail("Item data", itemStack.getDamageValue());
                crashreportcategory.setDetail("Item name", () -> {
                    return itemStack.getHoverName().getString();
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack itemStack) {
        this.placeItemBackInInventory(itemStack, true);
    }

    public void placeItemBackInInventory(ItemStack itemStack, boolean shouldSendSetSlotPacket) {
        while (true) {
            if (!itemStack.isEmpty()) {
                int i = this.getSlotWithRemainingSpace(itemStack);

                if (i == -1) {
                    i = this.getFreeSlot();
                }

                if (i != -1) {
                    int j = itemStack.getMaxStackSize() - this.getItem(i).getCount();

                    if (!this.add(i, itemStack.split(j)) || !shouldSendSetSlotPacket) {
                        continue;
                    }

                    Player player = this.player;

                    if (player instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) player;

                        serverplayer.connection.send(this.createInventoryUpdatePacket(i));
                    }
                    continue;
                }

                this.player.drop(itemStack, false);
            }

            return;
        }
    }

    public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int slot) {
        return new ClientboundSetPlayerInventoryPacket(slot, this.getItem(slot).copy());
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < this.items.size()) {
            return ContainerHelper.removeItem(this.items, slot, count);
        } else {
            EquipmentSlot equipmentslot = (EquipmentSlot) Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);

            if (equipmentslot != null) {
                ItemStack itemstack = this.equipment.get(equipmentslot);

                if (!itemstack.isEmpty()) {
                    return itemstack.split(count);
                }
            }

            return ItemStack.EMPTY;
        }
    }

    public void removeItem(ItemStack itemStack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i) == itemStack) {
                this.items.set(i, ItemStack.EMPTY);
                return;
            }
        }

        ObjectIterator objectiterator = Inventory.EQUIPMENT_SLOT_MAPPING.values().iterator();

        while (objectiterator.hasNext()) {
            EquipmentSlot equipmentslot = (EquipmentSlot) objectiterator.next();
            ItemStack itemstack1 = this.equipment.get(equipmentslot);

            if (itemstack1 == itemStack) {
                this.equipment.set(equipmentslot, ItemStack.EMPTY);
                return;
            }
        }

    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < this.items.size()) {
            ItemStack itemstack = this.items.get(slot);

            this.items.set(slot, ItemStack.EMPTY);
            return itemstack;
        } else {
            EquipmentSlot equipmentslot = (EquipmentSlot) Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);

            return equipmentslot != null ? this.equipment.set(equipmentslot, ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        if (slot < this.items.size()) {
            this.items.set(slot, itemStack);
        }

        EquipmentSlot equipmentslot = (EquipmentSlot) Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);

        if (equipmentslot != null) {
            this.equipment.set(equipmentslot, itemStack);
        }

    }

    public void save(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.items.get(i);

            if (!itemstack.isEmpty()) {
                output.add(new ItemStackWithSlot(i, itemstack));
            }
        }

    }

    public void load(ValueInput.TypedInputList<ItemStackWithSlot> input) {
        this.items.clear();

        for (ItemStackWithSlot itemstackwithslot : input) {
            if (itemstackwithslot.isValidInContainer(this.items.size())) {
                this.setItem(itemstackwithslot.slot(), itemstackwithslot.stack());
            }
        }

    }

    @Override
    public int getContainerSize() {
        return this.items.size() + Inventory.EQUIPMENT_SLOT_MAPPING.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        ObjectIterator objectiterator = Inventory.EQUIPMENT_SLOT_MAPPING.values().iterator();

        while (objectiterator.hasNext()) {
            EquipmentSlot equipmentslot = (EquipmentSlot) objectiterator.next();

            if (!this.equipment.get(equipmentslot).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < this.items.size()) {
            return this.items.get(slot);
        } else {
            EquipmentSlot equipmentslot = (EquipmentSlot) Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);

            return equipmentslot != null ? this.equipment.get(equipmentslot) : ItemStack.EMPTY;
        }
    }

    @Override
    public Component getName() {
        return Inventory.DEFAULT_NAME;
    }

    public void dropAll() {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.items.get(i);

            if (!itemstack.isEmpty()) {
                this.player.drop(itemstack, true, false);
                this.items.set(i, ItemStack.EMPTY);
            }
        }

        this.equipment.dropAll(this.player);
    }

    @Override
    public void setChanged() {
        ++this.timesChanged;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public boolean contains(ItemStack searchStack) {
        for (ItemStack itemstack1 : this) {
            if (!itemstack1.isEmpty() && ItemStack.isSameItemSameComponents(itemstack1, searchStack)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (ItemStack itemstack : this) {
            if (!itemstack.isEmpty() && itemstack.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(Predicate<ItemStack> predicate) {
        for (ItemStack itemstack : this) {
            if (predicate.test(itemstack)) {
                return true;
            }
        }

        return false;
    }

    public void replaceWith(Inventory other) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, other.getItem(i));
        }

        this.setSelectedSlot(other.getSelectedSlot());
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.equipment.clear();
    }

    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemstack : this.items) {
            contents.accountSimpleStack(itemstack);
        }

    }

    public ItemStack removeFromSelected(boolean all) {
        ItemStack itemstack = this.getSelectedItem();

        return itemstack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, all ? itemstack.getCount() : 1);
    }
}
