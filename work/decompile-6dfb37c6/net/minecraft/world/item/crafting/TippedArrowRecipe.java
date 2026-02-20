package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

public class TippedArrowRecipe extends CustomRecipe {

    public TippedArrowRecipe(CraftingBookCategory category) {
        super(category);
    }

    public boolean matches(CraftingInput input, Level level) {
        if (input.width() == 3 && input.height() == 3 && input.ingredientCount() == 9) {
            for (int i = 0; i < input.height(); ++i) {
                for (int j = 0; j < input.width(); ++j) {
                    ItemStack itemstack = input.getItem(j, i);

                    if (itemstack.isEmpty()) {
                        return false;
                    }

                    if (j == 1 && i == 1) {
                        if (!itemstack.is(Items.LINGERING_POTION)) {
                            return false;
                        }
                    } else if (!itemstack.is(Items.ARROW)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack itemstack = input.getItem(1, 1);

        if (!itemstack.is(Items.LINGERING_POTION)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = new ItemStack(Items.TIPPED_ARROW, 8);

            itemstack1.set(DataComponents.POTION_CONTENTS, (PotionContents) itemstack.get(DataComponents.POTION_CONTENTS));
            return itemstack1;
        }
    }

    @Override
    public RecipeSerializer<TippedArrowRecipe> getSerializer() {
        return RecipeSerializer.TIPPED_ARROW;
    }
}
