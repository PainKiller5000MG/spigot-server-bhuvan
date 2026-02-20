package net.minecraft.world.item.crafting;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BlastingRecipe extends AbstractCookingRecipe {

    public BlastingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    protected Item furnaceIcon() {
        return Items.BLAST_FURNACE;
    }

    @Override
    public RecipeSerializer<BlastingRecipe> getSerializer() {
        return RecipeSerializer.BLASTING_RECIPE;
    }

    @Override
    public RecipeType<BlastingRecipe> getType() {
        return RecipeType.BLASTING;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        RecipeBookCategory recipebookcategory;

        switch (this.category()) {
            case BLOCKS:
                recipebookcategory = RecipeBookCategories.BLAST_FURNACE_BLOCKS;
                break;
            case FOOD:
            case MISC:
                recipebookcategory = RecipeBookCategories.BLAST_FURNACE_MISC;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return recipebookcategory;
    }
}
