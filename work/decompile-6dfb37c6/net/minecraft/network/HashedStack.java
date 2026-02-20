package net.minecraft.network;

import com.mojang.datafixers.DataFixUtils;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface HashedStack {

    HashedStack EMPTY = new HashedStack() {
        public String toString() {
            return "<empty>";
        }

        @Override
        public boolean matches(ItemStack stack, HashedPatchMap.HashGenerator hasher) {
            return stack.isEmpty();
        }
    };
    StreamCodec<RegistryFriendlyByteBuf, HashedStack> STREAM_CODEC = ByteBufCodecs.optional(HashedStack.ActualItem.STREAM_CODEC).map((optional) -> {
        return (HashedStack) DataFixUtils.orElse(optional, HashedStack.EMPTY);
    }, (hashedstack) -> {
        Optional optional;

        if (hashedstack instanceof HashedStack.ActualItem hashedstack_actualitem) {
            optional = Optional.of(hashedstack_actualitem);
        } else {
            optional = Optional.empty();
        }

        return optional;
    });

    boolean matches(ItemStack stack, HashedPatchMap.HashGenerator hasher);

    static HashedStack create(ItemStack itemStack, HashedPatchMap.HashGenerator hasher) {
        return (HashedStack) (itemStack.isEmpty() ? HashedStack.EMPTY : new HashedStack.ActualItem(itemStack.getItemHolder(), itemStack.getCount(), HashedPatchMap.create(itemStack.getComponentsPatch(), hasher)));
    }

    public static record ActualItem(Holder<Item> item, int count, HashedPatchMap components) implements HashedStack {

        public static final StreamCodec<RegistryFriendlyByteBuf, HashedStack.ActualItem> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.holderRegistry(Registries.ITEM), HashedStack.ActualItem::item, ByteBufCodecs.VAR_INT, HashedStack.ActualItem::count, HashedPatchMap.STREAM_CODEC, HashedStack.ActualItem::components, HashedStack.ActualItem::new);

        @Override
        public boolean matches(ItemStack itemStack, HashedPatchMap.HashGenerator hasher) {
            return this.count != itemStack.getCount() ? false : (!this.item.equals(itemStack.getItemHolder()) ? false : this.components.matches(itemStack.getComponentsPatch(), hasher));
        }
    }
}
