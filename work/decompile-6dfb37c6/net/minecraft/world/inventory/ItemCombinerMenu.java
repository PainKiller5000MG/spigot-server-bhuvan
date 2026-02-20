package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class ItemCombinerMenu extends AbstractContainerMenu {

    private static final int INVENTORY_SLOTS_PER_ROW = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final int INPUT_SLOT_START = 0;
    protected final ContainerLevelAccess access;
    protected final Player player;
    protected final Container inputSlots;
    protected final ResultContainer resultSlots = new ResultContainer() {
        @Override
        public void setChanged() {
            ItemCombinerMenu.this.slotsChanged(this);
        }
    };
    private final int resultSlotIndex;

    protected boolean mayPickup(Player player, boolean hasItem) {
        return true;
    }

    protected abstract void onTake(Player player, ItemStack carried);

    protected abstract boolean isValidBlock(BlockState state);

    public ItemCombinerMenu(@Nullable MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition itemInputSlots) {
        super(menuType, containerId);
        this.access = access;
        this.player = inventory.player;
        this.inputSlots = this.createContainer(itemInputSlots.getNumOfInputSlots());
        this.resultSlotIndex = itemInputSlots.getResultSlotIndex();
        this.createInputSlots(itemInputSlots);
        this.createResultSlot(itemInputSlots);
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    private void createInputSlots(ItemCombinerMenuSlotDefinition itemInputSlots) {
        for (final ItemCombinerMenuSlotDefinition.SlotDefinition itemcombinermenuslotdefinition_slotdefinition : itemInputSlots.getSlots()) {
            this.addSlot(new Slot(this.inputSlots, itemcombinermenuslotdefinition_slotdefinition.slotIndex(), itemcombinermenuslotdefinition_slotdefinition.x(), itemcombinermenuslotdefinition_slotdefinition.y()) {
                @Override
                public boolean mayPlace(ItemStack itemStack) {
                    return itemcombinermenuslotdefinition_slotdefinition.mayPlace().test(itemStack);
                }
            });
        }

    }

    private void createResultSlot(ItemCombinerMenuSlotDefinition itemInputSlots) {
        this.addSlot(new Slot(this.resultSlots, itemInputSlots.getResultSlot().slotIndex(), itemInputSlots.getResultSlot().x(), itemInputSlots.getResultSlot().y()) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return ItemCombinerMenu.this.mayPickup(player, this.hasItem());
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                ItemCombinerMenu.this.onTake(player, carried);
            }
        });
    }

    public abstract void createResult();

    private SimpleContainer createContainer(int size) {
        return new SimpleContainer(size) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemCombinerMenu.this.slotsChanged(this);
            }
        };
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == this.inputSlots) {
            this.createResult();
        }

    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockpos) -> {
            this.clearContainer(player, this.inputSlots);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return (Boolean) this.access.evaluate((level, blockpos) -> {
            return !this.isValidBlock(level.getBlockState(blockpos)) ? false : player.isWithinBlockInteractionRange(blockpos, 4.0D);
        }, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            int j = this.getInventorySlotStart();
            int k = this.getUseRowEnd();

            if (slotIndex == this.getResultSlot()) {
                if (!this.moveItemStackTo(itemstack1, j, k, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotIndex >= 0 && slotIndex < this.getResultSlot()) {
                if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.canMoveIntoInputSlots(itemstack1) && slotIndex >= this.getInventorySlotStart() && slotIndex < this.getUseRowEnd()) {
                if (!this.moveItemStackTo(itemstack1, 0, this.getResultSlot(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= this.getInventorySlotStart() && slotIndex < this.getInventorySlotEnd()) {
                if (!this.moveItemStackTo(itemstack1, this.getUseRowStart(), this.getUseRowEnd(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= this.getUseRowStart() && slotIndex < this.getUseRowEnd() && !this.moveItemStackTo(itemstack1, this.getInventorySlotStart(), this.getInventorySlotEnd(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return true;
    }

    public int getResultSlot() {
        return this.resultSlotIndex;
    }

    private int getInventorySlotStart() {
        return this.getResultSlot() + 1;
    }

    private int getInventorySlotEnd() {
        return this.getInventorySlotStart() + 27;
    }

    private int getUseRowStart() {
        return this.getInventorySlotEnd();
    }

    private int getUseRowEnd() {
        return this.getUseRowStart() + 9;
    }
}
