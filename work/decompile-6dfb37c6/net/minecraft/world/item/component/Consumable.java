package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public record Consumable(float consumeSeconds, ItemUseAnimation animation, Holder<SoundEvent> sound, boolean hasConsumeParticles, List<ConsumeEffect> onConsumeEffects) {

    public static final float DEFAULT_CONSUME_SECONDS = 1.6F;
    private static final int CONSUME_EFFECTS_INTERVAL = 4;
    private static final float CONSUME_EFFECTS_START_FRACTION = 0.21875F;
    public static final Codec<Consumable> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("consume_seconds", 1.6F).forGetter(Consumable::consumeSeconds), ItemUseAnimation.CODEC.optionalFieldOf("animation", ItemUseAnimation.EAT).forGetter(Consumable::animation), SoundEvent.CODEC.optionalFieldOf("sound", SoundEvents.GENERIC_EAT).forGetter(Consumable::sound), Codec.BOOL.optionalFieldOf("has_consume_particles", true).forGetter(Consumable::hasConsumeParticles), ConsumeEffect.CODEC.listOf().optionalFieldOf("on_consume_effects", List.of()).forGetter(Consumable::onConsumeEffects)).apply(instance, Consumable::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, Consumable> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Consumable::consumeSeconds, ItemUseAnimation.STREAM_CODEC, Consumable::animation, SoundEvent.STREAM_CODEC, Consumable::sound, ByteBufCodecs.BOOL, Consumable::hasConsumeParticles, ConsumeEffect.STREAM_CODEC.apply(ByteBufCodecs.list()), Consumable::onConsumeEffects, Consumable::new);

    public InteractionResult startConsuming(LivingEntity user, ItemStack stack, InteractionHand hand) {
        if (!this.canConsume(user, stack)) {
            return InteractionResult.FAIL;
        } else {
            boolean flag = this.consumeTicks() > 0;

            if (flag) {
                user.startUsingItem(hand);
                return InteractionResult.CONSUME;
            } else {
                ItemStack itemstack1 = this.onConsume(user.level(), user, stack);

                return InteractionResult.CONSUME.heldItemTransformedTo(itemstack1);
            }
        }
    }

    public ItemStack onConsume(Level level, LivingEntity user, ItemStack stack) {
        RandomSource randomsource = user.getRandom();

        this.emitParticlesAndSounds(randomsource, user, stack, 16);
        if (user instanceof ServerPlayer serverplayer) {
            serverplayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            CriteriaTriggers.CONSUME_ITEM.trigger(serverplayer, stack);
        }

        stack.getAllOfType(ConsumableListener.class).forEach((consumablelistener) -> {
            consumablelistener.onConsume(level, user, stack, this);
        });
        if (!level.isClientSide()) {
            this.onConsumeEffects.forEach((consumeeffect) -> {
                consumeeffect.apply(level, stack, user);
            });
        }

        user.gameEvent(this.animation == ItemUseAnimation.DRINK ? GameEvent.DRINK : GameEvent.EAT);
        stack.consume(1, user);
        return stack;
    }

    public boolean canConsume(LivingEntity user, ItemStack stack) {
        FoodProperties foodproperties = (FoodProperties) stack.get(DataComponents.FOOD);

        if (foodproperties != null && user instanceof Player player) {
            return player.canEat(foodproperties.canAlwaysEat());
        } else {
            return true;
        }
    }

    public int consumeTicks() {
        return (int) (this.consumeSeconds * 20.0F);
    }

    public void emitParticlesAndSounds(RandomSource random, LivingEntity user, ItemStack itemStack, int particleCount) {
        float f = random.nextBoolean() ? 0.5F : 1.0F;
        float f1 = random.triangle(1.0F, 0.2F);
        float f2 = 0.5F;
        float f3 = Mth.randomBetween(random, 0.9F, 1.0F);
        float f4 = this.animation == ItemUseAnimation.DRINK ? 0.5F : f;
        float f5 = this.animation == ItemUseAnimation.DRINK ? f3 : f1;

        if (this.hasConsumeParticles) {
            user.spawnItemParticles(itemStack, particleCount);
        }

        SoundEvent soundevent;

        if (user instanceof Consumable.OverrideConsumeSound consumable_overrideconsumesound) {
            soundevent = consumable_overrideconsumesound.getConsumeSound(itemStack);
        } else {
            soundevent = this.sound.value();
        }

        SoundEvent soundevent1 = soundevent;

        user.playSound(soundevent1, f4, f5);
    }

    public boolean shouldEmitParticlesAndSounds(int useItemRemainingTicks) {
        int j = this.consumeTicks() - useItemRemainingTicks;
        int k = (int) ((float) this.consumeTicks() * 0.21875F);
        boolean flag = j > k;

        return flag && useItemRemainingTicks % 4 == 0;
    }

    public static Consumable.Builder builder() {
        return new Consumable.Builder();
    }

    public static class Builder {

        private float consumeSeconds = 1.6F;
        private ItemUseAnimation animation;
        private Holder<SoundEvent> sound;
        private boolean hasConsumeParticles;
        private final List<ConsumeEffect> onConsumeEffects;

        private Builder() {
            this.animation = ItemUseAnimation.EAT;
            this.sound = SoundEvents.GENERIC_EAT;
            this.hasConsumeParticles = true;
            this.onConsumeEffects = new ArrayList();
        }

        public Consumable.Builder consumeSeconds(float consumeSeconds) {
            this.consumeSeconds = consumeSeconds;
            return this;
        }

        public Consumable.Builder animation(ItemUseAnimation animation) {
            this.animation = animation;
            return this;
        }

        public Consumable.Builder sound(Holder<SoundEvent> sound) {
            this.sound = sound;
            return this;
        }

        public Consumable.Builder soundAfterConsume(Holder<SoundEvent> soundAfterConsume) {
            return this.onConsume(new PlaySoundConsumeEffect(soundAfterConsume));
        }

        public Consumable.Builder hasConsumeParticles(boolean hasConsumeParticles) {
            this.hasConsumeParticles = hasConsumeParticles;
            return this;
        }

        public Consumable.Builder onConsume(ConsumeEffect effect) {
            this.onConsumeEffects.add(effect);
            return this;
        }

        public Consumable build() {
            return new Consumable(this.consumeSeconds, this.animation, this.sound, this.hasConsumeParticles, this.onConsumeEffects);
        }
    }

    public interface OverrideConsumeSound {

        SoundEvent getConsumeSound(ItemStack itemStack);
    }
}
