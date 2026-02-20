package net.minecraft.world.inventory;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SLOT_CLICKED_OUTSIDE = -999;
    public static final int QUICKCRAFT_TYPE_CHARITABLE = 0;
    public static final int QUICKCRAFT_TYPE_GREEDY = 1;
    public static final int QUICKCRAFT_TYPE_CLONE = 2;
    public static final int QUICKCRAFT_HEADER_START = 0;
    public static final int QUICKCRAFT_HEADER_CONTINUE = 1;
    public static final int QUICKCRAFT_HEADER_END = 2;
    public static final int CARRIED_SLOT_SIZE = Integer.MAX_VALUE;
    public static final int SLOTS_PER_ROW = 9;
    public static final int SLOT_SIZE = 18;
    public NonNullList<ItemStack> lastSlots = NonNullList.<ItemStack>create();
    public NonNullList<Slot> slots = NonNullList.<Slot>create();
    private final List<DataSlot> dataSlots = Lists.newArrayList();
    private ItemStack carried;
    public NonNullList<RemoteSlot> remoteSlots;
    private final IntList remoteDataSlots;
    private RemoteSlot remoteCarried;
    private int stateId;
    private final @Nullable MenuType<?> menuType;
    public final int containerId;
    private int quickcraftType;
    public int quickcraftStatus;
    private final Set<Slot> quickcraftSlots;
    private final List<ContainerListener> containerListeners;
    private @Nullable ContainerSynchronizer synchronizer;
    private boolean suppressRemoteUpdates;

    protected AbstractContainerMenu(@Nullable MenuType<?> menuType, int containerId) {
        this.carried = ItemStack.EMPTY;
        this.remoteSlots = NonNullList.<RemoteSlot>create();
        this.remoteDataSlots = new IntArrayList();
        this.remoteCarried = RemoteSlot.PLACEHOLDER;
        this.quickcraftType = -1;
        this.quickcraftSlots = Sets.newHashSet();
        this.containerListeners = Lists.newArrayList();
        this.menuType = menuType;
        this.containerId = containerId;
    }

    protected void addInventoryHotbarSlots(Container inventory, int left, int top) {
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(inventory, k, left + k * 18, top));
        }

    }

    protected void addInventoryExtendedSlots(Container inventory, int left, int top) {
        for (int k = 0; k < 3; ++k) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(inventory, l + (k + 1) * 9, left + l * 18, top + k * 18));
            }
        }

    }

    protected void addStandardInventorySlots(Container container, int left, int top) {
        this.addInventoryExtendedSlots(container, left, top);
        int k = 4;
        int l = 58;

        this.addInventoryHotbarSlots(container, left, top + 58);
    }

    protected static boolean stillValid(ContainerLevelAccess access, Player player, Block block) {
        return (Boolean) access.evaluate((level, blockpos) -> {
            return !level.getBlockState(blockpos).is(block) ? false : player.isWithinBlockInteractionRange(blockpos, 4.0D);
        }, true);
    }

    public MenuType<?> getType() {
        if (this.menuType == null) {
            throw new UnsupportedOperationException("Unable to construct this menu by type");
        } else {
            return this.menuType;
        }
    }

    protected static void checkContainerSize(Container container, int expected) {
        int j = container.getContainerSize();

        if (j < expected) {
            throw new IllegalArgumentException("Container size " + j + " is smaller than expected " + expected);
        }
    }

    protected static void checkContainerDataCount(ContainerData data, int expected) {
        int j = data.getCount();

        if (j < expected) {
            throw new IllegalArgumentException("Container data count " + j + " is smaller than expected " + expected);
        }
    }

    public boolean isValidSlotIndex(int slotIndex) {
        return slotIndex == -1 || slotIndex == -999 || slotIndex < this.slots.size();
    }

    protected Slot addSlot(Slot slot) {
        slot.index = this.slots.size();
        this.slots.add(slot);
        this.lastSlots.add(ItemStack.EMPTY);
        this.remoteSlots.add(this.synchronizer != null ? this.synchronizer.createSlot() : RemoteSlot.PLACEHOLDER);
        return slot;
    }

    protected DataSlot addDataSlot(DataSlot dataSlot) {
        this.dataSlots.add(dataSlot);
        this.remoteDataSlots.add(0);
        return dataSlot;
    }

    protected void addDataSlots(ContainerData container) {
        for (int i = 0; i < container.getCount(); ++i) {
            this.addDataSlot(DataSlot.forContainer(container, i));
        }

    }

    public void addSlotListener(ContainerListener listener) {
        if (!this.containerListeners.contains(listener)) {
            this.containerListeners.add(listener);
            this.broadcastChanges();
        }
    }

    public void setSynchronizer(ContainerSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
        this.remoteCarried = synchronizer.createSlot();
        this.remoteSlots.replaceAll((remoteslot) -> {
            return synchronizer.createSlot();
        });
        this.sendAllDataToRemote();
    }

    public void sendAllDataToRemote() {
        List<ItemStack> list = new ArrayList(this.slots.size());
        int i = 0;

        for (int j = this.slots.size(); i < j; ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            list.add(itemstack.copy());
            ((RemoteSlot) this.remoteSlots.get(i)).force(itemstack);
        }

        ItemStack itemstack1 = this.getCarried();

        this.remoteCarried.force(itemstack1);
        int k = 0;

        for (int l = this.dataSlots.size(); k < l; ++k) {
            this.remoteDataSlots.set(k, ((DataSlot) this.dataSlots.get(k)).get());
        }

        if (this.synchronizer != null) {
            this.synchronizer.sendInitialData(this, list, itemstack1.copy(), this.remoteDataSlots.toIntArray());
        }

    }

    public void removeSlotListener(ContainerListener listener) {
        this.containerListeners.remove(listener);
    }

    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>create();

        for (Slot slot : this.slots) {
            nonnulllist.add(slot.getItem());
        }

        return nonnulllist;
    }

    public void broadcastChanges() {
        for (int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            Supplier<ItemStack> supplier = Suppliers.memoize(itemstack::copy);

            this.triggerSlotListeners(i, itemstack, supplier);
            this.synchronizeSlotToRemote(i, itemstack, supplier);
        }

        this.synchronizeCarriedToRemote();

        for (int j = 0; j < this.dataSlots.size(); ++j) {
            DataSlot dataslot = (DataSlot) this.dataSlots.get(j);
            int k = dataslot.get();

            if (dataslot.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(j, k);
            }

            this.synchronizeDataSlotToRemote(j, k);
        }

    }

    public void broadcastFullState() {
        for (int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            this.triggerSlotListeners(i, itemstack, itemstack::copy);
        }

        for (int j = 0; j < this.dataSlots.size(); ++j) {
            DataSlot dataslot = (DataSlot) this.dataSlots.get(j);

            if (dataslot.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(j, dataslot.get());
            }
        }

        this.sendAllDataToRemote();
    }

    private void updateDataSlotListeners(int id, int currentValue) {
        for (ContainerListener containerlistener : this.containerListeners) {
            containerlistener.dataChanged(this, id, currentValue);
        }

    }

    private void triggerSlotListeners(int i, ItemStack current, Supplier<ItemStack> currentCopy) {
        ItemStack itemstack1 = this.lastSlots.get(i);

        if (!ItemStack.matches(itemstack1, current)) {
            ItemStack itemstack2 = (ItemStack) currentCopy.get();

            this.lastSlots.set(i, itemstack2);

            for (ContainerListener containerlistener : this.containerListeners) {
                containerlistener.slotChanged(this, i, itemstack2);
            }
        }

    }

    private void synchronizeSlotToRemote(int i, ItemStack current, Supplier<ItemStack> currentCopy) {
        if (!this.suppressRemoteUpdates) {
            RemoteSlot remoteslot = this.remoteSlots.get(i);

            if (!remoteslot.matches(current)) {
                remoteslot.force(current);
                if (this.synchronizer != null) {
                    this.synchronizer.sendSlotChange(this, i, (ItemStack) currentCopy.get());
                }
            }

        }
    }

    private void synchronizeDataSlotToRemote(int i, int current) {
        if (!this.suppressRemoteUpdates) {
            int k = this.remoteDataSlots.getInt(i);

            if (k != current) {
                this.remoteDataSlots.set(i, current);
                if (this.synchronizer != null) {
                    this.synchronizer.sendDataChange(this, i, current);
                }
            }

        }
    }

    private void synchronizeCarriedToRemote() {
        if (!this.suppressRemoteUpdates) {
            ItemStack itemstack = this.getCarried();

            if (!this.remoteCarried.matches(itemstack)) {
                this.remoteCarried.force(itemstack);
                if (this.synchronizer != null) {
                    this.synchronizer.sendCarriedChange(this, itemstack.copy());
                }
            }

        }
    }

    public void setRemoteSlot(int slot, ItemStack itemStack) {
        ((RemoteSlot) this.remoteSlots.get(slot)).force(itemStack);
    }

    public void setRemoteSlotUnsafe(int slot, HashedStack itemStack) {
        if (slot >= 0 && slot < this.remoteSlots.size()) {
            ((RemoteSlot) this.remoteSlots.get(slot)).receive(itemStack);
        } else {
            AbstractContainerMenu.LOGGER.debug("Incorrect slot index: {} available slots: {}", slot, this.remoteSlots.size());
        }
    }

    public void setRemoteCarried(HashedStack carriedItem) {
        this.remoteCarried.receive(carriedItem);
    }

    public boolean clickMenuButton(Player player, int buttonId) {
        return false;
    }

    public Slot getSlot(int index) {
        return this.slots.get(index);
    }

    public abstract ItemStack quickMoveStack(Player player, int slotIndex);

    public void setSelectedBundleItemIndex(int slotIndex, int selectedItemIndex) {
        if (slotIndex >= 0 && slotIndex < this.slots.size()) {
            ItemStack itemstack = ((Slot) this.slots.get(slotIndex)).getItem();

            BundleItem.toggleSelectedItem(itemstack, selectedItemIndex);
        }

    }

    public void clicked(int slotIndex, int buttonNum, ClickType clickType, Player player) {
        try {
            this.doClick(slotIndex, buttonNum, clickType, player);
        } catch (Exception exception) {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Container click");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Click info");

            crashreportcategory.setDetail("Menu Type", () -> {
                return this.menuType != null ? BuiltInRegistries.MENU.getKey(this.menuType).toString() : "<no type>";
            });
            crashreportcategory.setDetail("Menu Class", () -> {
                return this.getClass().getCanonicalName();
            });
            crashreportcategory.setDetail("Slot Count", this.slots.size());
            crashreportcategory.setDetail("Slot", slotIndex);
            crashreportcategory.setDetail("Button", buttonNum);
            crashreportcategory.setDetail("Type", clickType);
            throw new ReportedException(crashreport);
        }
    }

    private void doClick(int slotIndex, int buttonNum, ClickType clickType, Player player) {
        Inventory inventory = player.getInventory();

        if (clickType == ClickType.QUICK_CRAFT) {
            int k = this.quickcraftStatus;

            this.quickcraftStatus = getQuickcraftHeader(buttonNum);
            if ((k != 1 || this.quickcraftStatus != 2) && k != this.quickcraftStatus) {
                this.resetQuickCraft();
            } else if (this.getCarried().isEmpty()) {
                this.resetQuickCraft();
            } else if (this.quickcraftStatus == 0) {
                this.quickcraftType = getQuickcraftType(buttonNum);
                if (isValidQuickcraftType(this.quickcraftType, player)) {
                    this.quickcraftStatus = 1;
                    this.quickcraftSlots.clear();
                } else {
                    this.resetQuickCraft();
                }
            } else if (this.quickcraftStatus == 1) {
                Slot slot = this.slots.get(slotIndex);
                ItemStack itemstack = this.getCarried();

                if (canItemQuickReplace(slot, itemstack, true) && slot.mayPlace(itemstack) && (this.quickcraftType == 2 || itemstack.getCount() > this.quickcraftSlots.size()) && this.canDragTo(slot)) {
                    this.quickcraftSlots.add(slot);
                }
            } else if (this.quickcraftStatus == 2) {
                if (!this.quickcraftSlots.isEmpty()) {
                    if (this.quickcraftSlots.size() == 1) {
                        int l = ((Slot) this.quickcraftSlots.iterator().next()).index;

                        this.resetQuickCraft();
                        this.doClick(l, this.quickcraftType, ClickType.PICKUP, player);
                        return;
                    }

                    ItemStack itemstack1 = this.getCarried().copy();

                    if (itemstack1.isEmpty()) {
                        this.resetQuickCraft();
                        return;
                    }

                    int i1 = this.getCarried().getCount();

                    for (Slot slot1 : this.quickcraftSlots) {
                        ItemStack itemstack2 = this.getCarried();

                        if (slot1 != null && canItemQuickReplace(slot1, itemstack2, true) && slot1.mayPlace(itemstack2) && (this.quickcraftType == 2 || itemstack2.getCount() >= this.quickcraftSlots.size()) && this.canDragTo(slot1)) {
                            int j1 = slot1.hasItem() ? slot1.getItem().getCount() : 0;
                            int k1 = Math.min(itemstack1.getMaxStackSize(), slot1.getMaxStackSize(itemstack1));
                            int l1 = Math.min(getQuickCraftPlaceCount(this.quickcraftSlots, this.quickcraftType, itemstack1) + j1, k1);

                            i1 -= l1 - j1;
                            slot1.setByPlayer(itemstack1.copyWithCount(l1));
                        }
                    }

                    itemstack1.setCount(i1);
                    this.setCarried(itemstack1);
                }

                this.resetQuickCraft();
            } else {
                this.resetQuickCraft();
            }
        } else if (this.quickcraftStatus != 0) {
            this.resetQuickCraft();
        } else if ((clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) && (buttonNum == 0 || buttonNum == 1)) {
            ClickAction clickaction = buttonNum == 0 ? ClickAction.PRIMARY : ClickAction.SECONDARY;

            if (slotIndex == -999) {
                if (!this.getCarried().isEmpty()) {
                    if (clickaction == ClickAction.PRIMARY) {
                        player.drop(this.getCarried(), true);
                        this.setCarried(ItemStack.EMPTY);
                    } else {
                        player.drop(this.getCarried().split(1), true);
                    }
                }
            } else if (clickType == ClickType.QUICK_MOVE) {
                if (slotIndex < 0) {
                    return;
                }

                Slot slot2 = this.slots.get(slotIndex);

                if (!slot2.mayPickup(player)) {
                    return;
                }

                for (ItemStack itemstack3 = this.quickMoveStack(player, slotIndex); !itemstack3.isEmpty() && ItemStack.isSameItem(slot2.getItem(), itemstack3); itemstack3 = this.quickMoveStack(player, slotIndex)) {
                    ;
                }
            } else {
                if (slotIndex < 0) {
                    return;
                }

                Slot slot3 = this.slots.get(slotIndex);
                ItemStack itemstack4 = slot3.getItem();
                ItemStack itemstack5 = this.getCarried();

                player.updateTutorialInventoryAction(itemstack5, slot3.getItem(), clickaction);
                if (!this.tryItemClickBehaviourOverride(player, clickaction, slot3, itemstack4, itemstack5)) {
                    if (itemstack4.isEmpty()) {
                        if (!itemstack5.isEmpty()) {
                            int i2 = clickaction == ClickAction.PRIMARY ? itemstack5.getCount() : 1;

                            this.setCarried(slot3.safeInsert(itemstack5, i2));
                        }
                    } else if (slot3.mayPickup(player)) {
                        if (itemstack5.isEmpty()) {
                            int j2 = clickaction == ClickAction.PRIMARY ? itemstack4.getCount() : (itemstack4.getCount() + 1) / 2;
                            Optional<ItemStack> optional = slot3.tryRemove(j2, Integer.MAX_VALUE, player);

                            optional.ifPresent((itemstack6) -> {
                                this.setCarried(itemstack6);
                                slot3.onTake(player, itemstack6);
                            });
                        } else if (slot3.mayPlace(itemstack5)) {
                            if (ItemStack.isSameItemSameComponents(itemstack4, itemstack5)) {
                                int k2 = clickaction == ClickAction.PRIMARY ? itemstack5.getCount() : 1;

                                this.setCarried(slot3.safeInsert(itemstack5, k2));
                            } else if (itemstack5.getCount() <= slot3.getMaxStackSize(itemstack5)) {
                                this.setCarried(itemstack4);
                                slot3.setByPlayer(itemstack5);
                            }
                        } else if (ItemStack.isSameItemSameComponents(itemstack4, itemstack5)) {
                            Optional<ItemStack> optional1 = slot3.tryRemove(itemstack4.getCount(), itemstack5.getMaxStackSize() - itemstack5.getCount(), player);

                            optional1.ifPresent((itemstack6) -> {
                                itemstack5.grow(itemstack6.getCount());
                                slot3.onTake(player, itemstack6);
                            });
                        }
                    }
                }

                slot3.setChanged();
            }
        } else if (clickType == ClickType.SWAP && (buttonNum >= 0 && buttonNum < 9 || buttonNum == 40)) {
            ItemStack itemstack6 = inventory.getItem(buttonNum);
            Slot slot4 = this.slots.get(slotIndex);
            ItemStack itemstack7 = slot4.getItem();

            if (!itemstack6.isEmpty() || !itemstack7.isEmpty()) {
                if (itemstack6.isEmpty()) {
                    if (slot4.mayPickup(player)) {
                        inventory.setItem(buttonNum, itemstack7);
                        slot4.onSwapCraft(itemstack7.getCount());
                        slot4.setByPlayer(ItemStack.EMPTY);
                        slot4.onTake(player, itemstack7);
                    }
                } else if (itemstack7.isEmpty()) {
                    if (slot4.mayPlace(itemstack6)) {
                        int l2 = slot4.getMaxStackSize(itemstack6);

                        if (itemstack6.getCount() > l2) {
                            slot4.setByPlayer(itemstack6.split(l2));
                        } else {
                            inventory.setItem(buttonNum, ItemStack.EMPTY);
                            slot4.setByPlayer(itemstack6);
                        }
                    }
                } else if (slot4.mayPickup(player) && slot4.mayPlace(itemstack6)) {
                    int i3 = slot4.getMaxStackSize(itemstack6);

                    if (itemstack6.getCount() > i3) {
                        slot4.setByPlayer(itemstack6.split(i3));
                        slot4.onTake(player, itemstack7);
                        if (!inventory.add(itemstack7)) {
                            player.drop(itemstack7, true);
                        }
                    } else {
                        inventory.setItem(buttonNum, itemstack7);
                        slot4.setByPlayer(itemstack6);
                        slot4.onTake(player, itemstack7);
                    }
                }
            }
        } else if (clickType == ClickType.CLONE && player.hasInfiniteMaterials() && this.getCarried().isEmpty() && slotIndex >= 0) {
            Slot slot5 = this.slots.get(slotIndex);

            if (slot5.hasItem()) {
                ItemStack itemstack8 = slot5.getItem();

                this.setCarried(itemstack8.copyWithCount(itemstack8.getMaxStackSize()));
            }
        } else if (clickType == ClickType.THROW && this.getCarried().isEmpty() && slotIndex >= 0) {
            Slot slot6 = this.slots.get(slotIndex);
            int j3 = buttonNum == 0 ? 1 : slot6.getItem().getCount();

            if (!player.canDropItems()) {
                return;
            }

            ItemStack itemstack9 = slot6.safeTake(j3, Integer.MAX_VALUE, player);

            player.drop(itemstack9, true);
            player.handleCreativeModeItemDrop(itemstack9);
            if (buttonNum == 1) {
                while (!itemstack9.isEmpty() && ItemStack.isSameItem(slot6.getItem(), itemstack9)) {
                    if (!player.canDropItems()) {
                        return;
                    }

                    itemstack9 = slot6.safeTake(j3, Integer.MAX_VALUE, player);
                    player.drop(itemstack9, true);
                    player.handleCreativeModeItemDrop(itemstack9);
                }
            }
        } else if (clickType == ClickType.PICKUP_ALL && slotIndex >= 0) {
            Slot slot7 = this.slots.get(slotIndex);
            ItemStack itemstack10 = this.getCarried();

            if (!itemstack10.isEmpty() && (!slot7.hasItem() || !slot7.mayPickup(player))) {
                int k3 = buttonNum == 0 ? 0 : this.slots.size() - 1;
                int l3 = buttonNum == 0 ? 1 : -1;

                for (int i4 = 0; i4 < 2; ++i4) {
                    for (int j4 = k3; j4 >= 0 && j4 < this.slots.size() && itemstack10.getCount() < itemstack10.getMaxStackSize(); j4 += l3) {
                        Slot slot8 = this.slots.get(j4);

                        if (slot8.hasItem() && canItemQuickReplace(slot8, itemstack10, true) && slot8.mayPickup(player) && this.canTakeItemForPickAll(itemstack10, slot8)) {
                            ItemStack itemstack11 = slot8.getItem();

                            if (i4 != 0 || itemstack11.getCount() != itemstack11.getMaxStackSize()) {
                                ItemStack itemstack12 = slot8.safeTake(itemstack11.getCount(), itemstack10.getMaxStackSize() - itemstack10.getCount(), player);

                                itemstack10.grow(itemstack12.getCount());
                            }
                        }
                    }
                }
            }
        }

    }

    private boolean tryItemClickBehaviourOverride(Player player, ClickAction clickAction, Slot slot, ItemStack clicked, ItemStack carried) {
        FeatureFlagSet featureflagset = player.level().enabledFeatures();

        return carried.isItemEnabled(featureflagset) && carried.overrideStackedOnOther(slot, clickAction, player) ? true : clicked.isItemEnabled(featureflagset) && clicked.overrideOtherStackedOnMe(carried, slot, clickAction, player, this.createCarriedSlotAccess());
    }

    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return AbstractContainerMenu.this.getCarried();
            }

            @Override
            public boolean set(ItemStack itemStack) {
                AbstractContainerMenu.this.setCarried(itemStack);
                return true;
            }
        };
    }

    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return true;
    }

    public void removed(Player player) {
        if (player instanceof ServerPlayer) {
            ItemStack itemstack = this.getCarried();

            if (!itemstack.isEmpty()) {
                dropOrPlaceInInventory(player, itemstack);
                this.setCarried(ItemStack.EMPTY);
            }

        }
    }

    private static void dropOrPlaceInInventory(Player player, ItemStack carried) {
        boolean flag;
        boolean flag1;
        label27:
        {
            flag = player.isRemoved() && player.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION;
            if (player instanceof ServerPlayer serverplayer) {
                if (serverplayer.hasDisconnected()) {
                    flag1 = true;
                    break label27;
                }
            }

            flag1 = false;
        }

        boolean flag2 = flag1;

        if (!flag && !flag2) {
            if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(carried);
            }
        } else {
            player.drop(carried, false);
        }

    }

    protected void clearContainer(Player player, Container container) {
        for (int i = 0; i < container.getContainerSize(); ++i) {
            dropOrPlaceInInventory(player, container.removeItemNoUpdate(i));
        }

    }

    public void slotsChanged(Container container) {
        this.broadcastChanges();
    }

    public void setItem(int slot, int stateId, ItemStack itemStack) {
        this.getSlot(slot).set(itemStack);
        this.stateId = stateId;
    }

    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        for (int j = 0; j < items.size(); ++j) {
            this.getSlot(j).set((ItemStack) items.get(j));
        }

        this.carried = carried;
        this.stateId = stateId;
    }

    public void setData(int id, int value) {
        ((DataSlot) this.dataSlots.get(id)).set(value);
    }

    public abstract boolean stillValid(Player player);

    protected boolean moveItemStackTo(ItemStack itemStack, int startSlot, int endSlot, boolean backwards) {
        boolean flag1 = false;
        int k = startSlot;

        if (backwards) {
            k = endSlot - 1;
        }

        if (itemStack.isStackable()) {
            while (!itemStack.isEmpty()) {
                if (backwards) {
                    if (k < startSlot) {
                        break;
                    }
                } else if (k >= endSlot) {
                    break;
                }

                Slot slot = this.slots.get(k);
                ItemStack itemstack1 = slot.getItem();

                if (!itemstack1.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, itemstack1)) {
                    int l = itemstack1.getCount() + itemStack.getCount();
                    int i1 = slot.getMaxStackSize(itemstack1);

                    if (l <= i1) {
                        itemStack.setCount(0);
                        itemstack1.setCount(l);
                        slot.setChanged();
                        flag1 = true;
                    } else if (itemstack1.getCount() < i1) {
                        itemStack.shrink(i1 - itemstack1.getCount());
                        itemstack1.setCount(i1);
                        slot.setChanged();
                        flag1 = true;
                    }
                }

                if (backwards) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            if (backwards) {
                k = endSlot - 1;
            } else {
                k = startSlot;
            }

            while (true) {
                if (backwards) {
                    if (k < startSlot) {
                        break;
                    }
                } else if (k >= endSlot) {
                    break;
                }

                Slot slot1 = this.slots.get(k);
                ItemStack itemstack2 = slot1.getItem();

                if (itemstack2.isEmpty() && slot1.mayPlace(itemStack)) {
                    int j1 = slot1.getMaxStackSize(itemStack);

                    slot1.setByPlayer(itemStack.split(Math.min(itemStack.getCount(), j1)));
                    slot1.setChanged();
                    flag1 = true;
                    break;
                }

                if (backwards) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        return flag1;
    }

    public static int getQuickcraftType(int mask) {
        return mask >> 2 & 3;
    }

    public static int getQuickcraftHeader(int mask) {
        return mask & 3;
    }

    public static int getQuickcraftMask(int header, int type) {
        return header & 3 | (type & 3) << 2;
    }

    public static boolean isValidQuickcraftType(int type, Player player) {
        return type == 0 ? true : (type == 1 ? true : type == 2 && player.hasInfiniteMaterials());
    }

    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    public static boolean canItemQuickReplace(@Nullable Slot slot, ItemStack itemStack, boolean ignoreSize) {
        boolean flag1 = slot == null || !slot.hasItem();

        return !flag1 && ItemStack.isSameItemSameComponents(itemStack, slot.getItem()) ? slot.getItem().getCount() + (ignoreSize ? 0 : itemStack.getCount()) <= itemStack.getMaxStackSize() : flag1;
    }

    public static int getQuickCraftPlaceCount(Set<Slot> quickCraftSlots, int quickCraftingType, ItemStack itemStack) {
        int j;

        switch (quickCraftingType) {
            case 0:
                j = Mth.floor((float) itemStack.getCount() / (float) quickCraftSlots.size());
                break;
            case 1:
                j = 1;
                break;
            case 2:
                j = itemStack.getMaxStackSize();
                break;
            default:
                j = itemStack.getCount();
        }

        return j;
    }

    public boolean canDragTo(Slot slot) {
        return true;
    }

    public static int getRedstoneSignalFromBlockEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof Container ? getRedstoneSignalFromContainer((Container) blockEntity) : 0;
    }

    public static int getRedstoneSignalFromContainer(@Nullable Container container) {
        if (container == null) {
            return 0;
        } else {
            float f = 0.0F;

            for (int i = 0; i < container.getContainerSize(); ++i) {
                ItemStack itemstack = container.getItem(i);

                if (!itemstack.isEmpty()) {
                    f += (float) itemstack.getCount() / (float) container.getMaxStackSize(itemstack);
                }
            }

            f /= (float) container.getContainerSize();
            return Mth.lerpDiscrete(f, 0, 15);
        }
    }

    public void setCarried(ItemStack carried) {
        this.carried = carried;
    }

    public ItemStack getCarried() {
        return this.carried;
    }

    public void suppressRemoteUpdates() {
        this.suppressRemoteUpdates = true;
    }

    public void resumeRemoteUpdates() {
        this.suppressRemoteUpdates = false;
    }

    public void transferState(AbstractContainerMenu otherContainer) {
        Table<Container, Integer, Integer> table = HashBasedTable.create();

        for (int i = 0; i < otherContainer.slots.size(); ++i) {
            Slot slot = otherContainer.slots.get(i);

            table.put(slot.container, slot.getContainerSlot(), i);
        }

        for (int j = 0; j < this.slots.size(); ++j) {
            Slot slot1 = this.slots.get(j);
            Integer integer = (Integer) table.get(slot1.container, slot1.getContainerSlot());

            if (integer != null) {
                this.lastSlots.set(j, otherContainer.lastSlots.get(integer));
                RemoteSlot remoteslot = otherContainer.remoteSlots.get(integer);
                RemoteSlot remoteslot1 = this.remoteSlots.get(j);

                if (remoteslot instanceof RemoteSlot.Synchronized) {
                    RemoteSlot.Synchronized remoteslot_synchronized = (RemoteSlot.Synchronized) remoteslot;

                    if (remoteslot1 instanceof RemoteSlot.Synchronized) {
                        RemoteSlot.Synchronized remoteslot_synchronized1 = (RemoteSlot.Synchronized) remoteslot1;

                        remoteslot_synchronized1.copyFrom(remoteslot_synchronized);
                    }
                }
            }
        }

    }

    public OptionalInt findSlot(Container inventory, int slotIndex) {
        for (int j = 0; j < this.slots.size(); ++j) {
            Slot slot = this.slots.get(j);

            if (slot.container == inventory && slotIndex == slot.getContainerSlot()) {
                return OptionalInt.of(j);
            }
        }

        return OptionalInt.empty();
    }

    public int getStateId() {
        return this.stateId;
    }

    public int incrementStateId() {
        this.stateId = this.stateId + 1 & 32767;
        return this.stateId;
    }
}
