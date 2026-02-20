package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public abstract class TamableAnimal extends Animal implements OwnableEntity {

    public static final int TELEPORT_WHEN_DISTANCE_IS_SQ = 144;
    private static final int MIN_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 2;
    private static final int MAX_HORIZONTAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 3;
    private static final int MAX_VERTICAL_DISTANCE_FROM_TARGET_AFTER_TELEPORTING = 1;
    private static final boolean DEFAULT_ORDERED_TO_SIT = false;
    protected static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.<Byte>defineId(TamableAnimal.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNERUUID_ID = SynchedEntityData.<Optional<EntityReference<LivingEntity>>>defineId(TamableAnimal.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    private boolean orderedToSit = false;

    protected TamableAnimal(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(TamableAnimal.DATA_FLAGS_ID, (byte) 0);
        entityData.define(TamableAnimal.DATA_OWNERUUID_ID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference<LivingEntity> entityreference = this.getOwnerReference();

        EntityReference.store(entityreference, output, "Owner");
        output.putBoolean("Sitting", this.orderedToSit);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        EntityReference<LivingEntity> entityreference = EntityReference.<LivingEntity>readWithOldOwnerConversion(input, "Owner", this.level());

        if (entityreference != null) {
            try {
                this.entityData.set(TamableAnimal.DATA_OWNERUUID_ID, Optional.of(entityreference));
                this.setTame(true, false);
            } catch (Throwable throwable) {
                this.setTame(false, true);
            }
        } else {
            this.entityData.set(TamableAnimal.DATA_OWNERUUID_ID, Optional.empty());
            this.setTame(false, true);
        }

        this.orderedToSit = input.getBooleanOr("Sitting", false);
        this.setInSittingPose(this.orderedToSit);
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    protected void spawnTamingParticles(boolean success) {
        ParticleOptions particleoptions = ParticleTypes.HEART;

        if (!success) {
            particleoptions = ParticleTypes.SMOKE;
        }

        for (int i = 0; i < 7; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(particleoptions, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 7) {
            this.spawnTamingParticles(true);
        } else if (id == 6) {
            this.spawnTamingParticles(false);
        } else {
            super.handleEntityEvent(id);
        }

    }

    public boolean isTame() {
        return ((Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID) & 4) != 0;
    }

    public void setTame(boolean isTame, boolean includeSideEffects) {
        byte b0 = (Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID);

        if (isTame) {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 | 4));
        } else {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 & -5));
        }

        if (includeSideEffects) {
            this.applyTamingSideEffects();
        }

    }

    protected void applyTamingSideEffects() {}

    public boolean isInSittingPose() {
        return ((Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID) & 1) != 0;
    }

    public void setInSittingPose(boolean value) {
        byte b0 = (Byte) this.entityData.get(TamableAnimal.DATA_FLAGS_ID);

        if (value) {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 | 1));
        } else {
            this.entityData.set(TamableAnimal.DATA_FLAGS_ID, (byte) (b0 & -2));
        }

    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return (EntityReference) ((Optional) this.entityData.get(TamableAnimal.DATA_OWNERUUID_ID)).orElse((Object) null);
    }

    public void setOwner(@Nullable LivingEntity owner) {
        this.entityData.set(TamableAnimal.DATA_OWNERUUID_ID, Optional.ofNullable(owner).map(EntityReference::of));
    }

    public void setOwnerReference(@Nullable EntityReference<LivingEntity> owner) {
        this.entityData.set(TamableAnimal.DATA_OWNERUUID_ID, Optional.ofNullable(owner));
    }

    public void tame(Player player) {
        this.setTame(true, true);
        this.setOwner(player);
        if (player instanceof ServerPlayer serverplayer) {
            CriteriaTriggers.TAME_ANIMAL.trigger(serverplayer, this);
        }

    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return this.isOwnedBy(target) ? false : super.canAttack(target);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return true;
    }

    @Override
    public @Nullable PlayerTeam getTeam() {
        PlayerTeam playerteam = super.getTeam();

        if (playerteam != null) {
            return playerteam;
        } else {
            if (this.isTame()) {
                LivingEntity livingentity = this.getRootOwner();

                if (livingentity != null) {
                    return livingentity.getTeam();
                }
            }

            return null;
        }
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (this.isTame()) {
            LivingEntity livingentity = this.getRootOwner();

            if (other == livingentity) {
                return true;
            }

            if (livingentity != null) {
                return livingentity.considersEntityAsAlly(other);
            }
        }

        return super.considersEntityAsAlly(other);
    }

    @Override
    public void die(DamageSource source) {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if ((Boolean) serverlevel.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) {
                LivingEntity livingentity = this.getOwner();

                if (livingentity instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer) livingentity;

                    serverplayer.sendSystemMessage(this.getCombatTracker().getDeathMessage());
                }
            }
        }

        super.die(source);
    }

    public boolean isOrderedToSit() {
        return this.orderedToSit;
    }

    public void setOrderedToSit(boolean orderedToSit) {
        this.orderedToSit = orderedToSit;
    }

    public void tryToTeleportToOwner() {
        LivingEntity livingentity = this.getOwner();

        if (livingentity != null) {
            this.teleportToAroundBlockPos(livingentity.blockPosition());
        }

    }

    public boolean shouldTryTeleportToOwner() {
        LivingEntity livingentity = this.getOwner();

        return livingentity != null && this.distanceToSqr((Entity) this.getOwner()) >= 144.0D;
    }

    private void teleportToAroundBlockPos(BlockPos targetPos) {
        for (int i = 0; i < 10; ++i) {
            int j = this.random.nextIntBetweenInclusive(-3, 3);
            int k = this.random.nextIntBetweenInclusive(-3, 3);

            if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                int l = this.random.nextIntBetweenInclusive(-1, 1);

                if (this.maybeTeleportTo(targetPos.getX() + j, targetPos.getY() + l, targetPos.getZ() + k)) {
                    return;
                }
            }
        }

    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.snapTo((double) x + 0.5D, (double) y, (double) z + 0.5D, this.getYRot(), this.getXRot());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        PathType pathtype = WalkNodeEvaluator.getPathTypeStatic((Mob) this, pos);

        if (pathtype != PathType.WALKABLE) {
            return false;
        } else {
            BlockState blockstate = this.level().getBlockState(pos.below());

            if (!this.canFlyToOwner() && blockstate.getBlock() instanceof LeavesBlock) {
                return false;
            } else {
                BlockPos blockpos1 = pos.subtract(this.blockPosition());

                return this.level().noCollision(this, this.getBoundingBox().move(blockpos1));
            }
        }
    }

    public final boolean unableToMoveToOwner() {
        return this.isOrderedToSit() || this.isPassenger() || this.mayBeLeashed() || this.getOwner() != null && this.getOwner().isSpectator();
    }

    protected boolean canFlyToOwner() {
        return false;
    }

    public class TamableAnimalPanicGoal extends PanicGoal {

        public TamableAnimalPanicGoal(double speedModifier, TagKey<DamageType> panicCausingDamageTypes) {
            super(TamableAnimal.this, speedModifier, panicCausingDamageTypes);
        }

        public TamableAnimalPanicGoal(double speedModifier) {
            super(TamableAnimal.this, speedModifier);
        }

        @Override
        public void tick() {
            if (!TamableAnimal.this.unableToMoveToOwner() && TamableAnimal.this.shouldTryTeleportToOwner()) {
                TamableAnimal.this.tryToTeleportToOwner();
            }

            super.tick();
        }
    }
}
