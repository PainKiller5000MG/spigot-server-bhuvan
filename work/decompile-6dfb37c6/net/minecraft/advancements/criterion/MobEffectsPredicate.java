package net.minecraft.advancements.criterion;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public record MobEffectsPredicate(Map<Holder<MobEffect>, MobEffectsPredicate.MobEffectInstancePredicate> effectMap) {

    public static final Codec<MobEffectsPredicate> CODEC = Codec.unboundedMap(MobEffect.CODEC, MobEffectsPredicate.MobEffectInstancePredicate.CODEC).xmap(MobEffectsPredicate::new, MobEffectsPredicate::effectMap);

    public boolean matches(Entity entity) {
        boolean flag;

        if (entity instanceof LivingEntity livingentity) {
            if (this.matches(livingentity.getActiveEffectsMap())) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    public boolean matches(LivingEntity entity) {
        return this.matches(entity.getActiveEffectsMap());
    }

    public boolean matches(Map<Holder<MobEffect>, MobEffectInstance> effects) {
        for (Map.Entry<Holder<MobEffect>, MobEffectsPredicate.MobEffectInstancePredicate> map_entry : this.effectMap.entrySet()) {
            MobEffectInstance mobeffectinstance = (MobEffectInstance) effects.get(map_entry.getKey());

            if (!((MobEffectsPredicate.MobEffectInstancePredicate) map_entry.getValue()).matches(mobeffectinstance)) {
                return false;
            }
        }

        return true;
    }

    public static class Builder {

        private final ImmutableMap.Builder<Holder<MobEffect>, MobEffectsPredicate.MobEffectInstancePredicate> effectMap = ImmutableMap.builder();

        public Builder() {}

        public static MobEffectsPredicate.Builder effects() {
            return new MobEffectsPredicate.Builder();
        }

        public MobEffectsPredicate.Builder and(Holder<MobEffect> effect) {
            this.effectMap.put(effect, new MobEffectsPredicate.MobEffectInstancePredicate());
            return this;
        }

        public MobEffectsPredicate.Builder and(Holder<MobEffect> effect, MobEffectsPredicate.MobEffectInstancePredicate predicate) {
            this.effectMap.put(effect, predicate);
            return this;
        }

        public Optional<MobEffectsPredicate> build() {
            return Optional.of(new MobEffectsPredicate(this.effectMap.build()));
        }
    }

    public static record MobEffectInstancePredicate(MinMaxBounds.Ints amplifier, MinMaxBounds.Ints duration, Optional<Boolean> ambient, Optional<Boolean> visible) {

        public static final Codec<MobEffectsPredicate.MobEffectInstancePredicate> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(MinMaxBounds.Ints.CODEC.optionalFieldOf("amplifier", MinMaxBounds.Ints.ANY).forGetter(MobEffectsPredicate.MobEffectInstancePredicate::amplifier), MinMaxBounds.Ints.CODEC.optionalFieldOf("duration", MinMaxBounds.Ints.ANY).forGetter(MobEffectsPredicate.MobEffectInstancePredicate::duration), Codec.BOOL.optionalFieldOf("ambient").forGetter(MobEffectsPredicate.MobEffectInstancePredicate::ambient), Codec.BOOL.optionalFieldOf("visible").forGetter(MobEffectsPredicate.MobEffectInstancePredicate::visible)).apply(instance, MobEffectsPredicate.MobEffectInstancePredicate::new);
        });

        public MobEffectInstancePredicate() {
            this(MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.ANY, Optional.empty(), Optional.empty());
        }

        public boolean matches(@Nullable MobEffectInstance instance) {
            return instance == null ? false : (!this.amplifier.matches(instance.getAmplifier()) ? false : (!this.duration.matches(instance.getDuration()) ? false : (this.ambient.isPresent() && (Boolean) this.ambient.get() != instance.isAmbient() ? false : !this.visible.isPresent() || (Boolean) this.visible.get() == instance.isVisible())));
        }
    }
}
