package net.minecraft.world.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class CartographyTableMenu extends AbstractContainerMenu {

    public static final int MAP_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final ContainerLevelAccess access;
    private long lastSoundTime;
    public final Container container;
    private final ResultContainer resultContainer;

    public CartographyTableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public CartographyTableMenu(int containerId, Inventory inventory, final ContainerLevelAccess access) {
        super(MenuType.CARTOGRAPHY_TABLE, containerId);
        this.container = new SimpleContainer(2) {
            @Override
            public void setChanged() {
                CartographyTableMenu.this.slotsChanged(this);
                super.setChanged();
            }
        };
        this.resultContainer = new ResultContainer() {
            @Override
            public void setChanged() {
                CartographyTableMenu.this.slotsChanged(this);
                super.setChanged();
            }
        };
        this.access = access;
        this.addSlot(new Slot(this.container, 0, 15, 15) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.has(DataComponents.MAP_ID);
            }
        });
        this.addSlot(new Slot(this.container, 1, 15, 52) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.is(Items.PAPER) || itemStack.is(Items.MAP) || itemStack.is(Items.GLASS_PANE);
            }
        });
        this.addSlot(new Slot(this.resultContainer, 2, 145, 39) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                ((Slot) CartographyTableMenu.this.slots.get(0)).remove(1);
                ((Slot) CartographyTableMenu.this.slots.get(1)).remove(1);
                carried.getItem().onCraftedBy(carried, player);
                access.execute((level, blockpos) -> {
                    long j = level.getGameTime();

                    if (CartographyTableMenu.this.lastSoundTime != j) {
                        level.playSound((Entity) null, blockpos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        CartographyTableMenu.this.lastSoundTime = j;
                    }

                });
                super.onTake(player, carried);
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.CARTOGRAPHY_TABLE);
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack itemstack = this.container.getItem(0);
        ItemStack itemstack1 = this.container.getItem(1);
        ItemStack itemstack2 = this.resultContainer.getItem(2);

        if (itemstack2.isEmpty() || !itemstack.isEmpty() && !itemstack1.isEmpty()) {
            if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
                this.setupResultSlot(itemstack, itemstack1, itemstack2);
            }
        } else {
            this.resultContainer.removeItemNoUpdate(2);
        }

    }

    private void setupResultSlot(ItemStack mapStack, ItemStack additionalStack, ItemStack resultStack) {
        this.access.execute((level, blockpos) -> {
            MapItemSavedData mapitemsaveddata = MapItem.getSavedData(mapStack, level);

            if (mapitemsaveddata != null) {
                ItemStack itemstack3;

                if (additionalStack.is(Items.PAPER) && !mapitemsaveddata.locked && mapitemsaveddata.scale < 4) {
                    itemstack3 = mapStack.copyWithCount(1);
                    itemstack3.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
                    this.broadcastChanges();
                } else if (additionalStack.is(Items.GLASS_PANE) && !mapitemsaveddata.locked) {
                    itemstack3 = mapStack.copyWithCount(1);
                    itemstack3.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
                    this.broadcastChanges();
                } else {
                    if (!additionalStack.is(Items.MAP)) {
                        this.resultContainer.removeItemNoUpdate(2);
                        this.broadcastChanges();
                        return;
                    }

                    itemstack3 = mapStack.copyWithCount(2);
                    this.broadcastChanges();
                }

                if (!ItemStack.matches(itemstack3, resultStack)) {
                    this.resultContainer.setItem(2, itemstack3);
                    this.broadcastChanges();
                }

            }
        });
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.resultContainer && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (slotIndex == 2) {
                itemstack1.getItem().onCraftedBy(itemstack1, player);
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotIndex != 1 && slotIndex != 0) {
                if (itemstack1.has(DataComponents.MAP_ID)) {
                    if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!itemstack1.is(Items.PAPER) && !itemstack1.is(Items.MAP) && !itemstack1.is(Items.GLASS_PANE)) {
                    if (slotIndex >= 3 && slotIndex < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
            this.broadcastChanges();
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(2);
        this.access.execute((level, blockpos) -> {
            this.clearContainer(player, this.container);
        });
    }
}
