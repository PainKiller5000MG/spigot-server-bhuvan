package net.minecraft.world.item.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SmeltingRecipe extends AbstractCookingRecipe {

    public SmeltingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    protected Item furnaceIcon() {
        return Items.FURNACE;
    }

    @Override
    public RecipeSerializer<SmeltingRecipe> getSerializer() {
        return RecipeSerializer.SMELTING_RECIPE;
    }

    @Override
    public RecipeType<SmeltingRecipe> getType() {
        return RecipeType.SMELTING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        RecipeBookCategory recipebookcategory;

        switch (this.category()) {
            case BLOCKS:
                recipebookcategory = RecipeBookCategories.FURNACE_BLOCKS;
                break;
            case FOOD:
                recipebookcategory = RecipeBookCategories.FURNACE_FOOD;
                break;
            case MISC:
                recipebookcategory = RecipeBookCategories.FURNACE_MISC;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return recipebookcategory;
    }
}
