package net.minecraft.world.item.crafting;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class RecipeCache {

    private final @Nullable RecipeCache.Entry[] entries;
    private WeakReference<@Nullable RecipeManager> cachedRecipeManager = new WeakReference((Object) null);

    public RecipeCache(int capacity) {
        this.entries = new RecipeCache.Entry[capacity];
    }

    public Optional<RecipeHolder<CraftingRecipe>> get(ServerLevel level, CraftingInput input) {
        if (input.isEmpty()) {
            return Optional.empty();
        } else {
            this.validateRecipeManager(level);

            for (int i = 0; i < this.entries.length; ++i) {
                RecipeCache.Entry recipecache_entry = this.entries[i];

                if (recipecache_entry != null && recipecache_entry.matches(input)) {
                    this.moveEntryToFront(i);
                    return Optional.ofNullable(recipecache_entry.value());
                }
            }

            return this.compute(input, level);
        }
    }

    private void validateRecipeManager(ServerLevel level) {
        RecipeManager recipemanager = level.recipeAccess();

        if (recipemanager != this.cachedRecipeManager.get()) {
            this.cachedRecipeManager = new WeakReference(recipemanager);
            Arrays.fill(this.entries, (Object) null);
        }

    }

    private Optional<RecipeHolder<CraftingRecipe>> compute(CraftingInput input, ServerLevel level) {
        Optional<RecipeHolder<CraftingRecipe>> optional = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);

        this.insert(input, (RecipeHolder) optional.orElse((Object) null));
        return optional;
    }

    private void moveEntryToFront(int index) {
        if (index > 0) {
            RecipeCache.Entry recipecache_entry = this.entries[index];

            System.arraycopy(this.entries, 0, this.entries, 1, index);
            this.entries[0] = recipecache_entry;
        }

    }

    private void insert(CraftingInput input, @Nullable RecipeHolder<CraftingRecipe> recipe) {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < input.size(); ++i) {
            nonnulllist.set(i, input.getItem(i).copyWithCount(1));
        }

        System.arraycopy(this.entries, 0, this.entries, 1, this.entries.length - 1);
        this.entries[0] = new RecipeCache.Entry(nonnulllist, input.width(), input.height(), recipe);
    }

    private static record Entry(NonNullList<ItemStack> key, int width, int height, @Nullable RecipeHolder<CraftingRecipe> value) {

        public boolean matches(CraftingInput input) {
            if (this.width == input.width() && this.height == input.height()) {
                for (int i = 0; i < this.key.size(); ++i) {
                    if (!ItemStack.isSameItemSameComponents(this.key.get(i), input.getItem(i))) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }
}
