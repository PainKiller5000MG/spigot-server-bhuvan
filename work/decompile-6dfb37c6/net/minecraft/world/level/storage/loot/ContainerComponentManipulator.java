package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.slot.SlotCollection;

public interface ContainerComponentManipulator<T> {

    DataComponentType<T> type();

    T empty();

    T setContents(T component, Stream<ItemStack> newContents);

    Stream<ItemStack> getContents(T component);

    default void setContents(ItemStack itemStack, T defaultValue, Stream<ItemStack> newContents) {
        T t1 = (T) itemStack.getOrDefault(this.type(), defaultValue);
        T t2 = (T) this.setContents(t1, newContents);

        itemStack.set(this.type(), t2);
    }

    default void setContents(ItemStack itemStack, Stream<ItemStack> newContents) {
        this.setContents(itemStack, this.empty(), newContents);
    }

    default void modifyItems(ItemStack itemStack, UnaryOperator<ItemStack> modifier) {
        T t0 = (T) itemStack.get(this.type());

        if (t0 != null) {
            UnaryOperator<ItemStack> unaryoperator1 = (itemstack1) -> {
                if (itemstack1.isEmpty()) {
                    return itemstack1;
                } else {
                    ItemStack itemstack2 = (ItemStack) modifier.apply(itemstack1);

                    itemstack2.limitSize(itemstack2.getMaxStackSize());
                    return itemstack2;
                }
            };

            this.setContents(itemStack, this.getContents(t0).map(unaryoperator1));
        }

    }

    default SlotCollection getSlots(ItemStack itemStack) {
        return () -> {
            T t0 = (T) itemStack.get(this.type());

            return t0 != null ? this.getContents(t0).filter((itemstack1) -> {
                return !itemstack1.isEmpty();
            }) : Stream.empty();
        };
    }
}
