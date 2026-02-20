package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Minecart extends AbstractMinecart {

    private float rotationOffset;
    private float playerRotationOffset;

    public Minecart(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive() && !this.isVehicle() && (this.level().isClientSide() || player.startRiding(this))) {
            this.playerRotationOffset = this.rotationOffset;
            return (InteractionResult) (!this.level().isClientSide() ? (player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS) : InteractionResult.SUCCESS);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.MINECART);
    }

    @Override
    public void activateMinecart(ServerLevel level, int xt, int yt, int zt, boolean state) {
        if (state) {
            if (this.isVehicle()) {
                this.ejectPassengers();
            }

            if (this.getHurtTime() == 0) {
                this.setHurtDir(-this.getHurtDir());
                this.setHurtTime(10);
                this.setDamage(50.0F);
                this.markHurt();
            }
        }

    }

    @Override
    public boolean isRideable() {
        return true;
    }

    @Override
    public void tick() {
        double d0 = (double) this.getYRot();
        Vec3 vec3 = this.position();

        super.tick();
        double d1 = ((double) this.getYRot() - d0) % 360.0D;

        if (this.level().isClientSide() && vec3.distanceTo(this.position()) > 0.01D) {
            this.rotationOffset += (float) d1;
            this.rotationOffset %= 360.0F;
        }

    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (this.level().isClientSide() && passenger instanceof Player player) {
            if (player.shouldRotateWithMinecart() && useExperimentalMovement(this.level())) {
                float f = (float) Mth.rotLerp(0.5D, (double) this.playerRotationOffset, (double) this.rotationOffset);

                player.setYRot(player.getYRot() - (f - this.playerRotationOffset));
                this.playerRotationOffset = f;
            }
        }

    }
}
