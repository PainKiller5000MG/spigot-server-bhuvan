package net.minecraft.world.item.crafting.display;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.FuelValues;

public interface SlotDisplay {

    Codec<SlotDisplay> CODEC = BuiltInRegistries.SLOT_DISPLAY.byNameCodec().dispatch(SlotDisplay::type, SlotDisplay.Type::codec);
    StreamCodec<RegistryFriendlyByteBuf, SlotDisplay> STREAM_CODEC = ByteBufCodecs.registry(Registries.SLOT_DISPLAY).dispatch(SlotDisplay::type, SlotDisplay.Type::streamCodec);

    <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> builder);

    SlotDisplay.Type<? extends SlotDisplay> type();

    default boolean isEnabled(FeatureFlagSet enabledFeatures) {
        return true;
    }

    default List<ItemStack> resolveForStacks(ContextMap context) {
        return this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).toList();
    }

    default ItemStack resolveForFirstStack(ContextMap context) {
        return (ItemStack) this.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).findFirst().orElse(ItemStack.EMPTY);
    }

    public static record Type<T extends SlotDisplay>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {

    }

    public static class ItemStackContentsFactory implements DisplayContentsFactory.ForStacks<ItemStack> {

        public static final SlotDisplay.ItemStackContentsFactory INSTANCE = new SlotDisplay.ItemStackContentsFactory();

        public ItemStackContentsFactory() {}

        @Override
        public ItemStack forStack(ItemStack stack) {
            return stack;
        }
    }

    public static class Empty implements SlotDisplay {

        public static final SlotDisplay.Empty INSTANCE = new SlotDisplay.Empty();
        public static final MapCodec<SlotDisplay.Empty> MAP_CODEC = MapCodec.unit(SlotDisplay.Empty.INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Empty> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, SlotDisplay.Empty>unit(SlotDisplay.Empty.INSTANCE);
        public static final SlotDisplay.Type<SlotDisplay.Empty> TYPE = new SlotDisplay.Type<SlotDisplay.Empty>(SlotDisplay.Empty.MAP_CODEC, SlotDisplay.Empty.STREAM_CODEC);

        private Empty() {}

        @Override
        public SlotDisplay.Type<SlotDisplay.Empty> type() {
            return SlotDisplay.Empty.TYPE;
        }

        public String toString() {
            return "<empty>";
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return Stream.empty();
        }
    }

    public static class AnyFuel implements SlotDisplay {

        public static final SlotDisplay.AnyFuel INSTANCE = new SlotDisplay.AnyFuel();
        public static final MapCodec<SlotDisplay.AnyFuel> MAP_CODEC = MapCodec.unit(SlotDisplay.AnyFuel.INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.AnyFuel> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, SlotDisplay.AnyFuel>unit(SlotDisplay.AnyFuel.INSTANCE);
        public static final SlotDisplay.Type<SlotDisplay.AnyFuel> TYPE = new SlotDisplay.Type<SlotDisplay.AnyFuel>(SlotDisplay.AnyFuel.MAP_CODEC, SlotDisplay.AnyFuel.STREAM_CODEC);

        private AnyFuel() {}

        @Override
        public SlotDisplay.Type<SlotDisplay.AnyFuel> type() {
            return SlotDisplay.AnyFuel.TYPE;
        }

        public String toString() {
            return "<any fuel>";
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> displaycontentsfactory_forstacks) {
                FuelValues fuelvalues = (FuelValues) context.getOptional(SlotDisplayContext.FUEL_VALUES);

                if (fuelvalues != null) {
                    Stream stream = fuelvalues.fuelItems().stream();

                    Objects.requireNonNull(displaycontentsfactory_forstacks);
                    return stream.map(displaycontentsfactory_forstacks::forStack);
                }
            }

            return Stream.empty();
        }
    }

    public static record SmithingTrimDemoSlotDisplay(SlotDisplay base, SlotDisplay material, Holder<TrimPattern> pattern) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.SmithingTrimDemoSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(SlotDisplay.CODEC.fieldOf("base").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::base), SlotDisplay.CODEC.fieldOf("material").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::material), TrimPattern.CODEC.fieldOf("pattern").forGetter(SlotDisplay.SmithingTrimDemoSlotDisplay::pattern)).apply(instance, SlotDisplay.SmithingTrimDemoSlotDisplay::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.SmithingTrimDemoSlotDisplay> STREAM_CODEC = StreamCodec.composite(SlotDisplay.STREAM_CODEC, SlotDisplay.SmithingTrimDemoSlotDisplay::base, SlotDisplay.STREAM_CODEC, SlotDisplay.SmithingTrimDemoSlotDisplay::material, TrimPattern.STREAM_CODEC, SlotDisplay.SmithingTrimDemoSlotDisplay::pattern, SlotDisplay.SmithingTrimDemoSlotDisplay::new);
        public static final SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> TYPE = new SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay>(SlotDisplay.SmithingTrimDemoSlotDisplay.MAP_CODEC, SlotDisplay.SmithingTrimDemoSlotDisplay.STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.SmithingTrimDemoSlotDisplay> type() {
            return SlotDisplay.SmithingTrimDemoSlotDisplay.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> displaycontentsfactory_forstacks) {
                HolderLookup.Provider holderlookup_provider = (HolderLookup.Provider) context.getOptional(SlotDisplayContext.REGISTRIES);

                if (holderlookup_provider != null) {
                    RandomSource randomsource = RandomSource.create((long) System.identityHashCode(this));
                    List<ItemStack> list = this.base.resolveForStacks(context);

                    if (list.isEmpty()) {
                        return Stream.empty();
                    }

                    List<ItemStack> list1 = this.material.resolveForStacks(context);

                    if (list1.isEmpty()) {
                        return Stream.empty();
                    }

                    Stream stream = Stream.generate(() -> {
                        ItemStack itemstack = (ItemStack) Util.getRandom(list, randomsource);
                        ItemStack itemstack1 = (ItemStack) Util.getRandom(list1, randomsource);

                        return SmithingTrimRecipe.applyTrim(holderlookup_provider, itemstack, itemstack1, this.pattern);
                    }).limit(256L).filter((itemstack) -> {
                        return !itemstack.isEmpty();
                    }).limit(16L);

                    Objects.requireNonNull(displaycontentsfactory_forstacks);
                    return stream.map(displaycontentsfactory_forstacks::forStack);
                }
            }

            return Stream.empty();
        }
    }

    public static record ItemSlotDisplay(Holder<Item> item) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.ItemSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(Item.CODEC.fieldOf("item").forGetter(SlotDisplay.ItemSlotDisplay::item)).apply(instance, SlotDisplay.ItemSlotDisplay::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemSlotDisplay> STREAM_CODEC = StreamCodec.composite(Item.STREAM_CODEC, SlotDisplay.ItemSlotDisplay::item, SlotDisplay.ItemSlotDisplay::new);
        public static final SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> TYPE = new SlotDisplay.Type<SlotDisplay.ItemSlotDisplay>(SlotDisplay.ItemSlotDisplay.MAP_CODEC, SlotDisplay.ItemSlotDisplay.STREAM_CODEC);

        public ItemSlotDisplay(Item item) {
            this((Holder) item.builtInRegistryHolder());
        }

        @Override
        public SlotDisplay.Type<SlotDisplay.ItemSlotDisplay> type() {
            return SlotDisplay.ItemSlotDisplay.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> displaycontentsfactory_forstacks) {
                return Stream.of(displaycontentsfactory_forstacks.forStack(this.item));
            } else {
                return Stream.empty();
            }
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return ((Item) this.item.value()).isEnabled(enabledFeatures);
        }
    }

    public static record ItemStackSlotDisplay(ItemStack stack) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.ItemStackSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(ItemStack.STRICT_CODEC.fieldOf("item").forGetter(SlotDisplay.ItemStackSlotDisplay::stack)).apply(instance, SlotDisplay.ItemStackSlotDisplay::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.ItemStackSlotDisplay> STREAM_CODEC = StreamCodec.composite(ItemStack.STREAM_CODEC, SlotDisplay.ItemStackSlotDisplay::stack, SlotDisplay.ItemStackSlotDisplay::new);
        public static final SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> TYPE = new SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay>(SlotDisplay.ItemStackSlotDisplay.MAP_CODEC, SlotDisplay.ItemStackSlotDisplay.STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.ItemStackSlotDisplay> type() {
            return SlotDisplay.ItemStackSlotDisplay.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> displaycontentsfactory_forstacks) {
                return Stream.of(displaycontentsfactory_forstacks.forStack(this.stack));
            } else {
                return Stream.empty();
            }
        }

        public boolean equals(Object o) {
            boolean flag;

            if (this != o) {
                label26:
                {
                    if (o instanceof SlotDisplay.ItemStackSlotDisplay) {
                        SlotDisplay.ItemStackSlotDisplay slotdisplay_itemstackslotdisplay = (SlotDisplay.ItemStackSlotDisplay) o;

                        if (ItemStack.matches(this.stack, slotdisplay_itemstackslotdisplay.stack)) {
                            break label26;
                        }
                    }

                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.stack.getItem().isEnabled(enabledFeatures);
        }
    }

    public static record TagSlotDisplay(TagKey<Item> tag) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.TagSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(SlotDisplay.TagSlotDisplay::tag)).apply(instance, SlotDisplay.TagSlotDisplay::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.TagSlotDisplay> STREAM_CODEC = StreamCodec.composite(TagKey.streamCodec(Registries.ITEM), SlotDisplay.TagSlotDisplay::tag, SlotDisplay.TagSlotDisplay::new);
        public static final SlotDisplay.Type<SlotDisplay.TagSlotDisplay> TYPE = new SlotDisplay.Type<SlotDisplay.TagSlotDisplay>(SlotDisplay.TagSlotDisplay.MAP_CODEC, SlotDisplay.TagSlotDisplay.STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.TagSlotDisplay> type() {
            return SlotDisplay.TagSlotDisplay.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForStacks<T> displaycontentsfactory_forstacks) {
                HolderLookup.Provider holderlookup_provider = (HolderLookup.Provider) context.getOptional(SlotDisplayContext.REGISTRIES);

                if (holderlookup_provider != null) {
                    return holderlookup_provider.lookupOrThrow(Registries.ITEM).get(this.tag).map((holderset_named) -> {
                        Stream stream = holderset_named.stream();

                        Objects.requireNonNull(displaycontentsfactory_forstacks);
                        return stream.map(displaycontentsfactory_forstacks::forStack);
                    }).stream().flatMap((stream) -> {
                        return stream;
                    });
                }
            }

            return Stream.empty();
        }
    }

    public static record Composite(List<SlotDisplay> contents) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.Composite> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(SlotDisplay.CODEC.listOf().fieldOf("contents").forGetter(SlotDisplay.Composite::contents)).apply(instance, SlotDisplay.Composite::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.Composite> STREAM_CODEC = StreamCodec.composite(SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()), SlotDisplay.Composite::contents, SlotDisplay.Composite::new);
        public static final SlotDisplay.Type<SlotDisplay.Composite> TYPE = new SlotDisplay.Type<SlotDisplay.Composite>(SlotDisplay.Composite.MAP_CODEC, SlotDisplay.Composite.STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.Composite> type() {
            return SlotDisplay.Composite.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            return this.contents.stream().flatMap((slotdisplay) -> {
                return slotdisplay.resolve(context, factory);
            });
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.contents.stream().allMatch((slotdisplay) -> {
                return slotdisplay.isEnabled(enabledFeatures);
            });
        }
    }

    public static record WithRemainder(SlotDisplay input, SlotDisplay remainder) implements SlotDisplay {

        public static final MapCodec<SlotDisplay.WithRemainder> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(SlotDisplay.CODEC.fieldOf("input").forGetter(SlotDisplay.WithRemainder::input), SlotDisplay.CODEC.fieldOf("remainder").forGetter(SlotDisplay.WithRemainder::remainder)).apply(instance, SlotDisplay.WithRemainder::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotDisplay.WithRemainder> STREAM_CODEC = StreamCodec.composite(SlotDisplay.STREAM_CODEC, SlotDisplay.WithRemainder::input, SlotDisplay.STREAM_CODEC, SlotDisplay.WithRemainder::remainder, SlotDisplay.WithRemainder::new);
        public static final SlotDisplay.Type<SlotDisplay.WithRemainder> TYPE = new SlotDisplay.Type<SlotDisplay.WithRemainder>(SlotDisplay.WithRemainder.MAP_CODEC, SlotDisplay.WithRemainder.STREAM_CODEC);

        @Override
        public SlotDisplay.Type<SlotDisplay.WithRemainder> type() {
            return SlotDisplay.WithRemainder.TYPE;
        }

        @Override
        public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
            if (factory instanceof DisplayContentsFactory.ForRemainders<T> displaycontentsfactory_forremainders) {
                List<T> list = this.remainder.resolve(context, factory).toList();

                return this.input.resolve(context, factory).map((object) -> {
                    return displaycontentsfactory_forremainders.addRemainder(object, list);
                });
            } else {
                return this.input.<T>resolve(context, factory);
            }
        }

        @Override
        public boolean isEnabled(FeatureFlagSet enabledFeatures) {
            return this.input.isEnabled(enabledFeatures) && this.remainder.isEnabled(enabledFeatures);
        }
    }
}
