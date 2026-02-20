package net.minecraft.world.entity;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class OminousItemSpawner extends Entity {

    private static final int SPAWN_ITEM_DELAY_MIN = 60;
    private static final int SPAWN_ITEM_DELAY_MAX = 120;
    private static final String TAG_SPAWN_ITEM_AFTER_TICKS = "spawn_item_after_ticks";
    private static final String TAG_ITEM = "item";
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.<ItemStack>defineId(OminousItemSpawner.class, EntityDataSerializers.ITEM_STACK);
    public static final int TICKS_BEFORE_ABOUT_TO_SPAWN_SOUND = 36;
    public long spawnItemAfterTicks;

    public OminousItemSpawner(EntityType<? extends OminousItemSpawner> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static OminousItemSpawner create(Level level, ItemStack item) {
        OminousItemSpawner ominousitemspawner = new OminousItemSpawner(EntityType.OMINOUS_ITEM_SPAWNER, level);

        ominousitemspawner.spawnItemAfterTicks = (long) level.random.nextIntBetweenInclusive(60, 120);
        ominousitemspawner.setItem(item);
        return ominousitemspawner;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            this.tickServer(serverlevel);
        } else {
            this.tickClient();
        }

    }

    private void tickServer(ServerLevel level) {
        if ((long) this.tickCount == this.spawnItemAfterTicks - 36L) {
            level.playSound((Entity) null, this.blockPosition(), SoundEvents.TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundSource.NEUTRAL);
        }

        if ((long) this.tickCount >= this.spawnItemAfterTicks) {
            this.spawnItem();
            this.kill(level);
        }

    }

    private void tickClient() {
        if (this.level().getGameTime() % 5L == 0L) {
            this.addParticles();
        }

    }

    private void spawnItem() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            ItemStack itemstack = this.getItem();

            if (!itemstack.isEmpty()) {
                Item item = itemstack.getItem();
                Entity entity;

                if (item instanceof ProjectileItem) {
                    ProjectileItem projectileitem = (ProjectileItem) item;

                    entity = this.spawnProjectile(serverlevel, projectileitem, itemstack);
                } else {
                    entity = new ItemEntity(serverlevel, this.getX(), this.getY(), this.getZ(), itemstack);
                    serverlevel.addFreshEntity(entity);
                }

                serverlevel.levelEvent(3021, this.blockPosition(), 1);
                serverlevel.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, this.position());
                this.setItem(ItemStack.EMPTY);
            }
        }
    }

    private Entity spawnProjectile(ServerLevel level, ProjectileItem projectileItem, ItemStack item) {
        ProjectileItem.DispenseConfig projectileitem_dispenseconfig = projectileItem.createDispenseConfig();

        projectileitem_dispenseconfig.overrideDispenseEvent().ifPresent((i) -> {
            level.levelEvent(i, this.blockPosition(), 0);
        });
        Direction direction = Direction.DOWN;
        Projectile projectile = Projectile.spawnProjectileUsingShoot(projectileItem.asProjectile(level, this.position(), item, direction), level, item, (double) direction.getStepX(), (double) direction.getStepY(), (double) direction.getStepZ(), projectileitem_dispenseconfig.power(), projectileitem_dispenseconfig.uncertainty());

        projectile.setOwner(this);
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(OminousItemSpawner.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem((ItemStack) input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.spawnItemAfterTicks = input.getLongOr("spawn_item_after_ticks", 0L);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!this.getItem().isEmpty()) {
            output.store("item", ItemStack.CODEC, this.getItem());
        }

        output.putLong("spawn_item_after_ticks", this.spawnItemAfterTicks);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    protected boolean couldAcceptPassenger() {
        return false;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        throw new IllegalStateException("Should never addPassenger without checking couldAcceptPassenger()");
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public void addParticles() {
        Vec3 vec3 = this.position();
        int i = this.random.nextIntBetweenInclusive(1, 3);

        for (int j = 0; j < i; ++j) {
            double d0 = 0.4D;
            Vec3 vec31 = new Vec3(this.getX() + 0.4D * (this.random.nextGaussian() - this.random.nextGaussian()), this.getY() + 0.4D * (this.random.nextGaussian() - this.random.nextGaussian()), this.getZ() + 0.4D * (this.random.nextGaussian() - this.random.nextGaussian()));
            Vec3 vec32 = vec3.vectorTo(vec31);

            this.level().addParticle(ParticleTypes.OMINOUS_SPAWNING, vec3.x(), vec3.y(), vec3.z(), vec32.x(), vec32.y(), vec32.z());
        }

    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(OminousItemSpawner.DATA_ITEM);
    }

    public void setItem(ItemStack itemStack) {
        this.getEntityData().set(OminousItemSpawner.DATA_ITEM, itemStack);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
