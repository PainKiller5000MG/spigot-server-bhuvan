package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class BookCloningRecipe extends CustomRecipe {

    public BookCloningRecipe(CraftingBookCategory category) {
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
                    if (itemstack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                        if (flag1) {
                            return false;
                        }

                        flag1 = true;
                    } else {
                        if (!itemstack.is(ItemTags.BOOK_CLONING_TARGET)) {
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
        int i = 0;
        ItemStack itemstack = ItemStack.EMPTY;

        for (int j = 0; j < input.size(); ++j) {
            ItemStack itemstack1 = input.getItem(j);

            if (!itemstack1.isEmpty()) {
                if (itemstack1.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                    if (!itemstack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemstack = itemstack1;
                } else {
                    if (!itemstack1.is(ItemTags.BOOK_CLONING_TARGET)) {
                        return ItemStack.EMPTY;
                    }

                    ++i;
                }
            }
        }

        WrittenBookContent writtenbookcontent = (WrittenBookContent) itemstack.get(DataComponents.WRITTEN_BOOK_CONTENT);

        if (!itemstack.isEmpty() && i >= 1 && writtenbookcontent != null) {
            WrittenBookContent writtenbookcontent1 = writtenbookcontent.tryCraftCopy();

            if (writtenbookcontent1 == null) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemstack2 = itemstack.copyWithCount(i);

                itemstack2.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenbookcontent1);
                return itemstack2;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonnulllist.size(); ++i) {
            ItemStack itemstack = input.getItem(i);
            ItemStack itemstack1 = itemstack.getItem().getCraftingRemainder();

            if (!itemstack1.isEmpty()) {
                nonnulllist.set(i, itemstack1);
            } else if (itemstack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                nonnulllist.set(i, itemstack.copyWithCount(1));
                break;
            }
        }

        return nonnulllist;
    }

    @Override
    public RecipeSerializer<BookCloningRecipe> getSerializer() {
        return RecipeSerializer.BOOK_CLONING;
    }
}
