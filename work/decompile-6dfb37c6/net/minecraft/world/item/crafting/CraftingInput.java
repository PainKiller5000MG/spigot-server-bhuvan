package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

public class CraftingInput implements RecipeInput {

    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());
    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedItemContents stackedContents = new StackedItemContents();
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = items;
        int k = 0;

        for (ItemStack itemstack : items) {
            if (!itemstack.isEmpty()) {
                ++k;
                this.stackedContents.accountStack(itemstack, 1);
            }
        }

        this.ingredientCount = k;
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return ofPositioned(width, height, items).input();
    }

    public static CraftingInput.Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (width != 0 && height != 0) {
            int k = width - 1;
            int l = 0;
            int i1 = height - 1;
            int j1 = 0;

            for (int k1 = 0; k1 < height; ++k1) {
                boolean flag = true;

                for (int l1 = 0; l1 < width; ++l1) {
                    ItemStack itemstack = (ItemStack) items.get(l1 + k1 * width);

                    if (!itemstack.isEmpty()) {
                        k = Math.min(k, l1);
                        l = Math.max(l, l1);
                        flag = false;
                    }
                }

                if (!flag) {
                    i1 = Math.min(i1, k1);
                    j1 = Math.max(j1, k1);
                }
            }

            int i2 = l - k + 1;
            int j2 = j1 - i1 + 1;

            if (i2 > 0 && j2 > 0) {
                if (i2 == width && j2 == height) {
                    return new CraftingInput.Positioned(new CraftingInput(width, height, items), k, i1);
                } else {
                    List<ItemStack> list1 = new ArrayList(i2 * j2);

                    for (int k2 = 0; k2 < j2; ++k2) {
                        for (int l2 = 0; l2 < i2; ++l2) {
                            int i3 = l2 + k + (k2 + i1) * width;

                            list1.add((ItemStack) items.get(i3));
                        }
                    }

                    return new CraftingInput.Positioned(new CraftingInput(i2, j2, list1), k, i1);
                }
            } else {
                return CraftingInput.Positioned.EMPTY;
            }
        } else {
            return CraftingInput.Positioned.EMPTY;
        }
    }

    @Override
    public ItemStack getItem(int index) {
        return (ItemStack) this.items.get(index);
    }

    public ItemStack getItem(int x, int y) {
        return (ItemStack) this.items.get(x + y * this.width);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.ingredientCount == 0;
    }

    public StackedItemContents stackedContents() {
        return this.stackedContents;
    }

    public List<ItemStack> items() {
        return this.items;
    }

    public int ingredientCount() {
        return this.ingredientCount;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof CraftingInput)) {
            return false;
        } else {
            CraftingInput craftinginput = (CraftingInput) obj;

            return this.width == craftinginput.width && this.height == craftinginput.height && this.ingredientCount == craftinginput.ingredientCount && ItemStack.listMatches(this.items, craftinginput.items);
        }
    }

    public int hashCode() {
        int i = ItemStack.hashStackList(this.items);

        i = 31 * i + this.width;
        i = 31 * i + this.height;
        return i;
    }

    public static record Positioned(CraftingInput input, int left, int top) {

        public static final CraftingInput.Positioned EMPTY = new CraftingInput.Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
