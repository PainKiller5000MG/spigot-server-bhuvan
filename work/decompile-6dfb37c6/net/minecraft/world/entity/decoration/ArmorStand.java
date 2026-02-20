package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ArmorStand extends LivingEntity {

    public static final int WOBBLE_TIME = 5;
    private static final boolean ENABLE_ARMS = true;
    public static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    public static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    public static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
    public static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
    public static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
    public static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
    private static final EntityDimensions MARKER_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ARMOR_STAND.getDimensions().scale(0.5F).withEyeHeight(0.9875F);
    private static final double FEET_OFFSET = 0.1D;
    private static final double CHEST_OFFSET = 0.9D;
    private static final double LEGS_OFFSET = 0.4D;
    private static final double HEAD_OFFSET = 1.6D;
    public static final int DISABLE_TAKING_OFFSET = 8;
    public static final int DISABLE_PUTTING_OFFSET = 16;
    public static final int CLIENT_FLAG_SMALL = 1;
    public static final int CLIENT_FLAG_SHOW_ARMS = 4;
    public static final int CLIENT_FLAG_NO_BASEPLATE = 8;
    public static final int CLIENT_FLAG_MARKER = 16;
    public static final EntityDataAccessor<Byte> DATA_CLIENT_FLAGS = SynchedEntityData.<Byte>defineId(ArmorStand.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Rotations> DATA_HEAD_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_BODY_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_ARM_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_ARM_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_LEG_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_LEG_POSE = SynchedEntityData.<Rotations>defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    private static final Predicate<Entity> RIDABLE_MINECARTS = (entity) -> {
        boolean flag;

        if (entity instanceof AbstractMinecart abstractminecart) {
            if (abstractminecart.isRideable()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    };
    private static final boolean DEFAULT_INVISIBLE = false;
    private static final int DEFAULT_DISABLED_SLOTS = 0;
    private static final boolean DEFAULT_SMALL = false;
    private static final boolean DEFAULT_SHOW_ARMS = false;
    private static final boolean DEFAULT_NO_BASE_PLATE = false;
    private static final boolean DEFAULT_MARKER = false;
    private boolean invisible;
    public long lastHit;
    public int disabledSlots;

    public ArmorStand(EntityType<? extends ArmorStand> type, Level level) {
        super(type, level);
        this.invisible = false;
        this.disabledSlots = 0;
    }

    public ArmorStand(Level level, double x, double y, double z) {
        this(EntityType.ARMOR_STAND, level);
        this.setPos(x, y, z);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes().add(Attributes.STEP_HEIGHT, 0.0D);
    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    private boolean hasPhysics() {
        return !this.isMarker() && !this.isNoGravity();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.hasPhysics();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ArmorStand.DATA_CLIENT_FLAGS, (byte) 0);
        entityData.define(ArmorStand.DATA_HEAD_POSE, ArmorStand.DEFAULT_HEAD_POSE);
        entityData.define(ArmorStand.DATA_BODY_POSE, ArmorStand.DEFAULT_BODY_POSE);
        entityData.define(ArmorStand.DATA_LEFT_ARM_POSE, ArmorStand.DEFAULT_LEFT_ARM_POSE);
        entityData.define(ArmorStand.DATA_RIGHT_ARM_POSE, ArmorStand.DEFAULT_RIGHT_ARM_POSE);
        entityData.define(ArmorStand.DATA_LEFT_LEG_POSE, ArmorStand.DEFAULT_LEFT_LEG_POSE);
        entityData.define(ArmorStand.DATA_RIGHT_LEG_POSE, ArmorStand.DEFAULT_RIGHT_LEG_POSE);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE && !this.isDisabled(slot);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Invisible", this.isInvisible());
        output.putBoolean("Small", this.isSmall());
        output.putBoolean("ShowArms", this.showArms());
        output.putInt("DisabledSlots", this.disabledSlots);
        output.putBoolean("NoBasePlate", !this.showBasePlate());
        if (this.isMarker()) {
            output.putBoolean("Marker", this.isMarker());
        }

        output.store("Pose", ArmorStand.ArmorStandPose.CODEC, this.getArmorStandPose());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setInvisible(input.getBooleanOr("Invisible", false));
        this.setSmall(input.getBooleanOr("Small", false));
        this.setShowArms(input.getBooleanOr("ShowArms", false));
        this.disabledSlots = input.getIntOr("DisabledSlots", 0);
        this.setNoBasePlate(input.getBooleanOr("NoBasePlate", false));
        this.setMarker(input.getBooleanOr("Marker", false));
        this.noPhysics = !this.hasPhysics();
        input.read("Pose", ArmorStand.ArmorStandPose.CODEC).ifPresent(this::setArmorStandPose);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox(), ArmorStand.RIDABLE_MINECARTS)) {
            if (this.distanceToSqr(entity) <= 0.2D) {
                entity.push((Entity) this);
            }
        }

    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!this.isMarker() && !itemstack.is(Items.NAME_TAG)) {
            if (player.isSpectator()) {
                return InteractionResult.SUCCESS;
            } else if (player.level().isClientSide()) {
                return InteractionResult.SUCCESS_SERVER;
            } else {
                EquipmentSlot equipmentslot = this.getEquipmentSlotForItem(itemstack);

                if (itemstack.isEmpty()) {
                    EquipmentSlot equipmentslot1 = this.getClickedSlot(location);
                    EquipmentSlot equipmentslot2 = this.isDisabled(equipmentslot1) ? equipmentslot : equipmentslot1;

                    if (this.hasItemInSlot(equipmentslot2) && this.swapItem(player, equipmentslot2, itemstack, hand)) {
                        return InteractionResult.SUCCESS_SERVER;
                    }
                } else {
                    if (this.isDisabled(equipmentslot)) {
                        return InteractionResult.FAIL;
                    }

                    if (equipmentslot.getType() == EquipmentSlot.Type.HAND && !this.showArms()) {
                        return InteractionResult.FAIL;
                    }

                    if (this.swapItem(player, equipmentslot, itemstack, hand)) {
                        return InteractionResult.SUCCESS_SERVER;
                    }
                }

                return InteractionResult.PASS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private EquipmentSlot getClickedSlot(Vec3 location) {
        EquipmentSlot equipmentslot = EquipmentSlot.MAINHAND;
        boolean flag = this.isSmall();
        double d0 = location.y / (double) (this.getScale() * this.getAgeScale());
        EquipmentSlot equipmentslot1 = EquipmentSlot.FEET;

        if (d0 >= 0.1D && d0 < 0.1D + (flag ? 0.8D : 0.45D) && this.hasItemInSlot(equipmentslot1)) {
            equipmentslot = EquipmentSlot.FEET;
        } else if (d0 >= 0.9D + (flag ? 0.3D : 0.0D) && d0 < 0.9D + (flag ? 1.0D : 0.7D) && this.hasItemInSlot(EquipmentSlot.CHEST)) {
            equipmentslot = EquipmentSlot.CHEST;
        } else if (d0 >= 0.4D && d0 < 0.4D + (flag ? 1.0D : 0.8D) && this.hasItemInSlot(EquipmentSlot.LEGS)) {
            equipmentslot = EquipmentSlot.LEGS;
        } else if (d0 >= 1.6D && this.hasItemInSlot(EquipmentSlot.HEAD)) {
            equipmentslot = EquipmentSlot.HEAD;
        } else if (!this.hasItemInSlot(EquipmentSlot.MAINHAND) && this.hasItemInSlot(EquipmentSlot.OFFHAND)) {
            equipmentslot = EquipmentSlot.OFFHAND;
        }

        return equipmentslot;
    }

    private boolean isDisabled(EquipmentSlot slot) {
        return (this.disabledSlots & 1 << slot.getFilterBit(0)) != 0 || slot.getType() == EquipmentSlot.Type.HAND && !this.showArms();
    }

    private boolean swapItem(Player player, EquipmentSlot slot, ItemStack playerItemStack, InteractionHand hand) {
        ItemStack itemstack1 = this.getItemBySlot(slot);

        if (!itemstack1.isEmpty() && (this.disabledSlots & 1 << slot.getFilterBit(8)) != 0) {
            return false;
        } else if (itemstack1.isEmpty() && (this.disabledSlots & 1 << slot.getFilterBit(16)) != 0) {
            return false;
        } else if (player.hasInfiniteMaterials() && itemstack1.isEmpty() && !playerItemStack.isEmpty()) {
            this.setItemSlot(slot, playerItemStack.copyWithCount(1));
            return true;
        } else if (!playerItemStack.isEmpty() && playerItemStack.getCount() > 1) {
            if (!itemstack1.isEmpty()) {
                return false;
            } else {
                this.setItemSlot(slot, playerItemStack.split(1));
                return true;
            }
        } else {
            this.setItemSlot(slot, playerItemStack);
            player.setItemInHand(hand, itemstack1);
            return true;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved()) {
            return false;
        } else if (!(Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING) && source.getEntity() instanceof Mob) {
            return false;
        } else if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.kill(level);
            return false;
        } else if (!this.isInvulnerableTo(level, source) && !this.invisible && !this.isMarker()) {
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                this.brokenByAnything(level, source);
                this.kill(level);
                return false;
            } else if (source.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
                if (this.isOnFire()) {
                    this.causeDamage(level, source, 0.15F);
                } else {
                    this.igniteForSeconds(5.0F);
                }

                return false;
            } else if (source.is(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
                this.causeDamage(level, source, 4.0F);
                return false;
            } else {
                boolean flag = source.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
                boolean flag1 = source.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);

                if (!flag && !flag1) {
                    return false;
                } else {
                    Entity entity = source.getEntity();

                    if (entity instanceof Player) {
                        Player player = (Player) entity;

                        if (!player.getAbilities().mayBuild) {
                            return false;
                        }
                    }

                    if (source.isCreativePlayer()) {
                        this.playBrokenSound();
                        this.showBreakingParticles();
                        this.kill(level);
                        return true;
                    } else {
                        long i = level.getGameTime();

                        if (i - this.lastHit > 5L && !flag1) {
                            level.broadcastEntityEvent(this, (byte) 32);
                            this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
                            this.lastHit = i;
                        } else {
                            this.brokenByPlayer(level, source);
                            this.showBreakingParticles();
                            this.kill(level);
                        }

                        return true;
                    }
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 32) {
            if (this.level().isClientSide()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, this.getSoundSource(), 0.3F, 1.0F, false);
                this.lastHit = this.level().getGameTime();
            }
        } else {
            super.handleEntityEvent(id);
        }

    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1) || d1 == 0.0D) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    private void showBreakingParticles() {
        if (this.level() instanceof ServerLevel) {
            ((ServerLevel) this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), this.getX(), this.getY(0.6666666666666666D), this.getZ(), 10, (double) (this.getBbWidth() / 4.0F), (double) (this.getBbHeight() / 4.0F), (double) (this.getBbWidth() / 4.0F), 0.05D);
        }

    }

    private void causeDamage(ServerLevel level, DamageSource source, float dmg) {
        float f1 = this.getHealth();

        f1 -= dmg;
        if (f1 <= 0.5F) {
            this.brokenByAnything(level, source);
            this.kill(level);
        } else {
            this.setHealth(f1);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        }

    }

    private void brokenByPlayer(ServerLevel level, DamageSource source) {
        ItemStack itemstack = new ItemStack(Items.ARMOR_STAND);

        itemstack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
        Block.popResource(this.level(), this.blockPosition(), itemstack);
        this.brokenByAnything(level, source);
    }

    private void brokenByAnything(ServerLevel level, DamageSource source) {
        this.playBrokenSound();
        this.dropAllDeathLoot(level, source);

        for (EquipmentSlot equipmentslot : EquipmentSlot.VALUES) {
            ItemStack itemstack = this.equipment.set(equipmentslot, ItemStack.EMPTY);

            if (!itemstack.isEmpty()) {
                Block.popResource(this.level(), this.blockPosition().above(), itemstack);
            }
        }

    }

    private void playBrokenSound() {
        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    protected void tickHeadTurn(float yBodyRotT) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
    }

    @Override
    public void travel(Vec3 input) {
        if (this.hasPhysics()) {
            super.travel(input);
        }
    }

    @Override
    public void setYBodyRot(float yBodyRot) {
        this.yBodyRotO = this.yRotO = yBodyRot;
        this.yHeadRotO = this.yHeadRot = yBodyRot;
    }

    @Override
    public void setYHeadRot(float yHeadRot) {
        this.yBodyRotO = this.yRotO = yHeadRot;
        this.yHeadRotO = this.yHeadRot = yHeadRot;
    }

    @Override
    protected void updateInvisibilityStatus() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
        super.setInvisible(invisible);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    @Override
    public void kill(ServerLevel level) {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.shouldAffectBlocklikeEntities() ? this.isInvisible() : true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return this.isMarker() ? PushReaction.IGNORE : super.getPistonPushReaction();
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isMarker();
    }

    public void setSmall(boolean value) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 1, value));
    }

    public boolean isSmall() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 1) != 0;
    }

    public void setShowArms(boolean value) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 4, value));
    }

    public boolean showArms() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 4) != 0;
    }

    public void setNoBasePlate(boolean value) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 8, value));
    }

    public boolean showBasePlate() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 8) == 0;
    }

    public void setMarker(boolean value) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 16, value));
    }

    public boolean isMarker() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 16) != 0;
    }

    private byte setBit(byte data, int bit, boolean value) {
        if (value) {
            data = (byte) (data | bit);
        } else {
            data = (byte) (data & ~bit);
        }

        return data;
    }

    public void setHeadPose(Rotations headPose) {
        this.entityData.set(ArmorStand.DATA_HEAD_POSE, headPose);
    }

    public void setBodyPose(Rotations bodyPose) {
        this.entityData.set(ArmorStand.DATA_BODY_POSE, bodyPose);
    }

    public void setLeftArmPose(Rotations leftArmPose) {
        this.entityData.set(ArmorStand.DATA_LEFT_ARM_POSE, leftArmPose);
    }

    public void setRightArmPose(Rotations rightArmPose) {
        this.entityData.set(ArmorStand.DATA_RIGHT_ARM_POSE, rightArmPose);
    }

    public void setLeftLegPose(Rotations leftLegPose) {
        this.entityData.set(ArmorStand.DATA_LEFT_LEG_POSE, leftLegPose);
    }

    public void setRightLegPose(Rotations rightLegPose) {
        this.entityData.set(ArmorStand.DATA_RIGHT_LEG_POSE, rightLegPose);
    }

    public Rotations getHeadPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_HEAD_POSE);
    }

    public Rotations getBodyPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_BODY_POSE);
    }

    public Rotations getLeftArmPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_LEFT_ARM_POSE);
    }

    public Rotations getRightArmPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_RIGHT_ARM_POSE);
    }

    public Rotations getLeftLegPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_LEFT_LEG_POSE);
    }

    public Rotations getRightLegPose() {
        return (Rotations) this.entityData.get(ArmorStand.DATA_RIGHT_LEG_POSE);
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isMarker();
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        boolean flag;

        if (source instanceof Player player) {
            if (!this.level().mayInteract(player, this.blockPosition())) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.ARMOR_STAND_FALL, SoundEvents.ARMOR_STAND_FALL);
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (ArmorStand.DATA_CLIENT_FLAGS.equals(accessor)) {
            this.refreshDimensions();
            this.blocksBuilding = !this.isMarker();
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getDimensionsMarker(this.isMarker());
    }

    private EntityDimensions getDimensionsMarker(boolean isMarker) {
        return isMarker ? ArmorStand.MARKER_DIMENSIONS : (this.isBaby() ? ArmorStand.BABY_DIMENSIONS : this.getType().getDimensions());
    }

    @Override
    public Vec3 getLightProbePosition(float partialTickTime) {
        if (this.isMarker()) {
            AABB aabb = this.getDimensionsMarker(false).makeBoundingBox(this.position());
            BlockPos blockpos = this.blockPosition();
            int i = Integer.MIN_VALUE;

            for (BlockPos blockpos1 : BlockPos.betweenClosed(BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ), BlockPos.containing(aabb.maxX, aabb.maxY, aabb.maxZ))) {
                int j = Math.max(this.level().getBrightness(LightLayer.BLOCK, blockpos1), this.level().getBrightness(LightLayer.SKY, blockpos1));

                if (j == 15) {
                    return Vec3.atCenterOf(blockpos1);
                }

                if (j > i) {
                    i = j;
                    blockpos = blockpos1.immutable();
                }
            }

            return Vec3.atCenterOf(blockpos);
        } else {
            return super.getLightProbePosition(partialTickTime);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.ARMOR_STAND);
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return !this.isInvisible() && !this.isMarker();
    }

    public void setArmorStandPose(ArmorStand.ArmorStandPose pose) {
        this.setHeadPose(pose.head());
        this.setBodyPose(pose.body());
        this.setLeftArmPose(pose.leftArm());
        this.setRightArmPose(pose.rightArm());
        this.setLeftLegPose(pose.leftLeg());
        this.setRightLegPose(pose.rightLeg());
    }

    public ArmorStand.ArmorStandPose getArmorStandPose() {
        return new ArmorStand.ArmorStandPose(this.getHeadPose(), this.getBodyPose(), this.getLeftArmPose(), this.getRightArmPose(), this.getLeftLegPose(), this.getRightLegPose());
    }

    public static record ArmorStandPose(Rotations head, Rotations body, Rotations leftArm, Rotations rightArm, Rotations leftLeg, Rotations rightLeg) {

        public static final ArmorStand.ArmorStandPose DEFAULT = new ArmorStand.ArmorStandPose(ArmorStand.DEFAULT_HEAD_POSE, ArmorStand.DEFAULT_BODY_POSE, ArmorStand.DEFAULT_LEFT_ARM_POSE, ArmorStand.DEFAULT_RIGHT_ARM_POSE, ArmorStand.DEFAULT_LEFT_LEG_POSE, ArmorStand.DEFAULT_RIGHT_LEG_POSE);
        public static final Codec<ArmorStand.ArmorStandPose> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Rotations.CODEC.optionalFieldOf("Head", ArmorStand.DEFAULT_HEAD_POSE).forGetter(ArmorStand.ArmorStandPose::head), Rotations.CODEC.optionalFieldOf("Body", ArmorStand.DEFAULT_BODY_POSE).forGetter(ArmorStand.ArmorStandPose::body), Rotations.CODEC.optionalFieldOf("LeftArm", ArmorStand.DEFAULT_LEFT_ARM_POSE).forGetter(ArmorStand.ArmorStandPose::leftArm), Rotations.CODEC.optionalFieldOf("RightArm", ArmorStand.DEFAULT_RIGHT_ARM_POSE).forGetter(ArmorStand.ArmorStandPose::rightArm), Rotations.CODEC.optionalFieldOf("LeftLeg", ArmorStand.DEFAULT_LEFT_LEG_POSE).forGetter(ArmorStand.ArmorStandPose::leftLeg), Rotations.CODEC.optionalFieldOf("RightLeg", ArmorStand.DEFAULT_RIGHT_LEG_POSE).forGetter(ArmorStand.ArmorStandPose::rightLeg)).apply(instance, ArmorStand.ArmorStandPose::new);
        });
    }
}
