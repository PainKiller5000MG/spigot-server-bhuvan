package net.minecraft.world.inventory;

import java.util.List;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public abstract class AbstractCraftingMenu extends RecipeBookMenu {

    private final int width;
    private final int height;
    public final CraftingContainer craftSlots;
    public final ResultContainer resultSlots = new ResultContainer();

    public AbstractCraftingMenu(MenuType<?> menuType, int containerId, int width, int height) {
        super(menuType, containerId);
        this.width = width;
        this.height = height;
        this.craftSlots = new TransientCraftingContainer(this, width, height);
    }

    protected Slot addResultSlot(Player player, int x, int y) {
        return this.addSlot(new ResultSlot(player, this.craftSlots, this.resultSlots, 0, x, y));
    }

    protected void addCraftingGridSlots(int left, int top) {
        for (int k = 0; k < this.width; ++k) {
            for (int l = 0; l < this.height; ++l) {
                this.addSlot(new Slot(this.craftSlots, l + k * this.width, left + l * 18, top + k * 18));
            }
        }

    }

    @Override
    public RecipeBookMenu.PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?> recipe, ServerLevel level, Inventory inventory) {
        RecipeHolder<CraftingRecipe> recipeholder1 = recipe;

        this.beginPlacingRecipe();

        RecipeBookMenu.PostPlaceAction recipebookmenu_postplaceaction;

        try {
            List<Slot> list = this.getInputGridSlots();

            recipebookmenu_postplaceaction = ServerPlaceRecipe.placeRecipe(new ServerPlaceRecipe.CraftingMenuAccess<CraftingRecipe>() {
                @Override
                public void fillCraftSlotsStackedContents(StackedItemContents stackedContents) {
                    AbstractCraftingMenu.this.fillCraftSlotsStackedContents(stackedContents);
                }

                @Override
                public void clearCraftingContent() {
                    AbstractCraftingMenu.this.resultSlots.clearContent();
                    AbstractCraftingMenu.this.craftSlots.clearContent();
                }

                @Override
                public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
                    return (recipe.value()).matches(AbstractCraftingMenu.this.craftSlots.asCraftInput(), AbstractCraftingMenu.this.owner().level());
                }
            }, this.width, this.height, list, list, inventory, recipeholder1, useMaxItems, allowDroppingItemsToClear);
        } finally {
            this.finishPlacingRecipe(level, recipe);
        }

        return recipebookmenu_postplaceaction;
    }

    protected void beginPlacingRecipe() {}

    protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {}

    public abstract Slot getResultSlot();

    public abstract List<Slot> getInputGridSlots();

    public int getGridWidth() {
        return this.width;
    }

    public int getGridHeight() {
        return this.height;
    }

    protected abstract Player owner();

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents stackedContents) {
        this.craftSlots.fillStackedContents(stackedContents);
    }
}
