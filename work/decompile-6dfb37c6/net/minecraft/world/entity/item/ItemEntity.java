package net.minecraft.world.entity.item;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ItemEntity extends Entity implements TraceableEntity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.<ItemStack>defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    private static final int DEFAULT_HEALTH = 5;
    private static final short DEFAULT_AGE = 0;
    private static final short DEFAULT_PICKUP_DELAY = 0;
    public int age;
    public int pickupDelay;
    private int health;
    public @Nullable EntityReference<Entity> thrower;
    public @Nullable UUID target;
    public final float bobOffs;

    public ItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        this.age = 0;
        this.pickupDelay = 0;
        this.health = 5;
        this.bobOffs = this.random.nextFloat() * (float) Math.PI * 2.0F;
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level level, double x, double y, double z, ItemStack itemStack) {
        this(level, x, y, z, itemStack, level.random.nextDouble() * 0.2D - 0.1D, 0.2D, level.random.nextDouble() * 0.2D - 0.1D);
    }

    public ItemEntity(Level level, double x, double y, double z, ItemStack itemStack, double deltaX, double deltaY, double deltaZ) {
        this(EntityType.ITEM, level);
        this.setPos(x, y, z);
        this.setDeltaMovement(deltaX, deltaY, deltaZ);
        this.setItem(itemStack);
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Override
    public @Nullable Entity getOwner() {
        return EntityReference.getEntity(this.thrower, this.level());
    }

    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        if (oldEntity instanceof ItemEntity itementity) {
            this.thrower = itementity.thrower;
        }

    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(ItemEntity.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard();
        } else {
            super.tick();
            if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
                --this.pickupDelay;
            }

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3 = this.getDeltaMovement();

            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > (double) 0.1F) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > (double) 0.1F) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide()) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > (double) 1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.applyEffectsFromBlocks();
                float f = 0.98F;

                if (this.onGround()) {
                    f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.98D, (double) f));
                if (this.onGround()) {
                    Vec3 vec31 = this.getDeltaMovement();

                    if (vec31.y < 0.0D) {
                        this.setDeltaMovement(vec31.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX()) || Mth.floor(this.yo) != Mth.floor(this.getY()) || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;

            if (this.tickCount % i == 0 && !this.level().isClientSide() && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            if (this.age != -32768) {
                ++this.age;
            }

            this.needsSync |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide()) {
                double d0 = this.getDeltaMovement().subtract(vec3).lengthSqr();

                if (d0 > 0.01D) {
                    this.needsSync = true;
                }
            }

            if (!this.level().isClientSide() && this.age >= 6000) {
                this.discard();
            }

        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        this.setFluidMovement((double) 0.99F);
    }

    private void setUnderLavaMovement() {
        this.setFluidMovement((double) 0.95F);
    }

    private void setFluidMovement(double multiplier) {
        Vec3 vec3 = this.getDeltaMovement();

        this.setDeltaMovement(vec3.x * multiplier, vec3.y + (double) (vec3.y < (double) 0.06F ? 5.0E-4F : 0.0F), vec3.z * multiplier);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            for (ItemEntity itementity : this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(0.5D, 0.0D, 0.5D), (itementity1) -> {
                return itementity1 != this && itementity1.isMergable();
            })) {
                if (itementity.isMergable()) {
                    this.tryToMerge(itementity);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }

        }
    }

    private boolean isMergable() {
        ItemStack itemstack = this.getItem();

        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && itemstack.getCount() < itemstack.getMaxStackSize();
    }

    private void tryToMerge(ItemEntity other) {
        ItemStack itemstack = this.getItem();
        ItemStack itemstack1 = other.getItem();

        if (Objects.equals(this.target, other.target) && areMergable(itemstack, itemstack1)) {
            if (itemstack1.getCount() < itemstack.getCount()) {
                merge(this, itemstack, other, itemstack1);
            } else {
                merge(other, itemstack1, this, itemstack);
            }

        }
    }

    public static boolean areMergable(ItemStack thisItemStack, ItemStack otherItemStack) {
        return otherItemStack.getCount() + thisItemStack.getCount() > otherItemStack.getMaxStackSize() ? false : ItemStack.isSameItemSameComponents(thisItemStack, otherItemStack);
    }

    public static ItemStack merge(ItemStack toStack, ItemStack fromStack, int maxCount) {
        int j = Math.min(Math.min(toStack.getMaxStackSize(), maxCount) - toStack.getCount(), fromStack.getCount());
        ItemStack itemstack2 = toStack.copyWithCount(toStack.getCount() + j);

        fromStack.shrink(j);
        return itemstack2;
    }

    private static void merge(ItemEntity toItem, ItemStack toStack, ItemStack fromStack) {
        ItemStack itemstack2 = merge(toStack, fromStack, 64);

        toItem.setItem(itemstack2);
    }

    private static void merge(ItemEntity toItem, ItemStack toStack, ItemEntity fromItem, ItemStack fromStack) {
        merge(toItem, toStack, fromStack);
        toItem.pickupDelay = Math.max(toItem.pickupDelay, fromItem.pickupDelay);
        toItem.age = Math.min(toItem.age, fromItem.age);
        if (fromStack.isEmpty()) {
            fromItem.discard();
        }

    }

    @Override
    public boolean fireImmune() {
        return !this.getItem().canBeHurtBy(this.damageSources().inFire()) || super.fireImmune();
    }

    @Override
    protected boolean shouldPlayLavaHurtSound() {
        return this.health <= 0 ? true : this.tickCount % 10 == 0;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return this.isInvulnerableToBase(source) ? false : this.getItem().canBeHurtBy(source);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        } else if (!(Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING) && source.getEntity() instanceof Mob) {
            return false;
        } else if (!this.getItem().canBeHurtBy(source)) {
            return false;
        } else {
            this.markHurt();
            this.health = (int) ((float) this.health - damage);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard();
            }

            return true;
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.shouldAffectBlocklikeEntities() ? super.ignoreExplosion(explosion) : true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("Health", (short) this.health);
        output.putShort("Age", (short) this.age);
        output.putShort("PickupDelay", (short) this.pickupDelay);
        EntityReference.store(this.thrower, output, "Thrower");
        output.storeNullable("Owner", UUIDUtil.CODEC, this.target);
        if (!this.getItem().isEmpty()) {
            output.store("Item", ItemStack.CODEC, this.getItem());
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.health = input.getShortOr("Health", (short) 5);
        this.age = input.getShortOr("Age", (short) 0);
        this.pickupDelay = input.getShortOr("PickupDelay", (short) 0);
        this.target = (UUID) input.read("Owner", UUIDUtil.CODEC).orElse((Object) null);
        this.thrower = EntityReference.<Entity>read(input, "Thrower");
        this.setItem((ItemStack) input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        if (this.getItem().isEmpty()) {
            this.discard();
        }

    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide()) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();

            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(player.getUUID())) && player.getInventory().add(itemstack)) {
                player.take(this, i);
                if (itemstack.isEmpty()) {
                    this.discard();
                    itemstack.setCount(i);
                }

                player.awardStat(Stats.ITEM_PICKED_UP.get(item), i);
                player.onItemPickup(this);
            }

        }
    }

    @Override
    public Component getName() {
        Component component = this.getCustomName();

        return component != null ? component : this.getItem().getItemName();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        Entity entity = super.teleport(transition);

        if (!this.level().isClientSide() && entity instanceof ItemEntity itementity) {
            itementity.mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemEntity.DATA_ITEM);
    }

    public void setItem(ItemStack itemStack) {
        this.getEntityData().set(ItemEntity.DATA_ITEM, itemStack);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (ItemEntity.DATA_ITEM.equals(accessor)) {
            this.getItem().setEntityRepresentation(this);
        }

    }

    public void setTarget(@Nullable UUID target) {
        this.target = target;
    }

    public void setThrower(Entity thrower) {
        this.thrower = EntityReference.of(thrower);
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int ticks) {
        this.pickupDelay = ticks;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = 5999;
    }

    public static float getSpin(float ageInTicks, float bobOffset) {
        return ageInTicks / 20.0F + bobOffset;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - getSpin((float) this.getAge() + 0.5F, this.bobOffs) / ((float) Math.PI * 2F) * 360.0F;
    }

    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        return slot == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(slot);
    }
}
