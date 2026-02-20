package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Phantom extends Mob implements Enemy {

    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.<Integer>defineId(Phantom.class, EntityDataSerializers.INT);
    private Vec3 moveTargetPoint;
    private @Nullable BlockPos anchorPoint;
    private Phantom.AttackPhase attackPhase;

    public Phantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        this.moveTargetPoint = Vec3.ZERO;
        this.attackPhase = Phantom.AttackPhase.CIRCLE;
        this.xpReward = 5;
        this.moveControl = new Phantom.PhantomMoveControl(this);
        this.lookControl = new Phantom.PhantomLookControl(this);
    }

    @Override
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % Phantom.TICKS_PER_FLAP == 0;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Phantom.PhantomBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new Phantom.PhantomAttackStrategyGoal());
        this.goalSelector.addGoal(2, new Phantom.PhantomSweepAttackGoal());
        this.goalSelector.addGoal(3, new Phantom.PhantomCircleAroundAnchorGoal());
        this.targetSelector.addGoal(1, new Phantom.PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(Phantom.ID_SIZE, 0);
    }

    public void setPhantomSize(int size) {
        this.entityData.set(Phantom.ID_SIZE, Mth.clamp(size, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) (6 + this.getPhantomSize()));
    }

    public int getPhantomSize() {
        return (Integer) this.entityData.get(Phantom.ID_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (Phantom.ID_SIZE.equals(accessor)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(accessor);
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            float f = Mth.cos((double) ((float) (this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * ((float) Math.PI / 180F) + (float) Math.PI));
            float f1 = Mth.cos((double) ((float) (this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * ((float) Math.PI / 180F) + (float) Math.PI));

            if (f > 0.0F && f1 <= 0.0F) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, this.getSoundSource(), 0.95F + this.random.nextFloat() * 0.05F, 0.95F + this.random.nextFloat() * 0.05F, false);
            }

            float f2 = this.getBbWidth() * 1.48F;
            float f3 = Mth.cos((double) (this.getYRot() * ((float) Math.PI / 180F))) * f2;
            float f4 = Mth.sin((double) (this.getYRot() * ((float) Math.PI / 180F))) * f2;
            float f5 = (0.3F + f * 0.45F) * this.getBbHeight() * 2.5F;

            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() + (double) f3, this.getY() + (double) f5, this.getZ() + (double) f4, 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() - (double) f3, this.getY() + (double) f5, this.getZ() - (double) f4, 0.0D, 0.0D, 0.0D);
        }

    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {}

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        this.travelFlying(input, 0.2F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        this.anchorPoint = this.blockPosition().above(5);
        this.setPhantomSize(0);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.anchorPoint = (BlockPos) input.read("anchor_pos", BlockPos.CODEC).orElse((Object) null);
        this.setPhantomSize(input.getIntOr("size", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("anchor_pos", BlockPos.CODEC, this.anchorPoint);
        output.putInt("size", this.getPhantomSize());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean canAttackType(EntityType<?> targetType) {
        return true;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        int i = this.getPhantomSize();
        EntityDimensions entitydimensions = super.getDefaultDimensions(pose);

        return entitydimensions.scale(1.0F + 0.15F * (float) i);
    }

    private boolean canAttack(ServerLevel level, LivingEntity target, TargetingConditions targetConditions) {
        return targetConditions.test(level, this, target);
    }

    private static enum AttackPhase {

        CIRCLE, SWOOP;

        private AttackPhase() {}
    }

    private class PhantomMoveControl extends MoveControl {

        private float speed = 0.1F;

        public PhantomMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (Phantom.this.horizontalCollision) {
                Phantom.this.setYRot(Phantom.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }

            double d0 = Phantom.this.moveTargetPoint.x - Phantom.this.getX();
            double d1 = Phantom.this.moveTargetPoint.y - Phantom.this.getY();
            double d2 = Phantom.this.moveTargetPoint.z - Phantom.this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);

            if (Math.abs(d3) > (double) 1.0E-5F) {
                double d4 = 1.0D - Math.abs(d1 * (double) 0.7F) / d3;

                d0 *= d4;
                d2 *= d4;
                d3 = Math.sqrt(d0 * d0 + d2 * d2);
                double d5 = Math.sqrt(d0 * d0 + d2 * d2 + d1 * d1);
                float f = Phantom.this.getYRot();
                float f1 = (float) Mth.atan2(d2, d0);
                float f2 = Mth.wrapDegrees(Phantom.this.getYRot() + 90.0F);
                float f3 = Mth.wrapDegrees(f1 * (180F / (float) Math.PI));

                Phantom.this.setYRot(Mth.approachDegrees(f2, f3, 4.0F) - 90.0F);
                Phantom.this.yBodyRot = Phantom.this.getYRot();
                if (Mth.degreesDifferenceAbs(f, Phantom.this.getYRot()) < 3.0F) {
                    this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
                }

                float f4 = (float) (-(Mth.atan2(-d1, d3) * (double) (180F / (float) Math.PI)));

                Phantom.this.setXRot(f4);
                float f5 = Phantom.this.getYRot() + 90.0F;
                double d6 = (double) (this.speed * Mth.cos((double) (f5 * ((float) Math.PI / 180F)))) * Math.abs(d0 / d5);
                double d7 = (double) (this.speed * Mth.sin((double) (f5 * ((float) Math.PI / 180F)))) * Math.abs(d2 / d5);
                double d8 = (double) (this.speed * Mth.sin((double) (f4 * ((float) Math.PI / 180F)))) * Math.abs(d1 / d5);
                Vec3 vec3 = Phantom.this.getDeltaMovement();

                Phantom.this.setDeltaMovement(vec3.add((new Vec3(d6, d8, d7)).subtract(vec3).scale(0.2D)));
            }

        }
    }

    private class PhantomBodyRotationControl extends BodyRotationControl {

        public PhantomBodyRotationControl(Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            Phantom.this.yHeadRot = Phantom.this.yBodyRot;
            Phantom.this.yBodyRot = Phantom.this.getYRot();
        }
    }

    private static class PhantomLookControl extends LookControl {

        public PhantomLookControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {}
    }

    private abstract class PhantomMoveTargetGoal extends Goal {

        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return Phantom.this.moveTargetPoint.distanceToSqr(Phantom.this.getX(), Phantom.this.getY(), Phantom.this.getZ()) < 4.0D;
        }
    }

    private class PhantomCircleAroundAnchorGoal extends Phantom.PhantomMoveTargetGoal {

        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        private PhantomCircleAroundAnchorGoal() {}

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() == null || Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + Phantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            this.clockwise = Phantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (Phantom.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                ++this.distance;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = Phantom.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y < Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y > Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }

        }

        private void selectNext() {
            if (Phantom.this.anchorPoint == null) {
                Phantom.this.anchorPoint = Phantom.this.blockPosition();
            }

            this.angle += this.clockwise * 15.0F * ((float) Math.PI / 180F);
            Phantom.this.moveTargetPoint = Vec3.atLowerCornerOf(Phantom.this.anchorPoint).add((double) (this.distance * Mth.cos((double) this.angle)), (double) (-4.0F + this.height), (double) (this.distance * Mth.sin((double) this.angle)));
        }
    }

    private class PhantomSweepAttackGoal extends Phantom.PhantomMoveTargetGoal {

        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        private PhantomSweepAttackGoal() {}

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() != null && Phantom.this.attackPhase == Phantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity livingentity = Phantom.this.getTarget();

            if (livingentity == null) {
                return false;
            } else if (!livingentity.isAlive()) {
                return false;
            } else {
                if (livingentity instanceof Player) {
                    Player player = (Player) livingentity;

                    if (livingentity.isSpectator() || player.isCreative()) {
                        return false;
                    }
                }

                if (!this.canUse()) {
                    return false;
                } else {
                    if (Phantom.this.tickCount > this.catSearchTick) {
                        this.catSearchTick = Phantom.this.tickCount + 20;
                        List<Cat> list = Phantom.this.level().<Cat>getEntitiesOfClass(Cat.class, Phantom.this.getBoundingBox().inflate(16.0D), EntitySelector.ENTITY_STILL_ALIVE);

                        for (Cat cat : list) {
                            cat.hiss();
                        }

                        this.isScaredOfCat = !list.isEmpty();
                    }

                    return !this.isScaredOfCat;
                }
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {
            Phantom.this.setTarget((LivingEntity) null);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity livingentity = Phantom.this.getTarget();

            if (livingentity != null) {
                Phantom.this.moveTargetPoint = new Vec3(livingentity.getX(), livingentity.getY(0.5D), livingentity.getZ());
                if (Phantom.this.getBoundingBox().inflate((double) 0.2F).intersects(livingentity.getBoundingBox())) {
                    Phantom.this.doHurtTarget(getServerLevel(Phantom.this.level()), livingentity);
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                    if (!Phantom.this.isSilent()) {
                        Phantom.this.level().levelEvent(1039, Phantom.this.blockPosition(), 0);
                    }
                } else if (Phantom.this.horizontalCollision || Phantom.this.hurtTime > 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                }

            }
        }
    }

    private class PhantomAttackStrategyGoal extends Goal {

        private int nextSweepTick;

        private PhantomAttackStrategyGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity livingentity = Phantom.this.getTarget();

            return livingentity != null ? Phantom.this.canAttack(getServerLevel(Phantom.this.level()), livingentity, TargetingConditions.DEFAULT) : false;
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, Phantom.this.anchorPoint).above(10 + Phantom.this.random.nextInt(20));
            }

        }

        @Override
        public void tick() {
            if (Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE) {
                --this.nextSweepTick;
                if (this.nextSweepTick <= 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + Phantom.this.random.nextInt(4)) * 20);
                    Phantom.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + Phantom.this.random.nextFloat() * 0.1F);
                }
            }

        }

        private void setAnchorAboveTarget() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.getTarget().blockPosition().above(20 + Phantom.this.random.nextInt(20));
                if (Phantom.this.anchorPoint.getY() < Phantom.this.level().getSeaLevel()) {
                    Phantom.this.anchorPoint = new BlockPos(Phantom.this.anchorPoint.getX(), Phantom.this.level().getSeaLevel() + 1, Phantom.this.anchorPoint.getZ());
                }

            }
        }
    }

    private class PhantomAttackPlayerTargetGoal extends Goal {

        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0D);
        private int nextScanTick = reducedTickDelay(20);

        private PhantomAttackPlayerTargetGoal() {}

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                --this.nextScanTick;
                return false;
            } else {
                this.nextScanTick = reducedTickDelay(60);
                ServerLevel serverlevel = getServerLevel(Phantom.this.level());
                List<Player> list = serverlevel.getNearbyPlayers(this.attackTargeting, Phantom.this, Phantom.this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D));

                if (!list.isEmpty()) {
                    list.sort(Comparator.comparing(Entity::getY).reversed());

                    for (Player player : list) {
                        if (Phantom.this.canAttack(serverlevel, player, TargetingConditions.DEFAULT)) {
                            Phantom.this.setTarget(player);
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity livingentity = Phantom.this.getTarget();

            return livingentity != null ? Phantom.this.canAttack(getServerLevel(Phantom.this.level()), livingentity, TargetingConditions.DEFAULT) : false;
        }
    }
}
