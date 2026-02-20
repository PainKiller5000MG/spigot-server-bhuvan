package net.minecraft.world.item.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class FireworkStarFadeRecipe extends CustomRecipe {

    private static final Ingredient STAR_INGREDIENT = Ingredient.of((ItemLike) Items.FIREWORK_STAR);

    public FireworkStarFadeRecipe(CraftingBookCategory category) {
        super(category);
    }

    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); ++i) {
                ItemStack itemstack = input.getItem(i);

                if (!itemstack.isEmpty()) {
                    if (itemstack.getItem() instanceof DyeItem) {
                        flag = true;
                    } else {
                        if (!FireworkStarFadeRecipe.STAR_INGREDIENT.test(itemstack)) {
                            return false;
                        }

                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    }
                }
            }

            return flag1 && flag;
        }
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        IntList intlist = new IntArrayList();
        ItemStack itemstack = null;

        for (int i = 0; i < input.size(); ++i) {
            ItemStack itemstack1 = input.getItem(i);
            Item item = itemstack1.getItem();

            if (item instanceof DyeItem dyeitem) {
                intlist.add(dyeitem.getDyeColor().getFireworkColor());
            } else if (FireworkStarFadeRecipe.STAR_INGREDIENT.test(itemstack1)) {
                itemstack = itemstack1.copyWithCount(1);
            }
        }

        if (itemstack != null && !intlist.isEmpty()) {
            itemstack.update(DataComponents.FIREWORK_EXPLOSION, FireworkExplosion.DEFAULT, intlist, FireworkExplosion::withFadeColors);
            return itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public RecipeSerializer<FireworkStarFadeRecipe> getSerializer() {
        return RecipeSerializer.FIREWORK_STAR_FADE;
    }
}
