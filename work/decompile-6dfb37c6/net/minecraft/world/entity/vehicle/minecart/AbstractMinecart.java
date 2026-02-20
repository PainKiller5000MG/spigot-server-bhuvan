package net.minecraft.world.entity.vehicle.minecart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractMinecart extends VehicleEntity {

    private static final Vec3 LOWERED_PASSENGER_ATTACHMENT = new Vec3(0.0D, 0.0D, 0.0D);
    private static final EntityDataAccessor<Optional<BlockState>> DATA_ID_CUSTOM_DISPLAY_BLOCK = SynchedEntityData.<Optional<BlockState>>defineId(AbstractMinecart.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
    private static final EntityDataAccessor<Integer> DATA_ID_DISPLAY_OFFSET = SynchedEntityData.<Integer>defineId(AbstractMinecart.class, EntityDataSerializers.INT);
    private static final ImmutableMap<Pose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(Pose.STANDING, ImmutableList.of(0, 1, -1), Pose.CROUCHING, ImmutableList.of(0, 1, -1), Pose.SWIMMING, ImmutableList.of(0, 1));
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private static final boolean DEFAULT_FLIPPED_ROTATION = false;
    private boolean onRails;
    private boolean flipped;
    private final MinecartBehavior behavior;
    private static final Map<RailShape, Pair<Vec3i, Vec3i>> EXITS = Maps.newEnumMap((Map) Util.make(() -> {
        Vec3i vec3i = Direction.WEST.getUnitVec3i();
        Vec3i vec3i1 = Direction.EAST.getUnitVec3i();
        Vec3i vec3i2 = Direction.NORTH.getUnitVec3i();
        Vec3i vec3i3 = Direction.SOUTH.getUnitVec3i();
        Vec3i vec3i4 = vec3i.below();
        Vec3i vec3i5 = vec3i1.below();
        Vec3i vec3i6 = vec3i2.below();
        Vec3i vec3i7 = vec3i3.below();

        return ImmutableMap.of(RailShape.NORTH_SOUTH, Pair.of(vec3i2, vec3i3), RailShape.EAST_WEST, Pair.of(vec3i, vec3i1), RailShape.ASCENDING_EAST, Pair.of(vec3i4, vec3i1), RailShape.ASCENDING_WEST, Pair.of(vec3i, vec3i5), RailShape.ASCENDING_NORTH, Pair.of(vec3i2, vec3i7), RailShape.ASCENDING_SOUTH, Pair.of(vec3i6, vec3i3), RailShape.SOUTH_EAST, Pair.of(vec3i3, vec3i1), RailShape.SOUTH_WEST, Pair.of(vec3i3, vec3i), RailShape.NORTH_WEST, Pair.of(vec3i2, vec3i), RailShape.NORTH_EAST, Pair.of(vec3i2, vec3i1));
    }));

    protected AbstractMinecart(EntityType<?> type, Level level) {
        super(type, level);
        this.flipped = false;
        this.blocksBuilding = true;
        if (useExperimentalMovement(level)) {
            this.behavior = new NewMinecartBehavior(this);
        } else {
            this.behavior = new OldMinecartBehavior(this);
        }

    }

    protected AbstractMinecart(EntityType<?> type, Level level, double x, double y, double z) {
        this(type, level);
        this.setInitialPos(x, y, z);
    }

    public void setInitialPos(double x, double y, double z) {
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public static <T extends AbstractMinecart> @Nullable T createMinecart(Level level, double x, double y, double z, EntityType<T> type, EntitySpawnReason reason, ItemStack itemStack, @Nullable Player player) {
        T t0 = type.create(level, reason);

        if (t0 != null) {
            t0.setInitialPos(x, y, z);
            EntityType.createDefaultStackConfig(level, itemStack, player).accept(t0);
            MinecartBehavior minecartbehavior = t0.getBehavior();

            if (minecartbehavior instanceof NewMinecartBehavior) {
                NewMinecartBehavior newminecartbehavior = (NewMinecartBehavior) minecartbehavior;
                BlockPos blockpos = t0.getCurrentBlockPosOrRailBelow();
                BlockState blockstate = level.getBlockState(blockpos);

                newminecartbehavior.adjustToRails(blockpos, blockstate, true);
            }
        }

        return t0;
    }

    public MinecartBehavior getBehavior() {
        return this.behavior;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY_BLOCK, Optional.empty());
        entityData.define(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, this.getDefaultDisplayOffset());
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return AbstractBoat.canVehicleCollide(this, entity);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portalArea) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portalArea));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        boolean flag = passenger instanceof Villager || passenger instanceof WanderingTrader;

        return flag ? AbstractMinecart.LOWERED_PASSENGER_ATTACHMENT : super.getPassengerAttachmentPoint(passenger, dimensions, scale);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction direction = this.getMotionDirection();

        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(passenger);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(direction);
            BlockPos blockpos = this.blockPosition();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            ImmutableList<Pose> immutablelist = passenger.getDismountPoses();
            UnmodifiableIterator unmodifiableiterator = immutablelist.iterator();

            while (unmodifiableiterator.hasNext()) {
                Pose pose = (Pose) unmodifiableiterator.next();
                EntityDimensions entitydimensions = passenger.getDimensions(pose);
                float f = Math.min(entitydimensions.width(), 1.0F) / 2.0F;
                UnmodifiableIterator unmodifiableiterator1 = ((ImmutableList) AbstractMinecart.POSE_DISMOUNT_HEIGHTS.get(pose)).iterator();

                while (unmodifiableiterator1.hasNext()) {
                    int i = (Integer) unmodifiableiterator1.next();

                    for (int[] aint1 : aint) {
                        blockpos_mutableblockpos.set(blockpos.getX() + aint1[0], blockpos.getY() + i, blockpos.getZ() + aint1[1]);
                        double d0 = this.level().getBlockFloorHeight(DismountHelper.nonClimbableShape(this.level(), blockpos_mutableblockpos), () -> {
                            return DismountHelper.nonClimbableShape(this.level(), blockpos_mutableblockpos.below());
                        });

                        if (DismountHelper.isBlockFloorValid(d0)) {
                            AABB aabb = new AABB((double) (-f), 0.0D, (double) (-f), (double) f, (double) entitydimensions.height(), (double) f);
                            Vec3 vec3 = Vec3.upFromBottomCenterOf(blockpos_mutableblockpos, d0);

                            if (DismountHelper.canDismountTo(this.level(), passenger, aabb.move(vec3))) {
                                passenger.setPose(pose);
                                return vec3;
                            }
                        }
                    }
                }
            }

            double d1 = this.getBoundingBox().maxY;

            blockpos_mutableblockpos.set((double) blockpos.getX(), d1, (double) blockpos.getZ());
            UnmodifiableIterator unmodifiableiterator2 = immutablelist.iterator();

            while (unmodifiableiterator2.hasNext()) {
                Pose pose1 = (Pose) unmodifiableiterator2.next();
                double d2 = (double) passenger.getDimensions(pose1).height();
                int j = Mth.ceil(d1 - (double) blockpos_mutableblockpos.getY() + d2);
                double d3 = DismountHelper.findCeilingFrom(blockpos_mutableblockpos, j, (blockpos1) -> {
                    return this.level().getBlockState(blockpos1).getCollisionShape(this.level(), blockpos1);
                });

                if (d1 + d2 <= d3) {
                    passenger.setPose(pose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(passenger);
        }
    }

    @Override
    protected float getBlockSpeedFactor() {
        BlockState blockstate = this.level().getBlockState(this.blockPosition());

        return blockstate.is(BlockTags.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    @Override
    public void animateHurt(float yaw) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    public static Pair<Vec3i, Vec3i> exits(RailShape shape) {
        return (Pair) AbstractMinecart.EXITS.get(shape);
    }

    @Override
    public Direction getMotionDirection() {
        return this.behavior.getMotionDirection();
    }

    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.005D : 0.04D;
    }

    @Override
    public void tick() {
        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        this.computeSpeed();
        this.handlePortal();
        this.behavior.tick();
        this.updateInWaterStateAndDoFluidPushing();
        if (this.isInLava()) {
            this.lavaIgnite();
            this.lavaHurt();
            this.fallDistance *= 0.5D;
        }

        this.firstTick = false;
    }

    public boolean isFirstTick() {
        return this.firstTick;
    }

    public BlockPos getCurrentBlockPosOrRailBelow() {
        int i = Mth.floor(this.getX());
        int j = Mth.floor(this.getY());
        int k = Mth.floor(this.getZ());

        if (useExperimentalMovement(this.level())) {
            double d0 = this.getY() - 0.1D - (double) 1.0E-5F;

            if (this.level().getBlockState(BlockPos.containing((double) i, d0, (double) k)).is(BlockTags.RAILS)) {
                j = Mth.floor(d0);
            }
        } else if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
            --j;
        }

        return new BlockPos(i, j, k);
    }

    protected double getMaxSpeed(ServerLevel level) {
        return this.behavior.getMaxSpeed(level);
    }

    public void activateMinecart(ServerLevel level, int xt, int yt, int zt, boolean state) {}

    @Override
    public void lerpPositionAndRotationStep(int stepsToTarget, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        super.lerpPositionAndRotationStep(stepsToTarget, targetX, targetY, targetZ, targetYRot, targetXRot);
    }

    @Override
    public void applyGravity() {
        super.applyGravity();
    }

    @Override
    public void reapplyPosition() {
        super.reapplyPosition();
    }

    @Override
    public boolean updateInWaterStateAndDoFluidPushing() {
        return super.updateInWaterStateAndDoFluidPushing();
    }

    @Override
    public Vec3 getKnownMovement() {
        return this.behavior.getKnownMovement(super.getKnownMovement());
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.behavior.getInterpolation();
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.behavior.lerpMotion(this.getDeltaMovement());
    }

    @Override
    public void lerpMotion(Vec3 movement) {
        this.behavior.lerpMotion(movement);
    }

    protected void moveAlongTrack(ServerLevel level) {
        this.behavior.moveAlongTrack(level);
    }

    protected void comeOffTrack(ServerLevel level) {
        double d0 = this.getMaxSpeed(level);
        Vec3 vec3 = this.getDeltaMovement();

        this.setDeltaMovement(Mth.clamp(vec3.x, -d0, d0), vec3.y, Mth.clamp(vec3.z, -d0, d0));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95D));
        }

    }

    protected double makeStepAlongTrack(BlockPos pos, RailShape shape, double movementLeft) {
        return this.behavior.stepAlongTrack(pos, shape, movementLeft);
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        if (useExperimentalMovement(this.level())) {
            Vec3 vec31 = this.position().add(delta);

            super.move(moverType, delta);
            boolean flag = this.behavior.pushAndPickupEntities();

            if (flag) {
                super.move(moverType, vec31.subtract(this.position()));
            }

            if (moverType.equals(MoverType.PISTON)) {
                this.onRails = false;
            }
        } else {
            super.move(moverType, delta);
            this.applyEffectsFromBlocks();
        }

    }

    @Override
    public void applyEffectsFromBlocks() {
        if (useExperimentalMovement(this.level())) {
            super.applyEffectsFromBlocks();
        } else {
            this.applyEffectsFromBlocks(this.position(), this.position());
            this.clearMovementThisTick();
        }

    }

    @Override
    public boolean isOnRails() {
        return this.onRails;
    }

    public void setOnRails(boolean onRails) {
        this.onRails = onRails;
    }

    public boolean isFlipped() {
        return this.flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    public Vec3 getRedstoneDirection(BlockPos pos) {
        BlockState blockstate = this.level().getBlockState(pos);

        if (blockstate.is(Blocks.POWERED_RAIL) && (Boolean) blockstate.getValue(PoweredRailBlock.POWERED)) {
            RailShape railshape = (RailShape) blockstate.getValue(((BaseRailBlock) blockstate.getBlock()).getShapeProperty());

            if (railshape == RailShape.EAST_WEST) {
                if (this.isRedstoneConductor(pos.west())) {
                    return new Vec3(1.0D, 0.0D, 0.0D);
                }

                if (this.isRedstoneConductor(pos.east())) {
                    return new Vec3(-1.0D, 0.0D, 0.0D);
                }
            } else if (railshape == RailShape.NORTH_SOUTH) {
                if (this.isRedstoneConductor(pos.north())) {
                    return new Vec3(0.0D, 0.0D, 1.0D);
                }

                if (this.isRedstoneConductor(pos.south())) {
                    return new Vec3(0.0D, 0.0D, -1.0D);
                }
            }

            return Vec3.ZERO;
        } else {
            return Vec3.ZERO;
        }
    }

    public boolean isRedstoneConductor(BlockPos pos) {
        return this.level().getBlockState(pos).isRedstoneConductor(this.level(), pos);
    }

    protected Vec3 applyNaturalSlowdown(Vec3 movement) {
        double d0 = this.behavior.getSlowdownFactor();
        Vec3 vec31 = movement.multiply(d0, 0.0D, d0);

        if (this.isInWater()) {
            vec31 = vec31.scale((double) 0.95F);
        }

        return vec31;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setCustomDisplayBlockState(input.read("DisplayState", BlockState.CODEC));
        this.setDisplayOffset(input.getIntOr("DisplayOffset", this.getDefaultDisplayOffset()));
        this.flipped = input.getBooleanOr("FlippedRotation", false);
        this.firstTick = input.getBooleanOr("HasTicked", false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        this.getCustomDisplayBlockState().ifPresent((blockstate) -> {
            output.store("DisplayState", BlockState.CODEC, blockstate);
        });
        int i = this.getDisplayOffset();

        if (i != this.getDefaultDisplayOffset()) {
            output.putInt("DisplayOffset", i);
        }

        output.putBoolean("FlippedRotation", this.flipped);
        output.putBoolean("HasTicked", this.firstTick);
    }

    @Override
    public void push(Entity entity) {
        if (!this.level().isClientSide()) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(entity)) {
                    double d0 = entity.getX() - this.getX();
                    double d1 = entity.getZ() - this.getZ();
                    double d2 = d0 * d0 + d1 * d1;

                    if (d2 >= (double) 1.0E-4F) {
                        d2 = Math.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0D / d2;

                        if (d3 > 1.0D) {
                            d3 = 1.0D;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= (double) 0.1F;
                        d1 *= (double) 0.1F;
                        d0 *= 0.5D;
                        d1 *= 0.5D;
                        if (entity instanceof AbstractMinecart) {
                            AbstractMinecart abstractminecart = (AbstractMinecart) entity;

                            this.pushOtherMinecart(abstractminecart, d0, d1);
                        } else {
                            this.push(-d0, 0.0D, -d1);
                            entity.push(d0 / 4.0D, 0.0D, d1 / 4.0D);
                        }
                    }

                }
            }
        }
    }

    private void pushOtherMinecart(AbstractMinecart otherMinecart, double xa, double za) {
        double d2;
        double d3;

        if (useExperimentalMovement(this.level())) {
            d2 = this.getDeltaMovement().x;
            d3 = this.getDeltaMovement().z;
        } else {
            d2 = otherMinecart.getX() - this.getX();
            d3 = otherMinecart.getZ() - this.getZ();
        }

        Vec3 vec3 = (new Vec3(d2, 0.0D, d3)).normalize();
        Vec3 vec31 = (new Vec3((double) Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F))), 0.0D, (double) Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))))).normalize();
        double d4 = Math.abs(vec3.dot(vec31));

        if (d4 >= (double) 0.8F || useExperimentalMovement(this.level())) {
            Vec3 vec32 = this.getDeltaMovement();
            Vec3 vec33 = otherMinecart.getDeltaMovement();

            if (otherMinecart.isFurnace() && !this.isFurnace()) {
                this.setDeltaMovement(vec32.multiply(0.2D, 1.0D, 0.2D));
                this.push(vec33.x - xa, 0.0D, vec33.z - za);
                otherMinecart.setDeltaMovement(vec33.multiply(0.95D, 1.0D, 0.95D));
            } else if (!otherMinecart.isFurnace() && this.isFurnace()) {
                otherMinecart.setDeltaMovement(vec33.multiply(0.2D, 1.0D, 0.2D));
                otherMinecart.push(vec32.x + xa, 0.0D, vec32.z + za);
                this.setDeltaMovement(vec32.multiply(0.95D, 1.0D, 0.95D));
            } else {
                double d5 = (vec33.x + vec32.x) / 2.0D;
                double d6 = (vec33.z + vec32.z) / 2.0D;

                this.setDeltaMovement(vec32.multiply(0.2D, 1.0D, 0.2D));
                this.push(d5 - xa, 0.0D, d6 - za);
                otherMinecart.setDeltaMovement(vec33.multiply(0.2D, 1.0D, 0.2D));
                otherMinecart.push(d5 + xa, 0.0D, d6 + za);
            }

        }
    }

    public BlockState getDisplayBlockState() {
        return (BlockState) this.getCustomDisplayBlockState().orElseGet(this::getDefaultDisplayBlockState);
    }

    private Optional<BlockState> getCustomDisplayBlockState() {
        return (Optional) this.getEntityData().get(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY_BLOCK);
    }

    public BlockState getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return (Integer) this.getEntityData().get(AbstractMinecart.DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setCustomDisplayBlockState(Optional<BlockState> state) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_CUSTOM_DISPLAY_BLOCK, state);
    }

    public void setDisplayOffset(int offset) {
        this.getEntityData().set(AbstractMinecart.DATA_ID_DISPLAY_OFFSET, offset);
    }

    public static boolean useExperimentalMovement(Level level) {
        return level.enabledFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS);
    }

    @Override
    public abstract ItemStack getPickResult();

    public boolean isRideable() {
        return false;
    }

    public boolean isFurnace() {
        return false;
    }
}
