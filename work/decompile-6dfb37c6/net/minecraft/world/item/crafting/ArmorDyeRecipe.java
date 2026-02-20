package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

public class ArmorDyeRecipe extends CustomRecipe {

    public ArmorDyeRecipe(CraftingBookCategory category) {
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
                    if (itemstack.is(ItemTags.DYEABLE)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else {
                        if (!(itemstack.getItem() instanceof DyeItem)) {
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
        List<DyeItem> list = new ArrayList();
        ItemStack itemstack = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); ++i) {
            ItemStack itemstack1 = input.getItem(i);

            if (!itemstack1.isEmpty()) {
                if (itemstack1.is(ItemTags.DYEABLE)) {
                    if (!itemstack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemstack = itemstack1.copy();
                } else {
                    Item item = itemstack1.getItem();

                    if (!(item instanceof DyeItem)) {
                        return ItemStack.EMPTY;
                    }

                    DyeItem dyeitem = (DyeItem) item;

                    list.add(dyeitem);
                }
            }
        }

        if (!itemstack.isEmpty() && !list.isEmpty()) {
            return DyedItemColor.applyDyes(itemstack, list);
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public RecipeSerializer<ArmorDyeRecipe> getSerializer() {
        return RecipeSerializer.ARMOR_DYE;
    }
}
