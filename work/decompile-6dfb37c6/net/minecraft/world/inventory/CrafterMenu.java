package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.CrafterBlock;

public class CrafterMenu extends AbstractContainerMenu implements ContainerListener {

    protected static final int SLOT_COUNT = 9;
    private static final int INV_SLOT_START = 9;
    private static final int INV_SLOT_END = 36;
    private static final int USE_ROW_SLOT_START = 36;
    private static final int USE_ROW_SLOT_END = 45;
    private final ResultContainer resultContainer = new ResultContainer();
    private final ContainerData containerData;
    private final Player player;
    private final CraftingContainer container;

    public CrafterMenu(int containerId, Inventory inventory) {
        super(MenuType.CRAFTER_3x3, containerId);
        this.player = inventory.player;
        this.containerData = new SimpleContainerData(10);
        this.container = new TransientCraftingContainer(this, 3, 3);
        this.addSlots(inventory);
    }

    public CrafterMenu(int containerId, Inventory inventory, CraftingContainer container, ContainerData containerData) {
        super(MenuType.CRAFTER_3x3, containerId);
        this.player = inventory.player;
        this.containerData = containerData;
        this.container = container;
        checkContainerSize(container, 9);
        container.startOpen(inventory.player);
        this.addSlots(inventory);
        this.addSlotListener(this);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                int k = j + i * 3;

                this.addSlot(new CrafterSlot(this.container, k, 26 + j * 18, 17 + i * 18, this));
            }
        }

        this.addStandardInventorySlots(inventory, 8, 84);
        this.addSlot(new NonInteractiveResultSlot(this.resultContainer, 0, 134, 35));
        this.addDataSlots(this.containerData);
        this.refreshRecipeResult();
    }

    public void setSlotState(int slotId, boolean isEnabled) {
        CrafterSlot crafterslot = (CrafterSlot) this.getSlot(slotId);

        this.containerData.set(crafterslot.index, isEnabled ? 0 : 1);
        this.broadcastChanges();
    }

    public boolean isSlotDisabled(int slotId) {
        return slotId > -1 && slotId < 9 ? this.containerData.get(slotId) == 1 : false;
    }

    public boolean isPowered() {
        return this.containerData.get(9) == 1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (slotIndex < 9) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 9, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
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

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    private void refreshRecipeResult() {
        Player player = this.player;

        if (player instanceof ServerPlayer serverplayer) {
            ServerLevel serverlevel = serverplayer.level();
            CraftingInput craftinginput = this.container.asCraftInput();
            ItemStack itemstack = (ItemStack) CrafterBlock.getPotentialResults(serverlevel, craftinginput).map((recipeholder) -> {
                return ((CraftingRecipe) recipeholder.value()).assemble(craftinginput, serverlevel.registryAccess());
            }).orElse(ItemStack.EMPTY);

            this.resultContainer.setItem(0, itemstack);
        }

    }

    public Container getContainer() {
        return this.container;
    }

    @Override
    public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack itemStack) {
        this.refreshRecipeResult();
    }

    @Override
    public void dataChanged(AbstractContainerMenu container, int id, int value) {}
}
