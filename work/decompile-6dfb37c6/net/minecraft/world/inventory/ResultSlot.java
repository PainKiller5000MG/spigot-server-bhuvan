package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class ResultSlot extends Slot {

    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player player, CraftingContainer craftSlots, Container container, int id, int x, int y) {
        super(container, id, x, y);
        this.player = player;
        this.craftSlots = craftSlots;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack picked, int count) {
        this.removeCount += count;
        this.checkTakeAchievements(picked);
    }

    @Override
    protected void onSwapCraft(int count) {
        this.removeCount += count;
    }

    @Override
    protected void checkTakeAchievements(ItemStack carried) {
        if (this.removeCount > 0) {
            carried.onCraftedBy(this.player, this.removeCount);
        }

        Container container = this.container;

        if (container instanceof RecipeCraftingHolder recipecraftingholder) {
            recipecraftingholder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    private static NonNullList<ItemStack> copyAllInputItems(CraftingInput input) {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonnulllist.size(); ++i) {
            nonnulllist.set(i, input.getItem(i));
        }

        return nonnulllist;
    }

    private NonNullList<ItemStack> getRemainingItems(CraftingInput input, Level level) {
        if (level instanceof ServerLevel serverlevel) {
            return (NonNullList) serverlevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, serverlevel).map((recipeholder) -> {
                return ((CraftingRecipe) recipeholder.value()).getRemainingItems(input);
            }).orElseGet(() -> {
                return copyAllInputItems(input);
            });
        } else {
            return CraftingRecipe.defaultCraftingReminder(input);
        }
    }

    @Override
    public void onTake(Player player, ItemStack carried) {
        this.checkTakeAchievements(carried);
        CraftingInput.Positioned craftinginput_positioned = this.craftSlots.asPositionedCraftInput();
        CraftingInput craftinginput = craftinginput_positioned.input();
        int i = craftinginput_positioned.left();
        int j = craftinginput_positioned.top();
        NonNullList<ItemStack> nonnulllist = this.getRemainingItems(craftinginput, player.level());

        for (int k = 0; k < craftinginput.height(); ++k) {
            for (int l = 0; l < craftinginput.width(); ++l) {
                int i1 = l + i + (k + j) * this.craftSlots.getWidth();
                ItemStack itemstack1 = this.craftSlots.getItem(i1);
                ItemStack itemstack2 = nonnulllist.get(l + k * craftinginput.width());

                if (!itemstack1.isEmpty()) {
                    this.craftSlots.removeItem(i1, 1);
                    itemstack1 = this.craftSlots.getItem(i1);
                }

                if (!itemstack2.isEmpty()) {
                    if (itemstack1.isEmpty()) {
                        this.craftSlots.setItem(i1, itemstack2);
                    } else if (ItemStack.isSameItemSameComponents(itemstack1, itemstack2)) {
                        itemstack2.grow(itemstack1.getCount());
                        this.craftSlots.setItem(i1, itemstack2);
                    } else if (!this.player.getInventory().add(itemstack2)) {
                        this.player.drop(itemstack2, false);
                    }
                }
            }
        }

    }

    @Override
    public boolean isFake() {
        return true;
    }
}
