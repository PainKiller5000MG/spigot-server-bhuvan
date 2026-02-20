package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;

public final class Ingredient implements Predicate<ItemStack>, StackedContents.IngredientInfo<Holder<Item>> {

    public static final StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC = ByteBufCodecs.holderSet(Registries.ITEM).map(Ingredient::new, (ingredient) -> {
        return ingredient.values;
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_CONTENTS_STREAM_CODEC = ByteBufCodecs.holderSet(Registries.ITEM).map((holderset) -> {
        return holderset.size() == 0 ? Optional.empty() : Optional.of(new Ingredient(holderset));
    }, (optional) -> {
        return (HolderSet) optional.map((ingredient) -> {
            return ingredient.values;
        }).orElse(HolderSet.direct());
    });
    public static final Codec<HolderSet<Item>> NON_AIR_HOLDER_SET_CODEC = HolderSetCodec.create(Registries.ITEM, Item.CODEC, false);
    public static final Codec<Ingredient> CODEC = ExtraCodecs.nonEmptyHolderSet(Ingredient.NON_AIR_HOLDER_SET_CODEC).xmap(Ingredient::new, (ingredient) -> {
        return ingredient.values;
    });
    private final HolderSet<Item> values;

    private Ingredient(HolderSet<Item> values) {
        values.unwrap().ifRight((list) -> {
            if (list.isEmpty()) {
                throw new UnsupportedOperationException("Ingredients can't be empty");
            } else if (list.contains(Items.AIR.builtInRegistryHolder())) {
                throw new UnsupportedOperationException("Ingredient can't contain air");
            }
        });
        this.values = values;
    }

    public static boolean testOptionalIngredient(Optional<Ingredient> ingredient, ItemStack stack) {
        Optional optional1 = ingredient.map((ingredient1) -> {
            return ingredient1.test(stack);
        });

        Objects.requireNonNull(stack);
        return (Boolean) optional1.orElseGet(stack::isEmpty);
    }

    /** @deprecated */
    @Deprecated
    public Stream<Holder<Item>> items() {
        return this.values.stream();
    }

    public boolean isEmpty() {
        return this.values.size() == 0;
    }

    public boolean test(ItemStack input) {
        return input.is(this.values);
    }

    public boolean acceptsItem(Holder<Item> item) {
        return this.values.contains(item);
    }

    public boolean equals(Object o) {
        if (o instanceof Ingredient ingredient) {
            return Objects.equals(this.values, ingredient.values);
        } else {
            return false;
        }
    }

    public static Ingredient of(ItemLike itemLike) {
        return new Ingredient(HolderSet.direct(itemLike.asItem().builtInRegistryHolder()));
    }

    public static Ingredient of(ItemLike... items) {
        return of(Arrays.stream(items));
    }

    public static Ingredient of(Stream<? extends ItemLike> stream) {
        return new Ingredient(HolderSet.direct(stream.map((itemlike) -> {
            return itemlike.asItem().builtInRegistryHolder();
        }).toList()));
    }

    public static Ingredient of(HolderSet<Item> tag) {
        return new Ingredient(tag);
    }

    public SlotDisplay display() {
        return (SlotDisplay) this.values.unwrap().map(SlotDisplay.TagSlotDisplay::new, (list) -> {
            return new SlotDisplay.Composite(list.stream().map(Ingredient::displayForSingleItem).toList());
        });
    }

    public static SlotDisplay optionalIngredientToDisplay(Optional<Ingredient> ingredient) {
        return (SlotDisplay) ingredient.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE);
    }

    private static SlotDisplay displayForSingleItem(Holder<Item> item) {
        SlotDisplay slotdisplay = new SlotDisplay.ItemSlotDisplay(item);
        ItemStack itemstack = ((Item) item.value()).getCraftingRemainder();

        if (!itemstack.isEmpty()) {
            SlotDisplay slotdisplay1 = new SlotDisplay.ItemStackSlotDisplay(itemstack);

            return new SlotDisplay.WithRemainder(slotdisplay, slotdisplay1);
        } else {
            return slotdisplay;
        }
    }
}
