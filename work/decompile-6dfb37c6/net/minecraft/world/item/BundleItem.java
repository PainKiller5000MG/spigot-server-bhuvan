package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

public class BundleItem extends Item {

    public static final int MAX_SHOWN_GRID_ITEMS_X = 4;
    public static final int MAX_SHOWN_GRID_ITEMS_Y = 3;
    public static final int MAX_SHOWN_GRID_ITEMS = 12;
    public static final int OVERFLOWING_MAX_SHOWN_GRID_ITEMS = 11;
    private static final int FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);
    private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F);
    private static final int TICKS_AFTER_FIRST_THROW = 10;
    private static final int TICKS_BETWEEN_THROWS = 2;
    private static final int TICKS_MAX_THROW_DURATION = 200;

    public BundleItem(Item.Properties properties) {
        super(properties);
    }

    public static float getFullnessDisplay(ItemStack itemStack) {
        BundleContents bundlecontents = (BundleContents) itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return bundlecontents.weight().floatValue();
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        BundleContents bundlecontents = (BundleContents) self.get(DataComponents.BUNDLE_CONTENTS);

        if (bundlecontents == null) {
            return false;
        } else {
            ItemStack itemstack1 = slot.getItem();
            BundleContents.Mutable bundlecontents_mutable = new BundleContents.Mutable(bundlecontents);

            if (clickAction == ClickAction.PRIMARY && !itemstack1.isEmpty()) {
                if (bundlecontents_mutable.tryTransfer(slot, player) > 0) {
                    playInsertSound(player);
                } else {
                    playInsertFailSound(player);
                }

                self.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
                this.broadcastChangesOnContainerMenu(player);
                return true;
            } else if (clickAction == ClickAction.SECONDARY && itemstack1.isEmpty()) {
                ItemStack itemstack2 = bundlecontents_mutable.removeOne();

                if (itemstack2 != null) {
                    ItemStack itemstack3 = slot.safeInsert(itemstack2);

                    if (itemstack3.getCount() > 0) {
                        bundlecontents_mutable.tryInsert(itemstack3);
                    } else {
                        playRemoveOneSound(player);
                    }
                }

                self.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
                this.broadcastChangesOnContainerMenu(player);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
        if (clickAction == ClickAction.PRIMARY && other.isEmpty()) {
            toggleSelectedItem(self, -1);
            return false;
        } else {
            BundleContents bundlecontents = (BundleContents) self.get(DataComponents.BUNDLE_CONTENTS);

            if (bundlecontents == null) {
                return false;
            } else {
                BundleContents.Mutable bundlecontents_mutable = new BundleContents.Mutable(bundlecontents);

                if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
                    if (slot.allowModification(player) && bundlecontents_mutable.tryInsert(other) > 0) {
                        playInsertSound(player);
                    } else {
                        playInsertFailSound(player);
                    }

                    self.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
                    this.broadcastChangesOnContainerMenu(player);
                    return true;
                } else if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
                    if (slot.allowModification(player)) {
                        ItemStack itemstack2 = bundlecontents_mutable.removeOne();

                        if (itemstack2 != null) {
                            playRemoveOneSound(player);
                            carriedItem.set(itemstack2);
                        }
                    }

                    self.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
                    this.broadcastChangesOnContainerMenu(player);
                    return true;
                } else {
                    toggleSelectedItem(self, -1);
                    return false;
                }
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    private void dropContent(Level level, Player player, ItemStack itemStack) {
        if (this.dropContent(itemStack, player)) {
            playDropContentsSound(level, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }

    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return bundlecontents.weight().compareTo(Fraction.ZERO) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return Math.min(1 + Mth.mulAndTruncate(bundlecontents.weight(), 12), 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return bundlecontents.weight().compareTo(Fraction.ONE) >= 0 ? BundleItem.FULL_BAR_COLOR : BundleItem.BAR_COLOR;
    }

    public static void toggleSelectedItem(ItemStack stack, int selectedItem) {
        BundleContents bundlecontents = (BundleContents) stack.get(DataComponents.BUNDLE_CONTENTS);

        if (bundlecontents != null) {
            BundleContents.Mutable bundlecontents_mutable = new BundleContents.Mutable(bundlecontents);

            bundlecontents_mutable.toggleSelectedItem(selectedItem);
            stack.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
        }
    }

    public static boolean hasSelectedItem(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.get(DataComponents.BUNDLE_CONTENTS);

        return bundlecontents != null && bundlecontents.getSelectedItem() != -1;
    }

    public static int getSelectedItem(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return bundlecontents.getSelectedItem();
    }

    public static ItemStack getSelectedItemStack(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.get(DataComponents.BUNDLE_CONTENTS);

        return bundlecontents != null && bundlecontents.getSelectedItem() != -1 ? bundlecontents.getItemUnsafe(bundlecontents.getSelectedItem()) : ItemStack.EMPTY;
    }

    public static int getNumberOfItemsToShow(ItemStack stack) {
        BundleContents bundlecontents = (BundleContents) stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        return bundlecontents.getNumberOfItemsToShow();
    }

    private boolean dropContent(ItemStack bundle, Player player) {
        BundleContents bundlecontents = (BundleContents) bundle.get(DataComponents.BUNDLE_CONTENTS);

        if (bundlecontents != null && !bundlecontents.isEmpty()) {
            Optional<ItemStack> optional = removeOneItemFromBundle(bundle, player, bundlecontents);

            if (optional.isPresent()) {
                player.drop((ItemStack) optional.get(), true);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static Optional<ItemStack> removeOneItemFromBundle(ItemStack self, Player player, BundleContents initialContents) {
        BundleContents.Mutable bundlecontents_mutable = new BundleContents.Mutable(initialContents);
        ItemStack itemstack1 = bundlecontents_mutable.removeOne();

        if (itemstack1 != null) {
            playRemoveOneSound(player);
            self.set(DataComponents.BUNDLE_CONTENTS, bundlecontents_mutable.toImmutable());
            return Optional.of(itemstack1);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining) {
        if (livingEntity instanceof Player player) {
            int j = this.getUseDuration(itemStack, livingEntity);
            boolean flag = ticksRemaining == j;

            if (flag || ticksRemaining < j - 10 && ticksRemaining % 2 == 0) {
                this.dropContent(level, player, itemStack);
            }
        }

    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity entity) {
        return 200;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.BUNDLE;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack bundle) {
        TooltipDisplay tooltipdisplay = (TooltipDisplay) bundle.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        return !tooltipdisplay.shows(DataComponents.BUNDLE_CONTENTS) ? Optional.empty() : Optional.ofNullable((BundleContents) bundle.get(DataComponents.BUNDLE_CONTENTS)).map(BundleTooltip::new);
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        BundleContents bundlecontents = (BundleContents) entity.getItem().get(DataComponents.BUNDLE_CONTENTS);

        if (bundlecontents != null) {
            entity.getItem().set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            ItemUtils.onContainerDestroyed(entity, bundlecontents.itemsCopy());
        }
    }

    public static List<BundleItem> getAllBundleItemColors() {
        return Stream.of(Items.BUNDLE, Items.WHITE_BUNDLE, Items.ORANGE_BUNDLE, Items.MAGENTA_BUNDLE, Items.LIGHT_BLUE_BUNDLE, Items.YELLOW_BUNDLE, Items.LIME_BUNDLE, Items.PINK_BUNDLE, Items.GRAY_BUNDLE, Items.LIGHT_GRAY_BUNDLE, Items.CYAN_BUNDLE, Items.BLACK_BUNDLE, Items.BROWN_BUNDLE, Items.GREEN_BUNDLE, Items.RED_BUNDLE, Items.BLUE_BUNDLE, Items.PURPLE_BUNDLE).map((item) -> {
            return (BundleItem) item;
        }).toList();
    }

    public static Item getByColor(DyeColor color) {
        Item item;

        switch (color) {
            case WHITE:
                item = Items.WHITE_BUNDLE;
                break;
            case ORANGE:
                item = Items.ORANGE_BUNDLE;
                break;
            case MAGENTA:
                item = Items.MAGENTA_BUNDLE;
                break;
            case LIGHT_BLUE:
                item = Items.LIGHT_BLUE_BUNDLE;
                break;
            case YELLOW:
                item = Items.YELLOW_BUNDLE;
                break;
            case LIME:
                item = Items.LIME_BUNDLE;
                break;
            case PINK:
                item = Items.PINK_BUNDLE;
                break;
            case GRAY:
                item = Items.GRAY_BUNDLE;
                break;
            case LIGHT_GRAY:
                item = Items.LIGHT_GRAY_BUNDLE;
                break;
            case CYAN:
                item = Items.CYAN_BUNDLE;
                break;
            case BLUE:
                item = Items.BLUE_BUNDLE;
                break;
            case BROWN:
                item = Items.BROWN_BUNDLE;
                break;
            case GREEN:
                item = Items.GREEN_BUNDLE;
                break;
            case RED:
                item = Items.RED_BUNDLE;
                break;
            case BLACK:
                item = Items.BLACK_BUNDLE;
                break;
            case PURPLE:
                item = Items.PURPLE_BUNDLE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return item;
    }

    private static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsertFailSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }

    private static void playDropContentsSound(Level level, Entity entity) {
        level.playSound((Entity) null, entity.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void broadcastChangesOnContainerMenu(Player player) {
        AbstractContainerMenu abstractcontainermenu = player.containerMenu;

        if (abstractcontainermenu != null) {
            abstractcontainermenu.slotsChanged(player.getInventory());
        }

    }
}
