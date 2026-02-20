package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public abstract class AgeableMob extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.<Boolean>defineId(AgeableMob.class, EntityDataSerializers.BOOLEAN);
    public static final int BABY_START_AGE = -24000;
    private static final int FORCED_AGE_PARTICLE_TICKS = 40;
    protected static final int DEFAULT_AGE = 0;
    protected static final int DEFAULT_FORCED_AGE = 0;
    protected int age = 0;
    protected int forcedAge = 0;
    protected int forcedAgeTimer;

    protected AgeableMob(EntityType<? extends AgeableMob> type, Level level) {
        super(type, level);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(true);
        }

        AgeableMob.AgeableMobGroupData ageablemob_ageablemobgroupdata = (AgeableMob.AgeableMobGroupData) groupData;

        if (ageablemob_ageablemobgroupdata.isShouldSpawnBaby() && ageablemob_ageablemobgroupdata.getGroupSize() > 0 && level.getRandom().nextFloat() <= ageablemob_ageablemobgroupdata.getBabySpawnChance()) {
            this.setAge(-24000);
        }

        ageablemob_ageablemobgroupdata.increaseGroupSizeByOne();
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public abstract @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(AgeableMob.DATA_BABY_ID, false);
    }

    public boolean canBreed() {
        return false;
    }

    public int getAge() {
        return this.level().isClientSide() ? ((Boolean) this.entityData.get(AgeableMob.DATA_BABY_ID) ? -1 : 1) : this.age;
    }

    public void ageUp(int seconds, boolean forced) {
        int j = this.getAge();
        int k = j;

        j += seconds * 20;
        if (j > 0) {
            j = 0;
        }

        int l = j - k;

        this.setAge(j);
        if (forced) {
            this.forcedAge += l;
            if (this.forcedAgeTimer == 0) {
                this.forcedAgeTimer = 40;
            }
        }

        if (this.getAge() == 0) {
            this.setAge(this.forcedAge);
        }

    }

    public void ageUp(int seconds) {
        this.ageUp(seconds, false);
    }

    public void setAge(int newAge) {
        int j = this.getAge();

        this.age = newAge;
        if (j < 0 && newAge >= 0 || j >= 0 && newAge < 0) {
            this.entityData.set(AgeableMob.DATA_BABY_ID, newAge < 0);
            this.ageBoundaryReached();
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", this.getAge());
        output.putInt("ForcedAge", this.forcedAge);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setAge(input.getIntOr("Age", 0));
        this.forcedAge = input.getIntOr("ForcedAge", 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (AgeableMob.DATA_BABY_ID.equals(accessor)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            if (this.forcedAgeTimer > 0) {
                if (this.forcedAgeTimer % 4 == 0) {
                    this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                }

                --this.forcedAgeTimer;
            }
        } else if (this.isAlive()) {
            int i = this.getAge();

            if (i < 0) {
                ++i;
                this.setAge(i);
            } else if (i > 0) {
                --i;
                this.setAge(i);
            }
        }

    }

    protected void ageBoundaryReached() {
        if (!this.isBaby() && this.isPassenger()) {
            Entity entity = this.getVehicle();

            if (entity instanceof AbstractBoat) {
                AbstractBoat abstractboat = (AbstractBoat) entity;

                if (!abstractboat.hasEnoughSpaceFor(this)) {
                    this.stopRiding();
                }
            }
        }

    }

    @Override
    public boolean isBaby() {
        return this.getAge() < 0;
    }

    @Override
    public void setBaby(boolean baby) {
        this.setAge(baby ? -24000 : 0);
    }

    public static int getSpeedUpSecondsWhenFeeding(int ticksUntilAdult) {
        return (int) ((float) (ticksUntilAdult / 20) * 0.1F);
    }

    @VisibleForTesting
    public int getForcedAge() {
        return this.forcedAge;
    }

    @VisibleForTesting
    public int getForcedAgeTimer() {
        return this.forcedAgeTimer;
    }

    public static class AgeableMobGroupData implements SpawnGroupData {

        private int groupSize;
        private final boolean shouldSpawnBaby;
        private final float babySpawnChance;

        public AgeableMobGroupData(boolean shouldSpawnBaby, float babySpawnChance) {
            this.shouldSpawnBaby = shouldSpawnBaby;
            this.babySpawnChance = babySpawnChance;
        }

        public AgeableMobGroupData(boolean shouldSpawnBaby) {
            this(shouldSpawnBaby, 0.05F);
        }

        public AgeableMobGroupData(float babySpawnChance) {
            this(true, babySpawnChance);
        }

        public int getGroupSize() {
            return this.groupSize;
        }

        public void increaseGroupSizeByOne() {
            ++this.groupSize;
        }

        public boolean isShouldSpawnBaby() {
            return this.shouldSpawnBaby;
        }

        public float getBabySpawnChance() {
            return this.babySpawnChance;
        }
    }
}
