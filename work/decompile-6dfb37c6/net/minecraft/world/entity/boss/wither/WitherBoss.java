package net.minecraft.world.entity.boss.wither;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WitherBoss extends Monster implements RangedAttackMob {

    private static final EntityDataAccessor<Integer> DATA_TARGET_A = SynchedEntityData.<Integer>defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_B = SynchedEntityData.<Integer>defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_C = SynchedEntityData.<Integer>defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Integer>> DATA_TARGETS = ImmutableList.of(WitherBoss.DATA_TARGET_A, WitherBoss.DATA_TARGET_B, WitherBoss.DATA_TARGET_C);
    private static final EntityDataAccessor<Integer> DATA_ID_INV = SynchedEntityData.<Integer>defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final int INVULNERABLE_TICKS = 220;
    private static final int DEFAULT_INVULNERABLE_TICKS = 0;
    private final float[] xRotHeads = new float[2];
    private final float[] yRotHeads = new float[2];
    private final float[] xRotOHeads = new float[2];
    private final float[] yRotOHeads = new float[2];
    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    public final ServerBossEvent bossEvent;
    private static final TargetingConditions.Selector LIVING_ENTITY_SELECTOR = (livingentity, serverlevel) -> {
        return !livingentity.getType().is(EntityTypeTags.WITHER_FRIENDS) && livingentity.attackable();
    };
    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forCombat().range(20.0D).selector(WitherBoss.LIVING_ENTITY_SELECTOR);

    public WitherBoss(EntityType<? extends WitherBoss> type, Level level) {
        super(type, level);
        this.bossEvent = (ServerBossEvent) (new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS)).setDarkenScreen(true);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.setHealth(this.getMaxHealth());
        this.xpReward = 50;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);

        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        return flyingpathnavigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WitherBoss.WitherDoNothingGoal());
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 40, 20.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, LivingEntity.class, 0, false, false, WitherBoss.LIVING_ENTITY_SELECTOR));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(WitherBoss.DATA_TARGET_A, 0);
        entityData.define(WitherBoss.DATA_TARGET_B, 0);
        entityData.define(WitherBoss.DATA_TARGET_C, 0);
        entityData.define(WitherBoss.DATA_ID_INV, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Invul", this.getInvulnerableTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setInvulnerableTicks(input.getIntOr("Invul", 0));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }

    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public void aiStep() {
        Vec3 vec3 = this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D);

        if (!this.level().isClientSide() && this.getAlternativeTarget(0) > 0) {
            Entity entity = this.level().getEntity(this.getAlternativeTarget(0));

            if (entity != null) {
                double d0 = vec3.y;

                if (this.getY() < entity.getY() || !this.isPowered() && this.getY() < entity.getY() + 5.0D) {
                    d0 = Math.max(0.0D, d0);
                    d0 += 0.3D - d0 * (double) 0.6F;
                }

                vec3 = new Vec3(vec3.x, d0, vec3.z);
                Vec3 vec31 = new Vec3(entity.getX() - this.getX(), 0.0D, entity.getZ() - this.getZ());

                if (vec31.horizontalDistanceSqr() > 9.0D) {
                    Vec3 vec32 = vec31.normalize();

                    vec3 = vec3.add(vec32.x * 0.3D - vec3.x * 0.6D, 0.0D, vec32.z * 0.3D - vec3.z * 0.6D);
                }
            }
        }

        this.setDeltaMovement(vec3);
        if (vec3.horizontalDistanceSqr() > 0.05D) {
            this.setYRot((float) Mth.atan2(vec3.z, vec3.x) * (180F / (float) Math.PI) - 90.0F);
        }

        super.aiStep();

        for (int i = 0; i < 2; ++i) {
            this.yRotOHeads[i] = this.yRotHeads[i];
            this.xRotOHeads[i] = this.xRotHeads[i];
        }

        for (int j = 0; j < 2; ++j) {
            int k = this.getAlternativeTarget(j + 1);
            Entity entity1 = null;

            if (k > 0) {
                entity1 = this.level().getEntity(k);
            }

            if (entity1 != null) {
                double d1 = this.getHeadX(j + 1);
                double d2 = this.getHeadY(j + 1);
                double d3 = this.getHeadZ(j + 1);
                double d4 = entity1.getX() - d1;
                double d5 = entity1.getEyeY() - d2;
                double d6 = entity1.getZ() - d3;
                double d7 = Math.sqrt(d4 * d4 + d6 * d6);
                float f = (float) (Mth.atan2(d6, d4) * (double) (180F / (float) Math.PI)) - 90.0F;
                float f1 = (float) (-(Mth.atan2(d5, d7) * (double) (180F / (float) Math.PI)));

                this.xRotHeads[j] = this.rotlerp(this.xRotHeads[j], f1, 40.0F);
                this.yRotHeads[j] = this.rotlerp(this.yRotHeads[j], f, 10.0F);
            } else {
                this.yRotHeads[j] = this.rotlerp(this.yRotHeads[j], this.yBodyRot, 10.0F);
            }
        }

        boolean flag = this.isPowered();

        for (int l = 0; l < 3; ++l) {
            double d8 = this.getHeadX(l);
            double d9 = this.getHeadY(l);
            double d10 = this.getHeadZ(l);
            float f2 = 0.3F * this.getScale();

            this.level().addParticle(ParticleTypes.SMOKE, d8 + this.random.nextGaussian() * (double) f2, d9 + this.random.nextGaussian() * (double) f2, d10 + this.random.nextGaussian() * (double) f2, 0.0D, 0.0D, 0.0D);
            if (flag && this.level().random.nextInt(4) == 0) {
                this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.5F), d8 + this.random.nextGaussian() * (double) f2, d9 + this.random.nextGaussian() * (double) f2, d10 + this.random.nextGaussian() * (double) f2, 0.0D, 0.0D, 0.0D);
            }
        }

        if (this.getInvulnerableTicks() > 0) {
            float f3 = 3.3F * this.getScale();

            for (int i1 = 0; i1 < 3; ++i1) {
                this.level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.9F), this.getX() + this.random.nextGaussian(), this.getY() + (double) (this.random.nextFloat() * f3), this.getZ() + this.random.nextGaussian(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (this.getInvulnerableTicks() > 0) {
            int i = this.getInvulnerableTicks() - 1;

            this.bossEvent.setProgress(1.0F - (float) i / 220.0F);
            if (i <= 0) {
                level.explode(this, this.getX(), this.getEyeY(), this.getZ(), 7.0F, false, Level.ExplosionInteraction.MOB);
                if (!this.isSilent()) {
                    level.globalLevelEvent(1023, this.blockPosition(), 0);
                }
            }

            this.setInvulnerableTicks(i);
            if (this.tickCount % 10 == 0) {
                this.heal(10.0F);
            }

        } else {
            super.customServerAiStep(level);

            for (int j = 1; j < 3; ++j) {
                if (this.tickCount >= this.nextHeadUpdate[j - 1]) {
                    this.nextHeadUpdate[j - 1] = this.tickCount + 10 + this.random.nextInt(10);
                    if (level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) {
                        int k = j - 1;
                        int l = this.idleHeadUpdates[j - 1];

                        this.idleHeadUpdates[k] = this.idleHeadUpdates[j - 1] + 1;
                        if (l > 15) {
                            float f = 10.0F;
                            float f1 = 5.0F;
                            double d0 = Mth.nextDouble(this.random, this.getX() - 10.0D, this.getX() + 10.0D);
                            double d1 = Mth.nextDouble(this.random, this.getY() - 5.0D, this.getY() + 5.0D);
                            double d2 = Mth.nextDouble(this.random, this.getZ() - 10.0D, this.getZ() + 10.0D);

                            this.performRangedAttack(j + 1, d0, d1, d2, true);
                            this.idleHeadUpdates[j - 1] = 0;
                        }
                    }

                    int i1 = this.getAlternativeTarget(j);

                    if (i1 > 0) {
                        LivingEntity livingentity = (LivingEntity) level.getEntity(i1);

                        if (livingentity != null && this.canAttack(livingentity) && this.distanceToSqr((Entity) livingentity) <= 900.0D && this.hasLineOfSight(livingentity)) {
                            this.performRangedAttack(j + 1, livingentity);
                            this.nextHeadUpdate[j - 1] = this.tickCount + 40 + this.random.nextInt(20);
                            this.idleHeadUpdates[j - 1] = 0;
                        } else {
                            this.setAlternativeTarget(j, 0);
                        }
                    } else {
                        List<LivingEntity> list = level.<LivingEntity>getNearbyEntities(LivingEntity.class, WitherBoss.TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0D, 8.0D, 20.0D));

                        if (!list.isEmpty()) {
                            LivingEntity livingentity1 = (LivingEntity) list.get(this.random.nextInt(list.size()));

                            this.setAlternativeTarget(j, livingentity1.getId());
                        }
                    }
                }
            }

            if (this.getTarget() != null) {
                this.setAlternativeTarget(0, this.getTarget().getId());
            } else {
                this.setAlternativeTarget(0, 0);
            }

            if (this.destroyBlocksTick > 0) {
                --this.destroyBlocksTick;
                if (this.destroyBlocksTick == 0 && (Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    boolean flag = false;
                    int j1 = Mth.floor(this.getBbWidth() / 2.0F + 1.0F);
                    int k1 = Mth.floor(this.getBbHeight());

                    for (BlockPos blockpos : BlockPos.betweenClosed(this.getBlockX() - j1, this.getBlockY(), this.getBlockZ() - j1, this.getBlockX() + j1, this.getBlockY() + k1, this.getBlockZ() + j1)) {
                        BlockState blockstate = level.getBlockState(blockpos);

                        if (canDestroy(blockstate)) {
                            flag = level.destroyBlock(blockpos, true, this) || flag;
                        }
                    }

                    if (flag) {
                        level.levelEvent((Entity) null, 1022, this.blockPosition(), 0);
                    }
                }
            }

            if (this.tickCount % 20 == 0) {
                this.heal(1.0F);
            }

            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(BlockState state) {
        return !state.isAir() && !state.is(BlockTags.WITHER_IMMUNE);
    }

    public void makeInvulnerable() {
        this.setInvulnerableTicks(220);
        this.bossEvent.setProgress(0.0F);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void makeStuckInBlock(BlockState blockState, Vec3 speedMultiplier) {}

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private double getHeadX(int index) {
        if (index <= 0) {
            return this.getX();
        } else {
            float f = (this.yBodyRot + (float) (180 * (index - 1))) * ((float) Math.PI / 180F);
            float f1 = Mth.cos((double) f);

            return this.getX() + (double) f1 * 1.3D * (double) this.getScale();
        }
    }

    private double getHeadY(int index) {
        float f = index <= 0 ? 3.0F : 2.2F;

        return this.getY() + (double) (f * this.getScale());
    }

    private double getHeadZ(int index) {
        if (index <= 0) {
            return this.getZ();
        } else {
            float f = (this.yBodyRot + (float) (180 * (index - 1))) * ((float) Math.PI / 180F);
            float f1 = Mth.sin((double) f);

            return this.getZ() + (double) f1 * 1.3D * (double) this.getScale();
        }
    }

    private float rotlerp(float a, float b, float max) {
        float f3 = Mth.wrapDegrees(b - a);

        if (f3 > max) {
            f3 = max;
        }

        if (f3 < -max) {
            f3 = -max;
        }

        return a + f3;
    }

    private void performRangedAttack(int head, LivingEntity target) {
        this.performRangedAttack(head, target.getX(), target.getY() + (double) target.getEyeHeight() * 0.5D, target.getZ(), head == 0 && this.random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(int head, double tx, double ty, double tz, boolean dangerous) {
        if (!this.isSilent()) {
            this.level().levelEvent((Entity) null, 1024, this.blockPosition(), 0);
        }

        double d3 = this.getHeadX(head);
        double d4 = this.getHeadY(head);
        double d5 = this.getHeadZ(head);
        double d6 = tx - d3;
        double d7 = ty - d4;
        double d8 = tz - d5;
        Vec3 vec3 = new Vec3(d6, d7, d8);
        WitherSkull witherskull = new WitherSkull(this.level(), this, vec3.normalize());

        witherskull.setOwner(this);
        if (dangerous) {
            witherskull.setDangerous(true);
        }

        witherskull.setPos(d3, d4, d5);
        this.level().addFreshEntity(witherskull);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        this.performRangedAttack(0, target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else if (!source.is(DamageTypeTags.WITHER_IMMUNE_TO) && !(source.getEntity() instanceof WitherBoss)) {
            if (this.getInvulnerableTicks() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                if (this.isPowered()) {
                    Entity entity = source.getDirectEntity();

                    if (entity instanceof AbstractArrow || entity instanceof WindCharge) {
                        return false;
                    }
                }

                Entity entity1 = source.getEntity();

                if (entity1 != null && entity1.getType().is(EntityTypeTags.WITHER_FRIENDS)) {
                    return false;
                } else {
                    if (this.destroyBlocksTick <= 0) {
                        this.destroyBlocksTick = 20;
                    }

                    for (int i = 0; i < this.idleHeadUpdates.length; ++i) {
                        this.idleHeadUpdates[i] += 3;
                    }

                    return super.hurtServer(level, source, damage);
                }
            }
        } else {
            return false;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        ItemEntity itementity = this.spawnAtLocation(level, (ItemLike) Items.NETHER_STAR);

        if (itementity != null) {
            itementity.setExtendedLifetime();
        }

    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && !this.getType().isAllowedInPeaceful()) {
            this.discard();
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean addEffect(MobEffectInstance newEffect, @Nullable Entity source) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.6F).add(Attributes.FLYING_SPEED, (double) 0.6F).add(Attributes.FOLLOW_RANGE, 40.0D).add(Attributes.ARMOR, 4.0D);
    }

    public float[] getHeadYRots() {
        return this.yRotHeads;
    }

    public float[] getHeadXRots() {
        return this.xRotHeads;
    }

    public int getInvulnerableTicks() {
        return (Integer) this.entityData.get(WitherBoss.DATA_ID_INV);
    }

    public void setInvulnerableTicks(int invulnerableTicks) {
        this.entityData.set(WitherBoss.DATA_ID_INV, invulnerableTicks);
    }

    public int getAlternativeTarget(int headIndex) {
        return (Integer) this.entityData.get((EntityDataAccessor) WitherBoss.DATA_TARGETS.get(headIndex));
    }

    public void setAlternativeTarget(int headIndex, int entityId) {
        this.entityData.set((EntityDataAccessor) WitherBoss.DATA_TARGETS.get(headIndex), entityId);
    }

    public boolean isPowered() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance newEffect) {
        return newEffect.is(MobEffects.WITHER) ? false : super.canBeAffected(newEffect);
    }

    private class WitherDoNothingGoal extends Goal {

        public WitherDoNothingGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return WitherBoss.this.getInvulnerableTicks() > 0;
        }
    }
}
