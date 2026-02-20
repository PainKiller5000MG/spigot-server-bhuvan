package net.minecraft.world.entity.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class PrimedTnt extends Entity implements TraceableEntity {

    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.<Integer>defineId(PrimedTnt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.<BlockState>defineId(PrimedTnt.class, EntityDataSerializers.BLOCK_STATE);
    private static final short DEFAULT_FUSE_TIME = 80;
    private static final float DEFAULT_EXPLOSION_POWER = 4.0F;
    private static final BlockState DEFAULT_BLOCK_STATE = Blocks.TNT.defaultBlockState();
    private static final String TAG_BLOCK_STATE = "block_state";
    public static final String TAG_FUSE = "fuse";
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final ExplosionDamageCalculator USED_PORTAL_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return state.is(Blocks.NETHER_PORTAL) ? false : super.shouldBlockExplode(explosion, level, pos, state, power);
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
            return block.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, level, pos, block, fluid);
        }
    };
    public @Nullable EntityReference<LivingEntity> owner;
    private boolean usedPortal;
    public float explosionPower;

    public PrimedTnt(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
        this.explosionPower = 4.0F;
        this.blocksBuilding = true;
    }

    public PrimedTnt(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(EntityType.TNT, level);
        this.setPos(x, y, z);
        double d3 = level.random.nextDouble() * (double) ((float) Math.PI * 2F);

        this.setDeltaMovement(-Math.sin(d3) * 0.02D, (double) 0.2F, -Math.cos(d3) * 0.02D);
        this.setFuse(80);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.owner = EntityReference.of(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(PrimedTnt.DATA_FUSE_ID, 80);
        entityData.define(PrimedTnt.DATA_BLOCK_STATE_ID, PrimedTnt.DEFAULT_BLOCK_STATE);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        this.handlePortal();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        int i = this.getFuse() - 1;

        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide()) {
                this.explode();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide()) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    private void explode() {
        Level level = this.level();

        if (level instanceof ServerLevel serverlevel) {
            if ((Boolean) serverlevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                this.level().explode(this, Explosion.getDefaultDamageSource(this.level(), this), this.usedPortal ? PrimedTnt.USED_PORTAL_DAMAGE_CALCULATOR : null, this.getX(), this.getY(0.0625D), this.getZ(), this.explosionPower, false, Level.ExplosionInteraction.TNT);
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putShort("fuse", (short) this.getFuse());
        output.store("block_state", BlockState.CODEC, this.getBlockState());
        if (this.explosionPower != 4.0F) {
            output.putFloat("explosion_power", this.explosionPower);
        }

        EntityReference.store(this.owner, output, "owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setFuse(input.getShortOr("fuse", (short) 80));
        this.setBlockState((BlockState) input.read("block_state", BlockState.CODEC).orElse(PrimedTnt.DEFAULT_BLOCK_STATE));
        this.explosionPower = Mth.clamp(input.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
        this.owner = EntityReference.<LivingEntity>read(input, "owner");
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    @Override
    public void restoreFrom(Entity oldEntity) {
        super.restoreFrom(oldEntity);
        if (oldEntity instanceof PrimedTnt primedtnt) {
            this.owner = primedtnt.owner;
        }

    }

    public void setFuse(int time) {
        this.entityData.set(PrimedTnt.DATA_FUSE_ID, time);
    }

    public int getFuse() {
        return (Integer) this.entityData.get(PrimedTnt.DATA_FUSE_ID);
    }

    public void setBlockState(BlockState blockState) {
        this.entityData.set(PrimedTnt.DATA_BLOCK_STATE_ID, blockState);
    }

    public BlockState getBlockState() {
        return (BlockState) this.entityData.get(PrimedTnt.DATA_BLOCK_STATE_ID);
    }

    private void setUsedPortal(boolean usedPortal) {
        this.usedPortal = usedPortal;
    }

    @Override
    public @Nullable Entity teleport(TeleportTransition transition) {
        Entity entity = super.teleport(transition);

        if (entity instanceof PrimedTnt primedtnt) {
            primedtnt.setUsedPortal(true);
        }

        return entity;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
