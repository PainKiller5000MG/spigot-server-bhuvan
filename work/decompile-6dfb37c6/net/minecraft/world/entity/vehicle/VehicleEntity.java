package net.minecraft.world.entity.vehicle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;

public abstract class VehicleEntity extends Entity {

    protected static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.<Integer>defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.<Integer>defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.<Float>defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    public VehicleEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved()) {
            return true;
        } else if (this.isInvulnerableToBase(source)) {
            return false;
        } else {
            boolean flag;
            label32:
            {
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.markHurt();
                this.setDamage(this.getDamage() + damage * 10.0F);
                this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
                Entity entity = source.getEntity();

                if (entity instanceof Player) {
                    Player player = (Player) entity;

                    if (player.getAbilities().instabuild) {
                        flag = true;
                        break label32;
                    }
                }

                flag = false;
            }

            boolean flag1 = flag;

            if ((flag1 || this.getDamage() <= 40.0F) && !this.shouldSourceDestroy(source)) {
                if (flag1) {
                    this.discard();
                }
            } else {
                this.destroy(level, source);
            }

            return true;
        }
    }

    protected boolean shouldSourceDestroy(DamageSource source) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return explosion.getIndirectSourceEntity() instanceof Mob && !(Boolean) explosion.level().getGameRules().get(GameRules.MOB_GRIEFING);
    }

    public void destroy(ServerLevel level, Item dropItem) {
        this.kill(level);
        if ((Boolean) level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            ItemStack itemstack = new ItemStack(dropItem);

            itemstack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            this.spawnAtLocation(level, itemstack);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(VehicleEntity.DATA_ID_HURT, 0);
        entityData.define(VehicleEntity.DATA_ID_HURTDIR, 1);
        entityData.define(VehicleEntity.DATA_ID_DAMAGE, 0.0F);
    }

    public void setHurtTime(int hurtTime) {
        this.entityData.set(VehicleEntity.DATA_ID_HURT, hurtTime);
    }

    public void setHurtDir(int hurtDir) {
        this.entityData.set(VehicleEntity.DATA_ID_HURTDIR, hurtDir);
    }

    public void setDamage(float damage) {
        this.entityData.set(VehicleEntity.DATA_ID_DAMAGE, damage);
    }

    public float getDamage() {
        return (Float) this.entityData.get(VehicleEntity.DATA_ID_DAMAGE);
    }

    public int getHurtTime() {
        return (Integer) this.entityData.get(VehicleEntity.DATA_ID_HURT);
    }

    public int getHurtDir() {
        return (Integer) this.entityData.get(VehicleEntity.DATA_ID_HURTDIR);
    }

    protected void destroy(ServerLevel level, DamageSource source) {
        this.destroy(level, this.getDropItem());
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    protected abstract Item getDropItem();
}
