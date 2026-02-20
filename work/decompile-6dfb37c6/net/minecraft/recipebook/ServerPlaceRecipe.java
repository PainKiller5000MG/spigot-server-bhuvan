package net.minecraft.recipebook;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public class ServerPlaceRecipe<R extends Recipe<?>> {

    private static final int ITEM_NOT_FOUND = -1;
    private final Inventory inventory;
    private final ServerPlaceRecipe.CraftingMenuAccess<R> menu;
    private final boolean useMaxItems;
    private final int gridWidth;
    private final int gridHeight;
    private final List<Slot> inputGridSlots;
    private final List<Slot> slotsToClear;

    public static <I extends RecipeInput, R extends Recipe<I>> RecipeBookMenu.PostPlaceAction placeRecipe(ServerPlaceRecipe.CraftingMenuAccess<R> menu, int gridWidth, int gridHeight, List<Slot> inputGridSlots, List<Slot> slotsToClear, Inventory inventory, RecipeHolder<R> recipe, boolean useMaxItems, boolean allowDroppingItemsToClear) {
        ServerPlaceRecipe<R> serverplacerecipe = new ServerPlaceRecipe<R>(menu, inventory, useMaxItems, gridWidth, gridHeight, inputGridSlots, slotsToClear);

        if (!allowDroppingItemsToClear && !serverplacerecipe.testClearGrid()) {
            return RecipeBookMenu.PostPlaceAction.NOTHING;
        } else {
            StackedItemContents stackeditemcontents = new StackedItemContents();

            inventory.fillStackedContents(stackeditemcontents);
            menu.fillCraftSlotsStackedContents(stackeditemcontents);
            return serverplacerecipe.tryPlaceRecipe(recipe, stackeditemcontents);
        }
    }

    private ServerPlaceRecipe(ServerPlaceRecipe.CraftingMenuAccess<R> menu, Inventory inventory, boolean useMaxItems, int gridWidth, int gridHeight, List<Slot> inputGridSlots, List<Slot> slotsToClear) {
        this.menu = menu;
        this.inventory = inventory;
        this.useMaxItems = useMaxItems;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.inputGridSlots = inputGridSlots;
        this.slotsToClear = slotsToClear;
    }

    private RecipeBookMenu.PostPlaceAction tryPlaceRecipe(RecipeHolder<R> recipe, StackedItemContents availableItems) {
        if (availableItems.canCraft(recipe.value(), (StackedContents.Output) null)) {
            this.placeRecipe(recipe, availableItems);
            this.inventory.setChanged();
            return RecipeBookMenu.PostPlaceAction.NOTHING;
        } else {
            this.clearGrid();
            this.inventory.setChanged();
            return RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE;
        }
    }

    private void clearGrid() {
        for (Slot slot : this.slotsToClear) {
            ItemStack itemstack = slot.getItem().copy();

            this.inventory.placeItemBackInInventory(itemstack, false);
            slot.set(itemstack);
        }

        this.menu.clearCraftingContent();
    }

    private void placeRecipe(RecipeHolder<R> recipe, StackedItemContents availableItems) {
        boolean flag = this.menu.recipeMatches(recipe);
        int i = availableItems.getBiggestCraftableStack(recipe.value(), (StackedContents.Output) null);

        if (flag) {
            for (Slot slot : this.inputGridSlots) {
                ItemStack itemstack = slot.getItem();

                if (!itemstack.isEmpty() && Math.min(i, itemstack.getMaxStackSize()) < itemstack.getCount() + 1) {
                    return;
                }
            }
        }

        int j = this.calculateAmountToCraft(i, flag);
        List<Holder<Item>> list = new ArrayList();
        Recipe recipe1 = recipe.value();

        Objects.requireNonNull(list);
        if (availableItems.canCraft(recipe1, j, list::add)) {
            int k = clampToMaxStackSize(j, list);

            if (k != j) {
                list.clear();
                recipe1 = recipe.value();
                Objects.requireNonNull(list);
                if (!availableItems.canCraft(recipe1, k, list::add)) {
                    return;
                }
            }

            this.clearGrid();
            PlaceRecipeHelper.placeRecipe(this.gridWidth, this.gridHeight, recipe.value(), recipe.value().placementInfo().slotsToIngredientIndex(), (integer, l, i1, j1) -> {
                if (integer != -1) {
                    Slot slot1 = (Slot) this.inputGridSlots.get(l);
                    Holder<Item> holder = (Holder) list.get(integer);
                    int k1 = k;

                    while (k1 > 0) {
                        k1 = this.moveItemToGrid(slot1, holder, k1);
                        if (k1 == -1) {
                            return;
                        }
                    }

                }
            });
        }
    }

    private static int clampToMaxStackSize(int value, List<Holder<Item>> items) {
        for (Holder<Item> holder : items) {
            value = Math.min(value, ((Item) holder.value()).getDefaultMaxStackSize());
        }

        return value;
    }

    private int calculateAmountToCraft(int biggestCraftableStack, boolean recipeMatchesPlaced) {
        if (this.useMaxItems) {
            return biggestCraftableStack;
        } else if (recipeMatchesPlaced) {
            int j = Integer.MAX_VALUE;

            for (Slot slot : this.inputGridSlots) {
                ItemStack itemstack = slot.getItem();

                if (!itemstack.isEmpty() && j > itemstack.getCount()) {
                    j = itemstack.getCount();
                }
            }

            if (j != Integer.MAX_VALUE) {
                ++j;
            }

            return j;
        } else {
            return 1;
        }
    }

    private int moveItemToGrid(Slot targetSlot, Holder<Item> itemInInventory, int count) {
        ItemStack itemstack = targetSlot.getItem();
        int j = this.inventory.findSlotMatchingCraftingIngredient(itemInInventory, itemstack);

        if (j == -1) {
            return -1;
        } else {
            ItemStack itemstack1 = this.inventory.getItem(j);
            ItemStack itemstack2;

            if (count < itemstack1.getCount()) {
                itemstack2 = this.inventory.removeItem(j, count);
            } else {
                itemstack2 = this.inventory.removeItemNoUpdate(j);
            }

            int k = itemstack2.getCount();

            if (itemstack.isEmpty()) {
                targetSlot.set(itemstack2);
            } else {
                itemstack.grow(k);
            }

            return count - k;
        }
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int i = this.getAmountOfFreeSlotsInInventory();

        for (Slot slot : this.inputGridSlots) {
            ItemStack itemstack = slot.getItem().copy();

            if (!itemstack.isEmpty()) {
                int j = this.inventory.getSlotWithRemainingSpace(itemstack);

                if (j == -1 && list.size() <= i) {
                    for (ItemStack itemstack1 : list) {
                        if (ItemStack.isSameItem(itemstack1, itemstack) && itemstack1.getCount() != itemstack1.getMaxStackSize() && itemstack1.getCount() + itemstack.getCount() <= itemstack1.getMaxStackSize()) {
                            itemstack1.grow(itemstack.getCount());
                            itemstack.setCount(0);
                            break;
                        }
                    }

                    if (!itemstack.isEmpty()) {
                        if (list.size() >= i) {
                            return false;
                        }

                        list.add(itemstack);
                    }
                } else if (j == -1) {
                    return false;
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for (ItemStack itemstack : this.inventory.getNonEquipmentItems()) {
            if (itemstack.isEmpty()) {
                ++i;
            }
        }

        return i;
    }

    public interface CraftingMenuAccess<T extends Recipe<?>> {

        void fillCraftSlotsStackedContents(StackedItemContents stackedContents);

        void clearCraftingContent();

        boolean recipeMatches(RecipeHolder<T> recipe);
    }
}
