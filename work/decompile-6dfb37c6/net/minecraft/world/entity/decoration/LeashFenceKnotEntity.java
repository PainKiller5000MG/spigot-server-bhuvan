package net.minecraft.world.entity.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LeashFenceKnotEntity extends BlockAttachedEntity {

    public static final double OFFSET_Y = 0.375D;

    public LeashFenceKnotEntity(EntityType<? extends LeashFenceKnotEntity> type, Level level) {
        super(type, level);
    }

    public LeashFenceKnotEntity(Level level, BlockPos pos) {
        super(EntityType.LEASH_KNOT, level, pos);
        this.setPos((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    protected void recalculateBoundingBox() {
        this.setPosRaw((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.375D, (double) this.pos.getZ() + 0.5D);
        double d0 = (double) this.getType().getWidth() / 2.0D;
        double d1 = (double) this.getType().getHeight();

        this.setBoundingBox(new AABB(this.getX() - d0, this.getY(), this.getZ() - d0, this.getX() + d0, this.getY() + d1, this.getZ() + d0));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0D;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        this.playSound(SoundEvents.LEAD_UNTIED, 1.0F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            if (player.getItemInHand(hand).is(Items.SHEARS)) {
                InteractionResult interactionresult = super.interact(player, hand);

                if (interactionresult instanceof InteractionResult.Success) {
                    InteractionResult.Success interactionresult_success = (InteractionResult.Success) interactionresult;

                    if (interactionresult_success.wasItemInteraction()) {
                        return interactionresult;
                    }
                }
            }

            boolean flag = false;

            for (Leashable leashable : Leashable.leashableLeashedTo(player)) {
                if (leashable.canHaveALeashAttachedTo(this)) {
                    leashable.setLeashedTo(this, true);
                    flag = true;
                }
            }

            boolean flag1 = false;

            if (!flag && !player.isSecondaryUseActive()) {
                for (Leashable leashable1 : Leashable.leashableLeashedTo(this)) {
                    if (leashable1.canHaveALeashAttachedTo(player)) {
                        leashable1.setLeashedTo(player, true);
                        flag1 = true;
                    }
                }
            }

            if (!flag && !flag1) {
                return super.interact(player, hand);
            } else {
                this.gameEvent(GameEvent.BLOCK_ATTACH, player);
                this.playSound(SoundEvents.LEAD_TIED);
                return InteractionResult.SUCCESS;
            }
        }
    }

    @Override
    public void notifyLeasheeRemoved(Leashable entity) {
        if (Leashable.leashableLeashedTo(this).isEmpty()) {
            this.discard();
        }

    }

    @Override
    public boolean survives() {
        return this.level().getBlockState(this.pos).is(BlockTags.FENCES);
    }

    public static LeashFenceKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        for (LeashFenceKnotEntity leashfenceknotentity : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB((double) i - 1.0D, (double) j - 1.0D, (double) k - 1.0D, (double) i + 1.0D, (double) j + 1.0D, (double) k + 1.0D))) {
            if (leashfenceknotentity.getPos().equals(pos)) {
                return leashfenceknotentity;
            }
        }

        LeashFenceKnotEntity leashfenceknotentity1 = new LeashFenceKnotEntity(level, pos);

        level.addFreshEntity(leashfenceknotentity1);
        return leashfenceknotentity1;
    }

    public void playPlacementSound() {
        this.playSound(SoundEvents.LEAD_TIED, 1.0F, 1.0F);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTickTime) {
        return this.getPosition(partialTickTime).add(0.0D, 0.2D, 0.0D);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
