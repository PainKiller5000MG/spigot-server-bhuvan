package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Ghast extends Mob implements Enemy {

    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.<Boolean>defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
    private static final byte DEFAULT_EXPLOSION_POWER = 1;
    private int explosionPower = 1;

    public Ghast(EntityType<? extends Ghast> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl(this, false, () -> {
            return false;
        });
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, (livingentity, serverlevel) -> {
            return Math.abs(livingentity.getY() - this.getY()) <= 4.0D;
        }));
    }

    public boolean isCharging() {
        return (Boolean) this.entityData.get(Ghast.DATA_IS_CHARGING);
    }

    public void setCharging(boolean onOff) {
        this.entityData.set(Ghast.DATA_IS_CHARGING, onOff);
    }

    public int getExplosionPower() {
        return this.explosionPower;
    }

    private static boolean isReflectedFireball(DamageSource source) {
        return source.getDirectEntity() instanceof LargeFireball && source.getEntity() instanceof Player;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isReflectedFireball(source) && super.isInvulnerableTo(level, source);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {}

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        this.travelFlying(input, 0.02F);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isReflectedFireball(source)) {
            super.hurtServer(level, source, 1000.0F);
            return true;
        } else {
            return this.isInvulnerableTo(level, source) ? false : super.hurtServer(level, source, damage);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Ghast.DATA_IS_CHARGING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.FOLLOW_RANGE, 100.0D).add(Attributes.CAMERA_DISTANCE, 8.0D).add(Attributes.FLYING_SPEED, 0.06D);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GHAST_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GHAST_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHAST_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    public static boolean checkGhastSpawnRules(EntityType<Ghast> type, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(20) == 0 && checkMobSpawnRules(type, level, spawnReason, pos, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("ExplosionPower", (byte) this.explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.explosionPower = input.getByteOr("ExplosionPower", (byte) 1);
    }

    @Override
    public boolean supportQuadLeashAsHolder() {
        return true;
    }

    @Override
    public double leashElasticDistance() {
        return 10.0D;
    }

    @Override
    public double leashSnapDistance() {
        return 16.0D;
    }

    public static void faceMovementDirection(Mob ghast) {
        if (ghast.getTarget() == null) {
            Vec3 vec3 = ghast.getDeltaMovement();

            ghast.setYRot(-((float) Mth.atan2(vec3.x, vec3.z)) * (180F / (float) Math.PI));
            ghast.yBodyRot = ghast.getYRot();
        } else {
            LivingEntity livingentity = ghast.getTarget();
            double d0 = 64.0D;

            if (livingentity.distanceToSqr((Entity) ghast) < 4096.0D) {
                double d1 = livingentity.getX() - ghast.getX();
                double d2 = livingentity.getZ() - ghast.getZ();

                ghast.setYRot(-((float) Mth.atan2(d1, d2)) * (180F / (float) Math.PI));
                ghast.yBodyRot = ghast.getYRot();
            }
        }

    }

    public static class GhastMoveControl extends MoveControl {

        private final Mob ghast;
        private int floatDuration;
        private final boolean careful;
        private final BooleanSupplier shouldBeStopped;

        public GhastMoveControl(Mob ghast, boolean careful, BooleanSupplier shouldBeStopped) {
            super(ghast);
            this.ghast = ghast;
            this.careful = careful;
            this.shouldBeStopped = shouldBeStopped;
        }

        @Override
        public void tick() {
            if (this.shouldBeStopped.getAsBoolean()) {
                this.operation = MoveControl.Operation.WAIT;
                this.ghast.stopInPlace();
            }

            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.floatDuration-- <= 0) {
                    this.floatDuration += this.ghast.getRandom().nextInt(5) + 2;
                    Vec3 vec3 = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());

                    if (this.canReach(vec3)) {
                        this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(vec3.normalize().scale(this.ghast.getAttributeValue(Attributes.FLYING_SPEED) * 5.0D / 3.0D)));
                    } else {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                }

            }
        }

        private boolean canReach(Vec3 travel) {
            AABB aabb = this.ghast.getBoundingBox();
            AABB aabb1 = aabb.move(travel);

            if (this.careful) {
                for (BlockPos blockpos : BlockPos.betweenClosed(aabb1.inflate(1.0D))) {
                    if (!this.blockTraversalPossible(this.ghast.level(), (Vec3) null, (Vec3) null, blockpos, false, false)) {
                        return false;
                    }
                }
            }

            boolean flag = this.ghast.isInWater();
            boolean flag1 = this.ghast.isInLava();
            Vec3 vec31 = this.ghast.position();
            Vec3 vec32 = vec31.add(travel);

            return BlockGetter.forEachBlockIntersectedBetween(vec31, vec32, aabb1, (blockpos1, i) -> {
                return aabb.intersects(blockpos1) ? true : this.blockTraversalPossible(this.ghast.level(), vec31, vec32, blockpos1, flag, flag1);
            });
        }

        private boolean blockTraversalPossible(BlockGetter level, @Nullable Vec3 start, @Nullable Vec3 end, BlockPos pos, boolean canPathThroughWater, boolean canPathThroughLava) {
            BlockState blockstate = level.getBlockState(pos);

            if (blockstate.isAir()) {
                return true;
            } else {
                boolean flag2 = start != null && end != null;
                boolean flag3 = flag2 ? !this.ghast.collidedWithShapeMovingFrom(start, end, blockstate.getCollisionShape(level, pos).move(new Vec3(pos)).toAabbs()) : blockstate.getCollisionShape(level, pos).isEmpty();

                if (!this.careful) {
                    return flag3;
                } else if (blockstate.is(BlockTags.HAPPY_GHAST_AVOIDS)) {
                    return false;
                } else {
                    FluidState fluidstate = level.getFluidState(pos);

                    if (!fluidstate.isEmpty() && (!flag2 || this.ghast.collidedWithFluid(fluidstate, pos, start, end))) {
                        if (fluidstate.is(FluidTags.WATER)) {
                            return canPathThroughWater;
                        }

                        if (fluidstate.is(FluidTags.LAVA)) {
                            return canPathThroughLava;
                        }
                    }

                    return flag3;
                }
            }
        }
    }

    public static class RandomFloatAroundGoal extends Goal {

        private static final int MAX_ATTEMPTS = 64;
        private final Mob ghast;
        private final int distanceToBlocks;

        public RandomFloatAroundGoal(Mob ghast) {
            this(ghast, 0);
        }

        public RandomFloatAroundGoal(Mob ghast, int distanceToBlocks) {
            this.ghast = ghast;
            this.distanceToBlocks = distanceToBlocks;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl movecontrol = this.ghast.getMoveControl();

            if (!movecontrol.hasWanted()) {
                return true;
            } else {
                double d0 = movecontrol.getWantedX() - this.ghast.getX();
                double d1 = movecontrol.getWantedY() - this.ghast.getY();
                double d2 = movecontrol.getWantedZ() - this.ghast.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                return d3 < 1.0D || d3 > 3600.0D;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Vec3 vec3 = getSuitableFlyToPosition(this.ghast, this.distanceToBlocks);

            this.ghast.getMoveControl().setWantedPosition(vec3.x(), vec3.y(), vec3.z(), 1.0D);
        }

        public static Vec3 getSuitableFlyToPosition(Mob mob, int distanceToBlocks) {
            Level level = mob.level();
            RandomSource randomsource = mob.getRandom();
            Vec3 vec3 = mob.position();
            Vec3 vec31 = null;

            for (int j = 0; j < 64; ++j) {
                vec31 = chooseRandomPositionWithRestriction(mob, vec3, randomsource);
                if (vec31 != null && isGoodTarget(level, vec31, distanceToBlocks)) {
                    return vec31;
                }
            }

            if (vec31 == null) {
                vec31 = chooseRandomPosition(vec3, randomsource);
            }

            BlockPos blockpos = BlockPos.containing(vec31);
            int k = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockpos.getX(), blockpos.getZ());

            if (k < blockpos.getY() && k > level.getMinY()) {
                vec31 = new Vec3(vec31.x(), mob.getY() - Math.abs(mob.getY() - vec31.y()), vec31.z());
            }

            return vec31;
        }

        private static boolean isGoodTarget(Level level, Vec3 target, int distanceToBlocks) {
            if (distanceToBlocks <= 0) {
                return true;
            } else {
                BlockPos blockpos = BlockPos.containing(target);

                if (!level.getBlockState(blockpos).isAir()) {
                    return false;
                } else {
                    for (Direction direction : Direction.values()) {
                        for (int j = 1; j < distanceToBlocks; ++j) {
                            BlockPos blockpos1 = blockpos.relative(direction, j);

                            if (!level.getBlockState(blockpos1).isAir()) {
                                return true;
                            }
                        }
                    }

                    return false;
                }
            }
        }

        private static Vec3 chooseRandomPosition(Vec3 center, RandomSource random) {
            double d0 = center.x() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d1 = center.y() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double d2 = center.z() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);

            return new Vec3(d0, d1, d2);
        }

        private static @Nullable Vec3 chooseRandomPositionWithRestriction(Mob mob, Vec3 center, RandomSource random) {
            Vec3 vec31 = chooseRandomPosition(center, random);

            return mob.hasHome() && !mob.isWithinHome(vec31) ? null : vec31;
        }
    }

    public static class GhastLookGoal extends Goal {

        private final Mob ghast;

        public GhastLookGoal(Mob ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Ghast.faceMovementDirection(this.ghast);
        }
    }

    private static class GhastShootFireballGoal extends Goal {

        private final Ghast ghast;
        public int chargeTime;

        public GhastShootFireballGoal(Ghast ghast) {
            this.ghast = ghast;
        }

        @Override
        public boolean canUse() {
            return this.ghast.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.ghast.getTarget();

            if (livingentity != null) {
                double d0 = 64.0D;

                if (livingentity.distanceToSqr((Entity) this.ghast) < 4096.0D && this.ghast.hasLineOfSight(livingentity)) {
                    Level level = this.ghast.level();

                    ++this.chargeTime;
                    if (this.chargeTime == 10 && !this.ghast.isSilent()) {
                        level.levelEvent((Entity) null, 1015, this.ghast.blockPosition(), 0);
                    }

                    if (this.chargeTime == 20) {
                        double d1 = 4.0D;
                        Vec3 vec3 = this.ghast.getViewVector(1.0F);
                        double d2 = livingentity.getX() - (this.ghast.getX() + vec3.x * 4.0D);
                        double d3 = livingentity.getY(0.5D) - (0.5D + this.ghast.getY(0.5D));
                        double d4 = livingentity.getZ() - (this.ghast.getZ() + vec3.z * 4.0D);
                        Vec3 vec31 = new Vec3(d2, d3, d4);

                        if (!this.ghast.isSilent()) {
                            level.levelEvent((Entity) null, 1016, this.ghast.blockPosition(), 0);
                        }

                        LargeFireball largefireball = new LargeFireball(level, this.ghast, vec31.normalize(), this.ghast.getExplosionPower());

                        largefireball.setPos(this.ghast.getX() + vec3.x * 4.0D, this.ghast.getY(0.5D) + 0.5D, largefireball.getZ() + vec3.z * 4.0D);
                        level.addFreshEntity(largefireball);
                        this.chargeTime = -40;
                    }
                } else if (this.chargeTime > 0) {
                    --this.chargeTime;
                }

                this.ghast.setCharging(this.chargeTime > 10);
            }
        }
    }
}
