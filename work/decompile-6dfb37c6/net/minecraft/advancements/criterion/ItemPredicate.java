package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentMatchers components) implements Predicate<ItemStack> {

    public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items), MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count), DataComponentMatchers.CODEC.forGetter(ItemPredicate::components)).apply(instance, ItemPredicate::new);
    });

    public boolean test(ItemStack itemStack) {
        return this.items.isPresent() && !itemStack.is((HolderSet) this.items.get()) ? false : (!this.count.matches(itemStack.getCount()) ? false : this.components.test((DataComponentGetter) itemStack));
    }

    public static class Builder {

        private Optional<HolderSet<Item>> items = Optional.empty();
        private MinMaxBounds.Ints count;
        private DataComponentMatchers components;

        public Builder() {
            this.count = MinMaxBounds.Ints.ANY;
            this.components = DataComponentMatchers.ANY;
        }

        public static ItemPredicate.Builder item() {
            return new ItemPredicate.Builder();
        }

        public ItemPredicate.Builder of(HolderGetter<Item> lookup, ItemLike... items) {
            this.items = Optional.of(HolderSet.direct((itemlike) -> {
                return itemlike.asItem().builtInRegistryHolder();
            }, items));
            return this;
        }

        public ItemPredicate.Builder of(HolderGetter<Item> lookup, TagKey<Item> tag) {
            this.items = Optional.of(lookup.getOrThrow(tag));
            return this;
        }

        public ItemPredicate.Builder withCount(MinMaxBounds.Ints count) {
            this.count = count;
            return this;
        }

        public ItemPredicate.Builder withComponents(DataComponentMatchers components) {
            this.components = components;
            return this;
        }

        public ItemPredicate build() {
            return new ItemPredicate(this.items, this.count, this.components);
        }
    }
}
