package net.minecraft.world.effect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.jspecify.annotations.Nullable;

public class MobEffect implements FeatureElement {

    public static final Codec<Holder<MobEffect>> CODEC = BuiltInRegistries.MOB_EFFECT.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<MobEffect>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT);
    private static final int AMBIENT_ALPHA = Mth.floor(38.25F);
    private final Map<Holder<Attribute>, MobEffect.AttributeTemplate> attributeModifiers = new Object2ObjectOpenHashMap();
    private final MobEffectCategory category;
    private final int color;
    private final Function<MobEffectInstance, ParticleOptions> particleFactory;
    private @Nullable String descriptionId;
    private int blendInDurationTicks;
    private int blendOutDurationTicks;
    private int blendOutAdvanceTicks;
    private Optional<SoundEvent> soundOnAdded = Optional.empty();
    private FeatureFlagSet requiredFeatures;

    protected MobEffect(MobEffectCategory category, int color) {
        this.requiredFeatures = FeatureFlags.VANILLA_SET;
        this.category = category;
        this.color = color;
        this.particleFactory = (mobeffectinstance) -> {
            int j = mobeffectinstance.isAmbient() ? MobEffect.AMBIENT_ALPHA : 255;

            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.color(j, color));
        };
    }

    protected MobEffect(MobEffectCategory category, int color, ParticleOptions particleOptions) {
        this.requiredFeatures = FeatureFlags.VANILLA_SET;
        this.category = category;
        this.color = color;
        this.particleFactory = (mobeffectinstance) -> {
            return particleOptions;
        };
    }

    public int getBlendInDurationTicks() {
        return this.blendInDurationTicks;
    }

    public int getBlendOutDurationTicks() {
        return this.blendOutDurationTicks;
    }

    public int getBlendOutAdvanceTicks() {
        return this.blendOutAdvanceTicks;
    }

    public boolean applyEffectTick(ServerLevel serverLevel, LivingEntity mob, int amplification) {
        return true;
    }

    public void applyInstantenousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob, int amplification, double scale) {
        this.applyEffectTick(level, mob, amplification);
    }

    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return false;
    }

    public void onEffectStarted(LivingEntity mob, int amplifier) {}

    public void onEffectAdded(LivingEntity mob, int amplifier) {
        this.soundOnAdded.ifPresent((soundevent) -> {
            mob.level().playSound((Entity) null, mob.getX(), mob.getY(), mob.getZ(), soundevent, mob.getSoundSource(), 1.0F, 1.0F);
        });
    }

    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {}

    public void onMobHurt(ServerLevel level, LivingEntity mob, int amplifier, DamageSource source, float damage) {}

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return Component.translatable(this.getDescriptionId());
    }

    public MobEffectCategory getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, amount, operation));
        return this;
    }

    public MobEffect setBlendDuration(int ticks) {
        return this.setBlendDuration(ticks, ticks, ticks);
    }

    public MobEffect setBlendDuration(int inTicks, int outTicks, int outAdvanceTicks) {
        this.blendInDurationTicks = inTicks;
        this.blendOutDurationTicks = outTicks;
        this.blendOutAdvanceTicks = outAdvanceTicks;
        return this;
    }

    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        this.attributeModifiers.forEach((holder, mobeffect_attributetemplate) -> {
            consumer.accept(holder, mobeffect_attributetemplate.create(amplifier));
        });
    }

    public void removeAttributeModifiers(AttributeMap attributes) {
        for (Map.Entry<Holder<Attribute>, MobEffect.AttributeTemplate> map_entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = attributes.getInstance((Holder) map_entry.getKey());

            if (attributeinstance != null) {
                attributeinstance.removeModifier(((MobEffect.AttributeTemplate) map_entry.getValue()).id());
            }
        }

    }

    public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
        for (Map.Entry<Holder<Attribute>, MobEffect.AttributeTemplate> map_entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = attributes.getInstance((Holder) map_entry.getKey());

            if (attributeinstance != null) {
                attributeinstance.removeModifier(((MobEffect.AttributeTemplate) map_entry.getValue()).id());
                attributeinstance.addPermanentModifier(((MobEffect.AttributeTemplate) map_entry.getValue()).create(amplifier));
            }
        }

    }

    public boolean isBeneficial() {
        return this.category == MobEffectCategory.BENEFICIAL;
    }

    public ParticleOptions createParticleOptions(MobEffectInstance mobEffectInstance) {
        return (ParticleOptions) this.particleFactory.apply(mobEffectInstance);
    }

    public MobEffect withSoundOnAdded(SoundEvent soundEvent) {
        this.soundOnAdded = Optional.of(soundEvent);
        return this;
    }

    public MobEffect requiredFeatures(FeatureFlag... flags) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(flags);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    private static record AttributeTemplate(Identifier id, double amount, AttributeModifier.Operation operation) {

        public AttributeModifier create(int amplifier) {
            return new AttributeModifier(this.id, this.amount * (double) (amplifier + 1), this.operation);
        }
    }
}
