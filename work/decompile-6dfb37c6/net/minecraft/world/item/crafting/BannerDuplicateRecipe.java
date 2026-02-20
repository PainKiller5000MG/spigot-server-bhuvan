package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerDuplicateRecipe extends CustomRecipe {

    public BannerDuplicateRecipe(CraftingBookCategory category) {
        super(category);
    }

    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        } else {
            DyeColor dyecolor = null;
            boolean flag = false;
            boolean flag1 = false;

            for (int i = 0; i < input.size(); ++i) {
                ItemStack itemstack = input.getItem(i);

                if (!itemstack.isEmpty()) {
                    Item item = itemstack.getItem();

                    if (!(item instanceof BannerItem)) {
                        return false;
                    }

                    BannerItem banneritem = (BannerItem) item;

                    if (dyecolor == null) {
                        dyecolor = banneritem.getColor();
                    } else if (dyecolor != banneritem.getColor()) {
                        return false;
                    }

                    int j = ((BannerPatternLayers) itemstack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)).layers().size();

                    if (j > 6) {
                        return false;
                    }

                    if (j > 0) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    }
                }
            }

            return flag1 && flag;
        }
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); ++i) {
            ItemStack itemstack = input.getItem(i);

            if (!itemstack.isEmpty()) {
                int j = ((BannerPatternLayers) itemstack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)).layers().size();

                if (j > 0 && j <= 6) {
                    return itemstack.copyWithCount(1);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonnulllist.size(); ++i) {
            ItemStack itemstack = input.getItem(i);

            if (!itemstack.isEmpty()) {
                ItemStack itemstack1 = itemstack.getItem().getCraftingRemainder();

                if (!itemstack1.isEmpty()) {
                    nonnulllist.set(i, itemstack1);
                } else if (!((BannerPatternLayers) itemstack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)).layers().isEmpty()) {
                    nonnulllist.set(i, itemstack.copyWithCount(1));
                }
            }
        }

        return nonnulllist;
    }

    @Override
    public RecipeSerializer<BannerDuplicateRecipe> getSerializer() {
        return RecipeSerializer.BANNER_DUPLICATE;
    }
}
