package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class Shulker extends AbstractGolem implements Enemy {

    private static final Identifier COVERED_ARMOR_MODIFIER_ID = Identifier.withDefaultNamespace("covered");
    private static final AttributeModifier COVERED_ARMOR_MODIFIER = new AttributeModifier(Shulker.COVERED_ARMOR_MODIFIER_ID, 20.0D, AttributeModifier.Operation.ADD_VALUE);
    protected static final EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID = SynchedEntityData.<Direction>defineId(Shulker.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Byte> DATA_PEEK_ID = SynchedEntityData.<Byte>defineId(Shulker.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Byte> DATA_COLOR_ID = SynchedEntityData.<Byte>defineId(Shulker.class, EntityDataSerializers.BYTE);
    private static final int TELEPORT_STEPS = 6;
    private static final byte NO_COLOR = 16;
    private static final byte DEFAULT_COLOR = 16;
    private static final int MAX_TELEPORT_DISTANCE = 8;
    private static final int OTHER_SHULKER_SCAN_RADIUS = 8;
    private static final int OTHER_SHULKER_LIMIT = 5;
    private static final float PEEK_PER_TICK = 0.05F;
    private static final byte DEFAULT_PEEK = 0;
    private static final Direction DEFAULT_ATTACH_FACE = Direction.DOWN;
    private static final Vector3f FORWARD = (Vector3f) Util.make(() -> {
        Vec3i vec3i = Direction.SOUTH.getUnitVec3i();

        return new Vector3f((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ());
    });
    private static final float MAX_SCALE = 3.0F;
    private float currentPeekAmountO;
    private float currentPeekAmount;
    private @Nullable BlockPos clientOldAttachPosition;
    private int clientSideTeleportInterpolation;
    private static final float MAX_LID_OPEN = 1.0F;

    public Shulker(EntityType<? extends Shulker> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.lookControl = new Shulker.ShulkerLookControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.02F, true));
        this.goalSelector.addGoal(4, new Shulker.ShulkerAttackGoal());
        this.goalSelector.addGoal(7, new Shulker.ShulkerPeekGoal());
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[]{this.getClass()})).setAlertOthers());
        this.targetSelector.addGoal(2, new Shulker.ShulkerNearestAttackGoal(this));
        this.targetSelector.addGoal(3, new Shulker.ShulkerDefenseAttackGoal(this));
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SHULKER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        if (!this.isClosed()) {
            super.playAmbientSound();
        }

    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SHULKER_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isClosed() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.SHULKER_HURT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Shulker.DATA_ATTACH_FACE_ID, Shulker.DEFAULT_ATTACH_FACE);
        entityData.define(Shulker.DATA_PEEK_ID, (byte) 0);
        entityData.define(Shulker.DATA_COLOR_ID, (byte) 16);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Shulker.ShulkerBodyRotationControl(this);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setAttachFace((Direction) input.read("AttachFace", Direction.LEGACY_ID_CODEC).orElse(Shulker.DEFAULT_ATTACH_FACE));
        this.entityData.set(Shulker.DATA_PEEK_ID, input.getByteOr("Peek", (byte) 0));
        this.entityData.set(Shulker.DATA_COLOR_ID, input.getByteOr("Color", (byte) 16));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("AttachFace", Direction.LEGACY_ID_CODEC, this.getAttachFace());
        output.putByte("Peek", (Byte) this.entityData.get(Shulker.DATA_PEEK_ID));
        output.putByte("Color", (Byte) this.entityData.get(Shulker.DATA_COLOR_ID));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && !this.isPassenger() && !this.canStayAt(this.blockPosition(), this.getAttachFace())) {
            this.findNewAttachment();
        }

        if (this.updatePeekAmount()) {
            this.onPeekAmountChange();
        }

        if (this.level().isClientSide()) {
            if (this.clientSideTeleportInterpolation > 0) {
                --this.clientSideTeleportInterpolation;
            } else {
                this.clientOldAttachPosition = null;
            }
        }

    }

    private void findNewAttachment() {
        Direction direction = this.findAttachableSurface(this.blockPosition());

        if (direction != null) {
            this.setAttachFace(direction);
        } else {
            this.teleportSomewhere();
        }

    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        float f = getPhysicalPeek(this.currentPeekAmount);
        Direction direction = this.getAttachFace().getOpposite();

        return getProgressAabb(this.getScale(), direction, f, position);
    }

    private static float getPhysicalPeek(float amount) {
        return 0.5F - Mth.sin((double) ((0.5F + amount) * (float) Math.PI)) * 0.5F;
    }

    private boolean updatePeekAmount() {
        this.currentPeekAmountO = this.currentPeekAmount;
        float f = (float) this.getRawPeekAmount() * 0.01F;

        if (this.currentPeekAmount == f) {
            return false;
        } else {
            if (this.currentPeekAmount > f) {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount - 0.05F, f, 1.0F);
            } else {
                this.currentPeekAmount = Mth.clamp(this.currentPeekAmount + 0.05F, 0.0F, f);
            }

            return true;
        }
    }

    private void onPeekAmountChange() {
        this.reapplyPosition();
        float f = getPhysicalPeek(this.currentPeekAmount);
        float f1 = getPhysicalPeek(this.currentPeekAmountO);
        Direction direction = this.getAttachFace().getOpposite();
        float f2 = (f - f1) * this.getScale();

        if (f2 > 0.0F) {
            for (Entity entity : this.level().getEntities(this, getProgressDeltaAabb(this.getScale(), direction, f1, f, this.position()), EntitySelector.NO_SPECTATORS.and((entity1) -> {
                return !entity1.isPassengerOfSameVehicle(this);
            }))) {
                if (!(entity instanceof Shulker) && !entity.noPhysics) {
                    entity.move(MoverType.SHULKER, new Vec3((double) (f2 * (float) direction.getStepX()), (double) (f2 * (float) direction.getStepY()), (double) (f2 * (float) direction.getStepZ())));
                }
            }

        }
    }

    public static AABB getProgressAabb(float size, Direction direction, float progressTo, Vec3 position) {
        return getProgressDeltaAabb(size, direction, -1.0F, progressTo, position);
    }

    public static AABB getProgressDeltaAabb(float size, Direction direction, float progressFrom, float progressTo, Vec3 position) {
        AABB aabb = new AABB((double) (-size) * 0.5D, 0.0D, (double) (-size) * 0.5D, (double) size * 0.5D, (double) size, (double) size * 0.5D);
        double d0 = (double) Math.max(progressFrom, progressTo);
        double d1 = (double) Math.min(progressFrom, progressTo);
        AABB aabb1 = aabb.expandTowards((double) direction.getStepX() * d0 * (double) size, (double) direction.getStepY() * d0 * (double) size, (double) direction.getStepZ() * d0 * (double) size).contract((double) (-direction.getStepX()) * (1.0D + d1) * (double) size, (double) (-direction.getStepY()) * (1.0D + d1) * (double) size, (double) (-direction.getStepZ()) * (1.0D + d1) * (double) size);

        return aabb1.move(position.x, position.y, position.z);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean sendEventAndTriggers) {
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = null;
            this.clientSideTeleportInterpolation = 0;
        }

        this.setAttachFace(Direction.DOWN);
        return super.startRiding(entity, force, sendEventAndTriggers);
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        if (this.level().isClientSide()) {
            this.clientOldAttachPosition = this.blockPosition();
        }

        this.yBodyRotO = 0.0F;
        this.yBodyRot = 0.0F;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setYRot(0.0F);
        this.yHeadRot = this.getYRot();
        this.setOldPosAndRot();
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        if (moverType == MoverType.SHULKER_BOX) {
            this.teleportSomewhere();
        } else {
            super.move(moverType, delta);
        }

    }

    @Override
    public Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {}

    @Override
    public void setPos(double x, double y, double z) {
        BlockPos blockpos = this.blockPosition();

        if (this.isPassenger()) {
            super.setPos(x, y, z);
        } else {
            super.setPos((double) Mth.floor(x) + 0.5D, (double) Mth.floor(y + 0.5D), (double) Mth.floor(z) + 0.5D);
        }

        if (this.tickCount != 0) {
            BlockPos blockpos1 = this.blockPosition();

            if (!blockpos1.equals(blockpos)) {
                this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                this.needsSync = true;
                if (this.level().isClientSide() && !this.isPassenger() && !blockpos1.equals(this.clientOldAttachPosition)) {
                    this.clientOldAttachPosition = blockpos;
                    this.clientSideTeleportInterpolation = 6;
                    this.xOld = this.getX();
                    this.yOld = this.getY();
                    this.zOld = this.getZ();
                }
            }

        }
    }

    protected @Nullable Direction findAttachableSurface(BlockPos target) {
        for (Direction direction : Direction.values()) {
            if (this.canStayAt(target, direction)) {
                return direction;
            }
        }

        return null;
    }

    private boolean canStayAt(BlockPos target, Direction face) {
        if (this.isPositionBlocked(target)) {
            return false;
        } else {
            Direction direction1 = face.getOpposite();

            if (!this.level().loadedAndEntityCanStandOnFace(target.relative(face), this, direction1)) {
                return false;
            } else {
                AABB aabb = getProgressAabb(this.getScale(), direction1, 1.0F, target.getBottomCenter()).deflate(1.0E-6D);

                return this.level().noCollision(this, aabb);
            }
        }
    }

    private boolean isPositionBlocked(BlockPos target) {
        BlockState blockstate = this.level().getBlockState(target);

        if (blockstate.isAir()) {
            return false;
        } else {
            boolean flag = blockstate.is(Blocks.MOVING_PISTON) && target.equals(this.blockPosition());

            return !flag;
        }
    }

    protected boolean teleportSomewhere() {
        if (!this.isNoAi() && this.isAlive()) {
            BlockPos blockpos = this.blockPosition();

            for (int i = 0; i < 5; ++i) {
                BlockPos blockpos1 = blockpos.offset(Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8), Mth.randomBetweenInclusive(this.random, -8, 8));

                if (blockpos1.getY() > this.level().getMinY() && this.level().isEmptyBlock(blockpos1) && this.level().getWorldBorder().isWithinBounds(blockpos1) && this.level().noCollision(this, (new AABB(blockpos1)).deflate(1.0E-6D))) {
                    Direction direction = this.findAttachableSurface(blockpos1);

                    if (direction != null) {
                        this.unRide();
                        this.setAttachFace(direction);
                        this.playSound(SoundEvents.SHULKER_TELEPORT, 1.0F, 1.0F);
                        this.setPos((double) blockpos1.getX() + 0.5D, (double) blockpos1.getY(), (double) blockpos1.getZ() + 0.5D);
                        this.level().gameEvent(GameEvent.TELEPORT, blockpos, GameEvent.Context.of((Entity) this));
                        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) 0);
                        this.setTarget((LivingEntity) null);
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return null;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isClosed()) {
            Entity entity = source.getDirectEntity();

            if (entity instanceof AbstractArrow) {
                return false;
            }
        }

        if (!super.hurtServer(level, source, damage)) {
            return false;
        } else {
            if ((double) this.getHealth() < (double) this.getMaxHealth() * 0.5D && this.random.nextInt(4) == 0) {
                this.teleportSomewhere();
            } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                Entity entity1 = source.getDirectEntity();

                if (entity1 != null && entity1.getType() == EntityType.SHULKER_BULLET) {
                    this.hitByShulkerBullet();
                }
            }

            return true;
        }
    }

    private boolean isClosed() {
        return this.getRawPeekAmount() == 0;
    }

    private void hitByShulkerBullet() {
        Vec3 vec3 = this.position();
        AABB aabb = this.getBoundingBox();

        if (!this.isClosed() && this.teleportSomewhere()) {
            int i = this.level().getEntities((EntityTypeTest) EntityType.SHULKER, aabb.inflate(8.0D), Entity::isAlive).size();
            float f = (float) (i - 1) / 5.0F;

            if (this.level().random.nextFloat() >= f) {
                Shulker shulker = EntityType.SHULKER.create(this.level(), EntitySpawnReason.BREEDING);

                if (shulker != null) {
                    shulker.setVariant(this.getVariant());
                    shulker.snapTo(vec3);
                    this.level().addFreshEntity(shulker);
                }

            }
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return this.isAlive();
    }

    public Direction getAttachFace() {
        return (Direction) this.entityData.get(Shulker.DATA_ATTACH_FACE_ID);
    }

    public void setAttachFace(Direction attachmentDirection) {
        this.entityData.set(Shulker.DATA_ATTACH_FACE_ID, attachmentDirection);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (Shulker.DATA_ATTACH_FACE_ID.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

        super.onSyncedDataUpdated(accessor);
    }

    public int getRawPeekAmount() {
        return (Byte) this.entityData.get(Shulker.DATA_PEEK_ID);
    }

    public void setRawPeekAmount(int amount) {
        if (!this.level().isClientSide()) {
            this.getAttribute(Attributes.ARMOR).removeModifier(Shulker.COVERED_ARMOR_MODIFIER_ID);
            if (amount == 0) {
                this.getAttribute(Attributes.ARMOR).addPermanentModifier(Shulker.COVERED_ARMOR_MODIFIER);
                this.playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_CLOSE);
            } else {
                this.playSound(SoundEvents.SHULKER_OPEN, 1.0F, 1.0F);
                this.gameEvent(GameEvent.CONTAINER_OPEN);
            }
        }

        this.entityData.set(Shulker.DATA_PEEK_ID, (byte) amount);
    }

    public float getClientPeekAmount(float a) {
        return Mth.lerp(a, this.currentPeekAmountO, this.currentPeekAmount);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.yBodyRot = 0.0F;
        this.yBodyRotO = 0.0F;
    }

    @Override
    public int getMaxHeadXRot() {
        return 180;
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    public void push(Entity entity) {}

    public @Nullable Vec3 getRenderPosition(float a) {
        if (this.clientOldAttachPosition != null && this.clientSideTeleportInterpolation > 0) {
            double d0 = (double) ((float) this.clientSideTeleportInterpolation - a) / 6.0D;

            d0 *= d0;
            d0 *= (double) this.getScale();
            BlockPos blockpos = this.blockPosition();
            double d1 = (double) (blockpos.getX() - this.clientOldAttachPosition.getX()) * d0;
            double d2 = (double) (blockpos.getY() - this.clientOldAttachPosition.getY()) * d0;
            double d3 = (double) (blockpos.getZ() - this.clientOldAttachPosition.getZ()) * d0;

            return new Vec3(-d1, -d2, -d3);
        } else {
            return null;
        }
    }

    @Override
    protected float sanitizeScale(float scale) {
        return Math.min(scale, 3.0F);
    }

    private void setVariant(Optional<DyeColor> color) {
        this.entityData.set(Shulker.DATA_COLOR_ID, (Byte) color.map((dyecolor) -> {
            return (byte) dyecolor.getId();
        }).orElse((byte) 16));
    }

    public Optional<DyeColor> getVariant() {
        return Optional.ofNullable(this.getColor());
    }

    public @Nullable DyeColor getColor() {
        byte b0 = (Byte) this.entityData.get(Shulker.DATA_COLOR_ID);

        return b0 != 16 && b0 <= 15 ? DyeColor.byId(b0) : null;
    }

    @Override
    public <T> @Nullable T get(DataComponentType<? extends T> type) {
        return (T) (type == DataComponents.SHULKER_COLOR ? castComponentValue(type, this.getColor()) : super.get(type));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        this.applyImplicitComponentIfPresent(components, DataComponents.SHULKER_COLOR);
        super.applyImplicitComponents(components);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> type, T value) {
        if (type == DataComponents.SHULKER_COLOR) {
            this.setVariant(Optional.of((DyeColor) castComponentValue(DataComponents.SHULKER_COLOR, value)));
            return true;
        } else {
            return super.applyImplicitComponent(type, value);
        }
    }

    private class ShulkerLookControl extends LookControl {

        public ShulkerLookControl(Mob mob) {
            super(mob);
        }

        @Override
        protected void clampHeadRotationToBody() {}

        @Override
        protected Optional<Float> getYRotD() {
            Direction direction = Shulker.this.getAttachFace().getOpposite();
            Vector3f vector3f = direction.getRotation().transform(new Vector3f(Shulker.FORWARD));
            Vec3i vec3i = direction.getUnitVec3i();
            Vector3f vector3f1 = new Vector3f((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ());

            vector3f1.cross(vector3f);
            double d0 = this.wantedX - this.mob.getX();
            double d1 = this.wantedY - this.mob.getEyeY();
            double d2 = this.wantedZ - this.mob.getZ();
            Vector3f vector3f2 = new Vector3f((float) d0, (float) d1, (float) d2);
            float f = vector3f1.dot(vector3f2);
            float f1 = vector3f.dot(vector3f2);

            return Math.abs(f) <= 1.0E-5F && Math.abs(f1) <= 1.0E-5F ? Optional.empty() : Optional.of((float) (Mth.atan2((double) (-f), (double) f1) * (double) (180F / (float) Math.PI)));
        }

        @Override
        protected Optional<Float> getXRotD() {
            return Optional.of(0.0F);
        }
    }

    private static class ShulkerBodyRotationControl extends BodyRotationControl {

        public ShulkerBodyRotationControl(Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {}
    }

    private class ShulkerPeekGoal extends Goal {

        private int peekTime;

        private ShulkerPeekGoal() {}

        @Override
        public boolean canUse() {
            return Shulker.this.getTarget() == null && Shulker.this.random.nextInt(reducedTickDelay(40)) == 0 && Shulker.this.canStayAt(Shulker.this.blockPosition(), Shulker.this.getAttachFace());
        }

        @Override
        public boolean canContinueToUse() {
            return Shulker.this.getTarget() == null && this.peekTime > 0;
        }

        @Override
        public void start() {
            this.peekTime = this.adjustedTickDelay(20 * (1 + Shulker.this.random.nextInt(3)));
            Shulker.this.setRawPeekAmount(30);
        }

        @Override
        public void stop() {
            if (Shulker.this.getTarget() == null) {
                Shulker.this.setRawPeekAmount(0);
            }

        }

        @Override
        public void tick() {
            --this.peekTime;
        }
    }

    private class ShulkerAttackGoal extends Goal {

        private int attackTime;

        public ShulkerAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity livingentity = Shulker.this.getTarget();

            return livingentity != null && livingentity.isAlive() ? Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL : false;
        }

        @Override
        public void start() {
            this.attackTime = 20;
            Shulker.this.setRawPeekAmount(100);
        }

        @Override
        public void stop() {
            Shulker.this.setRawPeekAmount(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (Shulker.this.level().getDifficulty() != Difficulty.PEACEFUL) {
                --this.attackTime;
                LivingEntity livingentity = Shulker.this.getTarget();

                if (livingentity != null) {
                    Shulker.this.getLookControl().setLookAt(livingentity, 180.0F, 180.0F);
                    double d0 = Shulker.this.distanceToSqr((Entity) livingentity);

                    if (d0 < 400.0D) {
                        if (this.attackTime <= 0) {
                            this.attackTime = 20 + Shulker.this.random.nextInt(10) * 20 / 2;
                            Shulker.this.level().addFreshEntity(new ShulkerBullet(Shulker.this.level(), Shulker.this, livingentity, Shulker.this.getAttachFace().getAxis()));
                            Shulker.this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (Shulker.this.random.nextFloat() - Shulker.this.random.nextFloat()) * 0.2F + 1.0F);
                        }
                    } else {
                        Shulker.this.setTarget((LivingEntity) null);
                    }

                    super.tick();
                }
            }
        }
    }

    private class ShulkerNearestAttackGoal extends NearestAttackableTargetGoal<Player> {

        public ShulkerNearestAttackGoal(Shulker mob) {
            super(mob, Player.class, true);
        }

        @Override
        public boolean canUse() {
            return Shulker.this.level().getDifficulty() == Difficulty.PEACEFUL ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double followDistance) {
            Direction direction = ((Shulker) this.mob).getAttachFace();

            return direction.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, followDistance, followDistance) : (direction.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(followDistance, followDistance, 4.0D) : this.mob.getBoundingBox().inflate(followDistance, 4.0D, followDistance));
        }
    }

    private static class ShulkerDefenseAttackGoal extends NearestAttackableTargetGoal<LivingEntity> {

        public ShulkerDefenseAttackGoal(Shulker mob) {
            super(mob, LivingEntity.class, 10, true, false, (livingentity, serverlevel) -> {
                return livingentity instanceof Enemy;
            });
        }

        @Override
        public boolean canUse() {
            return this.mob.getTeam() == null ? false : super.canUse();
        }

        @Override
        protected AABB getTargetSearchArea(double followDistance) {
            Direction direction = ((Shulker) this.mob).getAttachFace();

            return direction.getAxis() == Direction.Axis.X ? this.mob.getBoundingBox().inflate(4.0D, followDistance, followDistance) : (direction.getAxis() == Direction.Axis.Z ? this.mob.getBoundingBox().inflate(followDistance, followDistance, 4.0D) : this.mob.getBoundingBox().inflate(followDistance, 4.0D, followDistance));
        }
    }
}
