package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record UseRemainder(ItemStack convertInto) {

    public static final Codec<UseRemainder> CODEC = ItemStack.CODEC.xmap(UseRemainder::new, UseRemainder::convertInto);
    public static final StreamCodec<RegistryFriendlyByteBuf, UseRemainder> STREAM_CODEC = StreamCodec.composite(ItemStack.STREAM_CODEC, UseRemainder::convertInto, UseRemainder::new);

    public ItemStack convertIntoRemainder(ItemStack usedStack, int stackCountBeforeUsing, boolean hasInfiniteMaterials, UseRemainder.OnExtraCreatedRemainder onExtraCreatedRemainder) {
        if (hasInfiniteMaterials) {
            return usedStack;
        } else if (usedStack.getCount() >= stackCountBeforeUsing) {
            return usedStack;
        } else {
            ItemStack itemstack1 = this.convertInto.copy();

            if (usedStack.isEmpty()) {
                return itemstack1;
            } else {
                onExtraCreatedRemainder.apply(itemstack1);
                return usedStack;
            }
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            UseRemainder useremainder = (UseRemainder) o;

            return ItemStack.matches(this.convertInto, useremainder.convertInto);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return ItemStack.hashItemAndComponents(this.convertInto);
    }

    @FunctionalInterface
    public interface OnExtraCreatedRemainder {

        void apply(ItemStack extraCreatedRemainder);
    }
}
