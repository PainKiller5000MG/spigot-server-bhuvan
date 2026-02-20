package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnderMan extends Monster implements NeutralMob {

    private static final Identifier SPEED_MODIFIER_ATTACKING_ID = Identifier.withDefaultNamespace("attacking");
    private static final AttributeModifier SPEED_MODIFIER_ATTACKING = new AttributeModifier(EnderMan.SPEED_MODIFIER_ATTACKING_ID, (double) 0.15F, AttributeModifier.Operation.ADD_VALUE);
    private static final int DELAY_BETWEEN_CREEPY_STARE_SOUND = 400;
    private static final int MIN_DEAGGRESSION_TIME = 600;
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRY_STATE = SynchedEntityData.<Optional<BlockState>>defineId(EnderMan.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DATA_CREEPY = SynchedEntityData.<Boolean>defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STARED_AT = SynchedEntityData.<Boolean>defineId(EnderMan.class, EntityDataSerializers.BOOLEAN);
    private int lastStareSound = Integer.MIN_VALUE;
    private int targetChangeTime;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private long persistentAngerEndTime;
    private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

    public EnderMan(EntityType<? extends EnderMan> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnderMan.EndermanFreezeWhenLookedAt(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D, 0.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new EnderMan.EndermanLeaveBlockGoal(this));
        this.goalSelector.addGoal(11, new EnderMan.EndermanTakeBlockGoal(this));
        this.targetSelector.addGoal(1, new EnderMan.EndermanLookForPlayerGoal(this, this::isAngryAt));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this, new Class[0]));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Endermite.class, true, false));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal(this, false));
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, (double) 0.3F).add(Attributes.ATTACK_DAMAGE, 7.0D).add(Attributes.FOLLOW_RANGE, 64.0D).add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (target == null) {
            this.targetChangeTime = 0;
            this.entityData.set(EnderMan.DATA_CREEPY, false);
            this.entityData.set(EnderMan.DATA_STARED_AT, false);
            attributeinstance.removeModifier(EnderMan.SPEED_MODIFIER_ATTACKING_ID);
        } else {
            this.targetChangeTime = this.tickCount;
            this.entityData.set(EnderMan.DATA_CREEPY, true);
            if (!attributeinstance.hasModifier(EnderMan.SPEED_MODIFIER_ATTACKING_ID)) {
                attributeinstance.addTransientModifier(EnderMan.SPEED_MODIFIER_ATTACKING);
            }
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(EnderMan.DATA_CARRY_STATE, Optional.empty());
        entityData.define(EnderMan.DATA_CREEPY, false);
        entityData.define(EnderMan.DATA_STARED_AT, false);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setTimeToRemainAngry((long) EnderMan.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void setPersistentAngerEndTime(long endTime) {
        this.persistentAngerEndTime = endTime;
    }

    @Override
    public long getPersistentAngerEndTime() {
        return this.persistentAngerEndTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> persistentAngerTarget) {
        this.persistentAngerTarget = persistentAngerTarget;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public void playStareSound() {
        if (this.tickCount >= this.lastStareSound + 400) {
            this.lastStareSound = this.tickCount;
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ENDERMAN_STARE, this.getSoundSource(), 2.5F, 1.0F, false);
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (EnderMan.DATA_CREEPY.equals(accessor) && this.hasBeenStaredAt() && this.level().isClientSide()) {
            this.playStareSound();
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        BlockState blockstate = this.getCarriedBlock();

        if (blockstate != null) {
            output.store("carriedBlockState", BlockState.CODEC, blockstate);
        }

        this.addPersistentAngerSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setCarriedBlock((BlockState) input.read("carriedBlockState", BlockState.CODEC).filter((blockstate) -> {
            return !blockstate.isAir();
        }).orElse((Object) null));
        this.readPersistentAngerSaveData(this.level(), input);
    }

    private boolean isBeingStaredBy(Player player) {
        return !LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(player) ? false : this.isLookingAtMe(player, 0.025D, true, false, new double[]{this.getEyeY()});
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        }

        this.jumping = false;
        if (!this.level().isClientSide()) {
            this.updatePersistentAnger((ServerLevel) this.level(), true);
        }

        super.aiStep();
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (level.isBrightOutside() && this.tickCount >= this.targetChangeTime + 600) {
            float f = this.getLightLevelDependentMagicValue();

            if (f > 0.5F && level.canSeeSky(this.blockPosition()) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                this.setTarget((LivingEntity) null);
                this.teleport();
            }
        }

        super.customServerAiStep(level);
    }

    public boolean teleport() {
        if (!this.level().isClientSide() && this.isAlive()) {
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * 64.0D;
            double d1 = this.getY() + (double) (this.random.nextInt(64) - 32);
            double d2 = this.getZ() + (this.random.nextDouble() - 0.5D) * 64.0D;

            return this.teleport(d0, d1, d2);
        } else {
            return false;
        }
    }

    public boolean teleportTowards(Entity entity) {
        Vec3 vec3 = new Vec3(this.getX() - entity.getX(), this.getY(0.5D) - entity.getEyeY(), this.getZ() - entity.getZ());

        vec3 = vec3.normalize();
        double d0 = 16.0D;
        double d1 = this.getX() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3.x * 16.0D;
        double d2 = this.getY() + (double) (this.random.nextInt(16) - 8) - vec3.y * 16.0D;
        double d3 = this.getZ() + (this.random.nextDouble() - 0.5D) * 8.0D - vec3.z * 16.0D;

        return this.teleport(d1, d2, d3);
    }

    private boolean teleport(double x, double y, double z) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(x, y, z);

        while (blockpos_mutableblockpos.getY() > this.level().getMinY() && !this.level().getBlockState(blockpos_mutableblockpos).blocksMotion()) {
            blockpos_mutableblockpos.move(Direction.DOWN);
        }

        BlockState blockstate = this.level().getBlockState(blockpos_mutableblockpos);
        boolean flag = blockstate.blocksMotion();
        boolean flag1 = blockstate.getFluidState().is(FluidTags.WATER);

        if (flag && !flag1) {
            Vec3 vec3 = this.position();
            boolean flag2 = this.randomTeleport(x, y, z, true);

            if (flag2) {
                this.level().gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of((Entity) this));
                if (!this.isSilent()) {
                    this.level().playSound((Entity) null, this.xo, this.yo, this.zo, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                }
            }

            return flag2;
        } else {
            return false;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isCreepy() ? SoundEvents.ENDERMAN_SCREAM : SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        BlockState blockstate = this.getCarriedBlock();

        if (blockstate != null) {
            ItemStack itemstack = new ItemStack(Items.DIAMOND_AXE);

            EnchantmentHelper.enchantItemFromProvider(itemstack, level.registryAccess(), VanillaEnchantmentProviders.ENDERMAN_LOOT_DROP, level.getCurrentDifficultyAt(this.blockPosition()), this.getRandom());
            LootParams.Builder lootparams_builder = (new LootParams.Builder((ServerLevel) this.level())).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.TOOL, itemstack).withOptionalParameter(LootContextParams.THIS_ENTITY, this);

            for (ItemStack itemstack1 : blockstate.getDrops(lootparams_builder)) {
                this.spawnAtLocation(level, itemstack1);
            }
        }

    }

    public void setCarriedBlock(@Nullable BlockState carryingBlock) {
        this.entityData.set(EnderMan.DATA_CARRY_STATE, Optional.ofNullable(carryingBlock));
    }

    public @Nullable BlockState getCarriedBlock() {
        return (BlockState) ((Optional) this.entityData.get(EnderMan.DATA_CARRY_STATE)).orElse((Object) null);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else {
            Entity entity = source.getDirectEntity();
            AbstractThrownPotion abstractthrownpotion;

            if (entity instanceof AbstractThrownPotion) {
                AbstractThrownPotion abstractthrownpotion1 = (AbstractThrownPotion) entity;

                abstractthrownpotion = abstractthrownpotion1;
            } else {
                abstractthrownpotion = null;
            }

            AbstractThrownPotion abstractthrownpotion2 = abstractthrownpotion;

            if (!source.is(DamageTypeTags.IS_PROJECTILE) && abstractthrownpotion2 == null) {
                boolean flag = super.hurtServer(level, source, damage);

                if (!(source.getEntity() instanceof LivingEntity) && this.random.nextInt(10) != 0) {
                    this.teleport();
                }

                return flag;
            } else {
                boolean flag1 = abstractthrownpotion2 != null && this.hurtWithCleanWater(level, source, abstractthrownpotion2, damage);

                for (int i = 0; i < 64; ++i) {
                    if (this.teleport()) {
                        return true;
                    }
                }

                return flag1;
            }
        }
    }

    private boolean hurtWithCleanWater(ServerLevel level, DamageSource source, AbstractThrownPotion thrownPotion, float damage) {
        ItemStack itemstack = thrownPotion.getItem();
        PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

        return potioncontents.is(Potions.WATER) ? super.hurtServer(level, source, damage) : false;
    }

    public boolean isCreepy() {
        return (Boolean) this.entityData.get(EnderMan.DATA_CREEPY);
    }

    public boolean hasBeenStaredAt() {
        return (Boolean) this.entityData.get(EnderMan.DATA_STARED_AT);
    }

    public void setBeingStaredAt() {
        this.entityData.set(EnderMan.DATA_STARED_AT, true);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCarriedBlock() != null;
    }

    private static class EndermanLookForPlayerGoal extends NearestAttackableTargetGoal<Player> {

        private final EnderMan enderman;
        private @Nullable Player pendingTarget;
        private int aggroTime;
        private int teleportTime;
        private final TargetingConditions startAggroTargetConditions;
        private final TargetingConditions continueAggroTargetConditions = TargetingConditions.forCombat().ignoreLineOfSight();
        private final TargetingConditions.Selector isAngerInducing;

        public EndermanLookForPlayerGoal(EnderMan enderman, TargetingConditions.@Nullable Selector isAngryAt) {
            super(enderman, Player.class, 10, false, false, isAngryAt);
            this.enderman = enderman;
            this.isAngerInducing = (livingentity, serverlevel) -> {
                return (enderman.isBeingStaredBy((Player) livingentity) || enderman.isAngryAt(livingentity, serverlevel)) && !enderman.hasIndirectPassenger(livingentity);
            };
            this.startAggroTargetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(this.isAngerInducing);
        }

        @Override
        public boolean canUse() {
            this.pendingTarget = getServerLevel((Entity) this.enderman).getNearestPlayer(this.startAggroTargetConditions.range(this.getFollowDistance()), this.enderman);
            return this.pendingTarget != null;
        }

        @Override
        public void start() {
            this.aggroTime = this.adjustedTickDelay(5);
            this.teleportTime = 0;
            this.enderman.setBeingStaredAt();
        }

        @Override
        public void stop() {
            this.pendingTarget = null;
            super.stop();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.pendingTarget != null) {
                if (!this.isAngerInducing.test(this.pendingTarget, getServerLevel((Entity) this.enderman))) {
                    return false;
                } else {
                    this.enderman.lookAt(this.pendingTarget, 10.0F, 10.0F);
                    return true;
                }
            } else {
                if (this.target != null) {
                    if (this.enderman.hasIndirectPassenger(this.target)) {
                        return false;
                    }

                    if (this.continueAggroTargetConditions.test(getServerLevel((Entity) this.enderman), this.enderman, this.target)) {
                        return true;
                    }
                }

                return super.canContinueToUse();
            }
        }

        @Override
        public void tick() {
            if (this.enderman.getTarget() == null) {
                super.setTarget((LivingEntity) null);
            }

            if (this.pendingTarget != null) {
                if (--this.aggroTime <= 0) {
                    this.target = this.pendingTarget;
                    this.pendingTarget = null;
                    super.start();
                }
            } else {
                if (this.target != null && !this.enderman.isPassenger()) {
                    if (this.enderman.isBeingStaredBy((Player) this.target)) {
                        if (this.target.distanceToSqr((Entity) this.enderman) < 16.0D) {
                            this.enderman.teleport();
                        }

                        this.teleportTime = 0;
                    } else if (this.target.distanceToSqr((Entity) this.enderman) > 256.0D && this.teleportTime++ >= this.adjustedTickDelay(30) && this.enderman.teleportTowards(this.target)) {
                        this.teleportTime = 0;
                    }
                }

                super.tick();
            }

        }
    }

    private static class EndermanFreezeWhenLookedAt extends Goal {

        private final EnderMan enderman;
        private @Nullable LivingEntity target;

        public EndermanFreezeWhenLookedAt(EnderMan enderman) {
            this.enderman = enderman;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.target = this.enderman.getTarget();
            LivingEntity livingentity = this.target;

            if (livingentity instanceof Player player) {
                double d0 = this.target.distanceToSqr((Entity) this.enderman);

                return d0 > 256.0D ? false : this.enderman.isBeingStaredBy(player);
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            this.enderman.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.enderman.getLookControl().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        }
    }

    private static class EndermanLeaveBlockGoal extends Goal {

        private final EnderMan enderman;

        public EndermanLeaveBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() == null ? false : (!(Boolean) getServerLevel((Entity) this.enderman).getGameRules().get(GameRules.MOB_GRIEFING) ? false : this.enderman.getRandom().nextInt(reducedTickDelay(2000)) == 0);
        }

        @Override
        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level level = this.enderman.level();
            int i = Mth.floor(this.enderman.getX() - 1.0D + randomsource.nextDouble() * 2.0D);
            int j = Mth.floor(this.enderman.getY() + randomsource.nextDouble() * 2.0D);
            int k = Mth.floor(this.enderman.getZ() - 1.0D + randomsource.nextDouble() * 2.0D);
            BlockPos blockpos = new BlockPos(i, j, k);
            BlockState blockstate = level.getBlockState(blockpos);
            BlockPos blockpos1 = blockpos.below();
            BlockState blockstate1 = level.getBlockState(blockpos1);
            BlockState blockstate2 = this.enderman.getCarriedBlock();

            if (blockstate2 != null) {
                blockstate2 = Block.updateFromNeighbourShapes(blockstate2, this.enderman.level(), blockpos);
                if (this.canPlaceBlock(level, blockpos, blockstate2, blockstate, blockstate1, blockpos1)) {
                    level.setBlock(blockpos, blockstate2, 3);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(this.enderman, blockstate2));
                    this.enderman.setCarriedBlock((BlockState) null);
                }

            }
        }

        private boolean canPlaceBlock(Level level, BlockPos pos, BlockState carried, BlockState targetState, BlockState belowState, BlockPos below) {
            return targetState.isAir() && !belowState.isAir() && !belowState.is(Blocks.BEDROCK) && belowState.isCollisionShapeFullBlock(level, below) && carried.canSurvive(level, pos) && level.getEntities(this.enderman, AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos))).isEmpty();
        }
    }

    private static class EndermanTakeBlockGoal extends Goal {

        private final EnderMan enderman;

        public EndermanTakeBlockGoal(EnderMan enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canUse() {
            return this.enderman.getCarriedBlock() != null ? false : (!(Boolean) getServerLevel((Entity) this.enderman).getGameRules().get(GameRules.MOB_GRIEFING) ? false : this.enderman.getRandom().nextInt(reducedTickDelay(20)) == 0);
        }

        @Override
        public void tick() {
            RandomSource randomsource = this.enderman.getRandom();
            Level level = this.enderman.level();
            int i = Mth.floor(this.enderman.getX() - 2.0D + randomsource.nextDouble() * 4.0D);
            int j = Mth.floor(this.enderman.getY() + randomsource.nextDouble() * 3.0D);
            int k = Mth.floor(this.enderman.getZ() - 2.0D + randomsource.nextDouble() * 4.0D);
            BlockPos blockpos = new BlockPos(i, j, k);
            BlockState blockstate = level.getBlockState(blockpos);
            Vec3 vec3 = new Vec3((double) this.enderman.getBlockX() + 0.5D, (double) j + 0.5D, (double) this.enderman.getBlockZ() + 0.5D);
            Vec3 vec31 = new Vec3((double) i + 0.5D, (double) j + 0.5D, (double) k + 0.5D);
            BlockHitResult blockhitresult = level.clip(new ClipContext(vec3, vec31, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.enderman));
            boolean flag = blockhitresult.getBlockPos().equals(blockpos);

            if (blockstate.is(BlockTags.ENDERMAN_HOLDABLE) && flag) {
                level.removeBlock(blockpos, false);
                level.gameEvent(GameEvent.BLOCK_DESTROY, blockpos, GameEvent.Context.of(this.enderman, blockstate));
                this.enderman.setCarriedBlock(blockstate.getBlock().defaultBlockState());
            }

        }
    }
}
