package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class AreaEffectCloud extends Entity implements TraceableEntity {

    private static final int TIME_BETWEEN_APPLICATIONS = 5;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.<Float>defineId(AreaEffectCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.<Boolean>defineId(AreaEffectCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ParticleOptions> DATA_PARTICLE = SynchedEntityData.<ParticleOptions>defineId(AreaEffectCloud.class, EntityDataSerializers.PARTICLE);
    private static final float MAX_RADIUS = 32.0F;
    private static final int DEFAULT_AGE = 0;
    private static final int DEFAULT_DURATION_ON_USE = 0;
    private static final float DEFAULT_RADIUS_ON_USE = 0.0F;
    private static final float DEFAULT_RADIUS_PER_TICK = 0.0F;
    private static final float DEFAULT_POTION_DURATION_SCALE = 1.0F;
    private static final float MINIMAL_RADIUS = 0.5F;
    private static final float DEFAULT_RADIUS = 3.0F;
    public static final float DEFAULT_WIDTH = 6.0F;
    public static final float HEIGHT = 0.5F;
    public static final int INFINITE_DURATION = -1;
    public static final int DEFAULT_LINGERING_DURATION = 600;
    private static final int DEFAULT_WAIT_TIME = 20;
    private static final int DEFAULT_REAPPLICATION_DELAY = 20;
    private static final ColorParticleOption DEFAULT_PARTICLE = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1);
    private @Nullable ParticleOptions customParticle;
    public PotionContents potionContents;
    private float potionDurationScale;
    private final Map<Entity, Integer> victims;
    private int duration;
    public int waitTime;
    public int reapplicationDelay;
    public int durationOnUse;
    public float radiusOnUse;
    public float radiusPerTick;
    private @Nullable EntityReference<LivingEntity> owner;

    public AreaEffectCloud(EntityType<? extends AreaEffectCloud> type, Level level) {
        super(type, level);
        this.potionContents = PotionContents.EMPTY;
        this.potionDurationScale = 1.0F;
        this.victims = Maps.newHashMap();
        this.duration = -1;
        this.waitTime = 20;
        this.reapplicationDelay = 20;
        this.durationOnUse = 0;
        this.radiusOnUse = 0.0F;
        this.radiusPerTick = 0.0F;
        this.noPhysics = true;
    }

    public AreaEffectCloud(Level level, double x, double y, double z) {
        this(EntityType.AREA_EFFECT_CLOUD, level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(AreaEffectCloud.DATA_RADIUS, 3.0F);
        entityData.define(AreaEffectCloud.DATA_WAITING, false);
        entityData.define(AreaEffectCloud.DATA_PARTICLE, AreaEffectCloud.DEFAULT_PARTICLE);
    }

    public void setRadius(float radius) {
        if (!this.level().isClientSide()) {
            this.getEntityData().set(AreaEffectCloud.DATA_RADIUS, Mth.clamp(radius, 0.0F, 32.0F));
        }

    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    public float getRadius() {
        return (Float) this.getEntityData().get(AreaEffectCloud.DATA_RADIUS);
    }

    public void setPotionContents(PotionContents contents) {
        this.potionContents = contents;
        this.updateParticle();
    }

    public void setCustomParticle(@Nullable ParticleOptions customParticle) {
        this.customParticle = customParticle;
        this.updateParticle();
    }

    public void setPotionDurationScale(float scale) {
        this.potionDurationScale = scale;
    }

    public void updateParticle() {
        if (this.customParticle != null) {
            this.entityData.set(AreaEffectCloud.DATA_PARTICLE, this.customParticle);
        } else {
            int i = ARGB.opaque(this.potionContents.getColor());

            this.entityData.set(AreaEffectCloud.DATA_PARTICLE, ColorParticleOption.create(AreaEffectCloud.DEFAULT_PARTICLE.getType(), i));
        }

    }

    public void addEffect(MobEffectInstance effect) {
        this.setPotionContents(this.potionContents.withEffectAdded(effect));
    }

    public ParticleOptions getParticle() {
        return (ParticleOptions) this.getEntityData().get(AreaEffectCloud.DATA_PARTICLE);
    }

    protected void setWaiting(boolean waiting) {
        this.getEntityData().set(AreaEffectCloud.DATA_WAITING, waiting);
    }

    public boolean isWaiting() {
        return (Boolean) this.getEntityData().get(AreaEffectCloud.DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.serverTick(serverlevel);
        } else {
            this.clientTick();
        }

    }

    private void clientTick() {
        boolean flag = this.isWaiting();
        float f = this.getRadius();

        if (!flag || !this.random.nextBoolean()) {
            ParticleOptions particleoptions = this.getParticle();
            int i;
            float f1;

            if (flag) {
                i = 2;
                f1 = 0.2F;
            } else {
                i = Mth.ceil((float) Math.PI * f * f);
                f1 = f;
            }

            for (int j = 0; j < i; ++j) {
                float f2 = this.random.nextFloat() * ((float) Math.PI * 2F);
                float f3 = Mth.sqrt(this.random.nextFloat()) * f1;
                double d0 = this.getX() + (double) (Mth.cos((double) f2) * f3);
                double d1 = this.getY();
                double d2 = this.getZ() + (double) (Mth.sin((double) f2) * f3);

                if (particleoptions.getType() == ParticleTypes.ENTITY_EFFECT) {
                    if (flag && this.random.nextBoolean()) {
                        this.level().addAlwaysVisibleParticle(AreaEffectCloud.DEFAULT_PARTICLE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                    } else {
                        this.level().addAlwaysVisibleParticle(particleoptions, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                    }
                } else if (flag) {
                    this.level().addAlwaysVisibleParticle(particleoptions, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                } else {
                    this.level().addAlwaysVisibleParticle(particleoptions, d0, d1, d2, (0.5D - this.random.nextDouble()) * 0.15D, (double) 0.01F, (0.5D - this.random.nextDouble()) * 0.15D);
                }
            }

        }
    }

    private void serverTick(ServerLevel serverLevel) {
        if (this.duration != -1 && this.tickCount - this.waitTime >= this.duration) {
            this.discard();
        } else {
            boolean flag = this.isWaiting();
            boolean flag1 = this.tickCount < this.waitTime;

            if (flag != flag1) {
                this.setWaiting(flag1);
            }

            if (!flag1) {
                float f = this.getRadius();

                if (this.radiusPerTick != 0.0F) {
                    f += this.radiusPerTick;
                    if (f < 0.5F) {
                        this.discard();
                        return;
                    }

                    this.setRadius(f);
                }

                if (this.tickCount % 5 == 0) {
                    this.victims.entrySet().removeIf((entry) -> {
                        return this.tickCount >= (Integer) entry.getValue();
                    });
                    if (!this.potionContents.hasEffects()) {
                        this.victims.clear();
                    } else {
                        List<MobEffectInstance> list = new ArrayList();
                        PotionContents potioncontents = this.potionContents;

                        Objects.requireNonNull(list);
                        potioncontents.forEachEffect(list::add, this.potionDurationScale);
                        List<LivingEntity> list1 = this.level().<LivingEntity>getEntitiesOfClass(LivingEntity.class, this.getBoundingBox());

                        if (!list1.isEmpty()) {
                            for (LivingEntity livingentity : list1) {
                                if (!this.victims.containsKey(livingentity) && livingentity.isAffectedByPotions()) {
                                    Stream stream = list.stream();

                                    Objects.requireNonNull(livingentity);
                                    if (!stream.noneMatch(livingentity::canBeAffected)) {
                                        double d0 = livingentity.getX() - this.getX();
                                        double d1 = livingentity.getZ() - this.getZ();
                                        double d2 = d0 * d0 + d1 * d1;

                                        if (d2 <= (double) (f * f)) {
                                            this.victims.put(livingentity, this.tickCount + this.reapplicationDelay);

                                            for (MobEffectInstance mobeffectinstance : list) {
                                                if (((MobEffect) mobeffectinstance.getEffect().value()).isInstantenous()) {
                                                    ((MobEffect) mobeffectinstance.getEffect().value()).applyInstantenousEffect(serverLevel, this, this.getOwner(), livingentity, mobeffectinstance.getAmplifier(), 0.5D);
                                                } else {
                                                    livingentity.addEffect(new MobEffectInstance(mobeffectinstance), this);
                                                }
                                            }

                                            if (this.radiusOnUse != 0.0F) {
                                                f += this.radiusOnUse;
                                                if (f < 0.5F) {
                                                    this.discard();
                                                    return;
                                                }

                                                this.setRadius(f);
                                            }

                                            if (this.durationOnUse != 0 && this.duration != -1) {
                                                this.duration += this.durationOnUse;
                                                if (this.duration <= 0) {
                                                    this.discard();
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public float getRadiusOnUse() {
        return this.radiusOnUse;
    }

    public void setRadiusOnUse(float radiusOnUse) {
        this.radiusOnUse = radiusOnUse;
    }

    public float getRadiusPerTick() {
        return this.radiusPerTick;
    }

    public void setRadiusPerTick(float radiusPerTick) {
        this.radiusPerTick = radiusPerTick;
    }

    public int getDurationOnUse() {
        return this.durationOnUse;
    }

    public void setDurationOnUse(int durationOnUse) {
        this.durationOnUse = durationOnUse;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.owner = EntityReference.of(owner);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.tickCount = input.getIntOr("Age", 0);
        this.duration = input.getIntOr("Duration", -1);
        this.waitTime = input.getIntOr("WaitTime", 20);
        this.reapplicationDelay = input.getIntOr("ReapplicationDelay", 20);
        this.durationOnUse = input.getIntOr("DurationOnUse", 0);
        this.radiusOnUse = input.getFloatOr("RadiusOnUse", 0.0F);
        this.radiusPerTick = input.getFloatOr("RadiusPerTick", 0.0F);
        this.setRadius(input.getFloatOr("Radius", 3.0F));
        this.owner = EntityReference.<LivingEntity>read(input, "Owner");
        this.setCustomParticle((ParticleOptions) input.read("custom_particle", ParticleTypes.CODEC).orElse((Object) null));
        this.setPotionContents((PotionContents) input.read("potion_contents", PotionContents.CODEC).orElse(PotionContents.EMPTY));
        this.potionDurationScale = input.getFloatOr("potion_duration_scale", 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", this.tickCount);
        output.putInt("Duration", this.duration);
        output.putInt("WaitTime", this.waitTime);
        output.putInt("ReapplicationDelay", this.reapplicationDelay);
        output.putInt("DurationOnUse", this.durationOnUse);
        output.putFloat("RadiusOnUse", this.radiusOnUse);
        output.putFloat("RadiusPerTick", this.radiusPerTick);
        output.putFloat("Radius", this.getRadius());
        output.storeNullable("custom_particle", ParticleTypes.CODEC, this.customParticle);
        EntityReference.store(this.owner, output, "Owner");
        if (!this.potionContents.equals(PotionContents.EMPTY)) {
            output.store("potion_contents", PotionContents.CODEC, this.potionContents);
        }

        if (this.potionDurationScale != 1.0F) {
            output.putFloat("potion_duration_scale", this.potionDurationScale);
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (AreaEffectCloud.DATA_RADIUS.equals(accessor)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.POTION_CONTENTS ? castComponentValue(type, this.potionContents) : (type == DataComponents.POTION_DURATION_SCALE ? castComponentValue(type, this.potionDurationScale) : super.get(type)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.POTION_CONTENTS);
        this.applyImplicitComponentIfPresent(components, DataComponents.POTION_DURATION_SCALE);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.POTION_CONTENTS) {
            this.setPotionContents((PotionContents) castComponentValue(DataComponents.POTION_CONTENTS, value));
            return true;
        } else if (type == DataComponents.POTION_DURATION_SCALE) {
            this.setPotionDurationScale((Float) castComponentValue(DataComponents.POTION_DURATION_SCALE, value));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }
}
