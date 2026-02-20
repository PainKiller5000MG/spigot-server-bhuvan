package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.function.TriConsumer;
import org.jspecify.annotations.Nullable;

public record ItemAttributeModifiers(List<ItemAttributeModifiers.Entry> modifiers) {

    public static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(List.of());
    public static final Codec<ItemAttributeModifiers> CODEC = ItemAttributeModifiers.Entry.CODEC.listOf().xmap(ItemAttributeModifiers::new, ItemAttributeModifiers::modifiers);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers> STREAM_CODEC = StreamCodec.composite(ItemAttributeModifiers.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), ItemAttributeModifiers::modifiers, ItemAttributeModifiers::new);
    public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public static ItemAttributeModifiers.Builder builder() {
        return new ItemAttributeModifiers.Builder();
    }

    public ItemAttributeModifiers withModifierAdded(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        ImmutableList.Builder<ItemAttributeModifiers.Entry> immutablelist_builder = ImmutableList.builderWithExpectedSize(this.modifiers.size() + 1);

        for (ItemAttributeModifiers.Entry itemattributemodifiers_entry : this.modifiers) {
            if (!itemattributemodifiers_entry.matches(attribute, modifier.id())) {
                immutablelist_builder.add(itemattributemodifiers_entry);
            }
        }

        immutablelist_builder.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
        return new ItemAttributeModifiers(immutablelist_builder.build());
    }

    public void forEach(EquipmentSlotGroup slot, TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> consumer) {
        for (ItemAttributeModifiers.Entry itemattributemodifiers_entry : this.modifiers) {
            if (itemattributemodifiers_entry.slot.equals(slot)) {
                consumer.accept(itemattributemodifiers_entry.attribute, itemattributemodifiers_entry.modifier, itemattributemodifiers_entry.display);
            }
        }

    }

    public void forEach(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        for (ItemAttributeModifiers.Entry itemattributemodifiers_entry : this.modifiers) {
            if (itemattributemodifiers_entry.slot.equals(slot)) {
                consumer.accept(itemattributemodifiers_entry.attribute, itemattributemodifiers_entry.modifier);
            }
        }

    }

    public void forEach(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        for (ItemAttributeModifiers.Entry itemattributemodifiers_entry : this.modifiers) {
            if (itemattributemodifiers_entry.slot.test(slot)) {
                consumer.accept(itemattributemodifiers_entry.attribute, itemattributemodifiers_entry.modifier);
            }
        }

    }

    public double compute(Holder<Attribute> attribute, double baseValue, EquipmentSlot slot) {
        double d1 = baseValue;

        for (ItemAttributeModifiers.Entry itemattributemodifiers_entry : this.modifiers) {
            if (itemattributemodifiers_entry.slot.test(slot) && itemattributemodifiers_entry.attribute == attribute) {
                double d2 = itemattributemodifiers_entry.modifier.amount();
                double d3;

                switch (itemattributemodifiers_entry.modifier.operation()) {
                    case ADD_VALUE:
                        d3 = d2;
                        break;
                    case ADD_MULTIPLIED_BASE:
                        d3 = d2 * baseValue;
                        break;
                    case ADD_MULTIPLIED_TOTAL:
                        d3 = d2 * d1;
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                d1 += d3;
            }
        }

        return d1;
    }

    public interface Display {

        Codec<ItemAttributeModifiers.Display> CODEC = ItemAttributeModifiers.Display.Type.CODEC.dispatch("type", ItemAttributeModifiers.Display::type, (itemattributemodifiers_display_type) -> {
            return itemattributemodifiers_display_type.codec;
        });
        StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display> STREAM_CODEC = ItemAttributeModifiers.Display.Type.STREAM_CODEC.cast().dispatch(ItemAttributeModifiers.Display::type, ItemAttributeModifiers.Display.Type::streamCodec);

        static ItemAttributeModifiers.Display attributeModifiers() {
            return ItemAttributeModifiers.Display.Default.INSTANCE;
        }

        static ItemAttributeModifiers.Display hidden() {
            return ItemAttributeModifiers.Display.Hidden.INSTANCE;
        }

        static ItemAttributeModifiers.Display override(Component component) {
            return new ItemAttributeModifiers.Display.OverrideText(component);
        }

        ItemAttributeModifiers.Display.Type type();

        void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier);

        public static enum Type implements StringRepresentable {

            DEFAULT("default", 0, ItemAttributeModifiers.Display.Default.CODEC, ItemAttributeModifiers.Display.Default.STREAM_CODEC), HIDDEN("hidden", 1, ItemAttributeModifiers.Display.Hidden.CODEC, ItemAttributeModifiers.Display.Hidden.STREAM_CODEC), OVERRIDE("override", 2, ItemAttributeModifiers.Display.OverrideText.CODEC, ItemAttributeModifiers.Display.OverrideText.STREAM_CODEC);

            private static final Codec<ItemAttributeModifiers.Display.Type> CODEC = StringRepresentable.<ItemAttributeModifiers.Display.Type>fromEnum(ItemAttributeModifiers.Display.Type::values);
            private static final IntFunction<ItemAttributeModifiers.Display.Type> BY_ID = ByIdMap.<ItemAttributeModifiers.Display.Type>continuous(ItemAttributeModifiers.Display.Type::id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
            private static final StreamCodec<ByteBuf, ItemAttributeModifiers.Display.Type> STREAM_CODEC = ByteBufCodecs.idMapper(ItemAttributeModifiers.Display.Type.BY_ID, ItemAttributeModifiers.Display.Type::id);
            private final String name;
            private final int id;
            private final MapCodec<? extends ItemAttributeModifiers.Display> codec;
            private final StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec;

            private Type(String name, int id, MapCodec<? extends ItemAttributeModifiers.Display> codec, StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec) {
                this.name = name;
                this.id = id;
                this.codec = codec;
                this.streamCodec = streamCodec;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }

            private int id() {
                return this.id;
            }

            private StreamCodec<RegistryFriendlyByteBuf, ? extends ItemAttributeModifiers.Display> streamCodec() {
                return this.streamCodec;
            }
        }

        public static record Default() implements ItemAttributeModifiers.Display {

            private static final ItemAttributeModifiers.Display.Default INSTANCE = new ItemAttributeModifiers.Display.Default();
            private static final MapCodec<ItemAttributeModifiers.Display.Default> CODEC = MapCodec.unit(ItemAttributeModifiers.Display.Default.INSTANCE);
            private static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Default> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Default>unit(ItemAttributeModifiers.Display.Default.INSTANCE);

            @Override
            public ItemAttributeModifiers.Display.Type type() {
                return ItemAttributeModifiers.Display.Type.DEFAULT;
            }

            @Override
            public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier) {
                double d0 = modifier.amount();
                boolean flag = false;

                if (player != null) {
                    if (modifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                        d0 += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                        flag = true;
                    } else if (modifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                        d0 += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                        flag = true;
                    }
                }

                double d1;

                if (modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
                        d1 = d0 * 10.0D;
                    } else {
                        d1 = d0;
                    }
                } else {
                    d1 = d0 * 100.0D;
                }

                if (flag) {
                    consumer.accept(CommonComponents.space().append((Component) Component.translatable("attribute.modifier.equals." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId()))).withStyle(ChatFormatting.DARK_GREEN));
                } else if (d0 > 0.0D) {
                    consumer.accept(Component.translatable("attribute.modifier.plus." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId())).withStyle(((Attribute) attribute.value()).getStyle(true)));
                } else if (d0 < 0.0D) {
                    consumer.accept(Component.translatable("attribute.modifier.take." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId())).withStyle(((Attribute) attribute.value()).getStyle(false)));
                }

            }
        }

        public static record Hidden() implements ItemAttributeModifiers.Display {

            private static final ItemAttributeModifiers.Display.Hidden INSTANCE = new ItemAttributeModifiers.Display.Hidden();
            private static final MapCodec<ItemAttributeModifiers.Display.Hidden> CODEC = MapCodec.unit(ItemAttributeModifiers.Display.Hidden.INSTANCE);
            private static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Hidden> STREAM_CODEC = StreamCodec.<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.Hidden>unit(ItemAttributeModifiers.Display.Hidden.INSTANCE);

            @Override
            public ItemAttributeModifiers.Display.Type type() {
                return ItemAttributeModifiers.Display.Type.HIDDEN;
            }

            @Override
            public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier) {}
        }

        public static record OverrideText(Component component) implements ItemAttributeModifiers.Display {

            private static final MapCodec<ItemAttributeModifiers.Display.OverrideText> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(ComponentSerialization.CODEC.fieldOf("value").forGetter(ItemAttributeModifiers.Display.OverrideText::component)).apply(instance, ItemAttributeModifiers.Display.OverrideText::new);
            });
            private static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Display.OverrideText> STREAM_CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC, ItemAttributeModifiers.Display.OverrideText::component, ItemAttributeModifiers.Display.OverrideText::new);

            @Override
            public ItemAttributeModifiers.Display.Type type() {
                return ItemAttributeModifiers.Display.Type.OVERRIDE;
            }

            @Override
            public void apply(Consumer<Component> consumer, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier) {
                consumer.accept(this.component);
            }
        }
    }

    public static record Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot, ItemAttributeModifiers.Display display) {

        public static final Codec<ItemAttributeModifiers.Entry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Attribute.CODEC.fieldOf("type").forGetter(ItemAttributeModifiers.Entry::attribute), AttributeModifier.MAP_CODEC.forGetter(ItemAttributeModifiers.Entry::modifier), EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY).forGetter(ItemAttributeModifiers.Entry::slot), ItemAttributeModifiers.Display.CODEC.optionalFieldOf("display", ItemAttributeModifiers.Display.Default.INSTANCE).forGetter(ItemAttributeModifiers.Entry::display)).apply(instance, ItemAttributeModifiers.Entry::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Entry> STREAM_CODEC = StreamCodec.composite(Attribute.STREAM_CODEC, ItemAttributeModifiers.Entry::attribute, AttributeModifier.STREAM_CODEC, ItemAttributeModifiers.Entry::modifier, EquipmentSlotGroup.STREAM_CODEC, ItemAttributeModifiers.Entry::slot, ItemAttributeModifiers.Display.STREAM_CODEC, ItemAttributeModifiers.Entry::display, ItemAttributeModifiers.Entry::new);

        public Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            this(attribute, modifier, slot, ItemAttributeModifiers.Display.attributeModifiers());
        }

        public boolean matches(Holder<Attribute> attribute, Identifier id) {
            return attribute.equals(this.attribute) && this.modifier.is(id);
        }
    }

    public static class Builder {

        private final ImmutableList.Builder<ItemAttributeModifiers.Entry> entries = ImmutableList.builder();

        private Builder() {}

        public ItemAttributeModifiers.Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            this.entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
            return this;
        }

        public ItemAttributeModifiers.Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot, ItemAttributeModifiers.Display display) {
            this.entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot, display));
            return this;
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(this.entries.build());
        }
    }
}
