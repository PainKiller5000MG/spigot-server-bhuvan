package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetStewEffectFunction extends LootItemConditionalFunction {

    private static final Codec<List<SetStewEffectFunction.EffectEntry>> EFFECTS_LIST = SetStewEffectFunction.EffectEntry.CODEC.listOf().validate((list) -> {
        Set<Holder<MobEffect>> set = new ObjectOpenHashSet();

        for (SetStewEffectFunction.EffectEntry setsteweffectfunction_effectentry : list) {
            if (!set.add(setsteweffectfunction_effectentry.effect())) {
                return DataResult.error(() -> {
                    return "Encountered duplicate mob effect: '" + String.valueOf(setsteweffectfunction_effectentry.effect()) + "'";
                });
            }
        }

        return DataResult.success(list);
    });
    public static final MapCodec<SetStewEffectFunction> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return commonFields(instance).and(SetStewEffectFunction.EFFECTS_LIST.optionalFieldOf("effects", List.of()).forGetter((setsteweffectfunction) -> {
            return setsteweffectfunction.effects;
        })).apply(instance, SetStewEffectFunction::new);
    });
    private final List<SetStewEffectFunction.EffectEntry> effects;

    private SetStewEffectFunction(List<LootItemCondition> predicates, List<SetStewEffectFunction.EffectEntry> effects) {
        super(predicates);
        this.effects = effects;
    }

    @Override
    public LootItemFunctionType<SetStewEffectFunction> getType() {
        return LootItemFunctions.SET_STEW_EFFECT;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return (Set) this.effects.stream().flatMap((setsteweffectfunction_effectentry) -> {
            return setsteweffectfunction_effectentry.duration().getReferencedContextParams().stream();
        }).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext context) {
        if (itemStack.is(Items.SUSPICIOUS_STEW) && !this.effects.isEmpty()) {
            SetStewEffectFunction.EffectEntry setsteweffectfunction_effectentry = (SetStewEffectFunction.EffectEntry) Util.getRandom(this.effects, context.getRandom());
            Holder<MobEffect> holder = setsteweffectfunction_effectentry.effect();
            int i = setsteweffectfunction_effectentry.duration().getInt(context);

            if (!((MobEffect) holder.value()).isInstantenous()) {
                i *= 20;
            }

            SuspiciousStewEffects.Entry suspicioussteweffects_entry = new SuspiciousStewEffects.Entry(holder, i);

            itemStack.update(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY, suspicioussteweffects_entry, SuspiciousStewEffects::withEffectAdded);
            return itemStack;
        } else {
            return itemStack;
        }
    }

    public static SetStewEffectFunction.Builder stewEffect() {
        return new SetStewEffectFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetStewEffectFunction.Builder> {

        private final ImmutableList.Builder<SetStewEffectFunction.EffectEntry> effects = ImmutableList.builder();

        public Builder() {}

        @Override
        protected SetStewEffectFunction.Builder getThis() {
            return this;
        }

        public SetStewEffectFunction.Builder withEffect(Holder<MobEffect> effect, NumberProvider duration) {
            this.effects.add(new SetStewEffectFunction.EffectEntry(effect, duration));
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetStewEffectFunction(this.getConditions(), this.effects.build());
        }
    }

    private static record EffectEntry(Holder<MobEffect> effect, NumberProvider duration) {

        public static final Codec<SetStewEffectFunction.EffectEntry> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(MobEffect.CODEC.fieldOf("type").forGetter(SetStewEffectFunction.EffectEntry::effect), NumberProviders.CODEC.fieldOf("duration").forGetter(SetStewEffectFunction.EffectEntry::duration)).apply(instance, SetStewEffectFunction.EffectEntry::new);
        });
    }
}
