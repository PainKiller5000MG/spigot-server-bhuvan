package net.minecraft.world.inventory;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class LoomMenu extends AbstractContainerMenu {

    private static final int PATTERN_NOT_SET = -1;
    private static final int INV_SLOT_START = 4;
    private static final int INV_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;
    private final ContainerLevelAccess access;
    private final DataSlot selectedBannerPatternIndex;
    private List<Holder<BannerPattern>> selectablePatterns;
    private Runnable slotUpdateListener;
    private final HolderGetter<BannerPattern> patternGetter;
    private final Slot bannerSlot;
    private final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;
    private long lastSoundTime;
    private final Container inputContainer;
    private final Container outputContainer;

    public LoomMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public LoomMenu(int containerId, Inventory inventory, final ContainerLevelAccess access) {
        super(MenuType.LOOM, containerId);
        this.selectedBannerPatternIndex = DataSlot.standalone();
        this.selectablePatterns = List.of();
        this.slotUpdateListener = () -> {
        };
        this.inputContainer = new SimpleContainer(3) {
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotsChanged(this);
                LoomMenu.this.slotUpdateListener.run();
            }
        };
        this.outputContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                LoomMenu.this.slotUpdateListener.run();
            }
        };
        this.access = access;
        this.bannerSlot = this.addSlot(new Slot(this.inputContainer, 0, 13, 26) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.getItem() instanceof BannerItem;
            }
        });
        this.dyeSlot = this.addSlot(new Slot(this.inputContainer, 1, 33, 26) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.getItem() instanceof DyeItem;
            }
        });
        this.patternSlot = this.addSlot(new Slot(this.inputContainer, 2, 23, 45) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.has(DataComponents.PROVIDES_BANNER_PATTERNS);
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.outputContainer, 0, 143, 57) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                LoomMenu.this.bannerSlot.remove(1);
                LoomMenu.this.dyeSlot.remove(1);
                if (!LoomMenu.this.bannerSlot.hasItem() || !LoomMenu.this.dyeSlot.hasItem()) {
                    LoomMenu.this.selectedBannerPatternIndex.set(-1);
                }

                access.execute((level, blockpos) -> {
                    long j = level.getGameTime();

                    if (LoomMenu.this.lastSoundTime != j) {
                        level.playSound((Entity) null, blockpos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        LoomMenu.this.lastSoundTime = j;
                    }

                });
                super.onTake(player, carried);
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.selectedBannerPatternIndex);
        this.patternGetter = inventory.player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.LOOM);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= 0 && buttonId < this.selectablePatterns.size()) {
            this.selectedBannerPatternIndex.set(buttonId);
            this.setupResultSlot((Holder) this.selectablePatterns.get(buttonId));
            return true;
        } else {
            return false;
        }
    }

    private List<Holder<BannerPattern>> getSelectablePatterns(ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            return (List) this.patternGetter.get(BannerPatternTags.NO_ITEM_REQUIRED).map(ImmutableList::copyOf).orElse(ImmutableList.of());
        } else {
            TagKey<BannerPattern> tagkey = (TagKey) patternStack.get(DataComponents.PROVIDES_BANNER_PATTERNS);

            return tagkey != null ? (List) this.patternGetter.get(tagkey).map(ImmutableList::copyOf).orElse(ImmutableList.of()) : List.of();
        }
    }

    private boolean isValidPatternIndex(int selectedPattern) {
        return selectedPattern >= 0 && selectedPattern < this.selectablePatterns.size();
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack itemstack = this.bannerSlot.getItem();
        ItemStack itemstack1 = this.dyeSlot.getItem();
        ItemStack itemstack2 = this.patternSlot.getItem();

        if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
            int i = this.selectedBannerPatternIndex.get();
            boolean flag = this.isValidPatternIndex(i);
            List<Holder<BannerPattern>> list = this.selectablePatterns;

            this.selectablePatterns = this.getSelectablePatterns(itemstack2);
            Holder<BannerPattern> holder;

            if (this.selectablePatterns.size() == 1) {
                this.selectedBannerPatternIndex.set(0);
                holder = (Holder) this.selectablePatterns.get(0);
            } else if (!flag) {
                this.selectedBannerPatternIndex.set(-1);
                holder = null;
            } else {
                Holder<BannerPattern> holder1 = (Holder) list.get(i);
                int j = this.selectablePatterns.indexOf(holder1);

                if (j != -1) {
                    holder = holder1;
                    this.selectedBannerPatternIndex.set(j);
                } else {
                    holder = null;
                    this.selectedBannerPatternIndex.set(-1);
                }
            }

            if (holder != null) {
                BannerPatternLayers bannerpatternlayers = (BannerPatternLayers) itemstack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                boolean flag1 = bannerpatternlayers.layers().size() >= 6;

                if (flag1) {
                    this.selectedBannerPatternIndex.set(-1);
                    this.resultSlot.set(ItemStack.EMPTY);
                } else {
                    this.setupResultSlot(holder);
                }
            } else {
                this.resultSlot.set(ItemStack.EMPTY);
            }

            this.broadcastChanges();
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
            this.selectablePatterns = List.of();
            this.selectedBannerPatternIndex.set(-1);
        }
    }

    public List<Holder<BannerPattern>> getSelectablePatterns() {
        return this.selectablePatterns;
    }

    public int getSelectedBannerPatternIndex() {
        return this.selectedBannerPatternIndex.get();
    }

    public void registerUpdateListener(Runnable slotUpdateListener) {
        this.slotUpdateListener = slotUpdateListener;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            if (slotIndex == this.resultSlot.index) {
                if (!this.moveItemStackTo(itemstack1, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotIndex != this.dyeSlot.index && slotIndex != this.bannerSlot.index && slotIndex != this.patternSlot.index) {
                if (itemstack1.getItem() instanceof BannerItem) {
                    if (!this.moveItemStackTo(itemstack1, this.bannerSlot.index, this.bannerSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.getItem() instanceof DyeItem) {
                    if (!this.moveItemStackTo(itemstack1, this.dyeSlot.index, this.dyeSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemstack1.has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
                    if (!this.moveItemStackTo(itemstack1, this.patternSlot.index, this.patternSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 4 && slotIndex < 31) {
                    if (!this.moveItemStackTo(itemstack1, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= 31 && slotIndex < 40 && !this.moveItemStackTo(itemstack1, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 4, 40, false)) {
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockpos) -> {
            this.clearContainer(player, this.inputContainer);
        });
    }

    private void setupResultSlot(Holder<BannerPattern> pattern) {
        ItemStack itemstack = this.bannerSlot.getItem();
        ItemStack itemstack1 = this.dyeSlot.getItem();
        ItemStack itemstack2 = ItemStack.EMPTY;

        if (!itemstack.isEmpty() && !itemstack1.isEmpty()) {
            itemstack2 = itemstack.copyWithCount(1);
            DyeColor dyecolor = ((DyeItem) itemstack1.getItem()).getDyeColor();

            itemstack2.update(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY, (bannerpatternlayers) -> {
                return (new BannerPatternLayers.Builder()).addAll(bannerpatternlayers).add(pattern, dyecolor).build();
            });
        }

        if (!ItemStack.matches(itemstack2, this.resultSlot.getItem())) {
            this.resultSlot.set(itemstack2);
        }

    }

    public Slot getBannerSlot() {
        return this.bannerSlot;
    }

    public Slot getDyeSlot() {
        return this.dyeSlot;
    }

    public Slot getPatternSlot() {
        return this.patternSlot;
    }

    public Slot getResultSlot() {
        return this.resultSlot;
    }
}
