package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ChargedProjectiles implements TooltipProvider {

    public static final ChargedProjectiles EMPTY = new ChargedProjectiles(List.of());
    public static final Codec<ChargedProjectiles> CODEC = ItemStack.CODEC.listOf().xmap(ChargedProjectiles::new, (chargedprojectiles) -> {
        return chargedprojectiles.items;
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargedProjectiles> STREAM_CODEC = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).map(ChargedProjectiles::new, (chargedprojectiles) -> {
        return chargedprojectiles.items;
    });
    private final List<ItemStack> items;

    private ChargedProjectiles(List<ItemStack> items) {
        this.items = items;
    }

    public static ChargedProjectiles of(ItemStack itemStack) {
        return new ChargedProjectiles(List.of(itemStack.copy()));
    }

    public static ChargedProjectiles of(List<ItemStack> items) {
        return new ChargedProjectiles(List.copyOf(Lists.transform(items, ItemStack::copy)));
    }

    public boolean contains(Item item) {
        for (ItemStack itemstack : this.items) {
            if (itemstack.is(item)) {
                return true;
            }
        }

        return false;
    }

    public List<ItemStack> getItems() {
        return Lists.transform(this.items, ItemStack::copy);
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof ChargedProjectiles) {
                ChargedProjectiles chargedprojectiles = (ChargedProjectiles) obj;

                if (ItemStack.listMatches(this.items, chargedprojectiles.items)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return ItemStack.hashStackList(this.items);
    }

    public String toString() {
        return "ChargedProjectiles[items=" + String.valueOf(this.items) + "]";
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        ItemStack itemstack = null;
        int i = 0;

        for (ItemStack itemstack1 : this.items) {
            if (itemstack == null) {
                itemstack = itemstack1;
                i = 1;
            } else if (ItemStack.matches(itemstack, itemstack1)) {
                ++i;
            } else {
                addProjectileTooltip(context, consumer, itemstack, i);
                itemstack = itemstack1;
                i = 1;
            }
        }

        if (itemstack != null) {
            addProjectileTooltip(context, consumer, itemstack, i);
        }

    }

    private static void addProjectileTooltip(Item.TooltipContext context, Consumer<Component> consumer, ItemStack projectile, int count) {
        if (count == 1) {
            consumer.accept(Component.translatable("item.minecraft.crossbow.projectile.single", projectile.getDisplayName()));
        } else {
            consumer.accept(Component.translatable("item.minecraft.crossbow.projectile.multiple", count, projectile.getDisplayName()));
        }

        TooltipDisplay tooltipdisplay = (TooltipDisplay) projectile.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        projectile.addDetailsToTooltip(context, tooltipdisplay, (Player) null, TooltipFlag.NORMAL, (component) -> {
            consumer.accept(Component.literal("  ").append(component).withStyle(ChatFormatting.GRAY));
        });
    }
}
