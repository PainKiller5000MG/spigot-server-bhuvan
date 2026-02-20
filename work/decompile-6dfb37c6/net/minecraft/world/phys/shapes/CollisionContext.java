package net.minecraft.world.phys.shapes;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public interface CollisionContext {

    static CollisionContext empty() {
        return EntityCollisionContext.Empty.WITHOUT_FLUID_COLLISIONS;
    }

    static CollisionContext emptyWithFluidCollisions() {
        return EntityCollisionContext.Empty.WITH_FLUID_COLLISIONS;
    }

    static CollisionContext of(Entity entity) {
        Objects.requireNonNull(entity);
        byte b0 = 0;
        Object object;

        //$FF: b0->value
        //0->net/minecraft/world/entity/vehicle/minecart/AbstractMinecart
        switch (entity.typeSwitch<invokedynamic>(entity, b0)) {
            case 0:
                AbstractMinecart abstractminecart = (AbstractMinecart)entity;

                object = AbstractMinecart.useExperimentalMovement(abstractminecart.level()) ? new MinecartCollisionContext(abstractminecart, false) : new EntityCollisionContext(entity, false, false);
                break;
            default:
                object = new EntityCollisionContext(entity, false, false);
        }

        return (CollisionContext)object;
    }

    static CollisionContext of(Entity entity, boolean alwaysCollideWithFluid) {
        return new EntityCollisionContext(entity, alwaysCollideWithFluid, false);
    }

    static CollisionContext placementContext(@Nullable Player player) {
        return new EntityCollisionContext(player != null ? player.isDescending() : false, true, player != null ? player.getY() : -Double.MAX_VALUE, player instanceof LivingEntity ? ((LivingEntity) player).getMainHandItem() : ItemStack.EMPTY, false, player);
    }

    static CollisionContext withPosition(@Nullable Entity entity, double position) {
        EntityCollisionContext entitycollisioncontext = new EntityCollisionContext;
        boolean flag = entity != null ? entity.isDescending() : false;
        double d1 = entity != null ? position : -Double.MAX_VALUE;
        ItemStack itemstack;

        if (entity instanceof LivingEntity livingentity) {
            itemstack = livingentity.getMainHandItem();
        } else {
            itemstack = ItemStack.EMPTY;
        }

        entitycollisioncontext.<init>(flag, true, d1, itemstack, false, entity);
        return entitycollisioncontext;
    }

    boolean isDescending();

    boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue);

    boolean isHoldingItem(Item item);

    boolean alwaysCollideWithFluid();

    boolean canStandOnFluid(FluidState fluidStateAbove, FluidState fluid);

    VoxelShape getCollisionShape(BlockState state, CollisionGetter collisionGetter, BlockPos pos);

    default boolean isPlacement() {
        return false;
    }
}
