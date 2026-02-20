package net.minecraft.world.item.slot;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;

public interface SlotCollection {

    SlotCollection EMPTY = Stream::empty;

    Stream<ItemStack> itemCopies();

    default SlotCollection filter(Predicate<ItemStack> predicate) {
        return new SlotCollection.Filtered(this, predicate);
    }

    default SlotCollection flatMap(Function<ItemStack, ? extends SlotCollection> mapper) {
        return new SlotCollection.FlatMapped(this, mapper);
    }

    default SlotCollection limit(int limit) {
        return new SlotCollection.Limited(this, limit);
    }

    static SlotCollection of(SlotAccess slotAccess) {
        return () -> {
            return Stream.of(slotAccess.get().copy());
        };
    }

    static SlotCollection of(Collection<? extends SlotAccess> slots) {
        SlotCollection slotcollection;

        switch (slots.size()) {
            case 0:
                slotcollection = SlotCollection.EMPTY;
                break;
            case 1:
                slotcollection = of((SlotAccess) slots.iterator().next());
                break;
            default:
                slotcollection = () -> {
                    return slots.stream().map(SlotAccess::get).map(ItemStack::copy);
                };
        }

        return slotcollection;
    }

    static SlotCollection concat(SlotCollection first, SlotCollection second) {
        return () -> {
            return Stream.concat(first.itemCopies(), second.itemCopies());
        };
    }

    static SlotCollection concat(List<? extends SlotCollection> terms) {
        SlotCollection slotcollection;

        switch (terms.size()) {
            case 0:
                slotcollection = SlotCollection.EMPTY;
                break;
            case 1:
                slotcollection = (SlotCollection) terms.getFirst();
                break;
            case 2:
                slotcollection = concat((SlotCollection) terms.get(0), (SlotCollection) terms.get(1));
                break;
            default:
                slotcollection = () -> {
                    return terms.stream().flatMap(SlotCollection::itemCopies);
                };
        }

        return slotcollection;
    }

    public static record Filtered(SlotCollection slots, Predicate<ItemStack> filter) implements SlotCollection {

        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().filter(this.filter);
        }

        @Override
        public SlotCollection filter(Predicate<ItemStack> predicate) {
            return new SlotCollection.Filtered(this.slots, this.filter.and(predicate));
        }
    }

    public static record FlatMapped(SlotCollection slots, Function<ItemStack, ? extends SlotCollection> mapper) implements SlotCollection {

        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().map(this.mapper).flatMap(SlotCollection::itemCopies);
        }
    }

    public static record Limited(SlotCollection slots, int limit) implements SlotCollection {

        @Override
        public Stream<ItemStack> itemCopies() {
            return this.slots.itemCopies().limit((long) this.limit);
        }

        @Override
        public SlotCollection limit(int limit) {
            return new SlotCollection.Limited(this.slots, Math.min(this.limit, limit));
        }
    }
}
