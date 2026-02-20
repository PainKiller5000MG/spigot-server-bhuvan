package net.minecraft.world.entity.raid;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PathfindToRaidGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Raider extends PatrollingMonster {

    protected static final EntityDataAccessor<Boolean> IS_CELEBRATING = SynchedEntityData.<Boolean>defineId(Raider.class, EntityDataSerializers.BOOLEAN);
    private static final Predicate<ItemEntity> ALLOWED_ITEMS = (itementity) -> {
        return !itementity.hasPickUpDelay() && itementity.isAlive() && ItemStack.matches(itementity.getItem(), Raid.getOminousBannerInstance(itementity.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
    };
    private static final int DEFAULT_WAVE = 0;
    private static final boolean DEFAULT_CAN_JOIN_RAID = false;
    protected @Nullable Raid raid;
    private int wave = 0;
    private boolean canJoinRaid = false;
    private int ticksOutsideRaid;

    protected Raider(EntityType<? extends Raider> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new Raider.ObtainRaidLeaderBannerGoal(this));
        this.goalSelector.addGoal(3, new PathfindToRaidGoal(this));
        this.goalSelector.addGoal(4, new Raider.RaiderMoveThroughVillageGoal(this, (double) 1.05F, 1));
        this.goalSelector.addGoal(5, new Raider.RaiderCelebration(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Raider.IS_CELEBRATING, false);
    }

    public abstract void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain);

    public boolean canJoinRaid() {
        return this.canJoinRaid;
    }

    public void setCanJoinRaid(boolean canJoinRaid) {
        this.canJoinRaid = canJoinRaid;
    }

    @Override
    public void aiStep() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if (this.isAlive()) {
                Raid raid = this.getCurrentRaid();

                if (this.canJoinRaid()) {
                    if (raid == null) {
                        if (this.level().getGameTime() % 20L == 0L) {
                            Raid raid1 = serverlevel.getRaidAt(this.blockPosition());

                            if (raid1 != null && Raids.canJoinRaid(this)) {
                                raid1.joinRaid(serverlevel, raid1.getGroupsSpawned(), this, (BlockPos) null, true);
                            }
                        }
                    } else {
                        LivingEntity livingentity = this.getTarget();

                        if (livingentity != null && (livingentity.getType() == EntityType.PLAYER || livingentity.getType() == EntityType.IRON_GOLEM)) {
                            this.noActionTime = 0;
                        }
                    }
                }
            }
        }

        super.aiStep();
    }

    @Override
    protected void updateNoActionTime() {
        this.noActionTime += 2;
    }

    @Override
    public void die(DamageSource source) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            Entity entity = source.getEntity();
            Raid raid = this.getCurrentRaid();

            if (raid != null) {
                if (this.isPatrolLeader()) {
                    raid.removeLeader(this.getWave());
                }

                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    raid.addHeroOfTheVillage(entity);
                }

                raid.removeFromRaid(serverlevel, this, false);
            }
        }

        super.die(source);
    }

    @Override
    public boolean canJoinPatrol() {
        return !this.hasActiveRaid();
    }

    public void setCurrentRaid(@Nullable Raid raid) {
        this.raid = raid;
    }

    public @Nullable Raid getCurrentRaid() {
        return this.raid;
    }

    public boolean isCaptain() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
        boolean flag = !itemstack.isEmpty() && ItemStack.matches(itemstack, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        boolean flag1 = this.isPatrolLeader();

        return flag && flag1;
    }

    public boolean hasRaid() {
        Level level = this.level();

        if (!(level instanceof ServerLevel serverlevel)) {
            return false;
        } else {
            return this.getCurrentRaid() != null || serverlevel.getRaidAt(this.blockPosition()) != null;
        }
    }

    public boolean hasActiveRaid() {
        return this.getCurrentRaid() != null && this.getCurrentRaid().isActive();
    }

    public void setWave(int wave) {
        this.wave = wave;
    }

    public int getWave() {
        return this.wave;
    }

    public boolean isCelebrating() {
        return (Boolean) this.entityData.get(Raider.IS_CELEBRATING);
    }

    public void setCelebrating(boolean celebrating) {
        this.entityData.set(Raider.IS_CELEBRATING, celebrating);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Wave", this.wave);
        output.putBoolean("CanJoinRaid", this.canJoinRaid);
        if (this.raid != null) {
            Level level = this.level();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                serverlevel.getRaids().getId(this.raid).ifPresent((i) -> {
                    output.putInt("RaidId", i);
                });
            }
        }

    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.wave = input.getIntOr("Wave", 0);
        this.canJoinRaid = input.getBooleanOr("CanJoinRaid", false);
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            input.getInt("RaidId").ifPresent((integer) -> {
                this.raid = serverlevel.getRaids().get(integer);
                if (this.raid != null) {
                    this.raid.addWaveMob(serverlevel, this.wave, this, false);
                    if (this.isPatrolLeader()) {
                        this.raid.setLeader(this.wave, this);
                    }
                }

            });
        }

    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack itemstack = entity.getItem();
        boolean flag = this.hasActiveRaid() && this.getCurrentRaid().getLeader(this.getWave()) != null;

        if (this.hasActiveRaid() && !flag && ItemStack.matches(itemstack, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))) {
            EquipmentSlot equipmentslot = EquipmentSlot.HEAD;
            ItemStack itemstack1 = this.getItemBySlot(equipmentslot);
            double d0 = (double) this.getDropChances().byEquipment(equipmentslot);

            if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                this.spawnAtLocation(level, itemstack1);
            }

            this.onItemPickup(entity);
            this.setItemSlot(equipmentslot, itemstack);
            this.take(entity, itemstack.getCount());
            entity.discard();
            this.getCurrentRaid().setLeader(this.getWave(), this);
            this.setPatrolLeader(true);
        } else {
            super.pickUpItem(level, entity);
        }

    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return this.getCurrentRaid() == null ? super.removeWhenFarAway(distSqr) : false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCurrentRaid() != null;
    }

    public int getTicksOutsideRaid() {
        return this.ticksOutsideRaid;
    }

    public void setTicksOutsideRaid(int ticksOutsideRaid) {
        this.ticksOutsideRaid = ticksOutsideRaid;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.hasActiveRaid()) {
            this.getCurrentRaid().updateBossbar();
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.setCanJoinRaid(this.getType() != EntityType.WITCH || spawnReason != EntitySpawnReason.NATURAL);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    public abstract SoundEvent getCelebrateSound();

    public class ObtainRaidLeaderBannerGoal<T extends Raider> extends Goal {

        private final T mob;
        private Int2LongOpenHashMap unreachableBannerCache = new Int2LongOpenHashMap();
        private @Nullable Path pathToBanner;
        private @Nullable ItemEntity pursuedBannerItemEntity;

        public ObtainRaidLeaderBannerGoal(T mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cannotPickUpBanner()) {
                return false;
            } else {
                Int2LongOpenHashMap int2longopenhashmap = new Int2LongOpenHashMap();
                double d0 = Raider.this.getAttributeValue(Attributes.FOLLOW_RANGE);

                for (ItemEntity itementity : this.mob.level().getEntitiesOfClass(ItemEntity.class, this.mob.getBoundingBox().inflate(d0, 8.0D, d0), Raider.ALLOWED_ITEMS)) {
                    long i = this.unreachableBannerCache.getOrDefault(itementity.getId(), Long.MIN_VALUE);

                    if (Raider.this.level().getGameTime() < i) {
                        int2longopenhashmap.put(itementity.getId(), i);
                    } else {
                        Path path = this.mob.getNavigation().createPath(itementity, 1);

                        if (path != null && path.canReach()) {
                            this.pathToBanner = path;
                            this.pursuedBannerItemEntity = itementity;
                            return true;
                        }

                        int2longopenhashmap.put(itementity.getId(), Raider.this.level().getGameTime() + 600L);
                    }
                }

                this.unreachableBannerCache = int2longopenhashmap;
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.pursuedBannerItemEntity != null && this.pathToBanner != null ? (this.pursuedBannerItemEntity.isRemoved() ? false : (this.pathToBanner.isDone() ? false : !this.cannotPickUpBanner())) : false;
        }

        private boolean cannotPickUpBanner() {
            if (!this.mob.hasActiveRaid()) {
                return true;
            } else if (this.mob.getCurrentRaid().isOver()) {
                return true;
            } else if (!this.mob.canBeLeader()) {
                return true;
            } else if (ItemStack.matches(this.mob.getItemBySlot(EquipmentSlot.HEAD), Raid.getOminousBannerInstance(this.mob.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))) {
                return true;
            } else {
                Raider raider = Raider.this.raid.getLeader(this.mob.getWave());

                return raider != null && raider.isAlive();
            }
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.pathToBanner, (double) 1.15F);
        }

        @Override
        public void stop() {
            this.pathToBanner = null;
            this.pursuedBannerItemEntity = null;
        }

        @Override
        public void tick() {
            if (this.pursuedBannerItemEntity != null && this.pursuedBannerItemEntity.closerThan(this.mob, 1.414D)) {
                this.mob.pickUpItem(getServerLevel(Raider.this.level()), this.pursuedBannerItemEntity);
            }

        }
    }

    public class RaiderCelebration extends Goal {

        private final Raider mob;

        RaiderCelebration(Raider mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Raid raid = this.mob.getCurrentRaid();

            return this.mob.isAlive() && this.mob.getTarget() == null && raid != null && raid.isLoss();
        }

        @Override
        public void start() {
            this.mob.setCelebrating(true);
            super.start();
        }

        @Override
        public void stop() {
            this.mob.setCelebrating(false);
            super.stop();
        }

        @Override
        public void tick() {
            if (!this.mob.isSilent() && this.mob.random.nextInt(this.adjustedTickDelay(100)) == 0) {
                Raider.this.makeSound(Raider.this.getCelebrateSound());
            }

            if (!this.mob.isPassenger() && this.mob.random.nextInt(this.adjustedTickDelay(50)) == 0) {
                this.mob.getJumpControl().jump();
            }

            super.tick();
        }
    }

    protected static class HoldGroundAttackGoal extends Goal {

        private final Raider mob;
        private final float hostileRadiusSqr;
        public final TargetingConditions shoutTargeting = TargetingConditions.forNonCombat().range(8.0D).ignoreLineOfSight().ignoreInvisibilityTesting();

        public HoldGroundAttackGoal(AbstractIllager mob, float hostileRadius) {
            this.mob = mob;
            this.hostileRadiusSqr = hostileRadius * hostileRadius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity livingentity = this.mob.getLastHurtByMob();

            return this.mob.getCurrentRaid() == null && this.mob.isPatrolling() && this.mob.getTarget() != null && !this.mob.isAggressive() && (livingentity == null || livingentity.getType() != EntityType.PLAYER);
        }

        @Override
        public void start() {
            super.start();
            this.mob.getNavigation().stop();

            for (Raider raider : getServerLevel((Entity) this.mob).getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D))) {
                raider.setTarget(this.mob.getTarget());
            }

        }

        @Override
        public void stop() {
            super.stop();
            LivingEntity livingentity = this.mob.getTarget();

            if (livingentity != null) {
                for (Raider raider : getServerLevel((Entity) this.mob).getNearbyEntities(Raider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D))) {
                    raider.setTarget(livingentity);
                    raider.setAggressive(true);
                }

                this.mob.setAggressive(true);
            }

        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.mob.getTarget();

            if (livingentity != null) {
                if (this.mob.distanceToSqr((Entity) livingentity) > (double) this.hostileRadiusSqr) {
                    this.mob.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
                    if (this.mob.random.nextInt(50) == 0) {
                        this.mob.playAmbientSound();
                    }
                } else {
                    this.mob.setAggressive(true);
                }

                super.tick();
            }
        }
    }

    private static class RaiderMoveThroughVillageGoal extends Goal {

        private final Raider raider;
        private final double speedModifier;
        private BlockPos poiPos;
        private final List<BlockPos> visited = Lists.newArrayList();
        private final int distanceToPoi;
        private boolean stuck;

        public RaiderMoveThroughVillageGoal(Raider mob, double speedModifier, int distanceToPoi) {
            this.raider = mob;
            this.speedModifier = speedModifier;
            this.distanceToPoi = distanceToPoi;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            this.updateVisited();
            return this.isValidRaid() && this.hasSuitablePoi() && this.raider.getTarget() == null;
        }

        private boolean isValidRaid() {
            return this.raider.hasActiveRaid() && !this.raider.getCurrentRaid().isOver();
        }

        private boolean hasSuitablePoi() {
            ServerLevel serverlevel = (ServerLevel) this.raider.level();
            BlockPos blockpos = this.raider.blockPosition();
            Optional<BlockPos> optional = serverlevel.getPoiManager().getRandom((holder) -> {
                return holder.is(PoiTypes.HOME);
            }, this::hasNotVisited, PoiManager.Occupancy.ANY, blockpos, 48, this.raider.random);

            if (optional.isEmpty()) {
                return false;
            } else {
                this.poiPos = ((BlockPos) optional.get()).immutable();
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.raider.getNavigation().isDone() ? false : this.raider.getTarget() == null && !this.poiPos.closerToCenterThan(this.raider.position(), (double) (this.raider.getBbWidth() + (float) this.distanceToPoi)) && !this.stuck;
        }

        @Override
        public void stop() {
            if (this.poiPos.closerToCenterThan(this.raider.position(), (double) this.distanceToPoi)) {
                this.visited.add(this.poiPos);
            }

        }

        @Override
        public void start() {
            super.start();
            this.raider.setNoActionTime(0);
            this.raider.getNavigation().moveTo((double) this.poiPos.getX(), (double) this.poiPos.getY(), (double) this.poiPos.getZ(), this.speedModifier);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.raider.getNavigation().isDone()) {
                Vec3 vec3 = Vec3.atBottomCenterOf(this.poiPos);
                Vec3 vec31 = DefaultRandomPos.getPosTowards(this.raider, 16, 7, vec3, (double) ((float) Math.PI / 10F));

                if (vec31 == null) {
                    vec31 = DefaultRandomPos.getPosTowards(this.raider, 8, 7, vec3, (double) ((float) Math.PI / 2F));
                }

                if (vec31 == null) {
                    this.stuck = true;
                    return;
                }

                this.raider.getNavigation().moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
            }

        }

        private boolean hasNotVisited(BlockPos poi) {
            for (BlockPos blockpos1 : this.visited) {
                if (Objects.equals(poi, blockpos1)) {
                    return false;
                }
            }

            return true;
        }

        private void updateVisited() {
            if (this.visited.size() > 2) {
                this.visited.remove(0);
            }

        }
    }
}
