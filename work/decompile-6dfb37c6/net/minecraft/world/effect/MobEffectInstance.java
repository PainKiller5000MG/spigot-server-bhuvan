package net.minecraft.world.effect;

import com.google.common.collect.ComparisonChain;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MobEffectInstance implements Comparable<MobEffectInstance> {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final Codec<MobEffectInstance> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(MobEffect.CODEC.fieldOf("id").forGetter(MobEffectInstance::getEffect), MobEffectInstance.Details.MAP_CODEC.forGetter(MobEffectInstance::asDetails)).apply(instance, MobEffectInstance::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance> STREAM_CODEC = StreamCodec.composite(MobEffect.STREAM_CODEC, MobEffectInstance::getEffect, MobEffectInstance.Details.STREAM_CODEC, MobEffectInstance::asDetails, MobEffectInstance::new);
    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    private @Nullable MobEffectInstance hiddenEffect;
    private final MobEffectInstance.BlendState blendState;

    public MobEffectInstance(Holder<MobEffect> effect) {
        this(effect, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, (MobEffectInstance) null);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, @Nullable MobEffectInstance hiddenEffect) {
        this.blendState = new MobEffectInstance.BlendState();
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Mth.clamp(amplifier, 0, 255);
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
    }

    public MobEffectInstance(MobEffectInstance copy) {
        this.blendState = new MobEffectInstance.BlendState();
        this.effect = copy.effect;
        this.setDetailsFrom(copy);
    }

    private MobEffectInstance(Holder<MobEffect> effect, MobEffectInstance.Details details) {
        this(effect, details.duration(), details.amplifier(), details.ambient(), details.showParticles(), details.showIcon(), (MobEffectInstance) details.hiddenEffect().map((mobeffectinstance_details1) -> {
            return new MobEffectInstance(effect, mobeffectinstance_details1);
        }).orElse((Object) null));
    }

    private MobEffectInstance.Details asDetails() {
        return new MobEffectInstance.Details(this.getAmplifier(), this.getDuration(), this.isAmbient(), this.isVisible(), this.showIcon(), Optional.ofNullable(this.hiddenEffect).map(MobEffectInstance::asDetails));
    }

    public float getBlendFactor(LivingEntity livingEntity, float partialTickTime) {
        return this.blendState.getFactor(livingEntity, partialTickTime);
    }

    public ParticleOptions getParticleOptions() {
        return ((MobEffect) this.effect.value()).createParticleOptions(this);
    }

    void setDetailsFrom(MobEffectInstance copy) {
        this.duration = copy.duration;
        this.amplifier = copy.amplifier;
        this.ambient = copy.ambient;
        this.visible = copy.visible;
        this.showIcon = copy.showIcon;
    }

    public boolean update(MobEffectInstance takeOver) {
        if (!this.effect.equals(takeOver.effect)) {
            MobEffectInstance.LOGGER.warn("This method should only be called for matching effects!");
        }

        boolean flag = false;

        if (takeOver.amplifier > this.amplifier) {
            if (takeOver.isShorterDurationThan(this)) {
                MobEffectInstance mobeffectinstance1 = this.hiddenEffect;

                this.hiddenEffect = new MobEffectInstance(this);
                this.hiddenEffect.hiddenEffect = mobeffectinstance1;
            }

            this.amplifier = takeOver.amplifier;
            this.duration = takeOver.duration;
            flag = true;
        } else if (this.isShorterDurationThan(takeOver)) {
            if (takeOver.amplifier == this.amplifier) {
                this.duration = takeOver.duration;
                flag = true;
            } else if (this.hiddenEffect == null) {
                this.hiddenEffect = new MobEffectInstance(takeOver);
            } else {
                this.hiddenEffect.update(takeOver);
            }
        }

        if (!takeOver.ambient && this.ambient || flag) {
            this.ambient = takeOver.ambient;
            flag = true;
        }

        if (takeOver.visible != this.visible) {
            this.visible = takeOver.visible;
            flag = true;
        }

        if (takeOver.showIcon != this.showIcon) {
            this.showIcon = takeOver.showIcon;
            flag = true;
        }

        return flag;
    }

    private boolean isShorterDurationThan(MobEffectInstance other) {
        return !this.isInfiniteDuration() && (this.duration < other.duration || other.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() {
        return this.duration == -1;
    }

    public boolean endsWithin(int ticks) {
        return !this.isInfiniteDuration() && this.duration <= ticks;
    }

    public MobEffectInstance withScaledDuration(float scale) {
        MobEffectInstance mobeffectinstance = new MobEffectInstance(this);

        mobeffectinstance.duration = mobeffectinstance.mapDuration((i) -> {
            return Math.max(Mth.floor((float) i * scale), 1);
        });
        return mobeffectinstance;
    }

    public int mapDuration(Int2IntFunction mapper) {
        return !this.isInfiniteDuration() && this.duration != 0 ? mapper.applyAsInt(this.duration) : this.duration;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean showIcon() {
        return this.showIcon;
    }

    public boolean tickServer(ServerLevel serverLevel, LivingEntity target, Runnable onEffectUpdate) {
        if (!this.hasRemainingDuration()) {
            return false;
        } else {
            int i = this.isInfiniteDuration() ? target.tickCount : this.duration;

            if (((MobEffect) this.effect.value()).shouldApplyEffectTickThisTick(i, this.amplifier) && !((MobEffect) this.effect.value()).applyEffectTick(serverLevel, target, this.amplifier)) {
                return false;
            } else {
                this.tickDownDuration();
                if (this.downgradeToHiddenEffect()) {
                    onEffectUpdate.run();
                }

                return this.hasRemainingDuration();
            }
        }
    }

    public void tickClient() {
        if (this.hasRemainingDuration()) {
            this.tickDownDuration();
            this.downgradeToHiddenEffect();
        }

        this.blendState.tick(this);
    }

    private boolean hasRemainingDuration() {
        return this.isInfiniteDuration() || this.duration > 0;
    }

    private void tickDownDuration() {
        if (this.hiddenEffect != null) {
            this.hiddenEffect.tickDownDuration();
        }

        this.duration = this.mapDuration((i) -> {
            return i - 1;
        });
    }

    private boolean downgradeToHiddenEffect() {
        if (this.duration == 0 && this.hiddenEffect != null) {
            this.setDetailsFrom(this.hiddenEffect);
            this.hiddenEffect = this.hiddenEffect.hiddenEffect;
            return true;
        } else {
            return false;
        }
    }

    public void onEffectStarted(LivingEntity mob) {
        ((MobEffect) this.effect.value()).onEffectStarted(mob, this.amplifier);
    }

    public void onMobRemoved(ServerLevel level, LivingEntity mob, Entity.RemovalReason reason) {
        ((MobEffect) this.effect.value()).onMobRemoved(level, mob, this.amplifier, reason);
    }

    public void onMobHurt(ServerLevel level, LivingEntity mob, DamageSource source, float damage) {
        ((MobEffect) this.effect.value()).onMobHurt(level, mob, this.amplifier, source, damage);
    }

    public String getDescriptionId() {
        return ((MobEffect) this.effect.value()).getDescriptionId();
    }

    public String toString() {
        String s;

        if (this.amplifier > 0) {
            String s1 = this.getDescriptionId();

            s = s1 + " x " + (this.amplifier + 1) + ", Duration: " + this.describeDuration();
        } else {
            String s2 = this.getDescriptionId();

            s = s2 + ", Duration: " + this.describeDuration();
        }

        if (!this.visible) {
            s = s + ", Particles: false";
        }

        if (!this.showIcon) {
            s = s + ", Show Icon: false";
        }

        return s;
    }

    private String describeDuration() {
        return this.isInfiniteDuration() ? "infinite" : Integer.toString(this.duration);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof MobEffectInstance)) {
            return false;
        } else {
            MobEffectInstance mobeffectinstance = (MobEffectInstance) o;

            return this.duration == mobeffectinstance.duration && this.amplifier == mobeffectinstance.amplifier && this.ambient == mobeffectinstance.ambient && this.visible == mobeffectinstance.visible && this.showIcon == mobeffectinstance.showIcon && this.effect.equals(mobeffectinstance.effect);
        }
    }

    public int hashCode() {
        int i = this.effect.hashCode();

        i = 31 * i + this.duration;
        i = 31 * i + this.amplifier;
        i = 31 * i + (this.ambient ? 1 : 0);
        i = 31 * i + (this.visible ? 1 : 0);
        i = 31 * i + (this.showIcon ? 1 : 0);
        return i;
    }

    public int compareTo(MobEffectInstance o) {
        int i = 32147;

        return (this.getDuration() <= 32147 || o.getDuration() <= 32147) && (!this.isAmbient() || !o.isAmbient()) ? ComparisonChain.start().compareFalseFirst(this.isAmbient(), o.isAmbient()).compareFalseFirst(this.isInfiniteDuration(), o.isInfiniteDuration()).compare(this.getDuration(), o.getDuration()).compare(((MobEffect) this.getEffect().value()).getColor(), ((MobEffect) o.getEffect().value()).getColor()).result() : ComparisonChain.start().compare(this.isAmbient(), o.isAmbient()).compare(((MobEffect) this.getEffect().value()).getColor(), ((MobEffect) o.getEffect().value()).getColor()).result();
    }

    public void onEffectAdded(LivingEntity livingEntity) {
        ((MobEffect) this.effect.value()).onEffectAdded(livingEntity, this.amplifier);
    }

    public boolean is(Holder<MobEffect> effect) {
        return this.effect.equals(effect);
    }

    public void copyBlendState(MobEffectInstance instance) {
        this.blendState.copyFrom(instance.blendState);
    }

    public void skipBlending() {
        this.blendState.setImmediate(this);
    }

    private static record Details(int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect) {

        public static final MapCodec<MobEffectInstance.Details> MAP_CODEC = MapCodec.recursive("MobEffectInstance.Details", (codec) -> {
            return RecordCodecBuilder.mapCodec((instance) -> {
                return instance.group(ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance.Details::amplifier), Codec.INT.optionalFieldOf("duration", 0).forGetter(MobEffectInstance.Details::duration), Codec.BOOL.optionalFieldOf("ambient", false).forGetter(MobEffectInstance.Details::ambient), Codec.BOOL.optionalFieldOf("show_particles", true).forGetter(MobEffectInstance.Details::showParticles), Codec.BOOL.optionalFieldOf("show_icon").forGetter((mobeffectinstance_details) -> {
                    return Optional.of(mobeffectinstance_details.showIcon());
                }), codec.optionalFieldOf("hidden_effect").forGetter(MobEffectInstance.Details::hiddenEffect)).apply(instance, MobEffectInstance.Details::create);
            });
        });
        public static final StreamCodec<ByteBuf, MobEffectInstance.Details> STREAM_CODEC = StreamCodec.<ByteBuf, MobEffectInstance.Details>recursive((streamcodec) -> {
            return StreamCodec.composite(ByteBufCodecs.VAR_INT, MobEffectInstance.Details::amplifier, ByteBufCodecs.VAR_INT, MobEffectInstance.Details::duration, ByteBufCodecs.BOOL, MobEffectInstance.Details::ambient, ByteBufCodecs.BOOL, MobEffectInstance.Details::showParticles, ByteBufCodecs.BOOL, MobEffectInstance.Details::showIcon, streamcodec.apply(ByteBufCodecs::optional), MobEffectInstance.Details::hiddenEffect, MobEffectInstance.Details::new);
        });

        private static MobEffectInstance.Details create(int amplifier, int duration, boolean ambient, boolean showParticles, Optional<Boolean> showIcon, Optional<MobEffectInstance.Details> hiddenEffect) {
            return new MobEffectInstance.Details(amplifier, duration, ambient, showParticles, (Boolean) showIcon.orElse(showParticles), hiddenEffect);
        }
    }

    private static class BlendState {

        private float factor;
        private float factorPreviousFrame;

        private BlendState() {}

        public void setImmediate(MobEffectInstance instance) {
            this.factor = hasEffect(instance) ? 1.0F : 0.0F;
            this.factorPreviousFrame = this.factor;
        }

        public void copyFrom(MobEffectInstance.BlendState other) {
            this.factor = other.factor;
            this.factorPreviousFrame = other.factorPreviousFrame;
        }

        public void tick(MobEffectInstance instance) {
            this.factorPreviousFrame = this.factor;
            boolean flag = hasEffect(instance);
            float f = flag ? 1.0F : 0.0F;

            if (this.factor != f) {
                MobEffect mobeffect = (MobEffect) instance.getEffect().value();
                int i = flag ? mobeffect.getBlendInDurationTicks() : mobeffect.getBlendOutDurationTicks();

                if (i == 0) {
                    this.factor = f;
                } else {
                    float f1 = 1.0F / (float) i;

                    this.factor += Mth.clamp(f - this.factor, -f1, f1);
                }

            }
        }

        private static boolean hasEffect(MobEffectInstance instance) {
            return !instance.endsWithin(((MobEffect) instance.getEffect().value()).getBlendOutAdvanceTicks());
        }

        public float getFactor(LivingEntity livingEntity, float partialTickTime) {
            if (livingEntity.isRemoved()) {
                this.factorPreviousFrame = this.factor;
            }

            return Mth.lerp(partialTickTime, this.factorPreviousFrame, this.factor);
        }
    }
}
