package net.minecraft.world.entity.monster.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public class Husk extends Zombie {

    public Husk(EntityType<? extends Husk> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean flag = super.doHurtTarget(level, target);

        if (flag && this.getMainHandItem().isEmpty() && target instanceof LivingEntity) {
            float f = level.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();

            ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int) f), this);
        }

        return flag;
    }

    @Override
    protected boolean convertsInWater() {
        return true;
    }

    @Override
    protected void doUnderWaterConversion(ServerLevel level) {
        this.convertToZombieType(level, EntityType.ZOMBIE);
        if (!this.isSilent()) {
            level.levelEvent((Entity) null, 1041, this.blockPosition(), 0);
        }

    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        RandomSource randomsource = level.getRandom();

        groupData = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        float f = difficulty.getSpecialMultiplier();

        if (spawnReason != EntitySpawnReason.CONVERSION) {
            this.setCanPickUpLoot(randomsource.nextFloat() < 0.55F * f);
        }

        if (groupData != null) {
            groupData = new Husk.HuskGroupData((Zombie.ZombieGroupData) groupData);
            ((Husk.HuskGroupData) groupData).triedToSpawnCamelHusk = spawnReason != EntitySpawnReason.NATURAL;
        }

        if (groupData instanceof Husk.HuskGroupData husk_huskgroupdata) {
            if (!husk_huskgroupdata.triedToSpawnCamelHusk) {
                BlockPos blockpos = this.blockPosition();

                if (level.noCollision(EntityType.CAMEL_HUSK.getSpawnAABB((double) blockpos.getX() + 0.5D, (double) blockpos.getY(), (double) blockpos.getZ() + 0.5D))) {
                    husk_huskgroupdata.triedToSpawnCamelHusk = true;
                    if (randomsource.nextFloat() < 0.1F) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
                        CamelHusk camelhusk = EntityType.CAMEL_HUSK.create(this.level(), EntitySpawnReason.NATURAL);

                        if (camelhusk != null) {
                            camelhusk.setPos(this.getX(), this.getY(), this.getZ());
                            camelhusk.finalizeSpawn(level, difficulty, spawnReason, (SpawnGroupData) null);
                            this.startRiding(camelhusk, true, true);
                            level.addFreshEntity(camelhusk);
                            Parched parched = EntityType.PARCHED.create(this.level(), EntitySpawnReason.NATURAL);

                            if (parched != null) {
                                parched.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                                parched.finalizeSpawn(level, difficulty, spawnReason, (SpawnGroupData) null);
                                parched.startRiding(camelhusk, false, false);
                                level.addFreshEntityWithPassengers(parched);
                            }
                        }
                    }
                }
            }
        }

        return groupData;
    }

    public static class HuskGroupData extends Zombie.ZombieGroupData {

        public boolean triedToSpawnCamelHusk = false;

        public HuskGroupData(Zombie.ZombieGroupData groupData) {
            super(groupData.isBaby, groupData.canSpawnJockey);
        }
    }
}
