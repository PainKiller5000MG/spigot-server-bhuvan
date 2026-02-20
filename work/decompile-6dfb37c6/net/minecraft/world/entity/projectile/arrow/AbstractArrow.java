package net.minecraft.world.entity.projectile.arrow;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class AbstractArrow extends Projectile {

    private static final double ARROW_BASE_DAMAGE = 2.0D;
    private static final int SHAKE_TIME = 7;
    private static final float WATER_INERTIA = 0.6F;
    private static final float INERTIA = 0.99F;
    private static final short DEFAULT_LIFE = 0;
    private static final byte DEFAULT_SHAKE = 0;
    private static final boolean DEFAULT_IN_GROUND = false;
    private static final boolean DEFAULT_CRIT = false;
    private static final byte DEFAULT_PIERCE_LEVEL = 0;
    private static final EntityDataAccessor<Byte> ID_FLAGS = SynchedEntityData.<Byte>defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> PIERCE_LEVEL = SynchedEntityData.<Byte>defineId(AbstractArrow.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IN_GROUND = SynchedEntityData.<Boolean>defineId(AbstractArrow.class, EntityDataSerializers.BOOLEAN);
    private static final int FLAG_CRIT = 1;
    private static final int FLAG_NOPHYSICS = 2;
    private @Nullable BlockState lastState;
    protected int inGroundTime;
    public AbstractArrow.Pickup pickup;
    public int shakeTime;
    public int life;
    public double baseDamage;
    private SoundEvent soundEvent;
    private @Nullable IntOpenHashSet piercingIgnoreEntityIds;
    private @Nullable List<Entity> piercedAndKilledEntities;
    public ItemStack pickupItemStack;
    public @Nullable ItemStack firedFromWeapon;

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.shakeTime = 0;
        this.life = 0;
        this.baseDamage = 2.0D;
        this.soundEvent = this.getDefaultHitGroundSoundEvent();
        this.pickupItemStack = this.getDefaultPickupItem();
        this.firedFromWeapon = null;
    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, double x, double y, double z, Level level, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        this(type, level);
        this.pickupItemStack = pickupItemStack.copy();
        this.applyComponentsFromItemStack(pickupItemStack);
        Unit unit = (Unit) pickupItemStack.remove(DataComponents.INTANGIBLE_PROJECTILE);

        if (unit != null) {
            this.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        this.setPos(x, y, z);
        if (firedFromWeapon != null && level instanceof ServerLevel serverlevel) {
            if (firedFromWeapon.isEmpty()) {
                throw new IllegalArgumentException("Invalid weapon firing an arrow");
            }

            this.firedFromWeapon = firedFromWeapon.copy();
            int i = EnchantmentHelper.getPiercingCount(serverlevel, firedFromWeapon, this.pickupItemStack);

            if (i > 0) {
                this.setPierceLevel((byte) i);
            }
        }

    }

    protected AbstractArrow(EntityType<? extends AbstractArrow> type, LivingEntity mob, Level level, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        this(type, mob.getX(), mob.getEyeY() - (double) 0.1F, mob.getZ(), level, pickupItemStack, firedFromWeapon);
        this.setOwner(mob);
    }

    public void setSoundEvent(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 10.0D;

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * getViewScale();
        return distance < d1 * d1;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(AbstractArrow.ID_FLAGS, (byte) 0);
        entityData.define(AbstractArrow.PIERCE_LEVEL, (byte) 0);
        entityData.define(AbstractArrow.IN_GROUND, false);
    }

    @Override
    public void shoot(double xd, double yd, double zd, float pow, float uncertainty) {
        super.shoot(xd, yd, zd, pow, uncertainty);
        this.life = 0;
    }

    @Override
    public void lerpMotion(Vec3 movement) {
        super.lerpMotion(movement);
        this.life = 0;
        if (this.isInGround() && movement.lengthSqr() > 0.0D) {
            this.setInGround(false);
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!this.firstTick && this.shakeTime <= 0 && accessor.equals(AbstractArrow.IN_GROUND) && this.isInGround()) {
            this.shakeTime = 7;
        }

    }

    @Override
    public void tick() {
        boolean flag = !this.isNoPhysics();
        Vec3 vec3 = this.getDeltaMovement();
        BlockPos blockpos = this.blockPosition();
        BlockState blockstate = this.level().getBlockState(blockpos);

        if (!blockstate.isAir() && flag) {
            VoxelShape voxelshape = blockstate.getCollisionShape(this.level(), blockpos);

            if (!voxelshape.isEmpty()) {
                Vec3 vec31 = this.position();

                for (AABB aabb : voxelshape.toAabbs()) {
                    if (aabb.move(blockpos).contains(vec31)) {
                        this.setDeltaMovement(Vec3.ZERO);
                        this.setInGround(true);
                        break;
                    }
                }
            }
        }

        if (this.shakeTime > 0) {
            --this.shakeTime;
        }

        if (this.isInWaterOrRain()) {
            this.clearFire();
        }

        if (this.isInGround() && flag) {
            if (!this.level().isClientSide()) {
                if (this.lastState != blockstate && this.shouldFall()) {
                    this.startFalling();
                } else {
                    this.tickDespawn();
                }
            }

            ++this.inGroundTime;
            if (this.isAlive()) {
                this.applyEffectsFromBlocks();
            }

            if (!this.level().isClientSide()) {
                this.setSharedFlagOnFire(this.getRemainingFireTicks() > 0);
            }

        } else {
            this.inGroundTime = 0;
            Vec3 vec32 = this.position();

            if (this.isInWater()) {
                this.applyInertia(this.getWaterInertia());
                this.addBubbleParticles(vec32);
            }

            if (this.isCritArrow()) {
                for (int i = 0; i < 4; ++i) {
                    this.level().addParticle(ParticleTypes.CRIT, vec32.x + vec3.x * (double) i / 4.0D, vec32.y + vec3.y * (double) i / 4.0D, vec32.z + vec3.z * (double) i / 4.0D, -vec3.x, -vec3.y + 0.2D, -vec3.z);
                }
            }

            float f;

            if (!flag) {
                f = (float) (Mth.atan2(-vec3.x, -vec3.z) * (double) (180F / (float) Math.PI));
            } else {
                f = (float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI));
            }

            float f1 = (float) (Mth.atan2(vec3.y, vec3.horizontalDistance()) * (double) (180F / (float) Math.PI));

            this.setXRot(lerpRotation(this.getXRot(), f1));
            this.setYRot(lerpRotation(this.getYRot(), f));
            this.checkLeftOwner();
            if (flag) {
                BlockHitResult blockhitresult = this.level().clipIncludingBorder(new ClipContext(vec32, vec32.add(vec3), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

                this.stepMoveAndHit(blockhitresult);
            } else {
                this.setPos(vec32.add(vec3));
                this.applyEffectsFromBlocks();
            }

            if (!this.isInWater()) {
                this.applyInertia(0.99F);
            }

            if (flag && !this.isInGround()) {
                this.applyGravity();
            }

            super.tick();
        }
    }

    private void stepMoveAndHit(BlockHitResult blockHitResult) {
        while (true) {
            if (this.isAlive()) {
                Vec3 vec3 = this.position();
                ArrayList<EntityHitResult> arraylist = new ArrayList(this.findHitEntities(vec3, blockHitResult.getLocation()));

                arraylist.sort(Comparator.comparingDouble((entityhitresult) -> {
                    return vec3.distanceToSqr(entityhitresult.getEntity().position());
                }));
                EntityHitResult entityhitresult = arraylist.isEmpty() ? null : (EntityHitResult) arraylist.getFirst();
                Vec3 vec31 = ((HitResult) Objects.requireNonNullElse(entityhitresult, blockHitResult)).getLocation();

                this.setPos(vec31);
                this.applyEffectsFromBlocks(vec3, vec31);
                if (this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
                    this.handlePortal();
                }

                if (arraylist.isEmpty()) {
                    if (this.isAlive() && blockHitResult.getType() != HitResult.Type.MISS) {
                        this.hitTargetOrDeflectSelf(blockHitResult);
                        this.needsSync = true;
                    }
                } else {
                    if (!this.isAlive() || this.noPhysics) {
                        continue;
                    }

                    ProjectileDeflection projectiledeflection = this.hitTargetsOrDeflectSelf(arraylist);

                    this.needsSync = true;
                    if (this.getPierceLevel() > 0 && projectiledeflection == ProjectileDeflection.NONE) {
                        continue;
                    }
                }
            }

            return;
        }
    }

    private ProjectileDeflection hitTargetsOrDeflectSelf(Collection<EntityHitResult> entityHitResults) {
        for (EntityHitResult entityhitresult : entityHitResults) {
            ProjectileDeflection projectiledeflection = this.hitTargetOrDeflectSelf(entityhitresult);

            if (!this.isAlive() || projectiledeflection != ProjectileDeflection.NONE) {
                return projectiledeflection;
            }
        }

        return ProjectileDeflection.NONE;
    }

    private void applyInertia(float inertia) {
        Vec3 vec3 = this.getDeltaMovement();

        this.setDeltaMovement(vec3.scale((double) inertia));
    }

    private void addBubbleParticles(Vec3 position) {
        Vec3 vec31 = this.getDeltaMovement();

        for (int i = 0; i < 4; ++i) {
            float f = 0.25F;

            this.level().addParticle(ParticleTypes.BUBBLE, position.x - vec31.x * 0.25D, position.y - vec31.y * 0.25D, position.z - vec31.z * 0.25D, vec31.x, vec31.y, vec31.z);
        }

    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    private boolean shouldFall() {
        return this.isInGround() && this.level().noCollision((new AABB(this.position(), this.position())).inflate(0.06D));
    }

    private void startFalling() {
        this.setInGround(false);
        Vec3 vec3 = this.getDeltaMovement();

        this.setDeltaMovement(vec3.multiply((double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F), (double) (this.random.nextFloat() * 0.2F)));
        this.life = 0;
    }

    public boolean isInGround() {
        return (Boolean) this.entityData.get(AbstractArrow.IN_GROUND);
    }

    protected void setInGround(boolean inGround) {
        this.entityData.set(AbstractArrow.IN_GROUND, inGround);
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.isInGround();
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        super.move(moverType, delta);
        if (moverType != MoverType.SELF && this.shouldFall()) {
            this.startFalling();
        }

    }

    protected void tickDespawn() {
        ++this.life;
        if (this.life >= 1200) {
            this.discard();
        }

    }

    private void resetPiercedEntities() {
        if (this.piercedAndKilledEntities != null) {
            this.piercedAndKilledEntities.clear();
        }

        if (this.piercingIgnoreEntityIds != null) {
            this.piercingIgnoreEntityIds.clear();
        }

    }

    @Override
    public void onItemBreak(Item item) {
        this.firedFromWeapon = null;
    }

    @Override
    public void onAboveBubbleColumn(boolean dragDown, BlockPos pos) {
        if (!this.isInGround()) {
            super.onAboveBubbleColumn(dragDown, pos);
        }
    }

    @Override
    public void onInsideBubbleColumn(boolean dragDown) {
        if (!this.isInGround()) {
            super.onInsideBubbleColumn(dragDown);
        }
    }

    @Override
    public void push(double xa, double ya, double za) {
        if (!this.isInGround()) {
            super.push(xa, ya, za);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = hitResult.getEntity();
        float f = (float) this.getDeltaMovement().length();
        double d0 = this.baseDamage;
        Entity entity1 = this.getOwner();
        DamageSource damagesource = this.damageSources().arrow(this, (Entity) (entity1 != null ? entity1 : this));

        if (this.getWeaponItem() != null) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                d0 = (double) EnchantmentHelper.modifyDamage(serverlevel, this.getWeaponItem(), entity, damagesource, (float) d0);
            }
        }

        int i = Mth.ceil(Mth.clamp((double) f * d0, 0.0D, (double) Integer.MAX_VALUE));

        if (this.getPierceLevel() > 0) {
            if (this.piercingIgnoreEntityIds == null) {
                this.piercingIgnoreEntityIds = new IntOpenHashSet(5);
            }

            if (this.piercedAndKilledEntities == null) {
                this.piercedAndKilledEntities = Lists.newArrayListWithCapacity(5);
            }

            if (this.piercingIgnoreEntityIds.size() >= this.getPierceLevel() + 1) {
                this.discard();
                return;
            }

            this.piercingIgnoreEntityIds.add(entity.getId());
        }

        if (this.isCritArrow()) {
            long j = (long) this.random.nextInt(i / 2 + 2);

            i = (int) Math.min(j + (long) i, 2147483647L);
        }

        if (entity1 instanceof LivingEntity livingentity) {
            livingentity.setLastHurtMob(entity);
        }

        boolean flag = entity.getType() == EntityType.ENDERMAN;
        int k = entity.getRemainingFireTicks();

        if (this.isOnFire() && !flag) {
            entity.igniteForSeconds(5.0F);
        }

        if (entity.hurtOrSimulate(damagesource, (float) i)) {
            if (flag) {
                return;
            }

            if (entity instanceof LivingEntity) {
                LivingEntity livingentity1 = (LivingEntity) entity;

                if (!this.level().isClientSide() && this.getPierceLevel() <= 0) {
                    livingentity1.setArrowCount(livingentity1.getArrowCount() + 1);
                }

                this.doKnockback(livingentity1, damagesource);
                Level level1 = this.level();

                if (level1 instanceof ServerLevel) {
                    ServerLevel serverlevel1 = (ServerLevel) level1;

                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverlevel1, livingentity1, damagesource, this.getWeaponItem());
                }

                this.doPostHurtEffects(livingentity1);
                if (livingentity1 instanceof Player && entity1 instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) entity1;

                    if (!this.isSilent() && livingentity1 != serverplayer) {
                        serverplayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND, 0.0F));
                    }
                }

                if (!entity.isAlive() && this.piercedAndKilledEntities != null) {
                    this.piercedAndKilledEntities.add(livingentity1);
                }

                if (!this.level().isClientSide() && entity1 instanceof ServerPlayer) {
                    ServerPlayer serverplayer1 = (ServerPlayer) entity1;

                    if (this.piercedAndKilledEntities != null) {
                        CriteriaTriggers.KILLED_BY_ARROW.trigger(serverplayer1, this.piercedAndKilledEntities, this.firedFromWeapon);
                    } else if (!entity.isAlive()) {
                        CriteriaTriggers.KILLED_BY_ARROW.trigger(serverplayer1, List.of(entity), this.firedFromWeapon);
                    }
                }
            }

            this.playSound(this.soundEvent, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
            if (this.getPierceLevel() <= 0) {
                this.discard();
            }
        } else {
            entity.setRemainingFireTicks(k);
            this.deflect(ProjectileDeflection.REVERSE, entity, this.owner, false);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.2D));
            Level level2 = this.level();

            if (level2 instanceof ServerLevel) {
                ServerLevel serverlevel2 = (ServerLevel) level2;

                if (this.getDeltaMovement().lengthSqr() < 1.0E-7D) {
                    if (this.pickup == AbstractArrow.Pickup.ALLOWED) {
                        this.spawnAtLocation(serverlevel2, this.getPickupItem(), 0.1F);
                    }

                    this.discard();
                }
            }
        }

    }

    protected void doKnockback(LivingEntity mob, DamageSource damageSource) {
        float f;
        label18:
        {
            if (this.firedFromWeapon != null) {
                Level level = this.level();

                if (level instanceof ServerLevel) {
                    ServerLevel serverlevel = (ServerLevel) level;

                    f = EnchantmentHelper.modifyKnockback(serverlevel, this.firedFromWeapon, mob, damageSource, 0.0F);
                    break label18;
                }
            }

            f = 0.0F;
        }

        double d0 = (double) f;

        if (d0 > 0.0D) {
            double d1 = Math.max(0.0D, 1.0D - mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 vec3 = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale(d0 * 0.6D * d1);

            if (vec3.lengthSqr() > 0.0D) {
                mob.push(vec3.x, 0.1D, vec3.z);
            }
        }

    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        this.lastState = this.level().getBlockState(hitResult.getBlockPos());
        super.onHitBlock(hitResult);
        ItemStack itemstack = this.getWeaponItem();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (itemstack != null) {
                this.hitBlockEnchantmentEffects(serverlevel, hitResult, itemstack);
            }
        }

        Vec3 vec3 = this.getDeltaMovement();
        Vec3 vec31 = new Vec3(Math.signum(vec3.x), Math.signum(vec3.y), Math.signum(vec3.z));
        Vec3 vec32 = vec31.scale((double) 0.05F);

        this.setPos(this.position().subtract(vec32));
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.setInGround(true);
        this.shakeTime = 7;
        this.setCritArrow(false);
        this.setPierceLevel((byte) 0);
        this.setSoundEvent(SoundEvents.ARROW_HIT);
        this.resetPiercedEntities();
    }

    protected void hitBlockEnchantmentEffects(ServerLevel serverLevel, BlockHitResult hitResult, ItemStack weapon) {
        Vec3 vec3 = hitResult.getBlockPos().clampLocationWithin(hitResult.getLocation());
        Entity entity = this.getOwner();
        LivingEntity livingentity;

        if (entity instanceof LivingEntity livingentity1) {
            livingentity = livingentity1;
        } else {
            livingentity = null;
        }

        EnchantmentHelper.onHitBlock(serverLevel, weapon, livingentity, this, (EquipmentSlot) null, vec3, serverLevel.getBlockState(hitResult.getBlockPos()), (item) -> {
            this.firedFromWeapon = null;
        });
    }

    @Override
    public @Nullable ItemStack getWeaponItem() {
        return this.firedFromWeapon;
    }

    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }

    protected final SoundEvent getHitGroundSoundEvent() {
        return this.soundEvent;
    }

    protected void doPostHurtEffects(LivingEntity mob) {}

    protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return ProjectileUtil.getEntityHitResult(this.level(), this, from, to, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), this::canHitEntity);
    }

    protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
        return ProjectileUtil.getManyEntityHitResult(this.level(), this, from, to, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D), this::canHitEntity, false);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity instanceof Player) {
            Entity entity1 = this.getOwner();

            if (entity1 instanceof Player) {
                Player player = (Player) entity1;

                if (!player.canHarmPlayer((Player) entity)) {
                    return false;
                }
            }
        }

        return super.canHitEntity(entity) && (this.piercingIgnoreEntityIds == null || !this.piercingIgnoreEntityIds.contains(entity.getId()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putShort("life", (short) this.life);
        output.storeNullable("inBlockState", BlockState.CODEC, this.lastState);
        output.putByte("shake", (byte) this.shakeTime);
        output.putBoolean("inGround", this.isInGround());
        output.store("pickup", AbstractArrow.Pickup.LEGACY_CODEC, this.pickup);
        output.putDouble("damage", this.baseDamage);
        output.putBoolean("crit", this.isCritArrow());
        output.putByte("PierceLevel", this.getPierceLevel());
        output.store("SoundEvent", BuiltInRegistries.SOUND_EVENT.byNameCodec(), this.soundEvent);
        output.store("item", ItemStack.CODEC, this.pickupItemStack);
        output.storeNullable("weapon", ItemStack.CODEC, this.firedFromWeapon);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.life = input.getShortOr("life", (short) 0);
        this.lastState = (BlockState) input.read("inBlockState", BlockState.CODEC).orElse((Object) null);
        this.shakeTime = input.getByteOr("shake", (byte) 0) & 255;
        this.setInGround(input.getBooleanOr("inGround", false));
        this.baseDamage = input.getDoubleOr("damage", 2.0D);
        this.pickup = (AbstractArrow.Pickup) input.read("pickup", AbstractArrow.Pickup.LEGACY_CODEC).orElse(AbstractArrow.Pickup.DISALLOWED);
        this.setCritArrow(input.getBooleanOr("crit", false));
        this.setPierceLevel(input.getByteOr("PierceLevel", (byte) 0));
        this.soundEvent = (SoundEvent) input.read("SoundEvent", BuiltInRegistries.SOUND_EVENT.byNameCodec()).orElse(this.getDefaultHitGroundSoundEvent());
        this.setPickupItemStack((ItemStack) input.read("item", ItemStack.CODEC).orElse(this.getDefaultPickupItem()));
        this.firedFromWeapon = (ItemStack) input.read("weapon", ItemStack.CODEC).orElse((Object) null);
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        Entity entity1 = owner;
        byte b0 = 0;

        AbstractArrow.Pickup abstractarrow_pickup;

        label16:
        while(true) {
            //$FF: b0->value
            //0->net/minecraft/world/entity/player/Player
            //1->net/minecraft/world/entity/OminousItemSpawner
            switch (entity1.typeSwitch<invokedynamic>(entity1, b0)) {
                case -1:
                default:
                    abstractarrow_pickup = this.pickup;
                    break label16;
                case 0:
                    Player player = (Player)entity1;

                    if (this.pickup != AbstractArrow.Pickup.DISALLOWED) {
                        b0 = 1;
                        break;
                    }

                    abstractarrow_pickup = AbstractArrow.Pickup.ALLOWED;
                    break label16;
                case 1:
                    OminousItemSpawner ominousitemspawner = (OminousItemSpawner)entity1;

                    abstractarrow_pickup = AbstractArrow.Pickup.DISALLOWED;
                    break label16;
            }
        }

        this.pickup = abstractarrow_pickup;
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide() && (this.isInGround() || this.isNoPhysics()) && this.shakeTime <= 0) {
            if (this.tryPickup(player)) {
                player.take(this, 1);
                this.discard();
            }

        }
    }

    protected boolean tryPickup(Player player) {
        boolean flag;

        switch (this.pickup.ordinal()) {
            case 0:
                flag = false;
                break;
            case 1:
                flag = player.getInventory().add(this.getPickupItem());
                break;
            case 2:
                flag = player.hasInfiniteMaterials();
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return flag;
    }

    protected ItemStack getPickupItem() {
        return this.pickupItemStack.copy();
    }

    protected abstract ItemStack getDefaultPickupItem();

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    public ItemStack getPickupItemStackOrigin() {
        return this.pickupItemStack;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    @Override
    public boolean isAttackable() {
        return this.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE);
    }

    public void setCritArrow(boolean critArrow) {
        this.setFlag(1, critArrow);
    }

    public void setPierceLevel(byte pieceLevel) {
        this.entityData.set(AbstractArrow.PIERCE_LEVEL, pieceLevel);
    }

    private void setFlag(int flag, boolean value) {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        if (value) {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 | flag));
        } else {
            this.entityData.set(AbstractArrow.ID_FLAGS, (byte) (b0 & ~flag));
        }

    }

    protected void setPickupItemStack(ItemStack itemStack) {
        if (!itemStack.isEmpty()) {
            this.pickupItemStack = itemStack;
        } else {
            this.pickupItemStack = this.getDefaultPickupItem();
        }

    }

    public boolean isCritArrow() {
        byte b0 = (Byte) this.entityData.get(AbstractArrow.ID_FLAGS);

        return (b0 & 1) != 0;
    }

    public byte getPierceLevel() {
        return (Byte) this.entityData.get(AbstractArrow.PIERCE_LEVEL);
    }

    public void setBaseDamageFromMob(float power) {
        this.setBaseDamage((double) (power * 2.0F) + this.random.triangle((double) this.level().getDifficulty().getId() * 0.11D, 0.57425D));
    }

    protected float getWaterInertia() {
        return 0.6F;
    }

    public void setNoPhysics(boolean noPhysics) {
        this.noPhysics = noPhysics;
        this.setFlag(2, noPhysics);
    }

    public boolean isNoPhysics() {
        return !this.level().isClientSide() ? this.noPhysics : ((Byte) this.entityData.get(AbstractArrow.ID_FLAGS) & 2) != 0;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isInGround();
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getPickupItemStackOrigin, this::setPickupItemStack) : super.getSlot(slot);
    }

    @Override
    protected boolean shouldBounceOnWorldBorder() {
        return true;
    }

    public static enum Pickup {

        DISALLOWED, ALLOWED, CREATIVE_ONLY;

        public static final Codec<AbstractArrow.Pickup> LEGACY_CODEC = Codec.BYTE.xmap(AbstractArrow.Pickup::byOrdinal, (abstractarrow_pickup) -> {
            return (byte) abstractarrow_pickup.ordinal();
        });

        private Pickup() {}

        public static AbstractArrow.Pickup byOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal > values().length) {
                ordinal = 0;
            }

            return values()[ordinal];
        }
    }
}
