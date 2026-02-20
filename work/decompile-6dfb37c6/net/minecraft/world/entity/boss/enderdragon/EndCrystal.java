package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EndCrystal extends Entity {

    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.<Optional<BlockPos>>defineId(EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.<Boolean>defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_SHOW_BOTTOM = true;
    public int time;

    public EndCrystal(EntityType<? extends EndCrystal> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(Level level, double x, double y, double z) {
        this(EntityType.END_CRYSTAL, level);
        this.setPos(x, y, z);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(EndCrystal.DATA_BEAM_TARGET, Optional.empty());
        entityData.define(EndCrystal.DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        ++this.time;
        this.applyEffectsFromBlocks();
        this.handlePortal();
        if (this.level() instanceof ServerLevel) {
            BlockPos blockpos = this.blockPosition();

            if (((ServerLevel) this.level()).getDragonFight() != null && this.level().getBlockState(blockpos).isAir()) {
                this.level().setBlockAndUpdate(blockpos, BaseFireBlock.getState(this.level(), blockpos));
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("beam_target", BlockPos.CODEC, this.getBeamTarget());
        output.putBoolean("ShowBottom", this.showsBottom());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setBeamTarget((BlockPos) input.read("beam_target", BlockPos.CODEC).orElse((Object) null));
        this.setShowBottom(input.getBooleanOr("ShowBottom", true));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public final boolean hurtClient(DamageSource source) {
        return this.isInvulnerableToBase(source) ? false : !(source.getEntity() instanceof EnderDragon);
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        } else if (source.getEntity() instanceof EnderDragon) {
            return false;
        } else {
            if (!this.isRemoved()) {
                this.remove(Entity.RemovalReason.KILLED);
                if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                    DamageSource damagesource1 = source.getEntity() != null ? this.damageSources().explosion(this, source.getEntity()) : null;

                    level.explode(this, damagesource1, (ExplosionDamageCalculator) null, this.getX(), this.getY(), this.getZ(), 6.0F, false, Level.ExplosionInteraction.BLOCK);
                }

                this.onDestroyedBy(level, source);
            }

            return true;
        }
    }

    @Override
    public void kill(ServerLevel level) {
        this.onDestroyedBy(level, this.damageSources().generic());
        super.kill(level);
    }

    private void onDestroyedBy(ServerLevel level, DamageSource source) {
        EndDragonFight enddragonfight = level.getDragonFight();

        if (enddragonfight != null) {
            enddragonfight.onCrystalDestroyed(this, source);
        }

    }

    public void setBeamTarget(@Nullable BlockPos target) {
        this.getEntityData().set(EndCrystal.DATA_BEAM_TARGET, Optional.ofNullable(target));
    }

    public @Nullable BlockPos getBeamTarget() {
        return (BlockPos) ((Optional) this.getEntityData().get(EndCrystal.DATA_BEAM_TARGET)).orElse((Object) null);
    }

    public void setShowBottom(boolean showBottom) {
        this.getEntityData().set(EndCrystal.DATA_SHOW_BOTTOM, showBottom);
    }

    public boolean showsBottom() {
        return (Boolean) this.getEntityData().get(EndCrystal.DATA_SHOW_BOTTOM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return super.shouldRenderAtSqrDistance(distance) || this.getBeamTarget() != null;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.END_CRYSTAL);
    }
}
