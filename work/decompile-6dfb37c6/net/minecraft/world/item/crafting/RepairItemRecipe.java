package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class RepairItemRecipe extends CustomRecipe {

    public RepairItemRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static @Nullable Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        } else {
            ItemStack itemstack = null;

            for (int i = 0; i < input.size(); ++i) {
                ItemStack itemstack1 = input.getItem(i);

                if (!itemstack1.isEmpty()) {
                    if (itemstack != null) {
                        return canCombine(itemstack, itemstack1) ? Pair.of(itemstack, itemstack1) : null;
                    }

                    itemstack = itemstack1;
                }
            }

            return null;
        }
    }

    private static boolean canCombine(ItemStack first, ItemStack second) {
        return second.is(first.getItem()) && first.getCount() == 1 && second.getCount() == 1 && first.has(DataComponents.MAX_DAMAGE) && second.has(DataComponents.MAX_DAMAGE) && first.has(DataComponents.DAMAGE) && second.has(DataComponents.DAMAGE);
    }

    public boolean matches(CraftingInput input, Level level) {
        return getItemsToCombine(input) != null;
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> pair = getItemsToCombine(input);

        if (pair == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = (ItemStack) pair.getFirst();
            ItemStack itemstack1 = (ItemStack) pair.getSecond();
            int i = Math.max(itemstack.getMaxDamage(), itemstack1.getMaxDamage());
            int j = itemstack.getMaxDamage() - itemstack.getDamageValue();
            int k = itemstack1.getMaxDamage() - itemstack1.getDamageValue();
            int l = j + k + i * 5 / 100;
            ItemStack itemstack2 = new ItemStack(itemstack.getItem());

            itemstack2.set(DataComponents.MAX_DAMAGE, i);
            itemstack2.setDamageValue(Math.max(i - l, 0));
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack);
            ItemEnchantments itemenchantments1 = EnchantmentHelper.getEnchantmentsForCrafting(itemstack1);

            EnchantmentHelper.updateEnchantments(itemstack2, (itemenchantments_mutable) -> {
                registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().filter((holder_reference) -> {
                    return holder_reference.is(EnchantmentTags.CURSE);
                }).forEach((holder_reference) -> {
                    int i1 = Math.max(itemenchantments.getLevel(holder_reference), itemenchantments1.getLevel(holder_reference));

                    if (i1 > 0) {
                        itemenchantments_mutable.upgrade(holder_reference, i1);
                    }

                });
            });
            return itemstack2;
        }
    }

    @Override
    public RecipeSerializer<RepairItemRecipe> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}
