package net.minecraft.world.item.alchemy;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ConsumableListener;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;

public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor, List<MobEffectInstance> customEffects, Optional<String> customName) implements ConsumableListener, TooltipProvider {

    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    private static final Component NO_EFFECT = Component.translatable("effect.none").withStyle(ChatFormatting.GRAY);
    public static final int BASE_POTION_COLOR = -13083194;
    private static final Codec<PotionContents> FULL_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(Potion.CODEC.optionalFieldOf("potion").forGetter(PotionContents::potion), Codec.INT.optionalFieldOf("custom_color").forGetter(PotionContents::customColor), MobEffectInstance.CODEC.listOf().optionalFieldOf("custom_effects", List.of()).forGetter(PotionContents::customEffects), Codec.STRING.optionalFieldOf("custom_name").forGetter(PotionContents::customName)).apply(instance, PotionContents::new);
    });
    public static final Codec<PotionContents> CODEC = Codec.withAlternative(PotionContents.FULL_CODEC, Potion.CODEC, PotionContents::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionContents> STREAM_CODEC = StreamCodec.composite(Potion.STREAM_CODEC.apply(ByteBufCodecs::optional), PotionContents::potion, ByteBufCodecs.INT.apply(ByteBufCodecs::optional), PotionContents::customColor, MobEffectInstance.STREAM_CODEC.apply(ByteBufCodecs.list()), PotionContents::customEffects, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), PotionContents::customName, PotionContents::new);

    public PotionContents(Holder<Potion> potion) {
        this(Optional.of(potion), Optional.empty(), List.of(), Optional.empty());
    }

    public static ItemStack createItemStack(Item item, Holder<Potion> potion) {
        ItemStack itemstack = new ItemStack(item);

        itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return itemstack;
    }

    public boolean is(Holder<Potion> potion) {
        return this.potion.isPresent() && ((Holder) this.potion.get()).is(potion) && this.customEffects.isEmpty();
    }

    public Iterable<MobEffectInstance> getAllEffects() {
        return (Iterable<MobEffectInstance>) (this.potion.isEmpty() ? this.customEffects : (this.customEffects.isEmpty() ? ((Potion) ((Holder) this.potion.get()).value()).getEffects() : Iterables.concat(((Potion) ((Holder) this.potion.get()).value()).getEffects(), this.customEffects)));
    }

    public void forEachEffect(Consumer<MobEffectInstance> consumer, float durationScale) {
        if (this.potion.isPresent()) {
            for (MobEffectInstance mobeffectinstance : ((Potion) ((Holder) this.potion.get()).value()).getEffects()) {
                consumer.accept(mobeffectinstance.withScaledDuration(durationScale));
            }
        }

        for (MobEffectInstance mobeffectinstance1 : this.customEffects) {
            consumer.accept(mobeffectinstance1.withScaledDuration(durationScale));
        }

    }

    public PotionContents withPotion(Holder<Potion> potion) {
        return new PotionContents(Optional.of(potion), this.customColor, this.customEffects, this.customName);
    }

    public PotionContents withEffectAdded(MobEffectInstance effect) {
        return new PotionContents(this.potion, this.customColor, Util.copyAndAdd(this.customEffects, effect), this.customName);
    }

    public int getColor() {
        return this.getColorOr(-13083194);
    }

    public int getColorOr(int defaultColor) {
        return this.customColor.isPresent() ? (Integer) this.customColor.get() : getColorOptional(this.getAllEffects()).orElse(defaultColor);
    }

    public Component getName(String prefix) {
        String s1 = (String) this.customName.or(() -> {
            return this.potion.map((holder) -> {
                return ((Potion) holder.value()).name();
            });
        }).orElse("empty");

        return Component.translatable(prefix + s1);
    }

    public static OptionalInt getColorOptional(Iterable<MobEffectInstance> effects) {
        int i = 0;
        int j = 0;
        int k = 0;
        int l = 0;

        for (MobEffectInstance mobeffectinstance : effects) {
            if (mobeffectinstance.isVisible()) {
                int i1 = ((MobEffect) mobeffectinstance.getEffect().value()).getColor();
                int j1 = mobeffectinstance.getAmplifier() + 1;

                i += j1 * ARGB.red(i1);
                j += j1 * ARGB.green(i1);
                k += j1 * ARGB.blue(i1);
                l += j1;
            }
        }

        if (l == 0) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(ARGB.color(i / l, j / l, k / l));
        }
    }

    public boolean hasEffects() {
        return !this.customEffects.isEmpty() ? true : this.potion.isPresent() && !((Potion) ((Holder) this.potion.get()).value()).getEffects().isEmpty();
    }

    public List<MobEffectInstance> customEffects() {
        return Lists.transform(this.customEffects, MobEffectInstance::new);
    }

    public void applyToLivingEntity(LivingEntity entity, float durationScale) {
        Level level = entity.level();

        if (level instanceof ServerLevel serverlevel) {
            Player player;

            if (entity instanceof Player player1) {
                player = player1;
            } else {
                player = null;
            }

            Player player2 = player;

            this.forEachEffect((mobeffectinstance) -> {
                if (((MobEffect) mobeffectinstance.getEffect().value()).isInstantenous()) {
                    ((MobEffect) mobeffectinstance.getEffect().value()).applyInstantenousEffect(serverlevel, player2, player2, entity, mobeffectinstance.getAmplifier(), 1.0D);
                } else {
                    entity.addEffect(mobeffectinstance);
                }

            }, durationScale);
        }
    }

    public static void addPotionTooltip(Iterable<MobEffectInstance> effects, Consumer<Component> lines, float durationScale, float tickrate) {
        List<Pair<Holder<Attribute>, AttributeModifier>> list = Lists.newArrayList();
        boolean flag = true;

        for (MobEffectInstance mobeffectinstance : effects) {
            flag = false;
            Holder<MobEffect> holder = mobeffectinstance.getEffect();
            int i = mobeffectinstance.getAmplifier();

            (holder.value()).createModifiers(i, (holder1, attributemodifier) -> {
                list.add(new Pair(holder1, attributemodifier));
            });
            MutableComponent mutablecomponent = getPotionDescription(holder, i);

            if (!mobeffectinstance.endsWithin(20)) {
                mutablecomponent = Component.translatable("potion.withDuration", mutablecomponent, MobEffectUtil.formatDuration(mobeffectinstance, durationScale, tickrate));
            }

            lines.accept(mutablecomponent.withStyle(((MobEffect) holder.value()).getCategory().getTooltipFormatting()));
        }

        if (flag) {
            lines.accept(PotionContents.NO_EFFECT);
        }

        if (!list.isEmpty()) {
            lines.accept(CommonComponents.EMPTY);
            lines.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

            for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
                AttributeModifier attributemodifier = (AttributeModifier) pair.getSecond();
                double d0 = attributemodifier.amount();
                double d1;

                if (attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && attributemodifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    d1 = attributemodifier.amount();
                } else {
                    d1 = attributemodifier.amount() * 100.0D;
                }

                if (d0 > 0.0D) {
                    lines.accept(Component.translatable("attribute.modifier.plus." + attributemodifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) ((Holder) pair.getFirst()).value()).getDescriptionId())).withStyle(ChatFormatting.BLUE));
                } else if (d0 < 0.0D) {
                    d1 *= -1.0D;
                    lines.accept(Component.translatable("attribute.modifier.take." + attributemodifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) ((Holder) pair.getFirst()).value()).getDescriptionId())).withStyle(ChatFormatting.RED));
                }
            }
        }

    }

    public static MutableComponent getPotionDescription(Holder<MobEffect> mobEffect, int amplifier) {
        MutableComponent mutablecomponent = Component.translatable(((MobEffect) mobEffect.value()).getDescriptionId());

        return amplifier > 0 ? Component.translatable("potion.withAmplifier", mutablecomponent, Component.translatable("potion.potency." + amplifier)) : mutablecomponent;
    }

    @Override
    public void onConsume(Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
        this.applyToLivingEntity(user, (Float) stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F));
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        addPotionTooltip(this.getAllEffects(), consumer, (Float) components.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F), context.tickRate());
    }
}
