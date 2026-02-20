package net.minecraft.world.item.component;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ItemContainerContents implements TooltipProvider {

    private static final int NO_SLOT = -1;
    private static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(NonNullList.create());
    public static final Codec<ItemContainerContents> CODEC = ItemContainerContents.Slot.CODEC.sizeLimitedListOf(256).xmap(ItemContainerContents::fromSlots, ItemContainerContents::asSlots);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemContainerContents> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(256)).map(ItemContainerContents::new, (itemcontainercontents) -> {
        return itemcontainercontents.items;
    });
    private final NonNullList<ItemStack> items;
    private final int hashCode;

    private ItemContainerContents(NonNullList<ItemStack> items) {
        if (items.size() > 256) {
            throw new IllegalArgumentException("Got " + items.size() + " items, but maximum is 256");
        } else {
            this.items = items;
            this.hashCode = ItemStack.hashStackList(items);
        }
    }

    private ItemContainerContents(int size) {
        this(NonNullList.withSize(size, ItemStack.EMPTY));
    }

    private ItemContainerContents(List<ItemStack> items) {
        this(items.size());

        for (int i = 0; i < items.size(); ++i) {
            this.items.set(i, (ItemStack) items.get(i));
        }

    }

    private static ItemContainerContents fromSlots(List<ItemContainerContents.Slot> slots) {
        OptionalInt optionalint = slots.stream().mapToInt(ItemContainerContents.Slot::index).max();

        if (optionalint.isEmpty()) {
            return ItemContainerContents.EMPTY;
        } else {
            ItemContainerContents itemcontainercontents = new ItemContainerContents(optionalint.getAsInt() + 1);

            for (ItemContainerContents.Slot itemcontainercontents_slot : slots) {
                itemcontainercontents.items.set(itemcontainercontents_slot.index(), itemcontainercontents_slot.item());
            }

            return itemcontainercontents;
        }
    }

    public static ItemContainerContents fromItems(List<ItemStack> itemStacks) {
        int i = findLastNonEmptySlot(itemStacks);

        if (i == -1) {
            return ItemContainerContents.EMPTY;
        } else {
            ItemContainerContents itemcontainercontents = new ItemContainerContents(i + 1);

            for (int j = 0; j <= i; ++j) {
                itemcontainercontents.items.set(j, ((ItemStack) itemStacks.get(j)).copy());
            }

            return itemcontainercontents;
        }
    }

    private static int findLastNonEmptySlot(List<ItemStack> itemStacks) {
        for (int i = itemStacks.size() - 1; i >= 0; --i) {
            if (!((ItemStack) itemStacks.get(i)).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private List<ItemContainerContents.Slot> asSlots() {
        List<ItemContainerContents.Slot> list = new ArrayList();

        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.items.get(i);

            if (!itemstack.isEmpty()) {
                list.add(new ItemContainerContents.Slot(i, itemstack));
            }
        }

        return list;
    }

    public void copyInto(NonNullList<ItemStack> destination) {
        for (int i = 0; i < destination.size(); ++i) {
            ItemStack itemstack = i < this.items.size() ? (ItemStack) this.items.get(i) : ItemStack.EMPTY;

            destination.set(i, itemstack.copy());
        }

    }

    public ItemStack copyOne() {
        return this.items.isEmpty() ? ItemStack.EMPTY : ((ItemStack) this.items.get(0)).copy();
    }

    public Stream<ItemStack> stream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyStream() {
        return this.items.stream().filter((itemstack) -> {
            return !itemstack.isEmpty();
        }).map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return Iterables.filter(this.items, (itemstack) -> {
            return !itemstack.isEmpty();
        });
    }

    public Iterable<ItemStack> nonEmptyItemsCopy() {
        return Iterables.transform(this.nonEmptyItems(), ItemStack::copy);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else {
            boolean flag;

            if (obj instanceof ItemContainerContents) {
                ItemContainerContents itemcontainercontents = (ItemContainerContents) obj;

                if (ItemStack.listMatches(this.items, itemcontainercontents.items)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        int i = 0;
        int j = 0;

        for (ItemStack itemstack : this.nonEmptyItems()) {
            ++j;
            if (i <= 4) {
                ++i;
                consumer.accept(Component.translatable("item.container.item_count", itemstack.getHoverName(), itemstack.getCount()));
            }
        }

        if (j - i > 0) {
            consumer.accept(Component.translatable("item.container.more_items", j - i).withStyle(ChatFormatting.ITALIC));
        }

    }

    private static record Slot(int index, ItemStack item) {

        public static final Codec<ItemContainerContents.Slot> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.intRange(0, 255).fieldOf("slot").forGetter(ItemContainerContents.Slot::index), ItemStack.CODEC.fieldOf("item").forGetter(ItemContainerContents.Slot::item)).apply(instance, ItemContainerContents.Slot::new);
        });
    }
}
