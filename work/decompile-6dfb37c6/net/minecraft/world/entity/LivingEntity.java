package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class LivingEntity extends Entity implements Attackable, WaypointTransmitter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    public static final String TAG_ATTRIBUTES = "attributes";
    public static final String TAG_SLEEPING_POS = "sleeping_pos";
    public static final String TAG_EQUIPMENT = "equipment";
    public static final String TAG_BRAIN = "Brain";
    public static final String TAG_FALL_FLYING = "FallFlying";
    public static final String TAG_HURT_TIME = "HurtTime";
    public static final String TAG_DEATH_TIME = "DeathTime";
    public static final String TAG_HURT_BY_TIMESTAMP = "HurtByTimestamp";
    public static final String TAG_HEALTH = "Health";
    private static final Identifier SPEED_MODIFIER_POWDER_SNOW_ID = Identifier.withDefaultNamespace("powder_snow");
    private static final Identifier SPRINTING_MODIFIER_ID = Identifier.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(LivingEntity.SPRINTING_MODIFIER_ID, (double) 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SADDLE_OFFSET = 106;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003D;
    public static final double DEFAULT_BASE_GRAVITY = 0.08D;
    public static final int DEATH_DURATION = 20;
    protected static final float INPUT_FRICTION = 0.98F;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final float BASE_JUMP_POWER = 0.42F;
    protected static final float DEFAULT_KNOCKBACK = 0.4F;
    protected static final int INVULNERABLE_DURATION = 20;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0D;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    public static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.<Byte>defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.<Float>defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<ParticleOptions>> DATA_EFFECT_PARTICLES = SynchedEntityData.<List<ParticleOptions>>defineId(LivingEntity.class, EntityDataSerializers.PARTICLES);
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.<Boolean>defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.<Integer>defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.<Integer>defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.<Optional<BlockPos>>defineId(LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    private static final float WATER_FLOAT_IMPULSE = 0.04F;
    public static final Predicate<LivingEntity> PLAYER_NOT_WEARING_DISGUISE_ITEM = (livingentity) -> {
        if (livingentity instanceof Player player) {
            ItemStack itemstack = player.getItemBySlot(EquipmentSlot.HEAD);

            return !itemstack.is(ItemTags.GAZE_DISGUISE_EQUIPMENT);
        } else {
            return true;
        }
    };
    private static final Dynamic<?> EMPTY_BRAIN = new Dynamic(JavaOps.INSTANCE, Map.of("memories", Map.of()));
    private final AttributeMap attributes;
    public CombatTracker combatTracker = new CombatTracker(this);
    public final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final Map<EquipmentSlot, ItemStack> lastEquipmentItems = Util.<EquipmentSlot, ItemStack>makeEnumMap(EquipmentSlot.class, (equipmentslot) -> {
        return ItemStack.EMPTY;
    });
    public boolean swinging;
    private boolean discardFriction = false;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    protected int itemSwapTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    public final ElytraAnimationState elytraAnimationState = new ElytraAnimationState(this);
    public @Nullable EntityReference<Player> lastHurtByPlayer;
    protected int lastHurtByPlayerMemoryTime;
    protected boolean dead;
    protected int noActionTime;
    public float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected InterpolationHandler interpolation = new InterpolationHandler(this);
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    public boolean effectsDirty = true;
    public @Nullable EntityReference<LivingEntity> lastHurtByMob;
    public int lastHurtByMobTimestamp;
    private @Nullable LivingEntity lastHurtMob;
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem;
    public int useItemRemaining;
    protected int fallFlyTicks;
    private long lastKineticHitFeedbackTime;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos;
    private @Nullable DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    protected @Nullable ItemStack autoSpinAttackItemStack;
    protected @Nullable Object2LongMap<Entity> recentKineticEnemies;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    protected boolean skipDropExperience;
    private final EnumMap<EquipmentSlot, Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>>> activeLocationDependentEnchantments;
    public final EntityEquipment equipment;
    private Waypoint.Icon locatorBarIcon;

    protected LivingEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.useItem = ItemStack.EMPTY;
        this.lastKineticHitFeedbackTime = -2147483648L;
        this.lastClimbablePos = Optional.empty();
        this.activeLocationDependentEnchantments = new EnumMap(EquipmentSlot.class);
        this.locatorBarIcon = new Waypoint.Icon();
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(type));
        this.setHealth(this.getMaxHealth());
        this.equipment = this.createEquipment();
        this.blocksBuilding = true;
        this.reapplyPosition();
        this.setYRot(this.random.nextFloat() * ((float) Math.PI * 2F));
        this.yHeadRot = this.getYRot();
        this.brain = this.makeBrain(LivingEntity.EMPTY_BRAIN);
    }

    @Override
    public @Nullable LivingEntity asLivingEntity() {
        return this;
    }

    @Contract(pure = true)
    protected EntityEquipment createEquipment() {
        return new EntityEquipment();
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> input) {
        return this.brainProvider().makeBrain(input);
    }

    @Override
    public void kill(ServerLevel level) {
        this.hurtServer(level, this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> targetType) {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        entityData.define(LivingEntity.DATA_EFFECT_PARTICLES, List.of());
        entityData.define(LivingEntity.DATA_EFFECT_AMBIENCE_ID, false);
        entityData.define(LivingEntity.DATA_ARROW_COUNT_ID, 0);
        entityData.define(LivingEntity.DATA_STINGER_COUNT_ID, 0);
        entityData.define(LivingEntity.DATA_HEALTH_ID, 1.0F);
        entityData.define(LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder().add(Attributes.MAX_HEALTH).add(Attributes.KNOCKBACK_RESISTANCE).add(Attributes.MOVEMENT_SPEED).add(Attributes.ARMOR).add(Attributes.ARMOR_TOUGHNESS).add(Attributes.MAX_ABSORPTION).add(Attributes.STEP_HEIGHT).add(Attributes.SCALE).add(Attributes.GRAVITY).add(Attributes.SAFE_FALL_DISTANCE).add(Attributes.FALL_DAMAGE_MULTIPLIER).add(Attributes.JUMP_STRENGTH).add(Attributes.OXYGEN_BONUS).add(Attributes.BURNING_TIME).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).add(Attributes.WATER_MOVEMENT_EFFICIENCY).add(Attributes.MOVEMENT_EFFICIENCY).add(Attributes.ATTACK_KNOCKBACK).add(Attributes.CAMERA_DISTANCE).add(Attributes.WAYPOINT_TRANSMIT_RANGE);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (onGround && this.fallDistance > 0.0D) {
                this.onChangedBlock(serverlevel, pos);
                double d1 = (double) Math.max(0, Mth.floor(this.calculateFallPower(this.fallDistance)));

                if (d1 > 0.0D && !onState.isAir()) {
                    double d2 = this.getX();
                    double d3 = this.getY();
                    double d4 = this.getZ();
                    BlockPos blockpos1 = this.blockPosition();

                    if (pos.getX() != blockpos1.getX() || pos.getZ() != blockpos1.getZ()) {
                        double d5 = d2 - (double) pos.getX() - 0.5D;
                        double d6 = d4 - (double) pos.getZ() - 0.5D;
                        double d7 = Math.max(Math.abs(d5), Math.abs(d6));

                        d2 = (double) pos.getX() + 0.5D + d5 / d7 * 0.5D;
                        d4 = (double) pos.getZ() + 0.5D + d6 / d7 * 0.5D;
                    }

                    double d8 = Math.min((double) 0.2F + d1 / 15.0D, 2.5D);
                    int i = (int) (150.0D * d8);

                    serverlevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, onState), d2, d3, d4, i, 0.0D, 0.0D, 0.0D, (double) 0.15F);
                }
            }
        }

        super.checkFallDamage(ya, onGround, onState, pos);
        if (onGround) {
            this.lastClimbablePos = Optional.empty();
        }

    }

    public boolean canBreatheUnderwater() {
        return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float a) {
        return Mth.lerp(a, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < (double) 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        ServerLevel serverlevel = this.level();

        if (serverlevel instanceof ServerLevel serverlevel1) {
            EnchantmentHelper.tickEffects(serverlevel1, this);
        }

        super.baseTick();
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("livingEntityBaseTick");
        if (this.isAlive()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                serverlevel = (ServerLevel) level;
                boolean flag = this instanceof Player;

                if (this.isInWall()) {
                    this.hurtServer(serverlevel, this.damageSources().inWall(), 1.0F);
                } else if (flag && !serverlevel.getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d0 = serverlevel.getWorldBorder().getDistanceToBorder(this) + serverlevel.getWorldBorder().getSafeZone();

                    if (d0 < 0.0D) {
                        double d1 = serverlevel.getWorldBorder().getDamagePerBlock();

                        if (d1 > 0.0D) {
                            this.hurtServer(serverlevel, this.damageSources().outOfBorder(), (float) Math.max(1, Mth.floor(-d0 * d1)));
                        }
                    }
                }

                if (this.isEyeInFluid(FluidTags.WATER) && !serverlevel.getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                    boolean flag1 = !this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && (!flag || !((Player) this).getAbilities().invulnerable);

                    if (flag1) {
                        this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                        if (this.shouldTakeDrowningDamage()) {
                            this.setAirSupply(0);
                            serverlevel.broadcastEntityEvent(this, (byte) 67);
                            this.hurtServer(serverlevel, this.damageSources().drown(), 2.0F);
                        }
                    } else if (this.getAirSupply() < this.getMaxAirSupply() && MobEffectUtil.shouldEffectsRefillAirsupply(this)) {
                        this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
                    }

                    if (this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                        this.stopRiding();
                    }
                } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                    this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
                }

                BlockPos blockpos = this.blockPosition();

                if (!Objects.equal(this.lastPos, blockpos)) {
                    this.lastPos = blockpos;
                    this.onChangedBlock(serverlevel, blockpos);
                }
            }
        }

        if (this.hurtTime > 0) {
            --this.hurtTime;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            --this.invulnerableTime;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerMemoryTime > 0) {
            --this.lastHurtByPlayerMemoryTime;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        LivingEntity livingentity = this.getLastHurtByMob();

        if (livingentity != null) {
            if (!livingentity.isAlive()) {
                this.setLastHurtByMob((LivingEntity) null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob((LivingEntity) null);
            }
        }

        this.tickEffects();
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        profilerfiller.pop();
    }

    protected boolean shouldTakeDrowningDamage() {
        return this.getAirSupply() <= -20;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return Mth.lerp((float) this.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    public float getLuck() {
        return 0.0F;
    }

    protected void removeFrost() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attributeinstance != null) {
            if (attributeinstance.getModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attributeinstance.removeModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_ID);
            }

        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();

            if (i > 0) {
                AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

                if (attributeinstance == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();

                attributeinstance.addTransientModifier(new AttributeModifier(LivingEntity.SPEED_MODIFIER_POWDER_SNOW_ID, (double) f, AttributeModifier.Operation.ADD_VALUE));
            }
        }

    }

    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        EnchantmentHelper.runLocationChangedEffects(level, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public final float getScale() {
        AttributeMap attributemap = this.getAttributes();

        return attributemap == null ? 1.0F : this.sanitizeScale((float) attributemap.getValue(Attributes.SCALE));
    }

    protected float sanitizeScale(float scale) {
        return scale;
    }

    public boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }

    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot(ServerLevel level) {
        return !this.isBaby() && (Boolean) level.getGameRules().get(GameRules.MOB_DROPS);
    }

    protected int decreaseAirSupply(int currentSupply) {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.OXYGEN_BONUS);
        double d0;

        if (attributeinstance != null) {
            d0 = attributeinstance.getValue();
        } else {
            d0 = 0.0D;
        }

        return d0 > 0.0D && this.random.nextDouble() >= 1.0D / (d0 + 1.0D) ? currentSupply : currentSupply - 1;
    }

    protected int increaseAirSupply(int currentSupply) {
        return Math.min(currentSupply + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(ServerLevel level, @Nullable Entity killer) {
        return EnchantmentHelper.processMobExperience(level, killer, this, this.getBaseExperienceReward(level));
    }

    protected int getBaseExperienceReward(ServerLevel level) {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    public @Nullable LivingEntity getLastHurtByMob() {
        return EntityReference.getLivingEntity(this.lastHurtByMob, this.level());
    }

    public @Nullable Player getLastHurtByPlayer() {
        return EntityReference.getPlayer(this.lastHurtByPlayer, this.level());
    }

    @Override
    public LivingEntity getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(Player player, int timeToRemember) {
        this.setLastHurtByPlayer(EntityReference.of(player), timeToRemember);
    }

    public void setLastHurtByPlayer(UUID player, int timeToRemember) {
        this.setLastHurtByPlayer(EntityReference.of(player), timeToRemember);
    }

    private void setLastHurtByPlayer(EntityReference<Player> player, int timeToRemember) {
        this.lastHurtByPlayer = player;
        this.lastHurtByPlayerMemoryTime = timeToRemember;
    }

    public void setLastHurtByMob(@Nullable LivingEntity hurtBy) {
        this.lastHurtByMob = EntityReference.of(hurtBy);
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    public @Nullable LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity target) {
        if (target instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity) target;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int noActionTime) {
        this.noActionTime = noActionTime;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean discardFriction) {
        this.discardFriction = discardFriction;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return true;
    }

    public void onEquipItem(EquipmentSlot slot, ItemStack oldStack, ItemStack stack) {
        if (!this.level().isClientSide() && !this.isSpectator()) {
            if (!ItemStack.isSameItemSameComponents(oldStack, stack) && !this.firstTick) {
                Equippable equippable = (Equippable) stack.get(DataComponents.EQUIPPABLE);

                if (!this.isSilent() && equippable != null && slot == equippable.slot()) {
                    this.level().playSeededSound((Entity) null, this.getX(), this.getY(), this.getZ(), this.getEquipSound(slot, stack, equippable), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong());
                }

                if (this.doesEmitEquipEvent(slot)) {
                    this.gameEvent(equippable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }

            }
        }
    }

    protected Holder<SoundEvent> getEquipSound(EquipmentSlot slot, ItemStack stack, Equippable equippable) {
        return equippable.equipSound();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.triggerOnDeathMobEffects(serverlevel, reason);
            }
        }

        super.remove(reason);
        this.brain.clearMemories();
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        super.onRemoval(reason);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            serverlevel.getWaypointManager().untrackWaypoint((WaypointTransmitter) this);
        }

    }

    protected void triggerOnDeathMobEffects(ServerLevel level, Entity.RemovalReason reason) {
        for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
            mobeffectinstance.onMobRemoved(level, this, reason);
        }

        this.activeEffects.clear();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("Health", this.getHealth());
        output.putShort("HurtTime", (short) this.hurtTime);
        output.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        output.putShort("DeathTime", (short) this.deathTime);
        output.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        output.store("attributes", AttributeInstance.Packed.LIST_CODEC, this.getAttributes().pack());
        if (!this.activeEffects.isEmpty()) {
            output.store("active_effects", MobEffectInstance.CODEC.listOf(), List.copyOf(this.activeEffects.values()));
        }

        output.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent((blockpos) -> {
            output.store("sleeping_pos", BlockPos.CODEC, blockpos);
        });
        DataResult<Dynamic<?>> dataresult = this.brain.serializeStart(NbtOps.INSTANCE).map((tag) -> {
            return new Dynamic(NbtOps.INSTANCE, tag);
        });
        Logger logger = LivingEntity.LOGGER;

        java.util.Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((dynamic) -> {
            output.store("Brain", Codec.PASSTHROUGH, dynamic);
        });
        if (this.lastHurtByPlayer != null) {
            this.lastHurtByPlayer.store(output, "last_hurt_by_player");
            output.putInt("last_hurt_by_player_memory_time", this.lastHurtByPlayerMemoryTime);
        }

        if (this.lastHurtByMob != null) {
            this.lastHurtByMob.store(output, "last_hurt_by_mob");
            output.putInt("ticks_since_last_hurt_by_mob", this.tickCount - this.lastHurtByMobTimestamp);
        }

        if (!this.equipment.isEmpty()) {
            output.store("equipment", EntityEquipment.CODEC, this.equipment);
        }

        if (this.locatorBarIcon.hasData()) {
            output.store("locator_bar_icon", Waypoint.Icon.CODEC, this.locatorBarIcon);
        }

    }

    public @Nullable ItemEntity drop(ItemStack itemStack, boolean randomly, boolean thrownFromHand) {
        if (itemStack.isEmpty()) {
            return null;
        } else if (this.level().isClientSide()) {
            this.swing(InteractionHand.MAIN_HAND);
            return null;
        } else {
            ItemEntity itementity = this.createItemStackToDrop(itemStack, randomly, thrownFromHand);

            if (itementity != null) {
                this.level().addFreshEntity(itementity);
            }

            return itementity;
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.internalSetAbsorptionAmount(input.getFloatOr("AbsorptionAmount", 0.0F));
        if (this.level() != null && !this.level().isClientSide()) {
            Optional optional = input.read("attributes", AttributeInstance.Packed.LIST_CODEC);
            AttributeMap attributemap = this.getAttributes();

            java.util.Objects.requireNonNull(attributemap);
            optional.ifPresent(attributemap::apply);
        }

        List<MobEffectInstance> list = (List) input.read("active_effects", MobEffectInstance.CODEC.listOf()).orElse(List.of());

        this.activeEffects.clear();

        for (MobEffectInstance mobeffectinstance : list) {
            this.activeEffects.put(mobeffectinstance.getEffect(), mobeffectinstance);
            this.effectsDirty = true;
        }

        this.setHealth(input.getFloatOr("Health", this.getMaxHealth()));
        this.hurtTime = input.getShortOr("HurtTime", (short) 0);
        this.deathTime = input.getShortOr("DeathTime", (short) 0);
        this.lastHurtByMobTimestamp = input.getIntOr("HurtByTimestamp", 0);
        input.getString("Team").ifPresent((s) -> {
            Scoreboard scoreboard = this.level().getScoreboard();
            PlayerTeam playerteam = scoreboard.getPlayerTeam(s);
            boolean flag = playerteam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), playerteam);

            if (!flag) {
                LivingEntity.LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", s);
            }

        });
        this.setSharedFlag(7, input.getBooleanOr("FallFlying", false));
        input.read("sleeping_pos", BlockPos.CODEC).ifPresentOrElse((blockpos) -> {
            this.setSleepingPos(blockpos);
            this.entityData.set(LivingEntity.DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockpos);
            }

        }, this::clearSleepingPos);
        input.read("Brain", Codec.PASSTHROUGH).ifPresent((dynamic) -> {
            this.brain = this.makeBrain(dynamic);
        });
        this.lastHurtByPlayer = EntityReference.<Player>read(input, "last_hurt_by_player");
        this.lastHurtByPlayerMemoryTime = input.getIntOr("last_hurt_by_player_memory_time", 0);
        this.lastHurtByMob = EntityReference.<LivingEntity>read(input, "last_hurt_by_mob");
        this.lastHurtByMobTimestamp = input.getIntOr("ticks_since_last_hurt_by_mob", 0) + this.tickCount;
        this.equipment.setAll((EntityEquipment) input.read("equipment", EntityEquipment.CODEC).orElseGet(EntityEquipment::new));
        this.locatorBarIcon = (Waypoint.Icon) input.read("locator_bar_icon", Waypoint.Icon.CODEC).orElseGet(Waypoint.Icon::new);
    }

    @Override
    public void updateDataBeforeSync() {
        super.updateDataBeforeSync();
        this.updateDirtyEffects();
    }

    protected void tickEffects() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();

            try {
                while (iterator.hasNext()) {
                    Holder<MobEffect> holder = (Holder) iterator.next();
                    MobEffectInstance mobeffectinstance = (MobEffectInstance) this.activeEffects.get(holder);

                    if (!mobeffectinstance.tickServer(serverlevel, this, () -> {
                        this.onEffectUpdated(mobeffectinstance, true, (Entity) null);
                    })) {
                        iterator.remove();
                        this.onEffectsRemoved(List.of(mobeffectinstance));
                    } else if (mobeffectinstance.getDuration() % 600 == 0) {
                        this.onEffectUpdated(mobeffectinstance, false, (Entity) null);
                    }
                }
            } catch (ConcurrentModificationException concurrentmodificationexception) {
                ;
            }
        } else {
            for (MobEffectInstance mobeffectinstance1 : this.activeEffects.values()) {
                mobeffectinstance1.tickClient();
            }

            List<ParticleOptions> list = (List) this.entityData.get(LivingEntity.DATA_EFFECT_PARTICLES);

            if (!list.isEmpty()) {
                boolean flag = (Boolean) this.entityData.get(LivingEntity.DATA_EFFECT_AMBIENCE_ID);
                int i = this.isInvisible() ? 15 : 4;
                int j = flag ? 5 : 1;

                if (this.random.nextInt(i * j) == 0) {
                    this.level().addParticle((ParticleOptions) Util.getRandom(list, this.random), this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 1.0D, 1.0D, 1.0D);
                }
            }
        }

    }

    private void updateDirtyEffects() {
        if (this.effectsDirty) {
            this.updateInvisibilityStatus();
            this.updateGlowingStatus();
            this.effectsDirty = false;
        }

    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleOptions> list = this.activeEffects.values().stream().filter(MobEffectInstance::isVisible).map(MobEffectInstance::getParticleOptions).toList();

        this.entityData.set(LivingEntity.DATA_EFFECT_PARTICLES, list);
        this.entityData.set(LivingEntity.DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean flag = this.isCurrentlyGlowing();

        if (this.getSharedFlag(6) != flag) {
            this.setSharedFlag(6, flag);
        }

    }

    public double getVisibilityPercent(@Nullable Entity targetingEntity) {
        double d0 = 1.0D;

        if (this.isDiscrete()) {
            d0 *= 0.8D;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();

            if (f < 0.1F) {
                f = 0.1F;
            }

            d0 *= 0.7D * (double) f;
        }

        if (targetingEntity != null) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> entitytype = targetingEntity.getType();

            if (entitytype == EntityType.SKELETON && itemstack.is(Items.SKELETON_SKULL) || entitytype == EntityType.ZOMBIE && itemstack.is(Items.ZOMBIE_HEAD) || entitytype == EntityType.PIGLIN && itemstack.is(Items.PIGLIN_HEAD) || entitytype == EntityType.PIGLIN_BRUTE && itemstack.is(Items.PIGLIN_HEAD) || entitytype == EntityType.CREEPER && itemstack.is(Items.CREEPER_HEAD)) {
                d0 *= 0.5D;
            }
        }

        return d0;
    }

    public boolean canAttack(LivingEntity target) {
        return target instanceof Player && this.level().getDifficulty() == Difficulty.PEACEFUL ? false : target.canBeSeenAsEnemy();
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> effects) {
        for (MobEffectInstance mobeffectinstance : effects) {
            if (mobeffectinstance.isVisible() && !mobeffectinstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(LivingEntity.DATA_EFFECT_PARTICLES, List.of());
    }

    public boolean removeAllEffects() {
        if (this.level().isClientSide()) {
            return false;
        } else if (this.activeEffects.isEmpty()) {
            return false;
        } else {
            Map<Holder<MobEffect>, MobEffectInstance> map = Maps.newHashMap(this.activeEffects);

            this.activeEffects.clear();
            this.onEffectsRemoved(map.values());
            return true;
        }
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffect> effect) {
        return this.activeEffects.containsKey(effect);
    }

    public @Nullable MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return (MobEffectInstance) this.activeEffects.get(effect);
    }

    public float getEffectBlendFactor(Holder<MobEffect> effect, float partialTicks) {
        MobEffectInstance mobeffectinstance = this.getEffect(effect);

        return mobeffectinstance != null ? mobeffectinstance.getBlendFactor(this, partialTicks) : 0.0F;
    }

    public final boolean addEffect(MobEffectInstance newEffect) {
        return this.addEffect(newEffect, (Entity) null);
    }

    public boolean addEffect(MobEffectInstance newEffect, @Nullable Entity source) {
        if (!this.canBeAffected(newEffect)) {
            return false;
        } else {
            MobEffectInstance mobeffectinstance1 = (MobEffectInstance) this.activeEffects.get(newEffect.getEffect());
            boolean flag = false;

            if (mobeffectinstance1 == null) {
                this.activeEffects.put(newEffect.getEffect(), newEffect);
                this.onEffectAdded(newEffect, source);
                flag = true;
                newEffect.onEffectAdded(this);
            } else if (mobeffectinstance1.update(newEffect)) {
                this.onEffectUpdated(mobeffectinstance1, true, source);
                flag = true;
            }

            newEffect.onEffectStarted(this);
            return flag;
        }
    }

    public boolean canBeAffected(MobEffectInstance newEffect) {
        return this.getType().is(EntityTypeTags.IMMUNE_TO_INFESTED) ? !newEffect.is(MobEffects.INFESTED) : (this.getType().is(EntityTypeTags.IMMUNE_TO_OOZING) ? !newEffect.is(MobEffects.OOZING) : (!this.getType().is(EntityTypeTags.IGNORES_POISON_AND_REGEN) ? true : !newEffect.is(MobEffects.REGENERATION) && !newEffect.is(MobEffects.POISON)));
    }

    public void forceAddEffect(MobEffectInstance newEffect, @Nullable Entity source) {
        if (this.canBeAffected(newEffect)) {
            MobEffectInstance mobeffectinstance1 = (MobEffectInstance) this.activeEffects.put(newEffect.getEffect(), newEffect);

            if (mobeffectinstance1 == null) {
                this.onEffectAdded(newEffect, source);
            } else {
                newEffect.copyBlendState(mobeffectinstance1);
                this.onEffectUpdated(newEffect, true, source);
            }

        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }

    public final @Nullable MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect) {
        return (MobEffectInstance) this.activeEffects.remove(effect);
    }

    public boolean removeEffect(Holder<MobEffect> effect) {
        MobEffectInstance mobeffectinstance = this.removeEffectNoUpdate(effect);

        if (mobeffectinstance != null) {
            this.onEffectsRemoved(List.of(mobeffectinstance));
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        if (!this.level().isClientSide()) {
            this.effectsDirty = true;
            ((MobEffect) effect.getEffect().value()).addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
            this.sendEffectToPassengers(effect);
        }

    }

    public void sendEffectToPassengers(MobEffectInstance effect) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverplayer) {
                serverplayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect, false));
            }
        }

    }

    protected void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, @Nullable Entity source) {
        if (!this.level().isClientSide()) {
            this.effectsDirty = true;
            if (doRefreshAttributes) {
                MobEffect mobeffect = (MobEffect) effect.getEffect().value();

                mobeffect.removeAttributeModifiers(this.getAttributes());
                mobeffect.addAttributeModifiers(this.getAttributes(), effect.getAmplifier());
                this.refreshDirtyAttributes();
            }

            this.sendEffectToPassengers(effect);
        }
    }

    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        if (!this.level().isClientSide()) {
            this.effectsDirty = true;

            for (MobEffectInstance mobeffectinstance : effects) {
                ((MobEffect) mobeffectinstance.getEffect().value()).removeAttributeModifiers(this.getAttributes());

                for (Entity entity : this.getPassengers()) {
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) entity;

                        serverplayer.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), mobeffectinstance.getEffect()));
                    }
                }
            }

            this.refreshDirtyAttributes();
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeInstance> set = this.getAttributes().getAttributesToUpdate();

        for (AttributeInstance attributeinstance : set) {
            this.onAttributeUpdated(attributeinstance.getAttribute());
        }

        set.clear();
    }

    protected void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.MAX_HEALTH)) {
            float f = this.getMaxHealth();

            if (this.getHealth() > f) {
                this.setHealth(f);
            }
        } else if (attribute.is(Attributes.MAX_ABSORPTION)) {
            float f1 = this.getMaxAbsorption();

            if (this.getAbsorptionAmount() > f1) {
                this.setAbsorptionAmount(f1);
            }
        } else if (attribute.is(Attributes.SCALE)) {
            this.refreshDimensions();
        } else if (attribute.is(Attributes.WAYPOINT_TRANSMIT_RANGE)) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                ServerWaypointManager serverwaypointmanager = serverlevel.getWaypointManager();

                if (this.attributes.getValue(attribute) > 0.0D) {
                    serverwaypointmanager.trackWaypoint((WaypointTransmitter) this);
                } else {
                    serverwaypointmanager.untrackWaypoint((WaypointTransmitter) this);
                }
            }
        }

    }

    public void heal(float heal) {
        float f1 = this.getHealth();

        if (f1 > 0.0F) {
            this.setHealth(f1 + heal);
        }

    }

    public float getHealth() {
        return (Float) this.entityData.get(LivingEntity.DATA_HEALTH_ID);
    }

    public void setHealth(float health) {
        this.entityData.set(LivingEntity.DATA_HEALTH_ID, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (source.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else {
            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            if (damage < 0.0F) {
                damage = 0.0F;
            }

            float f1 = damage;
            ItemStack itemstack = this.getUseItem();
            float f2 = this.applyItemBlocking(level, source, damage);

            damage -= f2;
            boolean flag = f2 > 0.0F;

            if (source.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                damage *= 5.0F;
            }

            if (source.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(source, damage);
                damage *= 0.75F;
            }

            if (Float.isNaN(damage) || Float.isInfinite(damage)) {
                damage = Float.MAX_VALUE;
            }

            boolean flag1 = true;

            if ((float) this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (damage <= this.lastHurt) {
                    return false;
                }

                this.actuallyHurt(level, source, damage - this.lastHurt);
                this.lastHurt = damage;
                flag1 = false;
            } else {
                this.lastHurt = damage;
                this.invulnerableTime = 20;
                this.actuallyHurt(level, source, damage);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }

            this.resolveMobResponsibleForDamage(source);
            this.resolvePlayerResponsibleForDamage(source);
            if (flag1) {
                BlocksAttacks blocksattacks = (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS);

                if (flag && blocksattacks != null) {
                    blocksattacks.onBlocked(level, this);
                } else {
                    level.broadcastDamageEvent(this, source);
                }

                if (!source.is(DamageTypeTags.NO_IMPACT) && (!flag || damage > 0.0F)) {
                    this.markHurt();
                }

                if (!source.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double d0 = 0.0D;
                    double d1 = 0.0D;
                    Entity entity = source.getDirectEntity();

                    if (entity instanceof Projectile) {
                        Projectile projectile = (Projectile) entity;
                        DoubleDoubleImmutablePair doubledoubleimmutablepair = projectile.calculateHorizontalHurtKnockbackDirection(this, source);

                        d0 = -doubledoubleimmutablepair.leftDouble();
                        d1 = -doubledoubleimmutablepair.rightDouble();
                    } else if (source.getSourcePosition() != null) {
                        d0 = source.getSourcePosition().x() - this.getX();
                        d1 = source.getSourcePosition().z() - this.getZ();
                    }

                    this.knockback((double) 0.4F, d0, d1);
                    if (!flag) {
                        this.indicateDamage(d0, d1);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                if (!this.checkTotemDeathProtection(source)) {
                    if (flag1) {
                        this.makeSound(this.getDeathSound());
                        this.playSecondaryHurtSound(source);
                    }

                    this.die(source);
                }
            } else if (flag1) {
                this.playHurtSound(source);
                this.playSecondaryHurtSound(source);
            }

            boolean flag2 = !flag || damage > 0.0F;

            if (flag2) {
                this.lastDamageSource = source;
                this.lastDamageStamp = this.level().getGameTime();

                for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
                    mobeffectinstance.onMobHurt(level, this, source, damage);
                }
            }

            if (this instanceof ServerPlayer) {
                ServerPlayer serverplayer = (ServerPlayer) this;

                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverplayer, source, f1, damage, flag);
                if (f2 > 0.0F && f2 < 3.4028235E37F) {
                    serverplayer.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(f2 * 10.0F));
                }
            }

            Entity entity1 = source.getEntity();

            if (entity1 instanceof ServerPlayer) {
                ServerPlayer serverplayer1 = (ServerPlayer) entity1;

                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverplayer1, this, source, f1, damage, flag);
            }

            return flag2;
        }
    }

    public float applyItemBlocking(ServerLevel level, DamageSource source, float damage) {
        if (damage <= 0.0F) {
            return 0.0F;
        } else {
            ItemStack itemstack = this.getItemBlockingWith();

            if (itemstack == null) {
                return 0.0F;
            } else {
                BlocksAttacks blocksattacks = (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS);

                if (blocksattacks != null) {
                    Optional optional = blocksattacks.bypassedBy();

                    java.util.Objects.requireNonNull(source);
                    if (!(Boolean) optional.map(source::is).orElse(false)) {
                        Entity entity = source.getDirectEntity();

                        if (entity instanceof AbstractArrow) {
                            AbstractArrow abstractarrow = (AbstractArrow) entity;

                            if (abstractarrow.getPierceLevel() > 0) {
                                return 0.0F;
                            }
                        }

                        Vec3 vec3 = source.getSourcePosition();
                        double d0;

                        if (vec3 != null) {
                            Vec3 vec31 = this.calculateViewVector(0.0F, this.getYHeadRot());
                            Vec3 vec32 = vec3.subtract(this.position());

                            vec32 = (new Vec3(vec32.x, 0.0D, vec32.z)).normalize();
                            d0 = Math.acos(vec32.dot(vec31));
                        } else {
                            d0 = (double) (float) Math.PI;
                        }

                        float f1 = blocksattacks.resolveBlockedDamage(source, damage, d0);

                        blocksattacks.hurtBlockingItem(this.level(), itemstack, this, this.getUsedItemHand(), f1);
                        if (f1 > 0.0F && !source.is(DamageTypeTags.IS_PROJECTILE)) {
                            Entity entity1 = source.getDirectEntity();

                            if (entity1 instanceof LivingEntity) {
                                LivingEntity livingentity = (LivingEntity) entity1;

                                this.blockUsingItem(level, livingentity);
                            }
                        }

                        return f1;
                    }
                }

                return 0.0F;
            }
        }
    }

    private void playSecondaryHurtSound(DamageSource source) {
        if (source.is(DamageTypes.THORNS)) {
            SoundSource soundsource = this instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;

            this.level().playSound((Entity) null, this.position().x, this.position().y, this.position().z, SoundEvents.THORNS_HIT, soundsource);
        }

    }

    protected void resolveMobResponsibleForDamage(DamageSource source) {
        Entity entity = source.getEntity();

        if (entity instanceof LivingEntity livingentity) {
            if (!source.is(DamageTypeTags.NO_ANGER) && (!source.is(DamageTypes.WIND_CHARGE) || !this.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
                this.setLastHurtByMob(livingentity);
            }
        }

    }

    protected @Nullable Player resolvePlayerResponsibleForDamage(DamageSource source) {
        Entity entity = source.getEntity();

        if (entity instanceof Player player) {
            this.setLastHurtByPlayer(player, 100);
        } else if (entity instanceof Wolf wolf) {
            if (wolf.isTame()) {
                if (wolf.getOwnerReference() != null) {
                    this.setLastHurtByPlayer(wolf.getOwnerReference().getUUID(), 100);
                } else {
                    this.lastHurtByPlayer = null;
                    this.lastHurtByPlayerMemoryTime = 0;
                }
            }
        }

        return EntityReference.getPlayer(this.lastHurtByPlayer, this.level());
    }

    protected void blockUsingItem(ServerLevel level, LivingEntity attacker) {
        attacker.blockedByItem(this);
    }

    protected void blockedByItem(LivingEntity defender) {
        defender.knockback(0.5D, defender.getX() - this.getX(), defender.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource killingDamage) {
        if (killingDamage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            ItemStack itemstack = null;
            DeathProtection deathprotection = null;

            for (InteractionHand interactionhand : InteractionHand.values()) {
                ItemStack itemstack1 = this.getItemInHand(interactionhand);

                deathprotection = (DeathProtection) itemstack1.get(DataComponents.DEATH_PROTECTION);
                if (deathprotection != null) {
                    itemstack = itemstack1.copy();
                    itemstack1.shrink(1);
                    break;
                }
            }

            if (itemstack != null) {
                if (this instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) this;

                    serverplayer.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
                    CriteriaTriggers.USED_TOTEM.trigger(serverplayer, itemstack);
                    itemstack.causeUseVibration(this, GameEvent.ITEM_INTERACT_FINISH);
                }

                this.setHealth(1.0F);
                deathprotection.applyEffects(itemstack, this);
                this.level().broadcastEntityEvent(this, (byte) 35);
            }

            return deathprotection != null;
        }
    }

    public @Nullable DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource source) {
        this.makeSound(this.getHurtSound(source));
    }

    public void makeSound(@Nullable SoundEvent sound) {
        if (sound != null) {
            this.playSound(sound, this.getSoundVolume(), this.getVoicePitch());
        }

    }

    private void breakItem(ItemStack itemStack) {
        if (!itemStack.isEmpty()) {
            Holder<SoundEvent> holder = (Holder) itemStack.get(DataComponents.BREAK_SOUND);

            if (holder != null && !this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), holder.value(), this.getSoundSource(), 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F, false);
            }

            this.spawnItemParticles(itemStack, 5);
        }

    }

    public void die(DamageSource source) {
        if (!this.isRemoved() && !this.dead) {
            Entity entity = source.getEntity();
            LivingEntity livingentity = this.getKillCredit();

            if (livingentity != null) {
                livingentity.awardKillScore(this, source);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.stopUsingItem();
            if (!this.level().isClientSide() && this.hasCustomName()) {
                LivingEntity.LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString());
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (entity == null || entity.killedEntity(serverlevel, this, source)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(serverlevel, source);
                    this.createWitherRose(livingentity);
                }

                this.level().broadcastEntityEvent(this, (byte) 3);
            }

            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity killer) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            boolean flag = false;

            if (killer instanceof WitherBoss) {
                if ((Boolean) serverlevel.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    BlockPos blockpos = this.blockPosition();
                    BlockState blockstate = Blocks.WITHER_ROSE.defaultBlockState();

                    if (this.level().getBlockState(blockpos).isAir() && blockstate.canSurvive(this.level(), blockpos)) {
                        this.level().setBlock(blockpos, blockstate, 3);
                        flag = true;
                    }
                }

                if (!flag) {
                    ItemEntity itementity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));

                    this.level().addFreshEntity(itementity);
                }
            }

        }
    }

    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        boolean flag = this.lastHurtByPlayerMemoryTime > 0;

        if (this.shouldDropLoot(level)) {
            this.dropFromLootTable(level, source, flag);
            this.dropCustomDeathLoot(level, source, flag);
        }

        this.dropEquipment(level);
        this.dropExperience(level, source.getEntity());
    }

    protected void dropEquipment(ServerLevel level) {}

    protected void dropExperience(ServerLevel level, @Nullable Entity killer) {
        if (!this.wasExperienceConsumed() && (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerMemoryTime > 0 && this.shouldDropExperience() && (Boolean) level.getGameRules().get(GameRules.MOB_DROPS))) {
            ExperienceOrb.award(level, this.position(), this.getExperienceReward(level, killer));
        }

    }

    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {}

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity target, DamageSource damageSource) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            return EnchantmentHelper.modifyKnockback(serverlevel, this.getWeaponItem(), target, damageSource, f) / 2.0F;
        } else {
            return f / 2.0F;
        }
    }

    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled) {
        Optional<ResourceKey<LootTable>> optional = this.getLootTable();

        if (!optional.isEmpty()) {
            this.dropFromLootTable(level, source, playerKilled, (ResourceKey) optional.get());
        }
    }

    public void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled, ResourceKey<LootTable> lootTable) {
        this.dropFromLootTable(level, source, playerKilled, lootTable, (itemstack) -> {
            this.spawnAtLocation(level, itemstack);
        });
    }

    public void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled, ResourceKey<LootTable> lootTable, Consumer<ItemStack> itemStackConsumer) {
        LootTable loottable = level.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams.Builder lootparams_builder = (new LootParams.Builder(level)).withParameter(LootContextParams.THIS_ENTITY, this).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.DAMAGE_SOURCE, source).withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity()).withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
        Player player = this.getLastHurtByPlayer();

        if (playerKilled && player != null) {
            lootparams_builder = lootparams_builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
        }

        LootParams lootparams = lootparams_builder.create(LootContextParamSets.ENTITY);

        loottable.getRandomItems(lootparams, this.getLootTableSeed(), itemStackConsumer);
    }

    public boolean dropFromEntityInteractLootTable(ServerLevel level, ResourceKey<LootTable> key, @Nullable Entity interactingEntity, ItemStack tool, BiConsumer<ServerLevel, ItemStack> consumer) {
        return this.dropFromLootTable(level, key, (lootparams_builder) -> {
            return lootparams_builder.withParameter(LootContextParams.TARGET_ENTITY, this).withOptionalParameter(LootContextParams.INTERACTING_ENTITY, interactingEntity).withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.ENTITY_INTERACT);
        }, consumer);
    }

    public boolean dropFromGiftLootTable(ServerLevel level, ResourceKey<LootTable> key, BiConsumer<ServerLevel, ItemStack> consumer) {
        return this.dropFromLootTable(level, key, (lootparams_builder) -> {
            return lootparams_builder.withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.GIFT);
        }, consumer);
    }

    protected void dropFromShearingLootTable(ServerLevel level, ResourceKey<LootTable> key, ItemStack tool, BiConsumer<ServerLevel, ItemStack> consumer) {
        this.dropFromLootTable(level, key, (lootparams_builder) -> {
            return lootparams_builder.withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.THIS_ENTITY, this).withParameter(LootContextParams.TOOL, tool).create(LootContextParamSets.SHEARING);
        }, consumer);
    }

    protected boolean dropFromLootTable(ServerLevel level, ResourceKey<LootTable> key, Function<LootParams.Builder, LootParams> paramsBuilder, BiConsumer<ServerLevel, ItemStack> consumer) {
        LootTable loottable = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams lootparams = (LootParams) paramsBuilder.apply(new LootParams.Builder(level));
        List<ItemStack> list = loottable.getRandomItems(lootparams);

        if (!list.isEmpty()) {
            list.forEach((itemstack) -> {
                consumer.accept(level, itemstack);
            });
            return true;
        } else {
            return false;
        }
    }

    public void knockback(double power, double xd, double zd) {
        power *= 1.0D - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (power > 0.0D) {
            this.needsSync = true;

            Vec3 vec3;

            for (vec3 = this.getDeltaMovement(); xd * xd + zd * zd < (double) 1.0E-5F; zd = (this.random.nextDouble() - this.random.nextDouble()) * 0.01D) {
                xd = (this.random.nextDouble() - this.random.nextDouble()) * 0.01D;
            }

            Vec3 vec31 = (new Vec3(xd, 0.0D, zd)).normalize().scale(power);

            this.setDeltaMovement(vec3.x / 2.0D - vec31.x, this.onGround() ? Math.min(0.4D, vec3.y / 2.0D + power) : vec3.y, vec3.z / 2.0D - vec31.z);
        }
    }

    public void indicateDamage(double xd, double zd) {}

    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GENERIC_HURT;
    }

    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int dmg) {
        return dmg > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    protected AABB getHitbox() {
        AABB aabb = this.getBoundingBox();
        Entity entity = this.getVehicle();

        if (entity != null) {
            Vec3 vec3 = entity.getPassengerRidingPosition(this);

            return aabb.setMinY(Math.max(vec3.y, aabb.minY));
        } else {
            return aabb;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments(EquipmentSlot slot) {
        return (Map) this.activeLocationDependentEnchantments.computeIfAbsent(slot, (equipmentslot1) -> {
            return new Reference2ObjectArrayMap();
        });
    }

    public void lungeForwardMaybe() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            EnchantmentHelper.doLungeEffects(serverlevel, this);
        }

    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockpos = this.blockPosition();
            BlockState blockstate = this.getInBlockState();

            if (this.isFallFlying() && blockstate.is(BlockTags.CAN_GLIDE_THROUGH)) {
                return false;
            } else if (blockstate.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockpos);
                return true;
            } else if (blockstate.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockpos, blockstate)) {
                this.lastClimbablePos = Optional.of(blockpos);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos pos, BlockState state) {
        if (!(Boolean) state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        } else {
            BlockState blockstate1 = this.level().getBlockState(pos.below());

            return blockstate1.is(Blocks.LADDER) && blockstate1.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
        }
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    public boolean isLookingAtMe(LivingEntity target, double coneSize, boolean adjustForDistance, boolean seeThroughTransparentBlocks, double... gazeHeights) {
        Vec3 vec3 = target.getViewVector(1.0F).normalize();

        for (double d1 : gazeHeights) {
            Vec3 vec31 = new Vec3(this.getX() - target.getX(), d1 - target.getEyeY(), this.getZ() - target.getZ());
            double d2 = vec31.length();

            vec31 = vec31.normalize();
            double d3 = vec3.dot(vec31);

            if (d3 > 1.0D - coneSize / (adjustForDistance ? d2 : 1.0D) && target.hasLineOfSight(this, seeThroughTransparentBlocks ? ClipContext.Block.VISUAL : ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d1)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float allowedDamage) {
        return Mth.floor(allowedDamage + 3.0F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        boolean flag = super.causeFallDamage(fallDistance, damageModifier, damageSource);
        int i = this.calculateFallDamage(fallDistance, damageModifier);

        if (i > 0) {
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            this.hurt(damageSource, (float) i);
            return true;
        } else {
            return flag;
        }
    }

    protected int calculateFallDamage(double fallDistance, float damageModifier) {
        if (this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return 0;
        } else {
            double d1 = this.calculateFallPower(fallDistance);

            return Mth.floor(d1 * (double) damageModifier * this.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        }
    }

    private double calculateFallPower(double fallDistance) {
        return fallDistance + 1.0E-6D - this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - (double) 0.2F);
            int k = Mth.floor(this.getZ());
            BlockState blockstate = this.level().getBlockState(new BlockPos(i, j, k));

            if (!blockstate.isAir()) {
                SoundType soundtype = blockstate.getSoundType();

                this.playSound(soundtype.getFallSound(), soundtype.getVolume() * 0.5F, soundtype.getPitch() * 0.75F);
            }

        }
    }

    @Override
    public void animateHurt(float yaw) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damageSource, float damage) {}

    protected void hurtHelmet(DamageSource damageSource, float damage) {}

    protected void doHurtEquipment(DamageSource damageSource, float damage, EquipmentSlot... slots) {
        if (damage > 0.0F) {
            int i = (int) Math.max(1.0F, damage / 4.0F);

            for (EquipmentSlot equipmentslot : slots) {
                ItemStack itemstack = this.getItemBySlot(equipmentslot);
                Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

                if (equippable != null && equippable.damageOnHurt() && itemstack.isDamageableItem() && itemstack.canBeHurtBy(damageSource)) {
                    itemstack.hurtAndBreak(i, this, equipmentslot);
                }
            }

        }
    }

    protected float getDamageAfterArmorAbsorb(DamageSource damageSource, float damage) {
        if (!damageSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
            this.hurtArmor(damageSource, damage);
            damage = CombatRules.getDamageAfterAbsorb(this, damage, damageSource, (float) this.getArmorValue(), (float) this.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        return damage;
    }

    protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damage) {
        if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return damage;
        } else {
            if (this.hasEffect(MobEffects.RESISTANCE) && !damageSource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
                int i = (this.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
                int j = 25 - i;
                float f1 = damage * (float) j;
                float f2 = damage;

                damage = Math.max(f1 / 25.0F, 0.0F);
                float f3 = f2 - damage;

                if (f3 > 0.0F && f3 < 3.4028235E37F) {
                    if (this instanceof ServerPlayer) {
                        ((ServerPlayer) this).awardStat(Stats.DAMAGE_RESISTED, Math.round(f3 * 10.0F));
                    } else if (damageSource.getEntity() instanceof ServerPlayer) {
                        ((ServerPlayer) damageSource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f3 * 10.0F));
                    }
                }
            }

            if (damage <= 0.0F) {
                return 0.0F;
            } else if (damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                return damage;
            } else {
                Level level = this.level();
                float f4;

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    f4 = EnchantmentHelper.getDamageProtection(serverlevel, this, damageSource);
                } else {
                    f4 = 0.0F;
                }

                if (f4 > 0.0F) {
                    damage = CombatRules.getDamageAfterMagicAbsorb(damage, f4);
                }

                return damage;
            }
        }
    }

    protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
        if (!this.isInvulnerableTo(level, source)) {
            dmg = this.getDamageAfterArmorAbsorb(source, dmg);
            dmg = this.getDamageAfterMagicAbsorb(source, dmg);
            float f1 = dmg;

            dmg = Math.max(dmg - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f1 - dmg));
            float f2 = f1 - dmg;

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                Entity entity = source.getEntity();

                if (entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) entity;

                    serverplayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f2 * 10.0F));
                }
            }

            if (dmg != 0.0F) {
                this.getCombatTracker().recordDamage(source, dmg);
                this.setHealth(this.getHealth() - dmg);
                this.setAbsorptionAmount(this.getAbsorptionAmount() - dmg);
                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    public @Nullable LivingEntity getKillCredit() {
        return this.lastHurtByPlayer != null ? (LivingEntity) this.lastHurtByPlayer.getEntity(this.level(), Player.class) : (this.lastHurtByMob != null ? (LivingEntity) this.lastHurtByMob.getEntity(this.level(), LivingEntity.class) : null);
    }

    public final float getMaxHealth() {
        return (float) this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float) this.getAttributeValue(Attributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return (Integer) this.entityData.get(LivingEntity.DATA_ARROW_COUNT_ID);
    }

    public final void setArrowCount(int count) {
        this.entityData.set(LivingEntity.DATA_ARROW_COUNT_ID, count);
    }

    public final int getStingerCount() {
        return (Integer) this.entityData.get(LivingEntity.DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int count) {
        this.entityData.set(LivingEntity.DATA_STINGER_COUNT_ID, count);
    }

    private int getCurrentSwingDuration() {
        ItemStack itemstack = this.getItemInHand(InteractionHand.MAIN_HAND);
        int i = itemstack.getSwingAnimation().duration();

        return MobEffectUtil.hasDigSpeed(this) ? i - (1 + MobEffectUtil.getDigSpeedAmplification(this)) : (this.hasEffect(MobEffects.MINING_FATIGUE) ? i + (1 + this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2 : i);
    }

    public void swing(InteractionHand hand) {
        this.swing(hand, false);
    }

    public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = hand;
            if (this.level() instanceof ServerLevel) {
                ClientboundAnimatePacket clientboundanimatepacket = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache serverchunkcache = ((ServerLevel) this.level()).getChunkSource();

                if (sendToSwingingEntity) {
                    serverchunkcache.sendToTrackingPlayersAndSelf(this, clientboundanimatepacket);
                } else {
                    serverchunkcache.sendToTrackingPlayers(this, clientboundanimatepacket);
                }
            }
        }

    }

    @Override
    public void handleDamageEvent(DamageSource source) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEvent soundevent = this.getHurtSound(source);

        if (soundevent != null) {
            this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.lastDamageSource = source;
        this.lastDamageStamp = this.level().getGameTime();
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 2:
                this.onKineticHit();
                break;
            case 3:
                SoundEvent soundevent = this.getDeathSound();

                if (soundevent != null) {
                    this.playSound(soundevent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof Player)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 46:
                int i = 128;

                for (int j = 0; j < 128; ++j) {
                    double d0 = (double) j / 127.0D;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double d1 = Mth.lerp(d0, this.xo, this.getX()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;
                    double d2 = Mth.lerp(d0, this.yo, this.getY()) + this.random.nextDouble() * (double) this.getBbHeight();
                    double d3 = Mth.lerp(d0, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth() * 2.0D;

                    this.level().addParticle(ParticleTypes.PORTAL, d1, d2, d3, (double) f, (double) f1, (double) f2);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EquipmentSlot.BODY));
                break;
            case 67:
                this.makeDrownParticles();
                break;
            case 68:
                this.breakItem(this.getItemBySlot(EquipmentSlot.SADDLE));
                break;
            default:
                super.handleEntityEvent(id);
        }

    }

    public float getTicksSinceLastKineticHitFeedback(float partial) {
        return this.lastKineticHitFeedbackTime < 0L ? 0.0F : (float) (this.level().getGameTime() - this.lastKineticHitFeedbackTime) + partial;
    }

    public void makePoofParticles() {
        for (int i = 0; i < 20; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;
            double d3 = 10.0D;

            this.level().addParticle(ParticleTypes.POOF, this.getRandomX(1.0D) - d0 * 10.0D, this.getRandomY() - d1 * 10.0D, this.getRandomZ(1.0D) - d2 * 10.0D, d0, d1, d2);
        }

    }

    private void makeDrownParticles() {
        Vec3 vec3 = this.getDeltaMovement();

        for (int i = 0; i < 8; ++i) {
            double d0 = this.random.triangle(0.0D, 1.0D);
            double d1 = this.random.triangle(0.0D, 1.0D);
            double d2 = this.random.triangle(0.0D, 1.0D);

            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d0, this.getY() + d1, this.getZ() + d2, vec3.x, vec3.y, vec3.z);
        }

    }

    private void onKineticHit() {
        if (this.level().getGameTime() - this.lastKineticHitFeedbackTime > 10L) {
            this.lastKineticHitFeedbackTime = this.level().getGameTime();
            KineticWeapon kineticweapon = (KineticWeapon) this.useItem.get(DataComponents.KINETIC_WEAPON);

            if (kineticweapon != null) {
                kineticweapon.makeLocalHitSound(this);
            }
        }
    }

    private void swapHandItems() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.OFFHAND);

        this.setItemSlot(EquipmentSlot.OFFHAND, this.getItemBySlot(EquipmentSlot.MAINHAND));
        this.setItemSlot(EquipmentSlot.MAINHAND, itemstack);
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();

        if (this.swinging) {
            ++this.swingTime;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float) this.swingTime / (float) i;
    }

    public @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return this.getAttributes().getInstance(attribute);
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return this.getAttributes().getValue(attribute);
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return this.getAttributes().getBaseValue(attribute);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    public ItemStack getItemHeldByArm(HumanoidArm arm) {
        return this.getMainArm() == arm ? this.getMainHandItem() : this.getOffhandItem();
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public AttackRange entityAttackRange() {
        AttackRange attackrange = (AttackRange) this.getActiveItem().get(DataComponents.ATTACK_RANGE);

        return attackrange != null ? attackrange : AttackRange.defaultFor(this);
    }

    public ItemStack getActiveItem() {
        return this.isUsingItem() ? this.getUseItem() : this.getMainHandItem();
    }

    public boolean isHolding(Item item) {
        return this.isHolding((itemstack) -> {
            return itemstack.is(item);
        });
    }

    public boolean isHolding(Predicate<ItemStack> itemPredicate) {
        return itemPredicate.test(this.getMainHandItem()) || itemPredicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (hand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + String.valueOf(hand));
        }
    }

    public void setItemInHand(InteractionHand hand, ItemStack itemStack) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
        } else {
            if (hand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + String.valueOf(hand));
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, itemStack);
        }

    }

    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !this.getItemBySlot(slot).isEmpty();
    }

    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return this.equipment.get(slot);
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack itemStack) {
        this.onEquipItem(slot, this.equipment.set(slot, itemStack), itemStack);
    }

    public float getArmorCoverPercentage() {
        int i = 0;
        int j = 0;

        for (EquipmentSlot equipmentslot : EquipmentSlotGroup.ARMOR) {
            if (equipmentslot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack itemstack = this.getItemBySlot(equipmentslot);

                if (!itemstack.isEmpty()) {
                    ++j;
                }

                ++i;
            }
        }

        return i > 0 ? (float) j / (float) i : 0.0F;
    }

    @Override
    public void setSprinting(boolean isSprinting) {
        super.setSprinting(isSprinting);
        AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);

        attributeinstance.removeModifier(LivingEntity.SPEED_MODIFIER_SPRINTING.id());
        if (isSprinting) {
            attributeinstance.addTransientModifier(LivingEntity.SPEED_MODIFIER_SPRINTING);
        }

    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }

    }

    private void dismountVehicle(Entity vehicle) {
        Vec3 vec3;

        if (this.isRemoved()) {
            vec3 = this.position();
        } else if (!vehicle.isRemoved() && !this.level().getBlockState(vehicle.blockPosition()).is(BlockTags.PORTALS)) {
            vec3 = vehicle.getDismountLocationForPassenger(this);
        } else {
            double d0 = Math.max(this.getY(), vehicle.getY());

            vec3 = new Vec3(this.getX(), d0, this.getZ());
            boolean flag = this.getBbWidth() <= 4.0F && this.getBbHeight() <= 4.0F;

            if (flag) {
                double d1 = (double) this.getBbHeight() / 2.0D;
                Vec3 vec31 = vec3.add(0.0D, d1, 0.0D);
                VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec31, (double) this.getBbWidth(), (double) this.getBbHeight(), (double) this.getBbWidth()));

                vec3 = (Vec3) this.level().findFreePosition(this, voxelshape, vec31, (double) this.getBbWidth(), (double) this.getBbHeight(), (double) this.getBbWidth()).map((vec32) -> {
                    return vec32.add(0.0D, -d1, 0.0D);
                }).orElse(vec3);
            }
        }

        this.dismountTo(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float multiplier) {
        return (float) this.getAttributeValue(Attributes.JUMP_STRENGTH) * multiplier * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP_BOOST) ? 0.1F * ((float) this.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1.0F) : 0.0F;
    }

    @VisibleForTesting
    public void jumpFromGround() {
        float f = this.getJumpPower();

        if (f > 1.0E-5F) {
            Vec3 vec3 = this.getDeltaMovement();

            this.setDeltaMovement(vec3.x, Math.max((double) f, vec3.y), vec3.z);
            if (this.isSprinting()) {
                float f1 = this.getYRot() * ((float) Math.PI / 180F);

                this.addDeltaMovement(new Vec3((double) (-Mth.sin((double) f1)) * 0.2D, 0.0D, (double) Mth.cos((double) f1) * 0.2D));
            }

            this.needsSync = true;
        }
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) -0.04F, 0.0D));
    }

    protected void jumpInLiquid(TagKey<Fluid> type) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) 0.04F, 0.0D));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState fluid) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(Attributes.GRAVITY);
    }

    protected double getEffectiveGravity() {
        boolean flag = this.getDeltaMovement().y <= 0.0D;

        return flag && this.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(this.getGravity(), 0.01D) : this.getGravity();
    }

    public void travel(Vec3 input) {
        if (this.shouldTravelInFluid(this.level().getFluidState(this.blockPosition()))) {
            this.travelInFluid(input);
        } else if (this.isFallFlying()) {
            this.travelFallFlying(input);
        } else {
            this.travelInAir(input);
        }

    }

    protected boolean shouldTravelInFluid(FluidState fluidState) {
        return (this.isInWater() || this.isInLava()) && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState);
    }

    protected void travelFlying(Vec3 input, float speed) {
        this.travelFlying(input, 0.02F, 0.02F, speed);
    }

    protected void travelFlying(Vec3 input, float waterSpeed, float lavaSpeed, float airSpeed) {
        if (this.isInWater()) {
            this.moveRelative(waterSpeed, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale((double) 0.8F));
        } else if (this.isInLava()) {
            this.moveRelative(lavaSpeed, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
        } else {
            this.moveRelative(airSpeed, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale((double) 0.91F));
        }

    }

    private void travelInAir(Vec3 input) {
        BlockPos blockpos = this.getBlockPosBelowThatAffectsMyMovement();
        float f = this.onGround() ? this.level().getBlockState(blockpos).getBlock().getFriction() : 1.0F;
        float f1 = f * 0.91F;
        Vec3 vec31 = this.handleRelativeFrictionAndCalculateMovement(input, f);
        double d0 = vec31.y;
        MobEffectInstance mobeffectinstance = this.getEffect(MobEffects.LEVITATION);

        if (mobeffectinstance != null) {
            d0 += (0.05D * (double) (mobeffectinstance.getAmplifier() + 1) - vec31.y) * 0.2D;
        } else if (this.level().isClientSide() && !this.level().hasChunkAt(blockpos)) {
            if (this.getY() > (double) this.level().getMinY()) {
                d0 = -0.1D;
            } else {
                d0 = 0.0D;
            }
        } else {
            d0 -= this.getEffectiveGravity();
        }

        if (this.shouldDiscardFriction()) {
            this.setDeltaMovement(vec31.x, d0, vec31.z);
        } else {
            float f2 = this instanceof FlyingAnimal ? f1 : 0.98F;

            this.setDeltaMovement(vec31.x * (double) f1, d0 * (double) f2, vec31.z * (double) f1);
        }

    }

    private void travelInFluid(Vec3 input) {
        boolean flag = this.getDeltaMovement().y <= 0.0D;
        double d0 = this.getY();
        double d1 = this.getEffectiveGravity();

        if (this.isInWater()) {
            this.travelInWater(input, d1, flag, d0);
            this.floatInWaterWhileRidden();
        } else {
            this.travelInLava(input, d1, flag, d0);
        }

    }

    protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        float f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
        float f1 = 0.02F;
        float f2 = (float) this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);

        if (!this.onGround()) {
            f2 *= 0.5F;
        }

        if (f2 > 0.0F) {
            f += (0.54600006F - f) * f2;
            f1 += (this.getSpeed() - f1) * f2;
        }

        if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
            f = 0.96F;
        }

        this.moveRelative(f1, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec31 = this.getDeltaMovement();

        if (this.horizontalCollision && this.onClimbable()) {
            vec31 = new Vec3(vec31.x, 0.2D, vec31.z);
        }

        vec31 = vec31.multiply((double) f, (double) 0.8F, (double) f);
        this.setDeltaMovement(this.getFluidFallingAdjustedMovement(baseGravity, isFalling, vec31));
        this.jumpOutOfFluid(oldY);
    }

    private void travelInLava(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
        this.moveRelative(0.02F, input);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.5D, (double) 0.8F, 0.5D));
            Vec3 vec31 = this.getFluidFallingAdjustedMovement(baseGravity, isFalling, this.getDeltaMovement());

            this.setDeltaMovement(vec31);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
        }

        if (baseGravity != 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -baseGravity / 4.0D, 0.0D));
        }

        this.jumpOutOfFluid(oldY);
    }

    private void jumpOutOfFluid(double oldY) {
        Vec3 vec3 = this.getDeltaMovement();

        if (this.horizontalCollision && this.isFree(vec3.x, vec3.y + (double) 0.6F - this.getY() + oldY, vec3.z)) {
            this.setDeltaMovement(vec3.x, (double) 0.3F, vec3.z);
        }

    }

    private void floatInWaterWhileRidden() {
        boolean flag = this.getType().is(EntityTypeTags.CAN_FLOAT_WHILE_RIDDEN);

        if (flag && this.isVehicle() && this.getFluidHeight(FluidTags.WATER) > this.getFluidJumpThreshold()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) 0.04F, 0.0D));
        }

    }

    private void travelFallFlying(Vec3 input) {
        if (this.onClimbable()) {
            this.travelInAir(input);
            this.stopFallFlying();
        } else {
            Vec3 vec31 = this.getDeltaMovement();
            double d0 = vec31.horizontalDistance();

            this.setDeltaMovement(this.updateFallFlyingMovement(vec31));
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (!this.level().isClientSide()) {
                double d1 = this.getDeltaMovement().horizontalDistance();

                this.handleFallFlyingCollisions(d0, d1);
            }

        }
    }

    public void stopFallFlying() {
        this.setSharedFlag(7, true);
        this.setSharedFlag(7, false);
    }

    private Vec3 updateFallFlyingMovement(Vec3 movement) {
        Vec3 vec31 = this.getLookAngle();
        float f = this.getXRot() * ((float) Math.PI / 180F);
        double d0 = Math.sqrt(vec31.x * vec31.x + vec31.z * vec31.z);
        double d1 = movement.horizontalDistance();
        double d2 = this.getEffectiveGravity();
        double d3 = Mth.square(Math.cos((double) f));

        movement = movement.add(0.0D, d2 * (-1.0D + d3 * 0.75D), 0.0D);
        if (movement.y < 0.0D && d0 > 0.0D) {
            double d4 = movement.y * -0.1D * d3;

            movement = movement.add(vec31.x * d4 / d0, d4, vec31.z * d4 / d0);
        }

        if (f < 0.0F && d0 > 0.0D) {
            double d5 = d1 * (double) (-Mth.sin((double) f)) * 0.04D;

            movement = movement.add(-vec31.x * d5 / d0, d5 * 3.2D, -vec31.z * d5 / d0);
        }

        if (d0 > 0.0D) {
            movement = movement.add((vec31.x / d0 * d1 - movement.x) * 0.1D, 0.0D, (vec31.z / d0 * d1 - movement.z) * 0.1D);
        }

        return movement.multiply((double) 0.99F, (double) 0.98F, (double) 0.99F);
    }

    private void handleFallFlyingCollisions(double moveHorLength, double newMoveHorLength) {
        if (this.horizontalCollision) {
            double d2 = moveHorLength - newMoveHorLength;
            float f = (float) (d2 * 10.0D - 3.0D);

            if (f > 0.0F) {
                this.playSound(this.getFallDamageSound((int) f), 1.0F, 1.0F);
                this.hurt(this.damageSources().flyIntoWall(), f);
            }
        }

    }

    private void travelRidden(Player controller, Vec3 selfInput) {
        Vec3 vec31 = this.getRiddenInput(controller, selfInput);

        this.tickRidden(controller, vec31);
        if (this.canSimulateMovement()) {
            this.setSpeed(this.getRiddenSpeed(controller));
            this.travel(vec31);
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

    }

    protected void tickRidden(Player controller, Vec3 riddenInput) {}

    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return selfInput;
    }

    protected float getRiddenSpeed(Player controller) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean useY) {
        float f = (float) Mth.length(this.getX() - this.xo, useY ? this.getY() - this.yo : 0.0D, this.getZ() - this.zo);

        if (!this.isPassenger() && this.isAlive()) {
            this.updateWalkAnimation(f);
        } else {
            this.walkAnimation.stop();
        }

    }

    protected void updateWalkAnimation(float distance) {
        float f1 = Math.min(distance * 4.0F, 1.0F);

        this.walkAnimation.update(f1, 0.4F, this.isBaby() ? 3.0F : 1.0F);
    }

    private Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 input, float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed(friction), input);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec31 = this.getDeltaMovement();

        if ((this.horizontalCollision || this.jumping) && (this.onClimbable() || this.wasInPowderSnow && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec31 = new Vec3(vec31.x, 0.2D, vec31.z);
        }

        return vec31;
    }

    public Vec3 getFluidFallingAdjustedMovement(double baseGravity, boolean isFalling, Vec3 movement) {
        if (baseGravity != 0.0D && !this.isSprinting()) {
            double d1;

            if (isFalling && Math.abs(movement.y - 0.005D) >= 0.003D && Math.abs(movement.y - baseGravity / 16.0D) < 0.003D) {
                d1 = -0.003D;
            } else {
                d1 = movement.y - baseGravity / 16.0D;
            }

            return new Vec3(movement.x, d1, movement.z);
        } else {
            return movement;
        }
    }

    private Vec3 handleOnClimbable(Vec3 delta) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d0 = Mth.clamp(delta.x, (double) -0.15F, (double) 0.15F);
            double d1 = Mth.clamp(delta.z, (double) -0.15F, (double) 0.15F);
            double d2 = Math.max(delta.y, (double) -0.15F);

            if (d2 < 0.0D && !this.getInBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                d2 = 0.0D;
            }

            delta = new Vec3(d0, d2, d1);
        }

        return delta;
    }

    private float getFrictionInfluencedSpeed(float blockFriction) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof Player ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.setLastHurtMob(target);
        return false;
    }

    public void causeExtraKnockback(Entity target, float knockback, Vec3 oldMovement) {
        if (knockback > 0.0F && target instanceof LivingEntity livingentity) {
            livingentity.knockback((double) knockback, (double) Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))), (double) (-Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)))));
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
        }

    }

    protected void playAttackSound() {}

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide()) {
            int i = this.getArrowCount();

            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                --this.removeArrowTime;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();

            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                --this.removeStingerTime;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && (!this.canInteractWithLevel() || !this.checkBedExists())) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d0 = this.getX() - this.xo;
        double d1 = this.getZ() - this.zo;
        float f = (float) (d0 * d0 + d1 * d1);
        float f1 = this.yBodyRot;

        if (f > 0.0025000002F) {
            float f2 = (float) Mth.atan2(d1, d0) * (180F / (float) Math.PI) - 90.0F;
            float f3 = Mth.abs(Mth.wrapDegrees(this.getYRot()) - f2);

            if (95.0F < f3 && f3 < 265.0F) {
                f1 = f2 - 180.0F;
            } else {
                f1 = f2;
            }
        }

        if (this.attackAnim > 0.0F) {
            f1 = this.getYRot();
        }

        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("headTurn");
        this.tickHeadTurn(f1);
        profilerfiller.pop();
        profilerfiller.push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        profilerfiller.pop();
        if (this.isFallFlying()) {
            ++this.fallFlyTicks;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        this.elytraAnimationState.tick();
    }

    public boolean wasRecentlyStabbed(Entity target, int allowedTime) {
        return this.recentKineticEnemies == null ? false : (this.recentKineticEnemies.containsKey(target) ? this.level().getGameTime() - this.recentKineticEnemies.getLong(target) < (long) allowedTime : false);
    }

    public void rememberStabbedEntity(Entity target) {
        if (this.recentKineticEnemies != null) {
            this.recentKineticEnemies.put(target, this.level().getGameTime());
        }

    }

    public int stabbedEntities(Predicate<Entity> filter) {
        return this.recentKineticEnemies == null ? 0 : (int) this.recentKineticEnemies.keySet().stream().filter(filter).count();
    }

    public boolean stabAttack(EquipmentSlot weaponSlot, Entity target, float baseDamage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts) {
        Level level = this.level();

        if (!(level instanceof ServerLevel serverlevel)) {
            return false;
        } else {
            ItemStack itemstack = this.getItemBySlot(weaponSlot);
            DamageSource damagesource = itemstack.getDamageSource(this, () -> {
                return this.damageSources().mobAttack(this);
            });
            float f1 = EnchantmentHelper.modifyDamage(serverlevel, itemstack, target, damagesource, baseDamage);
            Vec3 vec3 = target.getDeltaMovement();
            boolean flag3 = dealsDamage && target.hurtServer(serverlevel, damagesource, f1);
            boolean flag4 = dealsKnockback | flag3;

            if (dealsKnockback) {
                this.causeExtraKnockback(target, 0.4F + this.getKnockback(target, damagesource), vec3);
            }

            if (dismounts && target.isPassenger()) {
                flag4 = true;
                target.stopRiding();
            }

            if (target instanceof LivingEntity livingentity) {
                itemstack.hurtEnemy(livingentity, this);
            }

            if (flag3) {
                EnchantmentHelper.doPostAttackEffects(serverlevel, target, damagesource);
            }

            if (!flag4) {
                return false;
            } else {
                this.setLastHurtMob(target);
                this.playAttackSound();
                return true;
            }
        }
    }

    public void onAttack() {}

    public void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();

        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }

    }

    private @Nullable Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = (ItemStack) this.lastEquipmentItems.get(equipmentslot);
            ItemStack itemstack1 = this.getItemBySlot(equipmentslot);

            if (this.equipmentHasChanged(itemstack, itemstack1)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentslot, itemstack1);
                AttributeMap attributemap = this.getAttributes();

                if (!itemstack.isEmpty()) {
                    this.stopLocationBasedEffects(itemstack, equipmentslot, attributemap);
                }
            }
        }

        if (map != null) {
            for (Map.Entry<EquipmentSlot, ItemStack> map_entry : map.entrySet()) {
                EquipmentSlot equipmentslot1 = (EquipmentSlot) map_entry.getKey();
                ItemStack itemstack2 = (ItemStack) map_entry.getValue();

                if (!itemstack2.isEmpty() && !itemstack2.isBroken()) {
                    itemstack2.forEachModifier(equipmentslot1, (holder, attributemodifier) -> {
                        AttributeInstance attributeinstance = this.attributes.getInstance(holder);

                        if (attributeinstance != null) {
                            attributeinstance.removeModifier(attributemodifier.id());
                            attributeinstance.addTransientModifier(attributemodifier);
                        }

                    });
                    Level level = this.level();

                    if (level instanceof ServerLevel) {
                        ServerLevel serverlevel = (ServerLevel) level;

                        EnchantmentHelper.runLocationChangedEffects(serverlevel, itemstack2, this, equipmentslot1);
                    }
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack previous, ItemStack current) {
        return !ItemStack.matches(current, previous);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> changedItems) {
        ItemStack itemstack = (ItemStack) changedItems.get(EquipmentSlot.MAINHAND);
        ItemStack itemstack1 = (ItemStack) changedItems.get(EquipmentSlot.OFFHAND);

        if (itemstack != null && itemstack1 != null && ItemStack.matches(itemstack, (ItemStack) this.lastEquipmentItems.get(EquipmentSlot.OFFHAND)) && ItemStack.matches(itemstack1, (ItemStack) this.lastEquipmentItems.get(EquipmentSlot.MAINHAND))) {
            ((ServerLevel) this.level()).getChunkSource().sendToTrackingPlayers(this, new ClientboundEntityEventPacket(this, (byte) 55));
            changedItems.remove(EquipmentSlot.MAINHAND);
            changedItems.remove(EquipmentSlot.OFFHAND);
            this.lastEquipmentItems.put(EquipmentSlot.MAINHAND, itemstack.copy());
            this.lastEquipmentItems.put(EquipmentSlot.OFFHAND, itemstack1.copy());
        }

    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> changedItems) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(changedItems.size());

        changedItems.forEach((equipmentslot, itemstack) -> {
            ItemStack itemstack1 = itemstack.copy();

            list.add(Pair.of(equipmentslot, itemstack1));
            this.lastEquipmentItems.put(equipmentslot, itemstack1);
        });
        ((ServerLevel) this.level()).getChunkSource().sendToTrackingPlayers(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    protected void tickHeadTurn(float yBodyRotT) {
        float f1 = Mth.wrapDegrees(yBodyRotT - this.yBodyRot);

        this.yBodyRot += f1 * 0.3F;
        float f2 = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        float f3 = this.getMaxHeadRotationRelativeToBody();

        if (Math.abs(f2) > f3) {
            this.yBodyRot += f2 - (float) Mth.sign((double) f2) * f3;
        }

    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            --this.noJumpDelay;
        }

        if (this.isInterpolating()) {
            this.getInterpolation().interpolate();
        } else if (!this.canSimulateMovement()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            --this.lerpHeadSteps;
        }

        this.equipment.tick(this);
        Vec3 vec3 = this.getDeltaMovement();
        double d0 = vec3.x;
        double d1 = vec3.y;
        double d2 = vec3.z;

        if (this.getType().equals(EntityType.PLAYER)) {
            if (vec3.horizontalDistanceSqr() < 9.0E-6D) {
                d0 = 0.0D;
                d2 = 0.0D;
            }
        } else {
            if (Math.abs(vec3.x) < 0.003D) {
                d0 = 0.0D;
            }

            if (Math.abs(vec3.z) < 0.003D) {
                d2 = 0.0D;
            }
        }

        if (Math.abs(vec3.y) < 0.003D) {
            d1 = 0.0D;
        }

        this.setDeltaMovement(d0, d1, d2);
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("ai");
        this.applyInput();
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi() && !this.level().isClientSide()) {
            profilerfiller.push("newAi");
            this.serverAiStep();
            profilerfiller.pop();
        }

        profilerfiller.pop();
        profilerfiller.push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double d3;

            if (this.isInLava()) {
                d3 = this.getFluidHeight(FluidTags.LAVA);
            } else {
                d3 = this.getFluidHeight(FluidTags.WATER);
            }

            boolean flag = this.isInWater() && d3 > 0.0D;
            double d4 = this.getFluidJumpThreshold();

            if (!flag || this.onGround() && d3 <= d4) {
                if (!this.isInLava() || this.onGround() && d3 <= d4) {
                    if ((this.onGround() || flag && d3 <= d4) && this.noJumpDelay == 0) {
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                    }
                } else {
                    this.jumpInLiquid(FluidTags.LAVA);
                }
            } else {
                this.jumpInLiquid(FluidTags.WATER);
            }
        } else {
            this.noJumpDelay = 0;
        }

        profilerfiller.pop();
        profilerfiller.push("travel");
        if (this.isFallFlying()) {
            this.updateFallFlying();
        }

        AABB aabb = this.getBoundingBox();
        Vec3 vec31 = new Vec3((double) this.xxa, (double) this.yya, (double) this.zza);

        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        label124:
        {
            LivingEntity livingentity = this.getControllingPassenger();

            if (livingentity instanceof Player player) {
                if (this.isAlive()) {
                    this.travelRidden(player, vec31);
                    break label124;
                }
            }

            if (this.canSimulateMovement() && this.isEffectiveAi()) {
                this.travel(vec31);
            }
        }

        if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
            this.applyEffectsFromBlocks();
        }

        if (this.level().isClientSide()) {
            this.calculateEntityAnimation(this instanceof FlyingAnimal);
        }

        profilerfiller.pop();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            profilerfiller.push("freezing");
            if (!this.isInPowderSnow || !this.canFreeze()) {
                this.setTicksFrozen(Math.max(0, this.getTicksFrozen() - 2));
            }

            this.removeFrost();
            this.tryAddFrost();
            if (this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
                this.hurtServer(serverlevel, this.damageSources().freeze(), 1.0F);
            }

            profilerfiller.pop();
        }

        profilerfiller.push("push");
        if (this.autoSpinAttackTicks > 0) {
            --this.autoSpinAttackTicks;
            this.checkAutoSpinAttack(aabb, this.getBoundingBox());
        }

        this.pushEntities();
        profilerfiller.pop();
        level = this.level();
        if (level instanceof ServerLevel serverlevel1) {
            if (this.isSensitiveToWater() && this.isInWaterOrRain()) {
                this.hurtServer(serverlevel1, this.damageSources().drown(), 1.0F);
            }
        }

    }

    protected void applyInput() {
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    public boolean isJumping() {
        return this.jumping;
    }

    protected void updateFallFlying() {
        this.checkFallDistanceAccumulation();
        if (!this.level().isClientSide()) {
            if (!this.canGlide()) {
                this.setSharedFlag(7, false);
                return;
            }

            int i = this.fallFlyTicks + 1;

            if (i % 10 == 0) {
                int j = i / 10;

                if (j % 2 == 0) {
                    List<EquipmentSlot> list = EquipmentSlot.VALUES.stream().filter((equipmentslot) -> {
                        return canGlideUsing(this.getItemBySlot(equipmentslot), equipmentslot);
                    }).toList();
                    EquipmentSlot equipmentslot = (EquipmentSlot) Util.getRandom(list, this.random);

                    this.getItemBySlot(equipmentslot).hurtAndBreak(1, this, equipmentslot);
                }

                this.gameEvent(GameEvent.ELYTRA_GLIDE);
            }
        }

    }

    protected boolean canGlide() {
        if (!this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
                if (canGlideUsing(this.getItemBySlot(equipmentslot), equipmentslot)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    protected void serverAiStep() {}

    protected void pushEntities() {
        List<Entity> list = this.level().getPushableEntities(this, this.getBoundingBox());

        if (!list.isEmpty()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;
                int i = (Integer) serverlevel.getGameRules().get(GameRules.MAX_ENTITY_CRAMMING);

                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    int j = 0;

                    for (Entity entity : list) {
                        if (!entity.isPassenger()) {
                            ++j;
                        }
                    }

                    if (j > i - 1) {
                        this.hurtServer(serverlevel, this.damageSources().cramming(), 6.0F);
                    }
                }
            }

            for (Entity entity1 : list) {
                this.doPush(entity1);
            }

        }
    }

    protected void checkAutoSpinAttack(AABB old, AABB current) {
        AABB aabb2 = old.minmax(current);
        List<Entity> list = this.level().getEntities(this, aabb2);

        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity) entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2D));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide() && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }

    }

    protected void doPush(Entity entity) {
        entity.push((Entity) this);
    }

    protected void doAutoAttackOnTouch(LivingEntity entity) {}

    public boolean isAutoSpinAttack() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();

        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level().isClientSide()) {
            this.dismountVehicle(entity);
        }

    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.resetFallDistance();
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public void lerpHeadTo(float yRot, int steps) {
        this.lerpYHeadRot = (double) yRot;
        this.lerpHeadSteps = steps;
    }

    public void setJumping(boolean jump) {
        this.jumping = jump;
    }

    public void onItemPickup(ItemEntity entity) {
        Entity entity1 = entity.getOwner();

        if (entity1 instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer) entity1, entity.getItem(), this);
        }

    }

    public void take(Entity entity, int orgCount) {
        if (!entity.isRemoved() && !this.level().isClientSide() && (entity instanceof ItemEntity || entity instanceof AbstractArrow || entity instanceof ExperienceOrb)) {
            ((ServerLevel) this.level()).getChunkSource().sendToTrackingPlayers(entity, new ClientboundTakeItemEntityPacket(entity.getId(), this.getId(), orgCount));
        }

    }

    public boolean hasLineOfSight(Entity target) {
        return this.hasLineOfSight(target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target.getEyeY());
    }

    public boolean hasLineOfSight(Entity target, ClipContext.Block blockCollidingContext, ClipContext.Fluid fluidCollidingContext, double eyeHeight) {
        if (target.level() != this.level()) {
            return false;
        } else {
            Vec3 vec3 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
            Vec3 vec31 = new Vec3(target.getX(), eyeHeight, target.getZ());

            return vec31.distanceTo(vec3) > 128.0D ? false : this.level().clip(new ClipContext(vec3, vec31, blockCollidingContext, fluidCollidingContext, this)).getType() == HitResult.Type.MISS;
        }
    }

    @Override
    public float getViewYRot(float a) {
        return a == 1.0F ? this.yHeadRot : Mth.rotLerp(a, this.yHeadRotO, this.yHeadRot);
    }

    public float getAttackAnim(float a) {
        float f1 = this.attackAnim - this.oAttackAnim;

        if (f1 < 0.0F) {
            ++f1;
        }

        return this.oAttackAnim + f1 * a;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable();
    }

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    @Override
    public void setYHeadRot(float yHeadRot) {
        this.yHeadRot = yHeadRot;
    }

    @Override
    public void setYBodyRot(float yBodyRot) {
        this.yBodyRot = yBodyRot;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portalArea) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portalArea));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 offsets) {
        return new Vec3(offsets.x, offsets.y, 0.0D);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float absorptionAmount) {
        this.internalSetAbsorptionAmount(Mth.clamp(absorptionAmount, 0.0F, this.getMaxAbsorption()));
    }

    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.absorptionAmount = absorptionAmount;
    }

    public void onEnterCombat() {}

    public void onLeaveCombat() {}

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return ((Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameItem(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }

    }

    private @Nullable ItemEntity createItemStackToDrop(ItemStack itemStack, boolean randomly, boolean thrownFromHand) {
        if (itemStack.isEmpty()) {
            return null;
        } else {
            double d0 = this.getEyeY() - (double) 0.3F;
            ItemEntity itementity = new ItemEntity(this.level(), this.getX(), d0, this.getZ(), itemStack);

            itementity.setPickUpDelay(40);
            if (thrownFromHand) {
                itementity.setThrower(this);
            }

            if (randomly) {
                float f = this.random.nextFloat() * 0.5F;
                float f1 = this.random.nextFloat() * ((float) Math.PI * 2F);

                itementity.setDeltaMovement((double) (-Mth.sin((double) f1) * f), (double) 0.2F, (double) (Mth.cos((double) f1) * f));
            } else {
                float f2 = 0.3F;
                float f3 = Mth.sin((double) (this.getXRot() * ((float) Math.PI / 180F)));
                float f4 = Mth.cos((double) (this.getXRot() * ((float) Math.PI / 180F)));
                float f5 = Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F)));
                float f6 = Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F)));
                float f7 = this.random.nextFloat() * ((float) Math.PI * 2F);
                float f8 = 0.02F * this.random.nextFloat();

                itementity.setDeltaMovement((double) (-f5 * f4 * 0.3F) + Math.cos((double) f7) * (double) f8, (double) (-f3 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (f6 * f4 * 0.3F) + Math.sin((double) f7) * (double) f8);
            }

            return itementity;
        }
    }

    protected void updateUsingItem(ItemStack useItem) {
        useItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        if (--this.useItemRemaining == 0 && !this.level().isClientSide() && !useItem.useOnRelease()) {
            this.completeUsingItem();
        }

    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }

    }

    public void setLivingEntityFlag(int flag, boolean value) {
        int j = (Byte) this.entityData.get(LivingEntity.DATA_LIVING_ENTITY_FLAGS);

        if (value) {
            j |= flag;
        } else {
            j &= ~flag;
        }

        this.entityData.set(LivingEntity.DATA_LIVING_ENTITY_FLAGS, (byte) j);
    }

    public void startUsingItem(InteractionHand hand) {
        ItemStack itemstack = this.getItemInHand(hand);

        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            this.useItem = itemstack;
            this.useItemRemaining = itemstack.getUseDuration(this);
            if (!this.level().isClientSide()) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
                this.useItem.causeUseVibration(this, GameEvent.ITEM_INTERACT_START);
                if (this.useItem.has(DataComponents.KINETIC_WEAPON)) {
                    this.recentKineticEnemies = new Object2LongOpenHashMap();
                }
            }

        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (LivingEntity.SLEEPING_POS_ID.equals(accessor)) {
            if (this.level().isClientSide()) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (LivingEntity.DATA_LIVING_ENTITY_FLAGS.equals(accessor) && this.level().isClientSide()) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 pos) {
        super.lookAt(anchor, pos);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float partial) {
        return Mth.lerp(partial, this.yBodyRotO, this.yBodyRot);
    }

    public void spawnItemParticles(ItemStack itemStack, int count) {
        for (int j = 0; j < count; ++j) {
            Vec3 vec3 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.1D, (double) this.random.nextFloat() * 0.1D + 0.1D, 0.0D);

            vec3 = vec3.xRot(-this.getXRot() * ((float) Math.PI / 180F));
            vec3 = vec3.yRot(-this.getYRot() * ((float) Math.PI / 180F));
            double d0 = (double) (-this.random.nextFloat()) * 0.6D - 0.3D;
            Vec3 vec31 = new Vec3(((double) this.random.nextFloat() - 0.5D) * 0.3D, d0, 0.6D);

            vec31 = vec31.xRot(-this.getXRot() * ((float) Math.PI / 180F));
            vec31 = vec31.yRot(-this.getYRot() * ((float) Math.PI / 180F));
            vec31 = vec31.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, itemStack), vec31.x, vec31.y, vec31.z, vec3.x, vec3.y + 0.05D, vec3.z);
        }

    }

    protected void completeUsingItem() {
        if (!this.level().isClientSide() || this.isUsingItem()) {
            InteractionHand interactionhand = this.getUsedItemHand();

            if (!this.useItem.equals(this.getItemInHand(interactionhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    ItemStack itemstack = this.useItem.finishUsingItem(this.level(), this);

                    if (itemstack != this.useItem) {
                        this.setItemInHand(interactionhand, itemstack);
                    }

                    this.stopUsingItem();
                }

            }
        }
    }

    public void handleExtraItemsCreatedOnUse(ItemStack extraCreatedRemainder) {}

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public float getTicksUsingItem(float partialTicks) {
        return !this.isUsingItem() ? 0.0F : (float) this.getTicksUsingItem() + partialTicks;
    }

    public void releaseUsingItem() {
        ItemStack itemstack = this.getItemInHand(this.getUsedItemHand());

        if (!this.useItem.isEmpty() && ItemStack.isSameItem(itemstack, this.useItem)) {
            this.useItem = itemstack;
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level().isClientSide()) {
            boolean flag = this.isUsingItem();

            this.recentKineticEnemies = null;
            this.setLivingEntityFlag(1, false);
            if (flag) {
                this.useItem.causeUseVibration(this, GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        return this.getItemBlockingWith() != null;
    }

    public @Nullable ItemStack getItemBlockingWith() {
        if (!this.isUsingItem()) {
            return null;
        } else {
            BlocksAttacks blocksattacks = (BlocksAttacks) this.useItem.get(DataComponents.BLOCKS_ATTACKS);

            if (blocksattacks != null) {
                int i = this.useItem.getItem().getUseDuration(this.useItem, this) - this.useItemRemaining;

                if (i >= blocksattacks.blockDelayTicks()) {
                    return this.useItem;
                }
            }

            return null;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double xx, double yy, double zz, boolean showParticles) {
        double d3 = this.getX();
        double d4 = this.getY();
        double d5 = this.getZ();
        double d6 = yy;
        boolean flag1 = false;
        BlockPos blockpos = BlockPos.containing(xx, yy, zz);
        Level level = this.level();

        if (level.hasChunkAt(blockpos)) {
            boolean flag2 = false;

            while (!flag2 && blockpos.getY() > level.getMinY()) {
                BlockPos blockpos1 = blockpos.below();
                BlockState blockstate = level.getBlockState(blockpos1);

                if (blockstate.blocksMotion()) {
                    flag2 = true;
                } else {
                    --d6;
                    blockpos = blockpos1;
                }
            }

            if (flag2) {
                this.teleportTo(xx, d6, zz);
                if (level.noCollision((Entity) this) && !level.containsAnyLiquid(this.getBoundingBox())) {
                    flag1 = true;
                }
            }
        }

        if (!flag1) {
            this.teleportTo(d3, d4, d5);
            return false;
        } else {
            if (showParticles) {
                level.broadcastEntityEvent(this, (byte) 46);
            }

            if (this instanceof PathfinderMob) {
                PathfinderMob pathfindermob = (PathfinderMob) this;

                pathfindermob.getNavigation().stop();
            }

            return true;
        }
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    public void setRecordPlayingNearby(BlockPos jukebox, boolean isPlaying) {}

    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public final EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SLEEPING ? LivingEntity.SLEEPING_DIMENSIONS : this.getDefaultDimensions(pose).scale(this.getScale());
    }

    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose pose) {
        EntityDimensions entitydimensions = this.getDimensions(pose);

        return new AABB((double) (-entitydimensions.width() / 2.0F), 0.0D, (double) (-entitydimensions.width() / 2.0F), (double) (entitydimensions.width() / 2.0F), (double) entitydimensions.height(), (double) (entitydimensions.width() / 2.0F));
    }

    protected boolean wouldNotSuffocateAtTargetPose(Pose pose) {
        AABB aabb = this.getDimensions(pose).makeBoundingBox(this.position());

        return this.level().noBlockCollision(this, aabb);
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return super.canUsePortal(ignorePassenger) && !this.isSleeping();
    }

    public Optional<BlockPos> getSleepingPos() {
        return (Optional) this.entityData.get(LivingEntity.SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos bedPosition) {
        this.entityData.set(LivingEntity.SLEEPING_POS_ID, Optional.of(bedPosition));
    }

    public void clearSleepingPos() {
        this.entityData.set(LivingEntity.SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos bedPosition) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockstate = this.level().getBlockState(bedPosition);

        if (blockstate.getBlock() instanceof BedBlock) {
            this.level().setBlock(bedPosition, (BlockState) blockstate.setValue(BedBlock.OCCUPIED, true), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(bedPosition);
        this.setSleepingPos(bedPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.needsSync = true;
    }

    private void setPosToBed(BlockPos bedPosition) {
        this.setPos((double) bedPosition.getX() + 0.5D, (double) bedPosition.getY() + 0.6875D, (double) bedPosition.getZ() + 0.5D);
    }

    private boolean checkBedExists() {
        return (Boolean) this.getSleepingPos().map((blockpos) -> {
            return this.level().getBlockState(blockpos).getBlock() instanceof BedBlock;
        }).orElse(false);
    }

    public void stopSleeping() {
        Optional optional = this.getSleepingPos();
        Level level = this.level();

        java.util.Objects.requireNonNull(level);
        optional.filter(level::hasChunkAt).ifPresent((blockpos) -> {
            BlockState blockstate = this.level().getBlockState(blockpos);

            if (blockstate.getBlock() instanceof BedBlock) {
                Direction direction = (Direction) blockstate.getValue(BedBlock.FACING);

                this.level().setBlock(blockpos, (BlockState) blockstate.setValue(BedBlock.OCCUPIED, false), 3);
                Vec3 vec3 = (Vec3) BedBlock.findStandUpPosition(this.getType(), this.level(), blockpos, direction, this.getYRot()).orElseGet(() -> {
                    BlockPos blockpos1 = blockpos.above();

                    return new Vec3((double) blockpos1.getX() + 0.5D, (double) blockpos1.getY() + 0.1D, (double) blockpos1.getZ() + 0.5D);
                });
                Vec3 vec31 = Vec3.atBottomCenterOf(blockpos).subtract(vec3).normalize();
                float f = (float) Mth.wrapDegrees(Mth.atan2(vec31.z, vec31.x) * (double) (180F / (float) Math.PI) - 90.0D);

                this.setPos(vec3.x, vec3.y, vec3.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }

        });
        Vec3 vec3 = this.position();

        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
    }

    public @Nullable Direction getBedOrientation() {
        BlockPos blockpos = (BlockPos) this.getSleepingPos().orElse((Object) null);

        return blockpos != null ? BedBlock.getBedOrientation(this.level(), blockpos) : null;
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    public ItemStack getProjectile(ItemStack heldWeapon) {
        return ItemStack.EMPTY;
    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot equipmentSlot) {
        byte b0;

        switch (equipmentSlot) {
            case MAINHAND:
                b0 = 47;
                break;
            case OFFHAND:
                b0 = 48;
                break;
            case HEAD:
                b0 = 49;
                break;
            case CHEST:
                b0 = 50;
                break;
            case FEET:
                b0 = 52;
                break;
            case LEGS:
                b0 = 51;
                break;
            case BODY:
                b0 = 65;
                break;
            case SADDLE:
                b0 = 68;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return b0;
    }

    public void onEquippedItemBroken(Item brokenItem, EquipmentSlot inSlot) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(inSlot));
        this.stopLocationBasedEffects(this.getItemBySlot(inSlot), inSlot, this.attributes);
    }

    private void stopLocationBasedEffects(ItemStack previous, EquipmentSlot inSlot, AttributeMap attributes) {
        previous.forEachModifier(inSlot, (holder, attributemodifier) -> {
            AttributeInstance attributeinstance = attributes.getInstance(holder);

            if (attributeinstance != null) {
                attributeinstance.removeModifier(attributemodifier);
            }

        });
        EnchantmentHelper.stopLocationBasedEffects(previous, this, inSlot);
    }

    public final boolean canEquipWithDispenser(ItemStack itemStack) {
        if (this.isAlive() && !this.isSpectator()) {
            Equippable equippable = (Equippable) itemStack.get(DataComponents.EQUIPPABLE);

            if (equippable != null && equippable.dispensable()) {
                EquipmentSlot equipmentslot = equippable.slot();

                return this.canUseSlot(equipmentslot) && equippable.canBeEquippedBy(this.getType()) ? this.getItemBySlot(equipmentslot).isEmpty() && this.canDispenserEquipIntoSlot(equipmentslot) : false;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return true;
    }

    public final EquipmentSlot getEquipmentSlotForItem(ItemStack itemStack) {
        Equippable equippable = (Equippable) itemStack.get(DataComponents.EQUIPPABLE);

        return equippable != null && this.canUseSlot(equippable.slot()) ? equippable.slot() : EquipmentSlot.MAINHAND;
    }

    public final boolean isEquippableInSlot(ItemStack itemStack, EquipmentSlot slot) {
        Equippable equippable = (Equippable) itemStack.get(DataComponents.EQUIPPABLE);

        return equippable == null ? slot == EquipmentSlot.MAINHAND && this.canUseSlot(EquipmentSlot.MAINHAND) : slot == equippable.slot() && this.canUseSlot(equippable.slot()) && equippable.canBeEquippedBy(this.getType());
    }

    private static SlotAccess createEquipmentSlotAccess(LivingEntity entity, EquipmentSlot equipmentSlot) {
        return equipmentSlot != EquipmentSlot.HEAD && equipmentSlot != EquipmentSlot.MAINHAND && equipmentSlot != EquipmentSlot.OFFHAND ? SlotAccess.forEquipmentSlot(entity, equipmentSlot, (itemstack) -> {
            return itemstack.isEmpty() || entity.getEquipmentSlotForItem(itemstack) == equipmentSlot;
        }) : SlotAccess.forEquipmentSlot(entity, equipmentSlot);
    }

    private static @Nullable EquipmentSlot getEquipmentSlot(int slot) {
        return slot == 100 + EquipmentSlot.HEAD.getIndex() ? EquipmentSlot.HEAD : (slot == 100 + EquipmentSlot.CHEST.getIndex() ? EquipmentSlot.CHEST : (slot == 100 + EquipmentSlot.LEGS.getIndex() ? EquipmentSlot.LEGS : (slot == 100 + EquipmentSlot.FEET.getIndex() ? EquipmentSlot.FEET : (slot == 98 ? EquipmentSlot.MAINHAND : (slot == 99 ? EquipmentSlot.OFFHAND : (slot == 105 ? EquipmentSlot.BODY : (slot == 106 ? EquipmentSlot.SADDLE : null)))))));
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        EquipmentSlot equipmentslot = getEquipmentSlot(slot);

        return equipmentslot != null ? createEquipmentSlotAccess(this, equipmentslot) : super.getSlot(slot);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        } else {
            for (EquipmentSlot equipmentslot : EquipmentSlotGroup.ARMOR) {
                if (this.getItemBySlot(equipmentslot).is(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
                    return false;
                }
            }

            return super.canFreeze();
        }
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        float f = packet.getYRot();
        float f1 = packet.getXRot();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.yBodyRot = packet.getYHeadRot();
        this.yHeadRot = packet.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packet.getId());
        this.setUUID(packet.getUUID());
        this.absSnapTo(d0, d1, d2, f, f1);
        this.setDeltaMovement(packet.getMovement());
    }

    public float getSecondsToDisableBlocking() {
        ItemStack itemstack = this.getWeaponItem();
        Weapon weapon = (Weapon) itemstack.get(DataComponents.WEAPON);

        return weapon != null && itemstack == this.getActiveItem() ? weapon.disableBlockingForSeconds() : 0.0F;
    }

    @Override
    public float maxUpStep() {
        float f = (float) this.getAttributeValue(Attributes.STEP_HEIGHT);

        return this.getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(this.getPassengerAttachmentPoint(passenger, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int lerpHeadSteps, double targetYHeadRot) {
        this.yHeadRot = (float) Mth.rotLerp(1.0D / (double) lerpHeadSteps, (double) this.yHeadRot, targetYHeadRot);
    }

    @Override
    public void igniteForTicks(int numberOfTicks) {
        super.igniteForTicks(Mth.ceil((double) numberOfTicks * this.getAttributeValue(Attributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return this.isInvulnerableToBase(source) || EnchantmentHelper.isImmuneToDamage(level, this, source);
    }

    public static boolean canGlideUsing(ItemStack itemStack, EquipmentSlot slot) {
        if (!itemStack.has(DataComponents.GLIDER)) {
            return false;
        } else {
            Equippable equippable = (Equippable) itemStack.get(DataComponents.EQUIPPABLE);

            return equippable != null && slot == equippable.slot() && !itemStack.nextDamageWillBreak();
        }
    }

    @VisibleForTesting
    public int getLastHurtByPlayerMemoryTime() {
        return this.lastHurtByPlayerMemoryTime;
    }

    @Override
    public boolean isTransmittingWaypoint() {
        return this.getAttributeValue(Attributes.WAYPOINT_TRANSMIT_RANGE) > 0.0D;
    }

    @Override
    public Optional<WaypointTransmitter.Connection> makeWaypointConnectionWith(ServerPlayer player) {
        if (!this.firstTick && player != this) {
            if (WaypointTransmitter.doesSourceIgnoreReceiver(this, player)) {
                return Optional.empty();
            } else {
                Waypoint.Icon waypoint_icon = this.locatorBarIcon.cloneAndAssignStyle(this);

                return WaypointTransmitter.isReallyFar(this, player) ? Optional.of(new WaypointTransmitter.EntityAzimuthConnection(this, waypoint_icon, player)) : (!WaypointTransmitter.isChunkVisible(this.chunkPosition(), player) ? Optional.of(new WaypointTransmitter.EntityChunkConnection(this, waypoint_icon, player)) : Optional.of(new WaypointTransmitter.EntityBlockConnection(this, waypoint_icon, player)));
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Waypoint.Icon waypointIcon() {
        return this.locatorBarIcon;
    }

    public static record Fallsounds(SoundEvent small, SoundEvent big) {

    }
}
