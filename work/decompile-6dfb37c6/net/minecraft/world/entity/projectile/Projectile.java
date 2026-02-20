package net.minecraft.world.entity.projectile;

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Projectile extends Entity implements TraceableEntity {

    private static final boolean DEFAULT_LEFT_OWNER = false;
    private static final boolean DEFAULT_HAS_BEEN_SHOT = false;
    protected @Nullable EntityReference<Entity> owner;
    private boolean leftOwner = false;
    private boolean leftOwnerChecked;
    private boolean hasBeenShot = false;
    private @Nullable Entity lastDeflectedBy;

    protected Projectile(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    protected void setOwner(@Nullable EntityReference<Entity> owner) {
        this.owner = owner;
    }

    public void setOwner(@Nullable Entity owner) {
        this.setOwner(EntityReference.of(owner));
    }

    @Override
    public @Nullable Entity getOwner() {
        return EntityReference.getEntity(this.owner, this.level());
    }

    public Entity getEffectSource() {
        return (Entity) MoreObjects.firstNonNull(this.getOwner(), this);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        EntityReference.store(this.owner, output, "Owner");
        if (this.leftOwner) {
            output.putBoolean("LeftOwner", true);
        }

        output.putBoolean("HasBeenShot", this.hasBeenShot);
    }

    protected boolean ownedBy(Entity entity) {
        return this.owner != null && this.owner.matches(entity);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setOwner(EntityReference.read(input, "Owner"));
        this.leftOwner = input.getBooleanOr("LeftOwner", false);
        this.hasBeenShot = input.getBooleanOr("HasBeenShot", false);
    }

    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        if (oldEntity instanceof Projectile projectile) {
            this.owner = projectile.owner;
        }

    }

    @Override
    public void tick() {
        if (!this.hasBeenShot) {
            this.gameEvent(GameEvent.PROJECTILE_SHOOT, this.getOwner());
            this.hasBeenShot = true;
        }

        this.checkLeftOwner();
        super.tick();
        this.leftOwnerChecked = false;
    }

    protected void checkLeftOwner() {
        if (!this.leftOwner && !this.leftOwnerChecked) {
            this.leftOwner = this.isOutsideOwnerCollisionRange();
            this.leftOwnerChecked = true;
        }

    }

    private boolean isOutsideOwnerCollisionRange() {
        Entity entity = this.getOwner();

        if (entity != null) {
            AABB aabb = this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D);

            return entity.getRootVehicle().getSelfAndPassengers().filter(EntitySelector.CAN_BE_PICKED).noneMatch((entity1) -> {
                return aabb.intersects(entity1.getBoundingBox());
            });
        } else {
            return true;
        }
    }

    public Vec3 getMovementToShoot(double xd, double yd, double zd, float pow, float uncertainty) {
        return (new Vec3(xd, yd, zd)).normalize().add(this.random.triangle(0.0D, 0.0172275D * (double) uncertainty), this.random.triangle(0.0D, 0.0172275D * (double) uncertainty), this.random.triangle(0.0D, 0.0172275D * (double) uncertainty)).scale((double) pow);
    }

    public void shoot(double xd, double yd, double zd, float pow, float uncertainty) {
        Vec3 vec3 = this.getMovementToShoot(xd, yd, zd, pow, uncertainty);

        this.setDeltaMovement(vec3);
        this.needsSync = true;
        double d3 = vec3.horizontalDistance();

        this.setYRot((float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI)));
        this.setXRot((float) (Mth.atan2(vec3.y, d3) * (double) (180F / (float) Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void shootFromRotation(Entity source, float xRot, float yRot, float yOffset, float pow, float uncertainty) {
        float f5 = -Mth.sin((double) (yRot * ((float) Math.PI / 180F))) * Mth.cos((double) (xRot * ((float) Math.PI / 180F)));
        float f6 = -Mth.sin((double) ((xRot + yOffset) * ((float) Math.PI / 180F)));
        float f7 = Mth.cos((double) (yRot * ((float) Math.PI / 180F))) * Mth.cos((double) (xRot * ((float) Math.PI / 180F)));

        this.shoot((double) f5, (double) f6, (double) f7, pow, uncertainty);
        Vec3 vec3 = source.getKnownMovement();

        this.setDeltaMovement(this.getDeltaMovement().add(vec3.x, source.onGround() ? 0.0D : vec3.y, vec3.z));
    }

    @Override
    public void onAboveBubbleColumn(boolean dragDown, BlockPos pos) {
        double d0 = dragDown ? -0.03D : 0.1D;

        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d0, 0.0D));
        sendBubbleColumnParticles(this.level(), pos);
    }

    @Override
    public void onInsideBubbleColumn(boolean dragDown) {
        double d0 = dragDown ? -0.03D : 0.06D;

        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d0, 0.0D));
        this.resetFallDistance();
    }

    public static <T extends Projectile> T spawnProjectileFromRotation(Projectile.ProjectileFactory<T> creator, ServerLevel serverLevel, ItemStack itemStack, LivingEntity source, float yOffset, float pow, float uncertainty) {
        return (T) spawnProjectile(creator.create(serverLevel, source, itemStack), serverLevel, itemStack, (projectile) -> {
            projectile.shootFromRotation(source, source.getXRot(), source.getYRot(), yOffset, pow, uncertainty);
        });
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(Projectile.ProjectileFactory<T> creator, ServerLevel serverLevel, ItemStack itemStack, LivingEntity source, double targetX, double targetY, double targetZ, float pow, float uncertainty) {
        return (T) spawnProjectile(creator.create(serverLevel, source, itemStack), serverLevel, itemStack, (projectile) -> {
            projectile.shoot(targetX, targetY, targetZ, pow, uncertainty);
        });
    }

    public static <T extends Projectile> T spawnProjectileUsingShoot(T projectile, ServerLevel serverLevel, ItemStack itemStack, double targetX, double targetY, double targetZ, float pow, float uncertainty) {
        return (T) spawnProjectile(projectile, serverLevel, itemStack, (projectile1) -> {
            projectile.shoot(targetX, targetY, targetZ, pow, uncertainty);
        });
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel serverLevel, ItemStack itemStack) {
        return (T) spawnProjectile(projectile, serverLevel, itemStack, (projectile1) -> {
        });
    }

    public static <T extends Projectile> T spawnProjectile(T projectile, ServerLevel serverLevel, ItemStack itemStack, Consumer<T> shootFunction) {
        shootFunction.accept(projectile);
        serverLevel.addFreshEntity(projectile);
        projectile.applyOnProjectileSpawned(serverLevel, itemStack);
        return projectile;
    }

    public void applyOnProjectileSpawned(ServerLevel serverLevel, ItemStack pickupItemStack) {
        EnchantmentHelper.onProjectileSpawned(serverLevel, pickupItemStack, this, (item) -> {
        });
        if (this instanceof AbstractArrow abstractarrow) {
            ItemStack itemstack1 = abstractarrow.getWeaponItem();

            if (itemstack1 != null && !itemstack1.isEmpty() && !pickupItemStack.getItem().equals(itemstack1.getItem())) {
                Objects.requireNonNull(abstractarrow);
                EnchantmentHelper.onProjectileSpawned(serverLevel, itemstack1, this, abstractarrow::onItemBreak);
            }
        }

    }

    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityhitresult = (EntityHitResult) hitResult;
            Entity entity = entityhitresult.getEntity();
            ProjectileDeflection projectiledeflection = entity.deflection(this);

            if (projectiledeflection != ProjectileDeflection.NONE) {
                if (entity != this.lastDeflectedBy && this.deflect(projectiledeflection, entity, this.owner, false)) {
                    this.lastDeflectedBy = entity;
                }

                return projectiledeflection;
            }
        } else if (this.shouldBounceOnWorldBorder() && hitResult instanceof BlockHitResult) {
            BlockHitResult blockhitresult = (BlockHitResult) hitResult;

            if (blockhitresult.isWorldBorderHit()) {
                ProjectileDeflection projectiledeflection1 = ProjectileDeflection.REVERSE;

                if (this.deflect(projectiledeflection1, (Entity) null, this.owner, false)) {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.2D));
                    return projectiledeflection1;
                }
            }
        }

        this.onHit(hitResult);
        return ProjectileDeflection.NONE;
    }

    protected boolean shouldBounceOnWorldBorder() {
        return false;
    }

    public boolean deflect(ProjectileDeflection deflection, @Nullable Entity deflectingEntity, @Nullable EntityReference<Entity> newOwner, boolean byAttack) {
        deflection.deflect(this, deflectingEntity, this.random);
        if (!this.level().isClientSide()) {
            this.setOwner(newOwner);
            this.onDeflection(byAttack);
        }

        return true;
    }

    protected void onDeflection(boolean byAttack) {}

    protected void onItemBreak(Item item) {}

    protected void onHit(HitResult hitResult) {
        HitResult.Type hitresult_type = hitResult.getType();

        if (hitresult_type == HitResult.Type.ENTITY) {
            EntityHitResult entityhitresult = (EntityHitResult) hitResult;
            Entity entity = entityhitresult.getEntity();

            if (entity.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile) {
                Projectile projectile = (Projectile) entity;

                projectile.deflect(ProjectileDeflection.AIM_DEFLECT, this.getOwner(), this.owner, true);
            }

            this.onHitEntity(entityhitresult);
            this.level().gameEvent(GameEvent.PROJECTILE_LAND, hitResult.getLocation(), GameEvent.Context.of(this, (BlockState) null));
        } else if (hitresult_type == HitResult.Type.BLOCK) {
            BlockHitResult blockhitresult = (BlockHitResult) hitResult;

            this.onHitBlock(blockhitresult);
            BlockPos blockpos = blockhitresult.getBlockPos();

            this.level().gameEvent(GameEvent.PROJECTILE_LAND, blockpos, GameEvent.Context.of(this, this.level().getBlockState(blockpos)));
        }

    }

    protected void onHitEntity(EntityHitResult hitResult) {}

    protected void onHitBlock(BlockHitResult hitResult) {
        BlockState blockstate = this.level().getBlockState(hitResult.getBlockPos());

        blockstate.onProjectileHit(this.level(), blockstate, hitResult, this);
    }

    protected boolean canHitEntity(Entity entity) {
        if (!entity.canBeHitByProjectile()) {
            return false;
        } else {
            Entity entity1 = this.getOwner();

            return entity1 == null || this.leftOwner || !entity1.isPassengerOfSameVehicle(entity);
        }
    }

    protected void updateRotation() {
        Vec3 vec3 = this.getDeltaMovement();
        double d0 = vec3.horizontalDistance();

        this.setXRot(lerpRotation(this.xRotO, (float) (Mth.atan2(vec3.y, d0) * (double) (180F / (float) Math.PI))));
        this.setYRot(lerpRotation(this.yRotO, (float) (Mth.atan2(vec3.x, vec3.z) * (double) (180F / (float) Math.PI))));
    }

    protected static float lerpRotation(float rotO, float rot) {
        while (rot - rotO < -180.0F) {
            rotO -= 360.0F;
        }

        while (rot - rotO >= 180.0F) {
            rotO += 360.0F;
        }

        return Mth.lerp(0.2F, rotO, rot);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        Entity entity = this.getOwner();

        return new ClientboundAddEntityPacket(this, serverEntity, entity == null ? 0 : entity.getId());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Entity entity = this.level().getEntity(packet.getData());

        if (entity != null) {
            this.setOwner(entity);
        }

    }

    @Override
    public boolean mayInteract(ServerLevel level, BlockPos pos) {
        Entity entity = this.getOwner();

        return entity instanceof Player ? entity.mayInteract(level, pos) : entity == null || (Boolean) level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    public boolean mayBreak(ServerLevel level) {
        return this.getType().is(EntityTypeTags.IMPACT_PROJECTILES) && (Boolean) level.getGameRules().get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS);
    }

    @Override
    public boolean isPickable() {
        return this.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE);
    }

    @Override
    public float getPickRadius() {
        return this.isPickable() ? 1.0F : 0.0F;
    }

    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity hurtEntity, DamageSource damageSource) {
        double d0 = this.getDeltaMovement().x;
        double d1 = this.getDeltaMovement().z;

        return DoubleDoubleImmutablePair.of(d0, d1);
    }

    @Override
    public int getDimensionChangingDelay() {
        return 2;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!this.isInvulnerableToBase(source)) {
            this.markHurt();
        }

        return false;
    }

    @FunctionalInterface
    public interface ProjectileFactory<T extends Projectile> {

        T create(ServerLevel level, LivingEntity entity, ItemStack itemStack);
    }
}
