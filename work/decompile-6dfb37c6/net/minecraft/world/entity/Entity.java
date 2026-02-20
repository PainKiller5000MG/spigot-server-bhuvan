package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugEntityBlockIntersection;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class Entity implements Nameable, EntityAccess, ScoreHolder, SyncedDataHolder, DataComponentGetter, ItemOwner, SlotProvider, DebugValueSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String TAG_ID = "id";
    public static final String TAG_UUID = "UUID";
    public static final String TAG_PASSENGERS = "Passengers";
    public static final String TAG_DATA = "data";
    public static final String TAG_POS = "Pos";
    public static final String TAG_MOTION = "Motion";
    public static final String TAG_ROTATION = "Rotation";
    public static final String TAG_PORTAL_COOLDOWN = "PortalCooldown";
    public static final String TAG_NO_GRAVITY = "NoGravity";
    public static final String TAG_AIR = "Air";
    public static final String TAG_ON_GROUND = "OnGround";
    public static final String TAG_FALL_DISTANCE = "fall_distance";
    public static final String TAG_FIRE = "Fire";
    public static final String TAG_SILENT = "Silent";
    public static final String TAG_GLOWING = "Glowing";
    public static final String TAG_INVULNERABLE = "Invulnerable";
    public static final String TAG_CUSTOM_NAME = "CustomName";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    public static final int CONTENTS_SLOT_INDEX = 0;
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    private static final Codec<List<String>> TAG_LIST_CODEC = Codec.STRING.sizeLimitedListOf(1024);
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001D;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999D;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    public static final int BASE_SAFE_FALL_DISTANCE = 3;
    private static final AABB INITIAL_AABB = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final double WATER_FLOW_SCALE = 0.014D;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007D;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335D;
    private static final int MAX_BLOCK_ITERATIONS_ALONG_TRAVEL_PER_TICK = 16;
    private static final double MAX_MOVEMENT_RESETTING_TRACE_DISTANCE = 8.0D;
    private static double viewScale = 1.0D;
    private final EntityType<?> type;
    private boolean requiresPrecisePosition;
    private int id;
    public boolean blocksBuilding;
    public ImmutableList<Entity> passengers;
    protected int boardingCooldown;
    private @Nullable Entity vehicle;
    private Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier;
    private Entity.@Nullable RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float moveDist;
    public float flyDist;
    public double fallDistance;
    private float nextStep;
    public double xOld;
    public double yOld;
    public double zOld;
    public boolean noPhysics;
    public final RandomSource random;
    public int tickCount;
    private int remainingFireTicks;
    public boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes;
    public int invulnerableTime;
    protected boolean firstTick;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.<Byte>defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.<Integer>defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.<Optional<Component>>defineId(Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.<Boolean>defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.<Boolean>defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.<Boolean>defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Pose> DATA_POSE = SynchedEntityData.<Pose>defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.<Integer>defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback;
    private final VecDeltaCodec packetPositionCodec;
    public boolean needsSync;
    public @Nullable PortalProcessor portalProcess;
    public int portalCooldown;
    private boolean invulnerable;
    protected UUID uuid;
    protected String stringUUID;
    private boolean hasGlowingTag;
    private final Set<String> tags;
    private final double[] pistonDeltas;
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public Optional<BlockPos> mainSupportingBlockPos;
    private boolean onGroundNoBlocks;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    public boolean hasVisualFire;
    private Vec3 lastKnownSpeed;
    private @Nullable Vec3 lastKnownPosition;
    private @Nullable BlockState inBlockState;
    public static final int MAX_MOVEMENTS_HANDELED_PER_TICK = 100;
    private final ArrayDeque<Entity.Movement> movementThisTick;
    private final List<Entity.Movement> finalMovementsThisTick;
    private final LongSet visitedBlocks;
    private final InsideBlockEffectApplier.StepBasedCollector insideEffectCollector;
    private CustomData customData;

    public Entity(EntityType<?> type, Level level) {
        this.id = Entity.ENTITY_COUNTER.incrementAndGet();
        this.passengers = ImmutableList.of();
        this.deltaMovement = Vec3.ZERO;
        this.bb = Entity.INITIAL_AABB;
        this.stuckSpeedMultiplier = Vec3.ZERO;
        this.nextStep = 1.0F;
        this.random = RandomSource.create();
        this.fluidHeight = new Object2DoubleArrayMap(2);
        this.fluidOnEyes = new HashSet();
        this.firstTick = true;
        this.levelCallback = EntityInLevelCallback.NULL;
        this.packetPositionCodec = new VecDeltaCodec();
        this.uuid = Mth.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        this.tags = Sets.newHashSet();
        this.pistonDeltas = new double[]{0.0D, 0.0D, 0.0D};
        this.mainSupportingBlockPos = Optional.empty();
        this.onGroundNoBlocks = false;
        this.lastKnownSpeed = Vec3.ZERO;
        this.inBlockState = null;
        this.movementThisTick = new ArrayDeque(100);
        this.finalMovementsThisTick = new ObjectArrayList();
        this.visitedBlocks = new LongOpenHashSet();
        this.insideEffectCollector = new InsideBlockEffectApplier.StepBasedCollector();
        this.customData = CustomData.EMPTY;
        this.type = type;
        this.level = level;
        this.dimensions = type.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        SynchedEntityData.Builder synchedentitydata_builder = new SynchedEntityData.Builder(this);

        synchedentitydata_builder.define(Entity.DATA_SHARED_FLAGS_ID, (byte) 0);
        synchedentitydata_builder.define(Entity.DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        synchedentitydata_builder.define(Entity.DATA_CUSTOM_NAME_VISIBLE, false);
        synchedentitydata_builder.define(Entity.DATA_CUSTOM_NAME, Optional.empty());
        synchedentitydata_builder.define(Entity.DATA_SILENT, false);
        synchedentitydata_builder.define(Entity.DATA_NO_GRAVITY, false);
        synchedentitydata_builder.define(Entity.DATA_POSE, Pose.STANDING);
        synchedentitydata_builder.define(Entity.DATA_TICKS_FROZEN, 0);
        this.defineSynchedData(synchedentitydata_builder);
        this.entityData = synchedentitydata_builder.build();
        this.setPos(0.0D, 0.0D, 0.0D);
        this.eyeHeight = this.dimensions.eyeHeight();
    }

    public boolean isColliding(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getCollisionShape(this.level(), pos, CollisionContext.of(this)).move((Vec3i) pos);

        return Shapes.joinIsNotEmpty(voxelshape, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        Team team = this.getTeam();

        return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public boolean canInteractWithLevel() {
        return this.isAlive() && !this.isRemoved() && !this.isSpectator();
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    public void syncPacketPositionCodec(double x, double y, double z) {
        this.packetPositionCodec.setBase(new Vec3(x, y, z));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public boolean getRequiresPrecisePosition() {
        return this.requiresPrecisePosition;
    }

    public void setRequiresPrecisePosition(boolean requiresPrecisePosition) {
        this.requiresPrecisePosition = requiresPrecisePosition;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String tag) {
        return this.tags.size() >= 1024 ? false : this.tags.add(tag);
    }

    public boolean removeTag(String tag) {
        return this.tags.remove(tag);
    }

    public void kill(ServerLevel level) {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder entityData);

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    public boolean equals(Object obj) {
        return obj instanceof Entity ? ((Entity) obj).id == this.id : false;
    }

    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason reason) {
        this.setRemoved(reason);
    }

    public void onClientRemoval() {}

    public void onRemoval(Entity.RemovalReason reason) {}

    public void setPose(Pose pose) {
        this.entityData.set(Entity.DATA_POSE, pose);
    }

    public Pose getPose() {
        return (Pose) this.entityData.get(Entity.DATA_POSE);
    }

    public boolean hasPose(Pose pose) {
        return this.getPose() == pose;
    }

    public boolean closerThan(Entity other, double distance) {
        return this.position().closerThan(other.position(), distance);
    }

    public boolean closerThan(Entity other, double distanceXZ, double distanceY) {
        double d2 = other.getX() - this.getX();
        double d3 = other.getY() - this.getY();
        double d4 = other.getZ() - this.getZ();

        return Mth.lengthSquared(d2, d4) < Mth.square(distanceXZ) && Mth.square(d3) < Mth.square(distanceY);
    }

    protected void setRot(float yRot, float xRot) {
        this.setYRot(yRot % 360.0F);
        this.setXRot(xRot % 360.0F);
    }

    public final void setPos(Vec3 pos) {
        this.setPos(pos.x(), pos.y(), pos.z());
    }

    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
        this.setBoundingBox(this.makeBoundingBox());
    }

    protected final AABB makeBoundingBox() {
        return this.makeBoundingBox(this.position);
    }

    protected AABB makeBoundingBox(Vec3 position) {
        return this.dimensions.makeBoundingBox(position);
    }

    protected void reapplyPosition() {
        this.lastKnownPosition = null;
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double xo, double yo) {
        float f = (float) yo * 0.15F;
        float f1 = (float) xo * 0.15F;

        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }

    }

    public void updateDataBeforeSync() {}

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("entityBaseTick");
        this.computeSpeed();
        this.inBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        this.handlePortal();
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.remainingFireTicks > 0) {
                if (this.fireImmune()) {
                    this.clearFire();
                } else {
                    if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                        this.hurtServer(serverlevel, this.damageSources().onFire(), 1.0F);
                    }

                    this.setRemainingFireTicks(this.remainingFireTicks - 1);
                }
            }
        } else {
            this.clearFire();
        }

        if (this.isInLava()) {
            this.fallDistance *= 0.5D;
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide()) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        level = this.level();
        if (level instanceof ServerLevel serverlevel1) {
            if (this instanceof Leashable) {
                Leashable.tickLeash(serverlevel1, (Entity) ((Leashable) this));
            }
        }

        profilerfiller.pop();
    }

    protected void computeSpeed() {
        if (this.lastKnownPosition == null) {
            this.lastKnownPosition = this.position();
        }

        this.lastKnownSpeed = this.position().subtract(this.lastKnownPosition);
        this.lastKnownPosition = this.position();
    }

    public void setSharedFlagOnFire(boolean value) {
        this.setSharedFlag(0, value || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        if (this.getY() < (double) (this.level().getMinY() - 64)) {
            this.onBelowWorld();
        }

    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            --this.portalCooldown;
        }

    }

    public void lavaIgnite() {
        if (!this.fireImmune()) {
            this.igniteForSeconds(15.0F);
        }
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (this.hurtServer(serverlevel, this.damageSources().lava(), 4.0F) && this.shouldPlayLavaHurtSound() && !this.isSilent()) {
                    serverlevel.playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_BURN, this.getSoundSource(), 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
                }
            }

        }
    }

    protected boolean shouldPlayLavaHurtSound() {
        return true;
    }

    public final void igniteForSeconds(float numberOfSeconds) {
        this.igniteForTicks(Mth.floor(numberOfSeconds * 20.0F));
    }

    public void igniteForTicks(int numberOfTicks) {
        if (this.remainingFireTicks < numberOfTicks) {
            this.setRemainingFireTicks(numberOfTicks);
        }

        this.clearFreeze();
    }

    public void setRemainingFireTicks(int remainingTicks) {
        this.remainingFireTicks = remainingTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(Math.min(0, this.getRemainingFireTicks()));
    }

    protected void onBelowWorld() {
        this.discard();
    }

    public boolean isFree(double xa, double ya, double za) {
        return this.isFree(this.getBoundingBox().move(xa, ya, za));
    }

    private boolean isFree(AABB box) {
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, (Vec3) null);
    }

    public void setOnGroundWithMovement(boolean onGround, Vec3 movement) {
        this.setOnGroundWithMovement(onGround, this.horizontalCollision, movement);
    }

    public void setOnGroundWithMovement(boolean onGround, boolean horizontalCollision, Vec3 movement) {
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
        this.checkSupportingBlock(onGround, movement);
    }

    public boolean isSupportedBy(BlockPos pos) {
        return this.mainSupportingBlockPos.isPresent() && ((BlockPos) this.mainSupportingBlockPos.get()).equals(pos);
    }

    protected void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement) {
        if (onGround) {
            AABB aabb = this.getBoundingBox();
            AABB aabb1 = new AABB(aabb.minX, aabb.minY - 1.0E-6D, aabb.minZ, aabb.maxX, aabb.minY, aabb.maxZ);
            Optional<BlockPos> optional = this.level.findSupportingBlock(this, aabb1);

            if (!optional.isPresent() && !this.onGroundNoBlocks) {
                if (movement != null) {
                    AABB aabb2 = aabb1.move(-movement.x, 0.0D, -movement.z);

                    optional = this.level.findSupportingBlock(this, aabb2);
                    this.mainSupportingBlockPos = optional;
                }
            } else {
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }

    }

    public boolean onGround() {
        return this.onGround;
    }

    public void move(MoverType moverType, Vec3 delta) {
        if (this.noPhysics) {
            this.setPos(this.getX() + delta.x, this.getY() + delta.y, this.getZ() + delta.z);
            this.horizontalCollision = false;
            this.verticalCollision = false;
            this.verticalCollisionBelow = false;
            this.minorHorizontalCollision = false;
        } else {
            if (moverType == MoverType.PISTON) {
                delta = this.limitPistonMovement(delta);
                if (delta.equals(Vec3.ZERO)) {
                    return;
                }
            }

            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                if (moverType != MoverType.PISTON) {
                    delta = delta.multiply(this.stuckSpeedMultiplier);
                }

                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }

            delta = this.maybeBackOffFromEdge(delta, moverType);
            Vec3 vec31 = this.collide(delta);
            double d0 = vec31.lengthSqr();

            if (d0 > 1.0E-7D || delta.lengthSqr() - d0 < 1.0E-7D) {
                if (this.fallDistance != 0.0D && d0 >= 1.0D) {
                    double d1 = Math.min(vec31.length(), 8.0D);
                    Vec3 vec32 = this.position().add(vec31.normalize().scale(d1));
                    BlockHitResult blockhitresult = this.level().clip(new ClipContext(this.position(), vec32, ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this));

                    if (blockhitresult.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                Vec3 vec33 = this.position();
                Vec3 vec34 = vec33.add(vec31);

                this.addMovementThisTick(new Entity.Movement(vec33, vec34, delta));
                this.setPos(vec34);
            }

            profilerfiller.pop();
            profilerfiller.push("rest");
            boolean flag = !Mth.equal(delta.x, vec31.x);
            boolean flag1 = !Mth.equal(delta.z, vec31.z);

            this.horizontalCollision = flag || flag1;
            if (Math.abs(delta.y) > 0.0D || this.isLocalInstanceAuthoritative()) {
                this.verticalCollision = delta.y != vec31.y;
                this.verticalCollisionBelow = this.verticalCollision && delta.y < 0.0D;
                this.setOnGroundWithMovement(this.verticalCollisionBelow, this.horizontalCollision, vec31);
            }

            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec31);
            } else {
                this.minorHorizontalCollision = false;
            }

            BlockPos blockpos = this.getOnPosLegacy();
            BlockState blockstate = this.level().getBlockState(blockpos);

            if (this.isLocalInstanceAuthoritative()) {
                this.checkFallDamage(vec31.y, this.onGround(), blockstate, blockpos);
            }

            if (this.isRemoved()) {
                profilerfiller.pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 vec35 = this.getDeltaMovement();

                    this.setDeltaMovement(flag ? 0.0D : vec35.x, vec35.y, flag1 ? 0.0D : vec35.z);
                }

                if (this.canSimulateMovement()) {
                    Block block = blockstate.getBlock();

                    if (delta.y != vec31.y) {
                        block.updateEntityMovementAfterFallOn(this.level(), this);
                    }
                }

                if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
                    Entity.MovementEmission entity_movementemission = this.getMovementEmission();

                    if (entity_movementemission.emitsAnything() && !this.isPassenger()) {
                        this.applyMovementEmissionAndPlaySound(entity_movementemission, vec31, blockpos, blockstate);
                    }
                }

                float f = this.getBlockSpeedFactor();

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 1.0D, (double) f));
                profilerfiller.pop();
            }
        }
    }

    private void applyMovementEmissionAndPlaySound(Entity.MovementEmission emission, Vec3 clippedMovement, BlockPos effectPos, BlockState effectState) {
        float f = 0.6F;
        float f1 = (float) (clippedMovement.length() * (double) 0.6F);
        float f2 = (float) (clippedMovement.horizontalDistance() * (double) 0.6F);
        BlockPos blockpos1 = this.getOnPos();
        BlockState blockstate1 = this.level().getBlockState(blockpos1);
        boolean flag = this.isStateClimbable(blockstate1);

        this.moveDist += flag ? f1 : f2;
        this.flyDist += f1;
        if (this.moveDist > this.nextStep && !blockstate1.isAir()) {
            boolean flag1 = blockpos1.equals(effectPos);
            boolean flag2 = this.vibrationAndSoundEffectsFromBlock(effectPos, effectState, emission.emitsSounds(), flag1, clippedMovement);

            if (!flag1) {
                flag2 |= this.vibrationAndSoundEffectsFromBlock(blockpos1, blockstate1, false, emission.emitsEvents(), clippedMovement);
            }

            if (flag2) {
                this.nextStep = this.nextStep();
            } else if (this.isInWater()) {
                this.nextStep = this.nextStep();
                if (emission.emitsSounds()) {
                    this.waterSwimSound();
                }

                if (emission.emitsEvents()) {
                    this.gameEvent(GameEvent.SWIM);
                }
            }
        } else if (blockstate1.isAir()) {
            this.processFlappingMovement();
        }

    }

    protected void applyEffectsFromBlocks() {
        this.finalMovementsThisTick.clear();
        this.finalMovementsThisTick.addAll(this.movementThisTick);
        this.movementThisTick.clear();
        if (this.finalMovementsThisTick.isEmpty()) {
            this.finalMovementsThisTick.add(new Entity.Movement(this.oldPosition(), this.position()));
        } else if (((Entity.Movement) this.finalMovementsThisTick.getLast()).to.distanceToSqr(this.position()) > (double) 9.9999994E-11F) {
            this.finalMovementsThisTick.add(new Entity.Movement(((Entity.Movement) this.finalMovementsThisTick.getLast()).to, this.position()));
        }

        this.applyEffectsFromBlocks(this.finalMovementsThisTick);
    }

    private void addMovementThisTick(Entity.Movement movement) {
        if (this.movementThisTick.size() >= 100) {
            Entity.Movement entity_movement1 = (Entity.Movement) this.movementThisTick.removeFirst();
            Entity.Movement entity_movement2 = (Entity.Movement) this.movementThisTick.removeFirst();
            Entity.Movement entity_movement3 = new Entity.Movement(entity_movement1.from(), entity_movement2.to());

            this.movementThisTick.addFirst(entity_movement3);
        }

        this.movementThisTick.add(movement);
    }

    public void removeLatestMovementRecording() {
        if (!this.movementThisTick.isEmpty()) {
            this.movementThisTick.removeLast();
        }

    }

    protected void clearMovementThisTick() {
        this.movementThisTick.clear();
    }

    public boolean hasMovedHorizontallyRecently() {
        return Math.abs(this.lastKnownSpeed.horizontalDistance()) > (double) 1.0E-5F;
    }

    public void applyEffectsFromBlocks(Vec3 from, Vec3 to) {
        this.applyEffectsFromBlocks(List.of(new Entity.Movement(from, to)));
    }

    private void applyEffectsFromBlocks(List<Entity.Movement> movements) {
        if (this.isAffectedByBlocks()) {
            if (this.onGround()) {
                BlockPos blockpos = this.getOnPosLegacy();
                BlockState blockstate = this.level().getBlockState(blockpos);

                blockstate.getBlock().stepOn(this.level(), blockpos, blockstate, this);
            }

            boolean flag = this.isOnFire();
            boolean flag1 = this.isFreezing();
            int i = this.getRemainingFireTicks();

            this.checkInsideBlocks(movements, this.insideEffectCollector);
            this.insideEffectCollector.applyAndClear(this);
            if (this.isInRain()) {
                this.clearFire();
            }

            if (flag && !this.isOnFire() || flag1 && !this.isFreezing()) {
                this.playEntityOnFireExtinguishedSound();
            }

            boolean flag2 = this.getRemainingFireTicks() > i;

            if (!this.level().isClientSide() && !this.isOnFire() && !flag2) {
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }

        }
    }

    protected boolean isAffectedByBlocks() {
        return !this.isRemoved() && !this.noPhysics;
    }

    private boolean isStateClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPos pos, BlockState blockState, boolean shouldSound, boolean shouldVibrate, Vec3 clippedMovement) {
        if (blockState.isAir()) {
            return false;
        } else {
            boolean flag2 = this.isStateClimbable(blockState);

            if ((this.onGround() || flag2 || this.isCrouching() && clippedMovement.y == 0.0D || this.isOnRails()) && !this.isSwimming()) {
                if (shouldSound) {
                    this.walkingStepSound(pos, blockState);
                }

                if (shouldVibrate) {
                    this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.Context.of(this, blockState));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 movement) {
        return false;
    }

    protected void playEntityOnFireExtinguishedSound() {
        if (!this.level.isClientSide()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, this.getSoundSource(), 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

    }

    public void extinguishFire() {
        if (this.isOnFire()) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }

    }

    /** @deprecated */
    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPos getOnPos(float offset) {
        if (this.mainSupportingBlockPos.isPresent()) {
            BlockPos blockpos = (BlockPos) this.mainSupportingBlockPos.get();

            if (offset <= 1.0E-5F) {
                return blockpos;
            } else {
                BlockState blockstate = this.level().getBlockState(blockpos);

                return ((double) offset > 0.5D || !blockstate.is(BlockTags.FENCES)) && !blockstate.is(BlockTags.WALLS) && !(blockstate.getBlock() instanceof FenceGateBlock) ? blockpos.atY(Mth.floor(this.position.y - (double) offset)) : blockpos;
            }
        } else {
            int i = Mth.floor(this.position.x);
            int j = Mth.floor(this.position.y - (double) offset);
            int k = Mth.floor(this.position.z);

            return new BlockPos(i, j, k);
        }
    }

    protected float getBlockJumpFactor() {
        float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();

        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        BlockState blockstate = this.level().getBlockState(this.blockPosition());
        float f = blockstate.getBlock().getSpeedFactor();

        return !blockstate.is(Blocks.WATER) && !blockstate.is(Blocks.BUBBLE_COLUMN) ? ((double) f == 1.0D ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f) : f;
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 delta, MoverType moverType) {
        return delta;
    }

    protected Vec3 limitPistonMovement(Vec3 vec) {
        if (vec.lengthSqr() <= 1.0E-7D) {
            return vec;
        } else {
            long i = this.level().getGameTime();

            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            if (vec.x != 0.0D) {
                double d0 = this.applyPistonMovementRestriction(Direction.Axis.X, vec.x);

                return Math.abs(d0) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(d0, 0.0D, 0.0D);
            } else if (vec.y != 0.0D) {
                double d1 = this.applyPistonMovementRestriction(Direction.Axis.Y, vec.y);

                return Math.abs(d1) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(0.0D, d1, 0.0D);
            } else if (vec.z != 0.0D) {
                double d2 = this.applyPistonMovementRestriction(Direction.Axis.Z, vec.z);

                return Math.abs(d2) <= (double) 1.0E-5F ? Vec3.ZERO : new Vec3(0.0D, 0.0D, d2);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis axis, double amount) {
        int i = axis.ordinal();
        double d1 = Mth.clamp(amount + this.pistonDeltas[i], -0.51D, 0.51D);

        amount = d1 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d1;
        return amount;
    }

    public double getAvailableSpaceBelow(double maxDistance) {
        AABB aabb = this.getBoundingBox();
        AABB aabb1 = aabb.setMinY(aabb.minY - maxDistance).setMaxY(aabb.minY);
        List<VoxelShape> list = collectAllColliders(this, this.level, aabb1);

        return list.isEmpty() ? maxDistance : -Shapes.collide(Direction.Axis.Y, aabb, list, -maxDistance);
    }

    private Vec3 collide(Vec3 movement) {
        AABB aabb = this.getBoundingBox();
        List<VoxelShape> list = this.level().getEntityCollisions(this, aabb.expandTowards(movement));
        Vec3 vec31 = movement.lengthSqr() == 0.0D ? movement : collideBoundingBox(this, movement, aabb, this.level(), list);
        boolean flag = movement.x != vec31.x;
        boolean flag1 = movement.y != vec31.y;
        boolean flag2 = movement.z != vec31.z;
        boolean flag3 = flag1 && movement.y < 0.0D;

        if (this.maxUpStep() > 0.0F && (flag3 || this.onGround()) && (flag || flag2)) {
            AABB aabb1 = flag3 ? aabb.move(0.0D, vec31.y, 0.0D) : aabb;
            AABB aabb2 = aabb1.expandTowards(movement.x, (double) this.maxUpStep(), movement.z);

            if (!flag3) {
                aabb2 = aabb2.expandTowards(0.0D, (double) -1.0E-5F, 0.0D);
            }

            List<VoxelShape> list1 = collectColliders(this, this.level, list, aabb2);
            float f = (float) vec31.y;
            float[] afloat = collectCandidateStepUpHeights(aabb1, list1, this.maxUpStep(), f);

            for (float f1 : afloat) {
                Vec3 vec32 = collideWithShapes(new Vec3(movement.x, (double) f1, movement.z), aabb1, list1);

                if (vec32.horizontalDistanceSqr() > vec31.horizontalDistanceSqr()) {
                    double d0 = aabb.minY - aabb1.minY;

                    return vec32.subtract(0.0D, d0, 0.0D);
                }
            }
        }

        return vec31;
    }

    private static float[] collectCandidateStepUpHeights(AABB boundingBox, List<VoxelShape> colliders, float maxStepHeight, float stepHeightToSkip) {
        FloatSet floatset = new FloatArraySet(4);

        for (VoxelShape voxelshape : colliders) {
            DoubleList doublelist = voxelshape.getCoords(Direction.Axis.Y);
            DoubleListIterator doublelistiterator = doublelist.iterator();

            while (doublelistiterator.hasNext()) {
                double d0 = (Double) doublelistiterator.next();
                float f2 = (float) (d0 - boundingBox.minY);

                if (f2 >= 0.0F && f2 != stepHeightToSkip) {
                    if (f2 > maxStepHeight) {
                        break;
                    }

                    floatset.add(f2);
                }
            }
        }

        float[] afloat = floatset.toFloatArray();

        FloatArrays.unstableSort(afloat);
        return afloat;
    }

    public static Vec3 collideBoundingBox(@Nullable Entity source, Vec3 movement, AABB boundingBox, Level level, List<VoxelShape> entityColliders) {
        List<VoxelShape> list1 = collectColliders(source, level, entityColliders, boundingBox.expandTowards(movement));

        return collideWithShapes(movement, boundingBox, list1);
    }

    public static List<VoxelShape> collectAllColliders(@Nullable Entity source, Level level, AABB boundingBox) {
        List<VoxelShape> list = level.getEntityCollisions(source, boundingBox);

        return collectColliders(source, level, list, boundingBox);
    }

    private static List<VoxelShape> collectColliders(@Nullable Entity source, Level level, List<VoxelShape> entityColliders, AABB boundingBox) {
        ImmutableList.Builder<VoxelShape> immutablelist_builder = ImmutableList.builderWithExpectedSize(entityColliders.size() + 1);

        if (!entityColliders.isEmpty()) {
            immutablelist_builder.addAll(entityColliders);
        }

        WorldBorder worldborder = level.getWorldBorder();
        boolean flag = source != null && worldborder.isInsideCloseToBorder(source, boundingBox);

        if (flag) {
            immutablelist_builder.add(worldborder.getCollisionShape());
        }

        immutablelist_builder.addAll(level.getBlockCollisions(source, boundingBox));
        return immutablelist_builder.build();
    }

    private static Vec3 collideWithShapes(Vec3 movement, AABB boundingBox, List<VoxelShape> shapes) {
        if (shapes.isEmpty()) {
            return movement;
        } else {
            Vec3 vec31 = Vec3.ZERO;
            UnmodifiableIterator unmodifiableiterator = Direction.axisStepOrder(movement).iterator();

            while (unmodifiableiterator.hasNext()) {
                Direction.Axis direction_axis = (Direction.Axis) unmodifiableiterator.next();
                double d0 = movement.get(direction_axis);

                if (d0 != 0.0D) {
                    double d1 = Shapes.collide(direction_axis, boundingBox.move(vec31), shapes, d0);

                    vec31 = vec31.with(direction_axis, d1);
                }
            }

            return vec31;
        }
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    private void checkInsideBlocks(List<Entity.Movement> movements, InsideBlockEffectApplier.StepBasedCollector effectCollector) {
        if (this.isAffectedByBlocks()) {
            LongSet longset = this.visitedBlocks;

            for (Entity.Movement entity_movement : movements) {
                Vec3 vec3 = entity_movement.from;
                Vec3 vec31 = entity_movement.to().subtract(entity_movement.from());
                int i = 16;

                if (entity_movement.axisDependentOriginalMovement().isPresent() && vec31.lengthSqr() > 0.0D) {
                    UnmodifiableIterator unmodifiableiterator = Direction.axisStepOrder((Vec3) entity_movement.axisDependentOriginalMovement().get()).iterator();

                    while (unmodifiableiterator.hasNext()) {
                        Direction.Axis direction_axis = (Direction.Axis) unmodifiableiterator.next();
                        double d0 = vec31.get(direction_axis);

                        if (d0 != 0.0D) {
                            Vec3 vec32 = vec3.relative(direction_axis.getPositive(), d0);

                            i -= this.checkInsideBlocks(vec3, vec32, effectCollector, longset, i);
                            vec3 = vec32;
                        }
                    }
                } else {
                    i -= this.checkInsideBlocks(entity_movement.from(), entity_movement.to(), effectCollector, longset, 16);
                }

                if (i <= 0) {
                    this.checkInsideBlocks(entity_movement.to(), entity_movement.to(), effectCollector, longset, 1);
                }
            }

            longset.clear();
        }
    }

    private int checkInsideBlocks(Vec3 from, Vec3 to, InsideBlockEffectApplier.StepBasedCollector effectCollector, LongSet visitedBlocks, int maxMovementIterations) {
        AABB aabb;
        boolean flag;
        boolean flag1;
        label16:
        {
            aabb = this.makeBoundingBox(to).deflate((double) 1.0E-5F);
            flag = from.distanceToSqr(to) > Mth.square(0.9999900000002526D);
            Level level = this.level;

            if (level instanceof ServerLevel serverlevel) {
                if (serverlevel.getServer().debugSubscribers().hasAnySubscriberFor(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS)) {
                    flag1 = true;
                    break label16;
                }
            }

            flag1 = false;
        }

        boolean flag2 = flag1;
        AtomicInteger atomicinteger = new AtomicInteger();

        BlockGetter.forEachBlockIntersectedBetween(from, to, aabb, (blockpos, j) -> {
            if (!this.isAlive()) {
                return false;
            } else if (j >= maxMovementIterations) {
                return false;
            } else {
                atomicinteger.set(j);
                BlockState blockstate = this.level().getBlockState(blockpos);

                if (blockstate.isAir()) {
                    if (flag2) {
                        this.debugBlockIntersection((ServerLevel) this.level(), blockpos.immutable(), false, false);
                    }

                    return true;
                } else {
                    VoxelShape voxelshape = blockstate.getEntityInsideCollisionShape(this.level(), blockpos, this);
                    boolean flag3 = voxelshape == Shapes.block() || this.collidedWithShapeMovingFrom(from, to, voxelshape.move(new Vec3(blockpos)).toAabbs());
                    boolean flag4 = this.collidedWithFluid(blockstate.getFluidState(), blockpos, from, to);

                    if ((flag3 || flag4) && visitedBlocks.add(blockpos.asLong())) {
                        if (flag3) {
                            try {
                                boolean flag5 = flag || aabb.intersects(blockpos);

                                effectCollector.advanceStep(j);
                                blockstate.entityInside(this.level(), blockpos, this, effectCollector, flag5);
                                this.onInsideBlock(blockstate);
                            } catch (Throwable throwable) {
                                CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                                CrashReportCategory crashreportcategory = crashreport.addCategory("Block being collided with");

                                CrashReportCategory.populateBlockDetails(crashreportcategory, this.level(), blockpos, blockstate);
                                CrashReportCategory crashreportcategory1 = crashreport.addCategory("Entity being checked for collision");

                                this.fillCrashReportCategory(crashreportcategory1);
                                throw new ReportedException(crashreport);
                            }
                        }

                        if (flag4) {
                            effectCollector.advanceStep(j);
                            blockstate.getFluidState().entityInside(this.level(), blockpos, this, effectCollector);
                        }

                        if (flag2) {
                            this.debugBlockIntersection((ServerLevel) this.level(), blockpos.immutable(), flag3, flag4);
                        }

                        return true;
                    } else {
                        return true;
                    }
                }
            }
        });
        return atomicinteger.get() + 1;
    }

    private void debugBlockIntersection(ServerLevel level, BlockPos pos, boolean insideBlock, boolean insideFluid) {
        DebugEntityBlockIntersection debugentityblockintersection;

        if (insideFluid) {
            debugentityblockintersection = DebugEntityBlockIntersection.IN_FLUID;
        } else if (insideBlock) {
            debugentityblockintersection = DebugEntityBlockIntersection.IN_BLOCK;
        } else {
            debugentityblockintersection = DebugEntityBlockIntersection.IN_AIR;
        }

        level.debugSynchronizers().sendBlockValue(pos, DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, debugentityblockintersection);
    }

    public boolean collidedWithFluid(FluidState fluidState, BlockPos blockPos, Vec3 from, Vec3 to) {
        AABB aabb = fluidState.getAABB(this.level(), blockPos);

        return aabb != null && this.collidedWithShapeMovingFrom(from, to, List.of(aabb));
    }

    public boolean collidedWithShapeMovingFrom(Vec3 from, Vec3 to, List<AABB> aabbs) {
        AABB aabb = this.makeBoundingBox(from);
        Vec3 vec32 = to.subtract(from);

        return aabb.collidedAlongVector(vec32, aabbs);
    }

    protected void onInsideBlock(BlockState state) {}

    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos spawnSuggestion) {
        BlockPos blockpos1 = level.getRespawnData().pos();
        Vec3 vec3 = blockpos1.getCenter();
        int i = level.getChunkAt(blockpos1).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos1.getX(), blockpos1.getZ()) + 1;

        return BlockPos.containing(vec3.x, (double) i, vec3.z);
    }

    public void gameEvent(Holder<GameEvent> event, @Nullable Entity sourceEntity) {
        this.level().gameEvent(sourceEntity, event, this.position);
    }

    public void gameEvent(Holder<GameEvent> event) {
        this.gameEvent(event, this);
    }

    private void walkingStepSound(BlockPos onPos, BlockState onState) {
        this.playStepSound(onPos, onState);
        if (this.shouldPlayAmethystStepSound(onState)) {
            this.playAmethystStepSound();
        }

    }

    protected void waterSwimSound() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3 vec3 = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3.x * vec3.x * (double) 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * (double) 0.2F) * f);

        this.playSwimSound(f1);
    }

    protected BlockPos getPrimaryStepSoundBlockPos(BlockPos affectingPos) {
        BlockPos blockpos1 = affectingPos.above();
        BlockState blockstate = this.level().getBlockState(blockpos1);

        return !blockstate.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !blockstate.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? affectingPos : blockpos1;
    }

    protected void playCombinationStepSounds(BlockState primaryStepSound, BlockState secondaryStepSound) {
        SoundType soundtype = primaryStepSound.getSoundType();

        this.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
        this.playMuffledStepSound(secondaryStepSound);
    }

    protected void playMuffledStepSound(BlockState blockState) {
        SoundType soundtype = blockState.getSoundType();

        this.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.05F, soundtype.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPos pos, BlockState blockState) {
        SoundType soundtype = blockState.getSoundType();

        this.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(BlockState affectingState) {
        return affectingState.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity *= (float) Math.pow(0.997D, (double) (this.tickCount - this.lastCrystalSoundPlayTick));
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;

        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, f1, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float volume) {
        this.playSound(this.getSwimSound(), volume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {}

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
        }

    }

    public void playSound(SoundEvent sound) {
        if (!this.isSilent()) {
            this.playSound(sound, 1.0F, 1.0F);
        }

    }

    public boolean isSilent() {
        return (Boolean) this.entityData.get(Entity.DATA_SILENT);
    }

    public void setSilent(boolean silent) {
        this.entityData.set(Entity.DATA_SILENT, silent);
    }

    public boolean isNoGravity() {
        return (Boolean) this.entityData.get(Entity.DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean noGravity) {
        this.entityData.set(Entity.DATA_NO_GRAVITY, noGravity);
    }

    protected double getDefaultGravity() {
        return 0.0D;
    }

    public final double getGravity() {
        return this.isNoGravity() ? 0.0D : this.getDefaultGravity();
    }

    protected void applyGravity() {
        double d0 = this.getGravity();

        if (d0 != 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0, 0.0D));
        }

    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    public final void doCheckFallDamage(double xa, double ya, double za, boolean onGround) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(onGround, new Vec3(xa, ya, za));
            BlockPos blockpos = this.getOnPosLegacy();
            BlockState blockstate = this.level().getBlockState(blockpos);

            this.checkFallDamage(ya, onGround, blockstate, blockpos);
        }
    }

    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (!this.isInWater() && ya < 0.0D) {
            this.fallDistance -= (double) ((float) ya);
        }

        if (onGround) {
            if (this.fallDistance > 0.0D) {
                onState.getBlock().fallOn(this.level(), onState, pos, this, this.fallDistance);
                this.level().gameEvent(GameEvent.HIT_GROUND, this.position, GameEvent.Context.of(this, (BlockState) this.mainSupportingBlockPos.map((blockpos1) -> {
                    return this.level().getBlockState(blockpos1);
                }).orElse(onState)));
            }

            this.resetFallDistance();
        }

    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        } else {
            this.propagateFallToPassengers(fallDistance, damageModifier, damageSource);
            return false;
        }
    }

    protected void propagateFallToPassengers(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (this.isVehicle()) {
            for (Entity entity : this.getPassengers()) {
                entity.causeFallDamage(fallDistance, damageModifier, damageSource);
            }
        }

    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    boolean isInRain() {
        BlockPos blockpos = this.blockPosition();

        return this.level().isRainingAt(blockpos) || this.level().isRainingAt(BlockPos.containing((double) blockpos.getX(), this.getBoundingBox().maxY, (double) blockpos.getZ()));
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInLiquid() {
        return this.isInWater() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public boolean isInShallowWater() {
        return this.isInWater() && !this.isUnderWater();
    }

    public boolean isInClouds() {
        if (ARGB.alpha((Integer) this.level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, this.position())) == 0) {
            return false;
        } else {
            float f = (Float) this.level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, this.position());

            if (this.getY() + (double) this.getBbHeight() < (double) f) {
                return false;
            } else {
                float f1 = f + 4.0F;

                return this.getY() <= (double) f1;
            }
        }
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER));
        }

    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d0 = (Boolean) this.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA) ? 0.007D : 0.0023333333333333335D;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d0);

        return this.isInWater() || flag;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        Entity entity = this.getVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            if (!abstractboat.isUnderWater()) {
                this.wasTouchingWater = false;
                return;
            }
        }

        if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
        } else {
            this.wasTouchingWater = false;
        }

    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double d0 = this.getEyeY();
        Entity entity = this.getVehicle();

        if (entity instanceof AbstractBoat abstractboat) {
            if (!abstractboat.isUnderWater() && abstractboat.getBoundingBox().maxY >= d0 && abstractboat.getBoundingBox().minY <= d0) {
                return;
            }
        }

        BlockPos blockpos = BlockPos.containing(this.getX(), d0, this.getZ());
        FluidState fluidstate = this.level().getFluidState(blockpos);
        double d1 = (double) ((float) blockpos.getY() + fluidstate.getHeight(this.level(), blockpos));

        if (d1 > d0) {
            Stream stream = fluidstate.getTags();
            Set set = this.fluidOnEyes;

            Objects.requireNonNull(this.fluidOnEyes);
            stream.forEach(set::add);
        }

    }

    protected void doWaterSplashEffect() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3 = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3.x * vec3.x * (double) 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * (double) 0.2F) * f);

        if (f1 < 0.25F) {
            this.playSound(this.getSwimSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) Mth.floor(this.getY());

        for (int i = 0; (float) i < 1.0F + this.dimensions.width() * 20.0F; ++i) {
            double d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            double d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();

            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3.x, vec3.y - this.random.nextDouble() * (double) 0.2F, vec3.z);
        }

        for (int j = 0; (float) j < 1.0F + this.dimensions.width() * 20.0F; ++j) {
            double d2 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            double d3 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();

            this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d2, (double) (f2 + 1.0F), this.getZ() + d3, vec3.x, vec3.y, vec3.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    /** @deprecated */
    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPos blockpos = this.getOnPosLegacy();
        BlockState blockstate = this.level().getBlockState(blockpos);

        if (blockstate.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3 = this.getDeltaMovement();
            BlockPos blockpos1 = this.blockPosition();
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();
            double d1 = this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();

            if (blockpos1.getX() != blockpos.getX()) {
                d0 = Mth.clamp(d0, (double) blockpos.getX(), (double) blockpos.getX() + 1.0D);
            }

            if (blockpos1.getZ() != blockpos.getZ()) {
                d1 = Mth.clamp(d1, (double) blockpos.getZ(), (double) blockpos.getZ() + 1.0D);
            }

            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), d0, this.getY() + 0.1D, d1, vec3.x * -4.0D, 1.5D, vec3.z * -4.0D);
        }

    }

    public boolean isEyeInFluid(TagKey<Fluid> type) {
        return this.fluidOnEyes.contains(type);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public void moveRelative(float speed, Vec3 input) {
        Vec3 vec31 = getInputVector(input, speed, this.getYRot());

        this.setDeltaMovement(this.getDeltaMovement().add(vec31));
    }

    protected static Vec3 getInputVector(Vec3 input, float speed, float yRot) {
        double d0 = input.lengthSqr();

        if (d0 < 1.0E-7D) {
            return Vec3.ZERO;
        } else {
            Vec3 vec31 = (d0 > 1.0D ? input.normalize() : input).scale((double) speed);
            float f2 = Mth.sin((double) (yRot * ((float) Math.PI / 180F)));
            float f3 = Mth.cos((double) (yRot * ((float) Math.PI / 180F)));

            return new Vec3(vec31.x * (double) f3 - vec31.z * (double) f2, vec31.y, vec31.z * (double) f3 + vec31.x * (double) f2);
        }
    }

    /** @deprecated */
    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())) : 0.0F;
    }

    public void absSnapTo(double x, double y, double z, float yRot, float xRot) {
        this.absSnapTo(x, y, z);
        this.absSnapRotationTo(yRot, xRot);
    }

    public void absSnapRotationTo(float yRot, float xRot) {
        this.setYRot(yRot % 360.0F);
        this.setXRot(Mth.clamp(xRot, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void absSnapTo(double x, double y, double z) {
        double d3 = Mth.clamp(x, -3.0E7D, 3.0E7D);
        double d4 = Mth.clamp(z, -3.0E7D, 3.0E7D);

        this.xo = d3;
        this.yo = y;
        this.zo = d4;
        this.setPos(d3, y, d4);
    }

    public void snapTo(Vec3 pos) {
        this.snapTo(pos.x, pos.y, pos.z);
    }

    public void snapTo(double x, double y, double z) {
        this.snapTo(x, y, z, this.getYRot(), this.getXRot());
    }

    public void snapTo(BlockPos spawnPos, float yRot, float xRot) {
        this.snapTo(spawnPos.getBottomCenter(), yRot, xRot);
    }

    public void snapTo(Vec3 spawnPos, float yRot, float xRot) {
        this.snapTo(spawnPos.x, spawnPos.y, spawnPos.z, yRot, xRot);
    }

    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        this.setPosRaw(x, y, z);
        this.setYRot(yRot);
        this.setXRot(xRot);
        this.setOldPosAndRot();
        this.reapplyPosition();
    }

    public final void setOldPosAndRot() {
        this.setOldPos();
        this.setOldRot();
    }

    public final void setOldPosAndRot(Vec3 position, float yRot, float xRot) {
        this.setOldPos(position);
        this.setOldRot(yRot, xRot);
    }

    protected void setOldPos() {
        this.setOldPos(this.position);
    }

    public void setOldRot() {
        this.setOldRot(this.getYRot(), this.getXRot());
    }

    private void setOldPos(Vec3 position) {
        this.xo = this.xOld = position.x;
        this.yo = this.yOld = position.y;
        this.zo = this.zOld = position.z;
    }

    private void setOldRot(float yRot, float xRot) {
        this.yRotO = yRot;
        this.xRotO = xRot;
    }

    public final Vec3 oldPosition() {
        return new Vec3(this.xOld, this.yOld, this.zOld);
    }

    public float distanceTo(Entity entity) {
        float f = (float) (this.getX() - entity.getX());
        float f1 = (float) (this.getY() - entity.getY());
        float f2 = (float) (this.getZ() - entity.getZ());

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double x2, double y2, double z2) {
        double d3 = this.getX() - x2;
        double d4 = this.getY() - y2;
        double d5 = this.getZ() - z2;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 pos) {
        double d0 = this.getX() - pos.x;
        double d1 = this.getY() - pos.y;
        double d2 = this.getZ() - pos.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void playerTouch(Player player) {}

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                double d0 = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);

                if (d2 >= (double) 0.01F) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;

                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= (double) 0.05F;
                    d1 *= (double) 0.05F;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public void push(Vec3 impulse) {
        if (impulse.isFinite()) {
            this.push(impulse.x, impulse.y, impulse.z);
        }

    }

    public void push(double xa, double ya, double za) {
        if (Double.isFinite(xa) && Double.isFinite(ya) && Double.isFinite(za)) {
            this.setDeltaMovement(this.getDeltaMovement().add(xa, ya, za));
            this.needsSync = true;
        }

    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    /** @deprecated */
    @Deprecated
    public final void hurt(DamageSource source, float damage) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            this.hurtServer(serverlevel, source, damage);
        }

    }

    /** @deprecated */
    @Deprecated
    public final boolean hurtOrSimulate(DamageSource source, float damage) {
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            return this.hurtServer(serverlevel, source, damage);
        } else {
            return this.hurtClient(source);
        }
    }

    public abstract boolean hurtServer(ServerLevel level, DamageSource source, float damage);

    public boolean hurtClient(DamageSource source) {
        return false;
    }

    public final Vec3 getViewVector(float a) {
        return this.calculateViewVector(this.getViewXRot(a), this.getViewYRot(a));
    }

    public Direction getNearestViewDirection() {
        return Direction.getApproximateNearest(this.getViewVector(1.0F));
    }

    public float getViewXRot(float a) {
        return this.getXRot(a);
    }

    public float getViewYRot(float a) {
        return this.getYRot(a);
    }

    public float getXRot(float partialTicks) {
        return partialTicks == 1.0F ? this.getXRot() : Mth.lerp(partialTicks, this.xRotO, this.getXRot());
    }

    public float getYRot(float partialTicks) {
        return partialTicks == 1.0F ? this.getYRot() : Mth.rotLerp(partialTicks, this.yRotO, this.getYRot());
    }

    public final Vec3 calculateViewVector(float xRot, float yRot) {
        float f2 = xRot * ((float) Math.PI / 180F);
        float f3 = -yRot * ((float) Math.PI / 180F);
        float f4 = Mth.cos((double) f3);
        float f5 = Mth.sin((double) f3);
        float f6 = Mth.cos((double) f2);
        float f7 = Mth.sin((double) f2);

        return new Vec3((double) (f5 * f6), (double) (-f7), (double) (f4 * f6));
    }

    public final Vec3 getUpVector(float a) {
        return this.calculateUpVector(this.getViewXRot(a), this.getViewYRot(a));
    }

    protected final Vec3 calculateUpVector(float xRot, float yRot) {
        return this.calculateViewVector(xRot - 90.0F, yRot);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float partialTickTime) {
        double d0 = Mth.lerp((double) partialTickTime, this.xo, this.getX());
        double d1 = Mth.lerp((double) partialTickTime, this.yo, this.getY()) + (double) this.getEyeHeight();
        double d2 = Mth.lerp((double) partialTickTime, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public Vec3 getLightProbePosition(float partialTickTime) {
        return this.getEyePosition(partialTickTime);
    }

    public final Vec3 getPosition(float partialTickTime) {
        double d0 = Mth.lerp((double) partialTickTime, this.xo, this.getX());
        double d1 = Mth.lerp((double) partialTickTime, this.yo, this.getY());
        double d2 = Mth.lerp((double) partialTickTime, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public HitResult pick(double range, float a, boolean withLiquids) {
        Vec3 vec3 = this.getEyePosition(a);
        Vec3 vec31 = this.getViewVector(a);
        Vec3 vec32 = vec3.add(vec31.x * range, vec31.y * range, vec31.z * range);

        return this.level().clip(new ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, withLiquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public void awardKillScore(Entity victim, DamageSource killingBlow) {
        if (victim instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer) victim, this, killingBlow);
        }

    }

    public boolean shouldRender(double camX, double camY, double camZ) {
        double d3 = this.getX() - camX;
        double d4 = this.getY() - camY;
        double d5 = this.getZ() - camZ;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;

        return this.shouldRenderAtSqrDistance(d6);
    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize();

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * Entity.viewScale;
        return distance < d1 * d1;
    }

    public boolean saveAsPassenger(ValueOutput output) {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            String s = this.getEncodeId();

            if (s == null) {
                return false;
            } else {
                output.putString("id", s);
                this.saveWithoutId(output);
                return true;
            }
        }
    }

    public boolean save(ValueOutput output) {
        return this.isPassenger() ? false : this.saveAsPassenger(output);
    }

    public void saveWithoutId(ValueOutput output) {
        try {
            if (this.vehicle != null) {
                output.store("Pos", Vec3.CODEC, new Vec3(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
            } else {
                output.store("Pos", Vec3.CODEC, this.position());
            }

            output.store("Motion", Vec3.CODEC, this.getDeltaMovement());
            output.store("Rotation", Vec2.CODEC, new Vec2(this.getYRot(), this.getXRot()));
            output.putDouble("fall_distance", this.fallDistance);
            output.putShort("Fire", (short) this.remainingFireTicks);
            output.putShort("Air", (short) this.getAirSupply());
            output.putBoolean("OnGround", this.onGround());
            output.putBoolean("Invulnerable", this.invulnerable);
            output.putInt("PortalCooldown", this.portalCooldown);
            output.store("UUID", UUIDUtil.CODEC, this.getUUID());
            output.storeNullable("CustomName", ComponentSerialization.CODEC, this.getCustomName());
            if (this.isCustomNameVisible()) {
                output.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                output.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                output.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                output.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();

            if (i > 0) {
                output.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                output.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            if (!this.tags.isEmpty()) {
                output.store("Tags", Entity.TAG_LIST_CODEC, List.copyOf(this.tags));
            }

            if (!this.customData.isEmpty()) {
                output.store("data", CustomData.CODEC, this.customData);
            }

            this.addAdditionalSaveData(output);
            if (this.isVehicle()) {
                ValueOutput.ValueOutputList valueoutput_valueoutputlist = output.childrenList("Passengers");

                for (Entity entity : this.getPassengers()) {
                    ValueOutput valueoutput1 = valueoutput_valueoutputlist.addChild();

                    if (!entity.saveAsPassenger(valueoutput1)) {
                        valueoutput_valueoutputlist.discardLast();
                    }
                }

                if (valueoutput_valueoutputlist.isEmpty()) {
                    output.discard("Passengers");
                }
            }

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being saved");

            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    public void load(ValueInput input) {
        try {
            Vec3 vec3 = (Vec3) input.read("Pos", Vec3.CODEC).orElse(Vec3.ZERO);
            Vec3 vec31 = (Vec3) input.read("Motion", Vec3.CODEC).orElse(Vec3.ZERO);
            Vec2 vec2 = (Vec2) input.read("Rotation", Vec2.CODEC).orElse(Vec2.ZERO);

            this.setDeltaMovement(Math.abs(vec31.x) > 10.0D ? 0.0D : vec31.x, Math.abs(vec31.y) > 10.0D ? 0.0D : vec31.y, Math.abs(vec31.z) > 10.0D ? 0.0D : vec31.z);
            this.needsSync = true;
            double d0 = 3.0000512E7D;

            this.setPosRaw(Mth.clamp(vec3.x, -3.0000512E7D, 3.0000512E7D), Mth.clamp(vec3.y, -2.0E7D, 2.0E7D), Mth.clamp(vec3.z, -3.0000512E7D, 3.0000512E7D));
            this.setYRot(vec2.x);
            this.setXRot(vec2.y);
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = input.getDoubleOr("fall_distance", 0.0D);
            this.remainingFireTicks = input.getShortOr("Fire", (short) 0);
            this.setAirSupply(input.getIntOr("Air", this.getMaxAirSupply()));
            this.onGround = input.getBooleanOr("OnGround", false);
            this.invulnerable = input.getBooleanOr("Invulnerable", false);
            this.portalCooldown = input.getIntOr("PortalCooldown", 0);
            input.read("UUID", UUIDUtil.CODEC).ifPresent((uuid) -> {
                this.uuid = uuid;
                this.stringUUID = this.uuid.toString();
            });
            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.getYRot()) && Double.isFinite((double) this.getXRot())) {
                    this.reapplyPosition();
                    this.setRot(this.getYRot(), this.getXRot());
                    this.setCustomName((Component) input.read("CustomName", ComponentSerialization.CODEC).orElse((Object) null));
                    this.setCustomNameVisible(input.getBooleanOr("CustomNameVisible", false));
                    this.setSilent(input.getBooleanOr("Silent", false));
                    this.setNoGravity(input.getBooleanOr("NoGravity", false));
                    this.setGlowingTag(input.getBooleanOr("Glowing", false));
                    this.setTicksFrozen(input.getIntOr("TicksFrozen", 0));
                    this.hasVisualFire = input.getBooleanOr("HasVisualFire", false);
                    this.customData = (CustomData) input.read("data", CustomData.CODEC).orElse(CustomData.EMPTY);
                    this.tags.clear();
                    Optional optional = input.read("Tags", Entity.TAG_LIST_CODEC);
                    Set set = this.tags;

                    Objects.requireNonNull(this.tags);
                    optional.ifPresent(set::addAll);
                    this.readAdditionalSaveData(input);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being loaded");

            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    public final @Nullable String getEncodeId() {
        EntityType<?> entitytype = this.getType();
        Identifier identifier = EntityType.getKey(entitytype);

        return !entitytype.canSerialize() ? null : identifier.toString();
    }

    protected abstract void readAdditionalSaveData(ValueInput input);

    protected abstract void addAdditionalSaveData(ValueOutput output);

    public @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemLike resource) {
        return this.spawnAtLocation(level, new ItemStack(resource), 0.0F);
    }

    public @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack) {
        return this.spawnAtLocation(level, itemStack, 0.0F);
    }

    public @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack, Vec3 offset) {
        if (itemStack.isEmpty()) {
            return null;
        } else {
            ItemEntity itementity = new ItemEntity(level, this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z, itemStack);

            itementity.setDefaultPickUpDelay();
            level.addFreshEntity(itementity);
            return itementity;
        }
    }

    public @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack, float offset) {
        return this.spawnAtLocation(level, itemStack, new Vec3(0.0D, (double) offset, 0.0D));
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else {
            float f = this.dimensions.width() * 0.8F;
            AABB aabb = AABB.ofSize(this.getEyePosition(), (double) f, 1.0E-6D, (double) f);

            return BlockPos.betweenClosedStream(aabb).anyMatch((blockpos) -> {
                BlockState blockstate = this.level().getBlockState(blockpos);

                return !blockstate.isAir() && blockstate.isSuffocating(this.level(), blockpos) && Shapes.joinIsNotEmpty(blockstate.getCollisionShape(this.level(), blockpos).move((Vec3i) blockpos), Shapes.create(aabb), BooleanOp.AND);
            });
        }
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && player.isSecondaryUseActive() && this instanceof Leashable leashable) {
            if (leashable.canBeLeashed() && this.isAlive()) {
                label83:
                {
                    if (this instanceof LivingEntity) {
                        LivingEntity livingentity = (LivingEntity) this;

                        if (livingentity.isBaby()) {
                            break label83;
                        }
                    }

                    List<Leashable> list = Leashable.leashableInArea(this, (leashable1) -> {
                        return leashable1.getLeashHolder() == player;
                    });

                    if (!list.isEmpty()) {
                        boolean flag = false;

                        for (Leashable leashable1 : list) {
                            if (leashable1.canHaveALeashAttachedTo(this)) {
                                leashable1.setLeashedTo(this, true);
                                flag = true;
                            }
                        }

                        if (flag) {
                            this.level().gameEvent(GameEvent.ENTITY_ACTION, this.blockPosition(), GameEvent.Context.of((Entity) player));
                            this.playSound(SoundEvents.LEAD_TIED);
                            return InteractionResult.SUCCESS_SERVER.withoutItem();
                        }
                    }
                }
            }
        }

        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.SHEARS) && this.shearOffAllLeashConnections(player)) {
            itemstack.hurtAndBreak(1, player, hand);
            return InteractionResult.SUCCESS;
        } else {
            if (this instanceof Mob) {
                Mob mob = (Mob) this;

                if (itemstack.is(Items.SHEARS) && mob.canShearEquipment(player) && !player.isSecondaryUseActive() && this.attemptToShearEquipment(player, hand, itemstack, mob)) {
                    return InteractionResult.SUCCESS;
                }
            }

            if (this.isAlive() && this instanceof Leashable) {
                Leashable leashable2 = (Leashable) this;

                if (leashable2.getLeashHolder() == player) {
                    if (!this.level().isClientSide()) {
                        if (player.hasInfiniteMaterials()) {
                            leashable2.removeLeash();
                        } else {
                            leashable2.dropLeash();
                        }

                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        this.playSound(SoundEvents.LEAD_UNTIED);
                    }

                    return InteractionResult.SUCCESS.withoutItem();
                }

                ItemStack itemstack1 = player.getItemInHand(hand);

                if (itemstack1.is(Items.LEAD) && !(leashable2.getLeashHolder() instanceof Player)) {
                    if (this.level().isClientSide()) {
                        return InteractionResult.CONSUME;
                    }

                    if (leashable2.canHaveALeashAttachedTo(player)) {
                        if (leashable2.isLeashed()) {
                            leashable2.dropLeash();
                        }

                        leashable2.setLeashedTo(player, true);
                        this.playSound(SoundEvents.LEAD_TIED);
                        itemstack1.shrink(1);
                        return InteractionResult.SUCCESS_SERVER;
                    }
                }
            }

            return InteractionResult.PASS;
        }
    }

    public boolean shearOffAllLeashConnections(@Nullable Player player) {
        boolean flag = this.dropAllLeashConnections(player);

        if (flag) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                serverlevel.playSound((Entity) null, this.blockPosition(), SoundEvents.SHEARS_SNIP, player != null ? player.getSoundSource() : this.getSoundSource());
            }
        }

        return flag;
    }

    public boolean dropAllLeashConnections(@Nullable Player player) {
        List<Leashable> list = Leashable.leashableLeashedTo(this);
        boolean flag = !list.isEmpty();

        if (this instanceof Leashable leashable) {
            if (leashable.isLeashed()) {
                leashable.dropLeash();
                flag = true;
            }
        }

        for (Leashable leashable1 : list) {
            leashable1.dropLeash();
        }

        if (flag) {
            this.gameEvent(GameEvent.SHEAR, player);
            return true;
        } else {
            return false;
        }
    }

    private boolean attemptToShearEquipment(Player player, InteractionHand hand, ItemStack heldItem, Mob target) {
        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack1 = target.getItemBySlot(equipmentslot);
            Equippable equippable = (Equippable) itemstack1.get(DataComponents.EQUIPPABLE);

            if (equippable != null && equippable.canBeSheared() && (!EnchantmentHelper.has(itemstack1, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative())) {
                heldItem.hurtAndBreak(1, player, hand.asEquipmentSlot());
                Vec3 vec3 = this.dimensions.attachments().getAverage(EntityAttachment.PASSENGER);

                target.setItemSlotAndDropWhenKilled(equipmentslot, ItemStack.EMPTY);
                this.gameEvent(GameEvent.SHEAR, player);
                this.playSound((SoundEvent) equippable.shearingSound().value());
                Level level = this.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    this.spawnAtLocation(serverlevel, itemstack1, vec3);
                    CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT.trigger((ServerPlayer) player, itemstack1, target);
                }

                return true;
            }
        }

        return false;
    }

    public boolean canCollideWith(Entity entity) {
        return entity.canBeCollidedWith(this) && !this.isPassengerOfSameVehicle(entity);
    }

    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            this.positionRider(passenger, Entity::setPos);
        }
    }

    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        Vec3 vec3 = this.getPassengerRidingPosition(passenger);
        Vec3 vec31 = passenger.getVehicleAttachmentPoint(this);

        moveFunction.accept(passenger, vec3.x - vec31.x, vec3.y - vec31.y, vec3.z - vec31.z);
    }

    public void onPassengerTurned(Entity passenger) {}

    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
    }

    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(this.getPassengerAttachmentPoint(passenger, this.dimensions, 1.0F));
    }

    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return getDefaultPassengerAttachmentPoint(this, passenger, dimensions.attachments());
    }

    protected static Vec3 getDefaultPassengerAttachmentPoint(Entity vehicle, Entity passenger, EntityAttachments attachments) {
        int i = vehicle.getPassengers().indexOf(passenger);

        return attachments.getClamped(EntityAttachment.PASSENGER, i, vehicle.yRot);
    }

    public final boolean startRiding(Entity entity) {
        return this.startRiding(entity, false, true);
    }

    public boolean showVehicleHealth() {
        return this instanceof LivingEntity;
    }

    public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        if (entityToRide == this.vehicle) {
            return false;
        } else if (!entityToRide.couldAcceptPassenger()) {
            return false;
        } else if (!this.level().isClientSide() && !entityToRide.type.canSerialize()) {
            return false;
        } else {
            for (Entity entity1 = entityToRide; entity1.vehicle != null; entity1 = entity1.vehicle) {
                if (entity1.vehicle == this) {
                    return false;
                }
            }

            if (force || this.canRide(entityToRide) && entityToRide.canAddPassenger(this)) {
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(Pose.STANDING);
                this.vehicle = entityToRide;
                this.vehicle.addPassenger(this);
                if (sendEventAndTriggers) {
                    this.level().gameEvent(this, (Holder) GameEvent.ENTITY_MOUNT, this.vehicle.position);
                    entityToRide.getIndirectPassengersStream().filter((entity2) -> {
                        return entity2 instanceof ServerPlayer;
                    }).forEach((entity2) -> {
                        CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer) entity2);
                    });
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean canRide(Entity vehicle) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            ((Entity) this.passengers.get(i)).stopRiding();
        }

    }

    public void removeVehicle() {
        if (this.vehicle != null) {
            Entity entity = this.vehicle;

            this.vehicle = null;
            entity.removePassenger(this);
            Entity.RemovalReason entity_removalreason = this.getRemovalReason();

            if (entity_removalreason == null || entity_removalreason.shouldDestroy()) {
                this.level().gameEvent(this, (Holder) GameEvent.ENTITY_DISMOUNT, entity.position);
            }
        }

    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(passenger);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);

                if (!this.level().isClientSide() && passenger instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
                    list.add(0, passenger);
                } else {
                    list.add(passenger);
                }

                this.passengers = ImmutableList.copyOf(list);
            }

        }
    }

    protected void removePassenger(Entity passenger) {
        if (passenger.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            if (this.passengers.size() == 1 && this.passengers.get(0) == passenger) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = (ImmutableList) this.passengers.stream().filter((entity1) -> {
                    return entity1 != passenger;
                }).collect(ImmutableList.toImmutableList());
            }

            passenger.boardingCooldown = 60;
        }
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public final boolean isInterpolating() {
        return this.getInterpolation() != null && this.getInterpolation().hasActiveInterpolation();
    }

    public final void moveOrInterpolateTo(Vec3 position, float yRot, float xRot) {
        this.moveOrInterpolateTo(Optional.of(position), Optional.of(yRot), Optional.of(xRot));
    }

    public final void moveOrInterpolateTo(float yRot, float xRot) {
        this.moveOrInterpolateTo(Optional.empty(), Optional.of(yRot), Optional.of(xRot));
    }

    public final void moveOrInterpolateTo(Vec3 position) {
        this.moveOrInterpolateTo(Optional.of(position), Optional.empty(), Optional.empty());
    }

    public final void moveOrInterpolateTo(Optional<Vec3> position, Optional<Float> yRot, Optional<Float> xRot) {
        InterpolationHandler interpolationhandler = this.getInterpolation();

        if (interpolationhandler != null) {
            interpolationhandler.interpolateTo((Vec3) position.orElse(interpolationhandler.position()), (Float) yRot.orElse(interpolationhandler.yRot()), (Float) xRot.orElse(interpolationhandler.xRot()));
        } else {
            position.ifPresent(this::setPos);
            yRot.ifPresent((ofloat) -> {
                this.setYRot(ofloat % 360.0F);
            });
            xRot.ifPresent((ofloat) -> {
                this.setXRot(ofloat % 360.0F);
            });
        }

    }

    public @Nullable InterpolationHandler getInterpolation() {
        return null;
    }

    public void lerpHeadTo(float yRot, int steps) {
        this.setYHeadRot(yRot);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHeadLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYHeadRot());
    }

    public Vec3 getHandHoldingItemAngle(Item item) {
        if (!(this instanceof Player player)) {
            return Vec3.ZERO;
        } else {
            boolean flag = player.getOffhandItem().is(item) && !player.getMainHandItem().is(item);
            HumanoidArm humanoidarm = flag ? player.getMainArm().getOpposite() : player.getMainArm();

            return this.calculateViewVector(0.0F, this.getYRot() + (float) (humanoidarm == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5D);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public void setAsInsidePortal(Portal portal, BlockPos pos) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (this.portalProcess != null && this.portalProcess.isSamePortal(portal)) {
                if (!this.portalProcess.isInsidePortalThisTick()) {
                    this.portalProcess.updateEntryPosition(pos.immutable());
                    this.portalProcess.setAsInsidePortalThisTick(true);
                }
            } else {
                this.portalProcess = new PortalProcessor(portal, pos.immutable());
            }

        }
    }

    protected void handlePortal() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.processPortalCooldown();
            if (this.portalProcess != null) {
                if (this.portalProcess.processPortalTeleportation(serverlevel, this, this.canUsePortal(false))) {
                    ProfilerFiller profilerfiller = Profiler.get();

                    profilerfiller.push("portal");
                    this.setPortalCooldown();
                    TeleportTransition teleporttransition = this.portalProcess.getPortalDestination(serverlevel, this);

                    if (teleporttransition != null) {
                        ServerLevel serverlevel1 = teleporttransition.newLevel();

                        if (serverlevel.isAllowedToEnterPortal(serverlevel1) && (serverlevel1.dimension() == serverlevel.dimension() || this.canTeleport(serverlevel, serverlevel1))) {
                            this.teleport(teleporttransition);
                        }
                    }

                    profilerfiller.pop();
                } else if (this.portalProcess.hasExpired()) {
                    this.portalProcess = null;
                }

            }
        }
    }

    public int getDimensionChangingDelay() {
        Entity entity = this.getFirstPassenger();

        return entity instanceof ServerPlayer ? entity.getDimensionChangingDelay() : 300;
    }

    public void lerpMotion(Vec3 movement) {
        this.setDeltaMovement(movement);
    }

    public void handleDamageEvent(DamageSource source) {}

    public void handleEntityEvent(byte id) {
        switch (id) {
            case 53:
                HoneyBlock.showSlideParticles(this);
            default:
        }
    }

    public void animateHurt(float direction) {}

    public boolean isOnFire() {
        boolean flag = this.level() != null && this.level().isClientSide();

        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean shiftKeyDown) {
        this.setSharedFlag(1, shiftKeyDown);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean isSprinting) {
        this.setSharedFlag(3, isSprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean swimming) {
        this.setSharedFlag(4, swimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean value) {
        this.hasGlowingTag = value;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(Player player) {
        if (player.isSpectator()) {
            return false;
        } else {
            Team team = this.getTeam();

            return team != null && player != null && player.getTeam() == team && team.canSeeFriendlyInvisibles() ? false : this.isInvisible();
        }
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> action) {}

    public @Nullable PlayerTeam getTeam() {
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public final boolean isAlliedTo(@Nullable Entity other) {
        return other == null ? false : this == other || this.considersEntityAsAlly(other) || other.considersEntityAsAlly(this);
    }

    protected boolean considersEntityAsAlly(Entity other) {
        return this.isAlliedTo((Team) other.getTeam());
    }

    public boolean isAlliedTo(@Nullable Team other) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(other) : false;
    }

    public void setInvisible(boolean invisible) {
        this.setSharedFlag(5, invisible);
    }

    public boolean getSharedFlag(int flag) {
        return ((Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) & 1 << flag) != 0;
    }

    public void setSharedFlag(int flag, boolean value) {
        byte b0 = (Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID);

        if (value) {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << flag));
        } else {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << flag)));
        }

    }

    public int getMaxAirSupply() {
        return 300;
    }

    public int getAirSupply() {
        return (Integer) this.entityData.get(Entity.DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int supply) {
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, supply);
    }

    public void clearFreeze() {
        this.setTicksFrozen(0);
    }

    public int getTicksFrozen() {
        return (Integer) this.entityData.get(Entity.DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int ticks) {
        this.entityData.set(Entity.DATA_TICKS_FROZEN, ticks);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();

        return (float) Math.min(this.getTicksFrozen(), i) / (float) i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        if (this.remainingFireTicks == 0) {
            this.igniteForSeconds(8.0F);
        }

        this.hurtServer(level, this.damageSources().lightningBolt(), 5.0F);
    }

    public void onAboveBubbleColumn(boolean dragDown, BlockPos pos) {
        handleOnAboveBubbleColumn(this, dragDown, pos);
    }

    protected static void handleOnAboveBubbleColumn(Entity entity, boolean dragDown, BlockPos pos) {
        Vec3 vec3 = entity.getDeltaMovement();
        double d0;

        if (dragDown) {
            d0 = Math.max(-0.9D, vec3.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3.y + 0.1D);
        }

        entity.setDeltaMovement(vec3.x, d0, vec3.z);
        sendBubbleColumnParticles(entity.level, pos);
    }

    protected static void sendBubbleColumnParticles(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverlevel) {
            for (int i = 0; i < 2; ++i) {
                serverlevel.sendParticles(ParticleTypes.SPLASH, (double) pos.getX() + level.random.nextDouble(), (double) (pos.getY() + 1), (double) pos.getZ() + level.random.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 1.0D);
                serverlevel.sendParticles(ParticleTypes.BUBBLE, (double) pos.getX() + level.random.nextDouble(), (double) (pos.getY() + 1), (double) pos.getZ() + level.random.nextDouble(), 1, 0.0D, 0.01D, 0.0D, 0.2D);
            }
        }

    }

    public void onInsideBubbleColumn(boolean dragDown) {
        handleOnInsideBubbleColumn(this, dragDown);
    }

    protected static void handleOnInsideBubbleColumn(Entity entity, boolean dragDown) {
        Vec3 vec3 = entity.getDeltaMovement();
        double d0;

        if (dragDown) {
            d0 = Math.max(-0.3D, vec3.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3.y + 0.06D);
        }

        entity.setDeltaMovement(vec3.x, d0, vec3.z);
        entity.resetFallDistance();
    }

    public boolean killedEntity(ServerLevel level, LivingEntity entity, DamageSource source) {
        return true;
    }

    public void checkFallDistanceAccumulation() {
        if (this.getDeltaMovement().y() > -0.5D && this.fallDistance > 1.0D) {
            this.fallDistance = 1.0D;
        }

    }

    public void resetFallDistance() {
        this.fallDistance = 0.0D;
    }

    protected void moveTowardsClosestSpace(double x, double y, double z) {
        BlockPos blockpos = BlockPos.containing(x, y, z);
        Vec3 vec3 = new Vec3(x - (double) blockpos.getX(), y - (double) blockpos.getY(), z - (double) blockpos.getZ());
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.UP;
        double d3 = Double.MAX_VALUE;

        for (Direction direction1 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            blockpos_mutableblockpos.setWithOffset(blockpos, direction1);
            if (!this.level().getBlockState(blockpos_mutableblockpos).isCollisionShapeFullBlock(this.level(), blockpos_mutableblockpos)) {
                double d4 = vec3.get(direction1.getAxis());
                double d5 = direction1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d4 : d4;

                if (d5 < d3) {
                    d3 = d5;
                    direction = direction1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) direction.getAxisDirection().getStep();
        Vec3 vec31 = this.getDeltaMovement().scale(0.75D);

        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement((double) (f1 * f), vec31.y, vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec31.x, (double) (f1 * f), vec31.z);
        } else if (direction.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec31.x, vec31.y, (double) (f1 * f));
        }

    }

    public void makeStuckInBlock(BlockState blockState, Vec3 speedMultiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = speedMultiplier;
    }

    private static Component removeAction(Component component) {
        MutableComponent mutablecomponent = component.plainCopy().setStyle(component.getStyle().withClickEvent((ClickEvent) null));

        for (Component component1 : component.getSiblings()) {
            mutablecomponent.append(removeAction(component1));
        }

        return mutablecomponent;
    }

    @Override
    public Component getName() {
        Component component = this.getCustomName();

        return component != null ? removeAction(component) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity other) {
        return this == other;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float yHeadRot) {}

    public void setYBodyRot(float yBodyRot) {}

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity source) {
        return false;
    }

    public String toString() {
        String s = this.level() == null ? "~NULL~" : this.level().toString();

        return this.removalReason != null ? String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f, removed=%s]", this.getClass().getSimpleName(), this.getPlainTextName(), this.id, s, this.getX(), this.getY(), this.getZ(), this.removalReason) : String.format(Locale.ROOT, "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]", this.getClass().getSimpleName(), this.getPlainTextName(), this.id, s, this.getX(), this.getY(), this.getZ());
    }

    public final boolean isInvulnerableToBase(DamageSource source) {
        return this.isRemoved() || this.invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer() || source.is(DamageTypeTags.IS_FIRE) && this.fireImmune() || source.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public void copyPosition(Entity target) {
        this.snapTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
    }

    public void restoreFrom(Entity oldEntity) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), Entity.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, oldEntity.registryAccess());

            oldEntity.saveWithoutId(tagvalueoutput);
            this.load(TagValueInput.create(problemreporter_scopedcollector, this.registryAccess(), tagvalueoutput.buildResult()));
        }

        this.portalCooldown = oldEntity.portalCooldown;
        this.portalProcess = oldEntity.portalProcess;
    }

    public @Nullable Entity teleport(TeleportTransition transition) {
        ServerLevel serverlevel = this.level();

        if (serverlevel instanceof ServerLevel serverlevel1) {
            if (!this.isRemoved()) {
                serverlevel = transition.newLevel();
                boolean flag = serverlevel.dimension() != serverlevel1.dimension();

                if (!transition.asPassenger()) {
                    this.stopRiding();
                }

                if (flag) {
                    return this.teleportCrossDimension(serverlevel1, serverlevel, transition);
                }

                return this.teleportSameDimension(serverlevel1, transition);
            }
        }

        return null;
    }

    private Entity teleportSameDimension(ServerLevel level, TeleportTransition transition) {
        for (Entity entity : this.getPassengers()) {
            entity.teleport(this.calculatePassengerTransition(transition, entity));
        }

        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("teleportSameDimension");
        this.teleportSetPosition(PositionMoveRotation.of(transition), transition.relatives());
        if (!transition.asPassenger()) {
            this.sendTeleportTransitionToRidingPlayers(transition);
        }

        transition.postTeleportTransition().onTransition(this);
        profilerfiller.pop();
        return this;
    }

    private @Nullable Entity teleportCrossDimension(ServerLevel oldLevel, ServerLevel newLevel, TeleportTransition transition) {
        List<Entity> list = this.getPassengers();
        List<Entity> list1 = new ArrayList(list.size());

        this.ejectPassengers();

        for (Entity entity : list) {
            Entity entity1 = entity.teleport(this.calculatePassengerTransition(transition, entity));

            if (entity1 != null) {
                list1.add(entity1);
            }
        }

        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("teleportCrossDimension");
        Entity entity2 = this.getType().create(newLevel, EntitySpawnReason.DIMENSION_TRAVEL);

        if (entity2 == null) {
            profilerfiller.pop();
            return null;
        } else {
            entity2.restoreFrom(this);
            this.removeAfterChangingDimensions();
            entity2.teleportSetPosition(PositionMoveRotation.of(this), PositionMoveRotation.of(transition), transition.relatives());
            newLevel.addDuringTeleport(entity2);

            for (Entity entity3 : list1) {
                entity3.startRiding(entity2, true, false);
            }

            newLevel.resetEmptyTime();
            transition.postTeleportTransition().onTransition(entity2);
            this.teleportSpectators(transition, oldLevel);
            profilerfiller.pop();
            return entity2;
        }
    }

    protected void teleportSpectators(TeleportTransition transition, ServerLevel oldLevel) {
        for (ServerPlayer serverplayer : List.copyOf(oldLevel.players())) {
            if (serverplayer.getCamera() == this) {
                serverplayer.teleport(transition);
                serverplayer.setCamera((Entity) null);
            }
        }

    }

    private TeleportTransition calculatePassengerTransition(TeleportTransition transition, Entity passenger) {
        float f = transition.yRot() + (transition.relatives().contains(Relative.Y_ROT) ? 0.0F : passenger.getYRot() - this.getYRot());
        float f1 = transition.xRot() + (transition.relatives().contains(Relative.X_ROT) ? 0.0F : passenger.getXRot() - this.getXRot());
        Vec3 vec3 = passenger.position().subtract(this.position());
        Vec3 vec31 = transition.position().add(transition.relatives().contains(Relative.X) ? 0.0D : vec3.x(), transition.relatives().contains(Relative.Y) ? 0.0D : vec3.y(), transition.relatives().contains(Relative.Z) ? 0.0D : vec3.z());

        return transition.withPosition(vec31).withRotation(f, f1).transitionAsPassenger();
    }

    private void sendTeleportTransitionToRidingPlayers(TeleportTransition transition) {
        Entity entity = this.getControllingPassenger();

        for (Entity entity1 : this.getIndirectPassengers()) {
            if (entity1 instanceof ServerPlayer serverplayer) {
                if (entity != null && serverplayer.getId() == entity.getId()) {
                    serverplayer.connection.send(ClientboundTeleportEntityPacket.teleport(this.getId(), PositionMoveRotation.of(transition), transition.relatives(), this.onGround));
                } else {
                    serverplayer.connection.send(ClientboundTeleportEntityPacket.teleport(this.getId(), PositionMoveRotation.of(this), Set.of(), this.onGround));
                }
            }
        }

    }

    public void teleportSetPosition(PositionMoveRotation destination, Set<Relative> relatives) {
        this.teleportSetPosition(PositionMoveRotation.of(this), destination, relatives);
    }

    public void teleportSetPosition(PositionMoveRotation currentValues, PositionMoveRotation destination, Set<Relative> relatives) {
        PositionMoveRotation positionmoverotation2 = PositionMoveRotation.calculateAbsolute(currentValues, destination, relatives);

        this.setPosRaw(positionmoverotation2.position().x, positionmoverotation2.position().y, positionmoverotation2.position().z);
        this.setYRot(positionmoverotation2.yRot());
        this.setYHeadRot(positionmoverotation2.yRot());
        this.setXRot(positionmoverotation2.xRot());
        this.reapplyPosition();
        this.setOldPosAndRot();
        this.setDeltaMovement(positionmoverotation2.deltaMovement());
        this.clearMovementThisTick();
    }

    public void forceSetRotation(float yRot, boolean relativeY, float xRot, boolean relativeX) {
        Set<Relative> set = Relative.rotation(relativeY, relativeX);
        PositionMoveRotation positionmoverotation = PositionMoveRotation.of(this);
        PositionMoveRotation positionmoverotation1 = positionmoverotation.withRotation(yRot, xRot);
        PositionMoveRotation positionmoverotation2 = PositionMoveRotation.calculateAbsolute(positionmoverotation, positionmoverotation1, set);

        this.setYRot(positionmoverotation2.yRot());
        this.setYHeadRot(positionmoverotation2.yRot());
        this.setXRot(positionmoverotation2.xRot());
        this.setOldRot();
    }

    public void placePortalTicket(BlockPos ticketPosition) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            serverlevel.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(ticketPosition), 3);
        }

    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        if (this instanceof Leashable leashable) {
            leashable.removeLeash();
        }

        if (this instanceof WaypointTransmitter waypointtransmitter) {
            Level level = this.level;

            if (level instanceof ServerLevel serverlevel) {
                serverlevel.getWaypointManager().untrackWaypoint(waypointtransmitter);
            }
        }

    }

    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portalArea) {
        return PortalShape.getRelativePosition(portalArea, axis, this.position(), this.getDimensions(this.getPose()));
    }

    public boolean canUsePortal(boolean ignorePassenger) {
        return (ignorePassenger || !this.isPassenger()) && this.isAlive();
    }

    public boolean canTeleport(Level from, Level to) {
        if (from.dimension() == Level.END && to.dimension() == Level.OVERWORLD) {
            for (Entity entity : this.getPassengers()) {
                if (entity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) entity;

                    if (!serverplayer.seenCredits) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public float getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid, float resistance) {
        return resistance;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Entity Type", () -> {
            String s = String.valueOf(EntityType.getKey(this.getType()));

            return s + " (" + this.getClass().getCanonicalName() + ")";
        });
        category.setDetail("Entity ID", this.id);
        category.setDetail("Entity Name", () -> {
            return this.getPlainTextName();
        });
        category.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        category.setDetail("Entity's Block location", CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ())));
        Vec3 vec3 = this.getDeltaMovement();

        category.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3.x, vec3.y, vec3.z));
        category.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        category.setDetail("Entity's Vehicle", () -> {
            return String.valueOf(this.getVehicle());
        });
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    @Override
    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return Entity.viewScale;
    }

    public static void setViewScale(double viewScale) {
        Entity.viewScale = viewScale;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((style) -> {
            return style.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable Component name) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME, Optional.ofNullable(name));
    }

    @Override
    public @Nullable Component getCustomName() {
        return (Component) ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).orElse((Object) null);
    }

    @Override
    public boolean hasCustomName() {
        return ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).isPresent();
    }

    public void setCustomNameVisible(boolean visible) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME_VISIBLE, visible);
    }

    public boolean isCustomNameVisible() {
        return (Boolean) this.entityData.get(Entity.DATA_CUSTOM_NAME_VISIBLE);
    }

    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<Relative> relatives, float newYRot, float newXRot, boolean resetCamera) {
        Entity entity = this.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, newYRot, newXRot, relatives, TeleportTransition.DO_NOTHING));

        return entity != null;
    }

    public void dismountTo(double x, double y, double z) {
        this.teleportTo(x, y, z);
    }

    public void teleportTo(double x, double y, double z) {
        if (this.level() instanceof ServerLevel) {
            this.snapTo(x, y, z, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    private void teleportPassengers() {
        this.getSelfAndPassengers().forEach((entity) -> {
            UnmodifiableIterator unmodifiableiterator = entity.passengers.iterator();

            while (unmodifiableiterator.hasNext()) {
                Entity entity1 = (Entity) unmodifiableiterator.next();

                entity.positionRider(entity1, Entity::snapTo);
            }

        });
    }

    public void teleportRelative(double dx, double dy, double dz) {
        this.teleportTo(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> updatedItems) {}

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (Entity.DATA_POSE.equals(accessor)) {
            this.refreshDimensions();
        }

    }

    /** @deprecated */
    @Deprecated
    protected void fixupDimensions() {
        Pose pose = this.getPose();
        EntityDimensions entitydimensions = this.getDimensions(pose);

        this.dimensions = entitydimensions;
        this.eyeHeight = entitydimensions.eyeHeight();
    }

    public void refreshDimensions() {
        EntityDimensions entitydimensions = this.dimensions;
        Pose pose = this.getPose();
        EntityDimensions entitydimensions1 = this.getDimensions(pose);

        this.dimensions = entitydimensions1;
        this.eyeHeight = entitydimensions1.eyeHeight();
        this.reapplyPosition();
        boolean flag = entitydimensions1.width() <= 4.0F && entitydimensions1.height() <= 4.0F;

        if (!this.level.isClientSide() && !this.firstTick && !this.noPhysics && flag && (entitydimensions1.width() > entitydimensions.width() || entitydimensions1.height() > entitydimensions.height()) && !(this instanceof Player)) {
            this.fudgePositionAfterSizeChange(entitydimensions);
        }

    }

    public boolean fudgePositionAfterSizeChange(EntityDimensions previousDimensions) {
        EntityDimensions entitydimensions1 = this.getDimensions(this.getPose());
        Vec3 vec3 = this.position().add(0.0D, (double) previousDimensions.height() / 2.0D, 0.0D);
        double d0 = (double) Math.max(0.0F, entitydimensions1.width() - previousDimensions.width()) + 1.0E-6D;
        double d1 = (double) Math.max(0.0F, entitydimensions1.height() - previousDimensions.height()) + 1.0E-6D;
        VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3, d0, d1, d0));
        Optional<Vec3> optional = this.level.findFreePosition(this, voxelshape, vec3, (double) entitydimensions1.width(), (double) entitydimensions1.height(), (double) entitydimensions1.width());

        if (optional.isPresent()) {
            this.setPos(((Vec3) optional.get()).add(0.0D, (double) (-entitydimensions1.height()) / 2.0D, 0.0D));
            return true;
        } else {
            if (entitydimensions1.width() > previousDimensions.width() && entitydimensions1.height() > previousDimensions.height()) {
                VoxelShape voxelshape1 = Shapes.create(AABB.ofSize(vec3, d0, 1.0E-6D, d0));
                Optional<Vec3> optional1 = this.level.findFreePosition(this, voxelshape1, vec3, (double) entitydimensions1.width(), (double) previousDimensions.height(), (double) entitydimensions1.width());

                if (optional1.isPresent()) {
                    this.setPos(((Vec3) optional1.get()).add(0.0D, (double) (-previousDimensions.height()) / 2.0D + 1.0E-6D, 0.0D));
                    return true;
                }
            }

            return false;
        }
    }

    public Direction getDirection() {
        return Direction.fromYRot((double) this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public final AABB getBoundingBox() {
        return this.bb;
    }

    public final void setBoundingBox(AABB bb) {
        this.bb = bb;
    }

    public final float getEyeHeight(Pose pose) {
        return this.getDimensions(pose).eyeHeight();
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return null;
    }

    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    public void startSeenByPlayer(ServerPlayer player) {}

    public void stopSeenByPlayer(ServerPlayer player) {}

    public float rotate(Rotation rotation) {
        float f = Mth.wrapDegrees(this.getYRot());
        float f1;

        switch (rotation) {
            case CLOCKWISE_180:
                f1 = f + 180.0F;
                break;
            case COUNTERCLOCKWISE_90:
                f1 = f + 270.0F;
                break;
            case CLOCKWISE_90:
                f1 = f + 90.0F;
                break;
            default:
                f1 = f;
        }

        return f1;
    }

    public float mirror(Mirror mirror) {
        float f = Mth.wrapDegrees(this.getYRot());
        float f1;

        switch (mirror) {
            case FRONT_BACK:
                f1 = -f;
                break;
            case LEFT_RIGHT:
                f1 = 180.0F - f;
                break;
            default:
                f1 = f;
        }

        return f1;
    }

    public ProjectileDeflection deflection(Projectile projectile) {
        return this.getType().is(EntityTypeTags.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
    }

    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    public @Nullable Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : (Entity) this.passengers.get(0);
    }

    public boolean hasPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean hasPassenger(Predicate<Entity> test) {
        UnmodifiableIterator unmodifiableiterator = this.passengers.iterator();

        while (unmodifiableiterator.hasNext()) {
            Entity entity = (Entity) unmodifiableiterator.next();

            if (test.test(entity)) {
                return true;
            }
        }

        return false;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        return () -> {
            return this.getIndirectPassengersStream().iterator();
        };
    }

    public int countPlayerPassengers() {
        return (int) this.getIndirectPassengersStream().filter((entity) -> {
            return entity instanceof Player;
        }).count();
    }

    public boolean hasExactlyOnePlayerPassenger() {
        return this.countPlayerPassengers() == 1;
    }

    public Entity getRootVehicle() {
        Entity entity;

        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
            ;
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity other) {
        return this.getRootVehicle() == other.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity entity) {
        if (!entity.isPassenger()) {
            return false;
        } else {
            Entity entity1 = entity.getVehicle();

            return entity1 == this ? true : this.hasIndirectPassenger(entity1);
        }
    }

    public final boolean isLocalInstanceAuthoritative() {
        return this.level.isClientSide() ? this.isLocalClientAuthoritative() : !this.isClientAuthoritative();
    }

    protected boolean isLocalClientAuthoritative() {
        LivingEntity livingentity = this.getControllingPassenger();

        return livingentity != null && livingentity.isLocalClientAuthoritative();
    }

    public boolean isClientAuthoritative() {
        LivingEntity livingentity = this.getControllingPassenger();

        return livingentity != null && livingentity.isClientAuthoritative();
    }

    public boolean canSimulateMovement() {
        return this.isLocalInstanceAuthoritative();
    }

    public boolean isEffectiveAi() {
        return this.isLocalInstanceAuthoritative();
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double colliderWidth, double collidingWidth, float directionDegrees) {
        double d2 = (colliderWidth + collidingWidth + (double) 1.0E-5F) / 2.0D;
        float f1 = -Mth.sin((double) (directionDegrees * ((float) Math.PI / 180F)));
        float f2 = Mth.cos((double) (directionDegrees * ((float) Math.PI / 180F)));
        float f3 = Math.max(Math.abs(f1), Math.abs(f2));

        return new Vec3((double) f1 * d2 / (double) f3, 0.0D, (double) f2 * d2 / (double) f3);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    public @Nullable Entity getVehicle() {
        return this.vehicle;
    }

    public @Nullable Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 0;
    }

    public CommandSourceStack createCommandSourceStackForNameResolution(ServerLevel level) {
        return new CommandSourceStack(CommandSource.NULL, this.position(), this.getRotationVector(), level, PermissionSet.NO_PERMISSIONS, this.getPlainTextName(), this.getDisplayName(), level.getServer(), this);
    }

    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 pos) {
        Vec3 vec31 = anchor.apply(this);
        double d0 = pos.x - vec31.x;
        double d1 = pos.y - vec31.y;
        double d2 = pos.z - vec31.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        this.setXRot(Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)))));
        this.setYRot(Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public float getPreciseBodyRotation(float partial) {
        return Mth.lerp(partial, this.yRotO, this.yRot);
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> type, double flowScale) {
        if (this.touchingUnloadedChunk()) {
            return false;
        } else {
            AABB aabb = this.getBoundingBox().deflate(0.001D);
            int i = Mth.floor(aabb.minX);
            int j = Mth.ceil(aabb.maxX);
            int k = Mth.floor(aabb.minY);
            int l = Mth.ceil(aabb.maxY);
            int i1 = Mth.floor(aabb.minZ);
            int j1 = Mth.ceil(aabb.maxZ);
            double d1 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3 = Vec3.ZERO;
            int k1 = 0;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockpos_mutableblockpos.set(l1, i2, j2);
                        FluidState fluidstate = this.level().getFluidState(blockpos_mutableblockpos);

                        if (fluidstate.is(type)) {
                            double d2 = (double) ((float) i2 + fluidstate.getHeight(this.level(), blockpos_mutableblockpos));

                            if (d2 >= aabb.minY) {
                                flag1 = true;
                                d1 = Math.max(d2 - aabb.minY, d1);
                                if (flag) {
                                    Vec3 vec31 = fluidstate.getFlow(this.level(), blockpos_mutableblockpos);

                                    if (d1 < 0.4D) {
                                        vec31 = vec31.scale(d1);
                                    }

                                    vec3 = vec3.add(vec31);
                                    ++k1;
                                }
                            }
                        }
                    }
                }
            }

            if (vec3.length() > 0.0D) {
                if (k1 > 0) {
                    vec3 = vec3.scale(1.0D / (double) k1);
                }

                if (!(this instanceof Player)) {
                    vec3 = vec3.normalize();
                }

                Vec3 vec32 = this.getDeltaMovement();

                vec3 = vec3.scale(flowScale);
                double d3 = 0.003D;

                if (Math.abs(vec32.x) < 0.003D && Math.abs(vec32.z) < 0.003D && vec3.length() < 0.0045000000000000005D) {
                    vec3 = vec3.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3));
            }

            this.fluidHeight.put(type, d1);
            return flag1;
        }
    }

    public boolean touchingUnloadedChunk() {
        AABB aabb = this.getBoundingBox().inflate(1.0D);
        int i = Mth.floor(aabb.minX);
        int j = Mth.ceil(aabb.maxX);
        int k = Mth.floor(aabb.minZ);
        int l = Mth.ceil(aabb.maxZ);

        return !this.level().hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<Fluid> type) {
        return this.fluidHeight.getDouble(type);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width();
    }

    public final float getBbHeight() {
        return this.dimensions.height();
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    public EntityDimensions getDimensions(Pose pose) {
        return this.type.getDimensions();
    }

    public final EntityAttachments getAttachments() {
        return this.dimensions.attachments();
    }

    @Override
    public Vec3 position() {
        return this.position;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public BlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.inBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 deltaMovement) {
        if (deltaMovement.isFinite()) {
            this.deltaMovement = deltaMovement;
        }

    }

    public void addDeltaMovement(Vec3 momentum) {
        if (momentum.isFinite()) {
            this.setDeltaMovement(this.getDeltaMovement().add(momentum));
        }

    }

    public void setDeltaMovement(double xd, double yd, double zd) {
        this.setDeltaMovement(new Vec3(xd, yd, zd));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double progress) {
        return this.position.x + (double) this.getBbWidth() * progress;
    }

    public double getRandomX(double spread) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * spread);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double progress) {
        return this.position.y + (double) this.getBbHeight() * progress;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double progress) {
        return this.position.z + (double) this.getBbWidth() * progress;
    }

    public double getRandomZ(double spread) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * spread);
    }

    public final void setPosRaw(double x, double y, double z) {
        if (this.position.x != x || this.position.y != y || this.position.z != z) {
            this.position = new Vec3(x, y, z);
            int i = Mth.floor(x);
            int j = Mth.floor(y);
            int k = Mth.floor(z);

            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
                this.inBlockState = null;
                if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
            if (!this.firstTick) {
                Level level = this.level;

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    if (!this.isRemoved()) {
                        if (this instanceof WaypointTransmitter) {
                            WaypointTransmitter waypointtransmitter = (WaypointTransmitter) this;

                            if (waypointtransmitter.isTransmittingWaypoint()) {
                                serverlevel.getWaypointManager().updateWaypoint(waypointtransmitter);
                            }
                        }

                        if (this instanceof ServerPlayer) {
                            ServerPlayer serverplayer = (ServerPlayer) this;

                            if (serverplayer.isReceivingWaypoints() && serverplayer.connection != null) {
                                serverlevel.getWaypointManager().updatePlayer(serverplayer);
                            }
                        }
                    }
                }
            }
        }

    }

    public void checkDespawn() {}

    public Vec3[] getQuadLeashHolderOffsets() {
        return Leashable.createQuadLeashOffsets(this, 0.0D, 0.5D, 0.5D, 0.0D);
    }

    public boolean supportQuadLeashAsHolder() {
        return false;
    }

    public void notifyLeashHolder(Leashable entity) {}

    public void notifyLeasheeRemoved(Leashable entity) {}

    public Vec3 getRopeHoldPosition(float partialTickTime) {
        return this.getPosition(partialTickTime).add(0.0D, (double) this.eyeHeight * 0.7D, 0.0D);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        int i = packet.getId();
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.snapTo(d0, d1, d2, packet.getYRot(), packet.getXRot());
        this.setId(i);
        this.setUUID(packet.getUUID());
        this.setDeltaMovement(packet.getMovement());
    }

    public @Nullable ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean isInPowderSnow) {
        this.isInPowderSnow = isInPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return this.getTicksFrozen() > 0;
    }

    public float getYRot() {
        return this.yRot;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float yRot) {
        if (!Float.isFinite(yRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yRot + ", discarding.");
        } else {
            this.yRot = yRot;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float xRot) {
        if (!Float.isFinite(xRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + xRot + ", discarding.");
        } else {
            this.xRot = Math.clamp(xRot % 360.0F, -90.0F, 90.0F);
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return 0.0F;
    }

    public void onExplosionHit(@Nullable Entity explosionCausedBy) {}

    @Override
    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    public Entity.@Nullable RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason reason) {
        if (this.removalReason == null) {
            this.removalReason = reason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(reason);
        this.onRemoval(reason);
    }

    public void unsetRemoved() {
        this.removalReason = null;
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback levelCallback) {
        this.levelCallback = levelCallback;
    }

    @Override
    public boolean shouldBeSaved() {
        return this.removalReason != null && !this.removalReason.shouldSave() ? false : (this.isPassenger() ? false : !this.isVehicle() || !this.hasExactlyOnePlayerPassenger());
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        return true;
    }

    public boolean isFlyingVehicle() {
        return false;
    }

    @Override
    public Level level() {
        return this.level;
    }

    protected void setLevel(Level level) {
        this.level = level;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    public RegistryAccess registryAccess() {
        return this.level().registryAccess();
    }

    protected void lerpPositionAndRotationStep(int stepsToTarget, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        double d5 = 1.0D / (double) stepsToTarget;
        double d6 = Mth.lerp(d5, this.getX(), targetX);
        double d7 = Mth.lerp(d5, this.getY(), targetY);
        double d8 = Mth.lerp(d5, this.getZ(), targetZ);
        float f = (float) Mth.rotLerp(d5, (double) this.getYRot(), targetYRot);
        float f1 = (float) Mth.lerp(d5, (double) this.getXRot(), targetXRot);

        this.setPos(d6, d7, d8);
        this.setRot(f, f1);
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public Vec3 getKnownMovement() {
        LivingEntity livingentity = this.getControllingPassenger();

        if (livingentity instanceof Player player) {
            if (this.isAlive()) {
                return player.getKnownMovement();
            }
        }

        return this.getDeltaMovement();
    }

    public Vec3 getKnownSpeed() {
        LivingEntity livingentity = this.getControllingPassenger();

        if (livingentity instanceof Player player) {
            if (this.isAlive()) {
                return player.getKnownSpeed();
            }
        }

        return this.lastKnownSpeed;
    }

    public @Nullable ItemStack getWeaponItem() {
        return null;
    }

    public Optional<ResourceKey<LootTable>> getLootTable() {
        return this.type.getDefaultLootTable();
    }

    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.CUSTOM_NAME);
        this.applyImplicitComponentIfPresent(components, DataComponents.CUSTOM_DATA);
    }

    public final void applyComponentsFromItemStack(ItemStack stack) {
        this.applyImplicitComponents(stack.getComponents());
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.CUSTOM_NAME ? castComponentValue(type, this.getCustomName()) : (type == DataComponents.CUSTOM_DATA ? castComponentValue(type, this.customData) : null));
    }

    @Contract("_,!null->!null;_,_->_")
    protected static <T> @Nullable T castComponentValue(DataComponentType<T> type, @Nullable Object value) {
        return (T) value;
    }

    public <T> void setComponent(DataComponentType<T> type, T value) {
        this.applyImplicitComponent(type, value);
    }

    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.CUSTOM_NAME) {
            this.setCustomName((Component) castComponentValue(DataComponents.CUSTOM_NAME, value));
            return true;
        } else if (type == DataComponents.CUSTOM_DATA) {
            this.customData = (CustomData) castComponentValue(DataComponents.CUSTOM_DATA, value);
            return true;
        } else {
            return false;
        }
    }

    protected <T> boolean applyImplicitComponentIfPresent(DataComponentGetter components, DataComponentType<T> type) {
        T t0 = (T) components.get(type);

        return t0 != null ? this.applyImplicitComponent(type, t0) : false;
    }

    public ProblemReporter.PathElement problemPath() {
        return new Entity.EntityPathElement(this);
    }

    @Override
    public void registerDebugValues(ServerLevel level, DebugValueSource.Registration registration) {}

    private static record Movement(Vec3 from, Vec3 to, Optional<Vec3> axisDependentOriginalMovement) {

        public Movement(Vec3 from, Vec3 to, Vec3 axisDependentOriginalMovement) {
            this(from, to, Optional.of(axisDependentOriginalMovement));
        }

        public Movement(Vec3 from, Vec3 to) {
            this(from, to, Optional.empty());
        }
    }

    public static enum MovementEmission {

        NONE(false, false), SOUNDS(true, false), EVENTS(false, true), ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(boolean sounds, boolean events) {
            this.sounds = sounds;
            this.events = events;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    public static enum RemovalReason {

        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(boolean destroy, boolean save) {
            this.destroy = destroy;
            this.save = save;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }

    private static record EntityPathElement(Entity entity) implements ProblemReporter.PathElement {

        @Override
        public String get() {
            return this.entity.toString();
        }
    }

    @FunctionalInterface
    public interface MoveFunction {

        void accept(Entity target, double x, double y, double z);
    }
}
