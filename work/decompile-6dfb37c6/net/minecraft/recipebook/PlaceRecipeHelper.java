package net.minecraft.recipebook;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

public interface PlaceRecipeHelper {

    static <T> void placeRecipe(int gridWidth, int gridHeight, Recipe<?> recipe, Iterable<T> entries, PlaceRecipeHelper.Output<T> output) {
        if (recipe instanceof ShapedRecipe shapedrecipe) {
            placeRecipe(gridWidth, gridHeight, shapedrecipe.getWidth(), shapedrecipe.getHeight(), entries, output);
        } else {
            placeRecipe(gridWidth, gridHeight, gridWidth, gridHeight, entries, output);
        }

    }

    static <T> void placeRecipe(int gridWidth, int gridHeight, int recipeWidth, int recipeHeight, Iterable<T> entries, PlaceRecipeHelper.Output<T> output) {
        Iterator<T> iterator = entries.iterator();
        int i1 = 0;

        for (int j1 = 0; j1 < gridHeight; ++j1) {
            boolean flag = (float) recipeHeight < (float) gridHeight / 2.0F;
            int k1 = Mth.floor((float) gridHeight / 2.0F - (float) recipeHeight / 2.0F);

            if (flag && k1 > j1) {
                i1 += gridWidth;
                ++j1;
            }

            for (int l1 = 0; l1 < gridWidth; ++l1) {
                if (!iterator.hasNext()) {
                    return;
                }

                flag = (float) recipeWidth < (float) gridWidth / 2.0F;
                k1 = Mth.floor((float) gridWidth / 2.0F - (float) recipeWidth / 2.0F);
                int i2 = recipeWidth;
                boolean flag1 = l1 < recipeWidth;

                if (flag) {
                    i2 = k1 + recipeWidth;
                    flag1 = k1 <= l1 && l1 < k1 + recipeWidth;
                }

                if (flag1) {
                    output.addItemToSlot(iterator.next(), i1, l1, j1);
                } else if (i2 == l1) {
                    i1 += gridWidth - l1;
                    break;
                }

                ++i1;
            }
        }

    }

    @FunctionalInterface
    public interface Output<T> {

        void addItemToSlot(T item, int gridIndex, int gridXPos, int gridYPos);
    }
}
