package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class FireworkRocketRecipe extends CustomRecipe {

    private static final Ingredient PAPER_INGREDIENT = Ingredient.of((ItemLike) Items.PAPER);
    private static final Ingredient GUNPOWDER_INGREDIENT = Ingredient.of((ItemLike) Items.GUNPOWDER);
    private static final Ingredient STAR_INGREDIENT = Ingredient.of((ItemLike) Items.FIREWORK_STAR);

    public FireworkRocketRecipe(CraftingBookCategory category) {
        super(category);
    }

    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() < 2) {
            return false;
        } else {
            boolean flag = false;
            int i = 0;

            for (int j = 0; j < input.size(); ++j) {
                ItemStack itemstack = input.getItem(j);

                if (!itemstack.isEmpty()) {
                    if (FireworkRocketRecipe.PAPER_INGREDIENT.test(itemstack)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (FireworkRocketRecipe.GUNPOWDER_INGREDIENT.test(itemstack)) {
                        ++i;
                        if (i > 3) {
                            return false;
                        }
                    } else if (!FireworkRocketRecipe.STAR_INGREDIENT.test(itemstack)) {
                        return false;
                    }
                }
            }

            return flag && i >= 1;
        }
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<FireworkExplosion> list = new ArrayList();
        int i = 0;

        for (int j = 0; j < input.size(); ++j) {
            ItemStack itemstack = input.getItem(j);

            if (!itemstack.isEmpty()) {
                if (FireworkRocketRecipe.GUNPOWDER_INGREDIENT.test(itemstack)) {
                    ++i;
                } else if (FireworkRocketRecipe.STAR_INGREDIENT.test(itemstack)) {
                    FireworkExplosion fireworkexplosion = (FireworkExplosion) itemstack.get(DataComponents.FIREWORK_EXPLOSION);

                    if (fireworkexplosion != null) {
                        list.add(fireworkexplosion);
                    }
                }
            }
        }

        ItemStack itemstack1 = new ItemStack(Items.FIREWORK_ROCKET, 3);

        itemstack1.set(DataComponents.FIREWORKS, new Fireworks(i, list));
        return itemstack1;
    }

    @Override
    public RecipeSerializer<FireworkRocketRecipe> getSerializer() {
        return RecipeSerializer.FIREWORK_ROCKET;
    }
}
