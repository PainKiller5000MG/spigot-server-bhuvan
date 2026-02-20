package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public class MinecartDispenseItemBehavior extends DefaultDispenseItemBehavior {

    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final EntityType<? extends AbstractMinecart> entityType;

    public MinecartDispenseItemBehavior(EntityType<? extends AbstractMinecart> entityType) {
        this.entityType = entityType;
    }

    @Override
    public ItemStack execute(BlockSource source, ItemStack dispensed) {
        Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
        ServerLevel serverlevel = source.level();
        Vec3 vec3 = source.center();
        double d0 = vec3.x() + (double) direction.getStepX() * 1.125D;
        double d1 = Math.floor(vec3.y()) + (double) direction.getStepY();
        double d2 = vec3.z() + (double) direction.getStepZ() * 1.125D;
        BlockPos blockpos = source.pos().relative(direction);
        BlockState blockstate = serverlevel.getBlockState(blockpos);
        double d3;

        if (blockstate.is(BlockTags.RAILS)) {
            if (getRailShape(blockstate).isSlope()) {
                d3 = 0.6D;
            } else {
                d3 = 0.1D;
            }
        } else {
            if (!blockstate.isAir()) {
                return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }

            BlockState blockstate1 = serverlevel.getBlockState(blockpos.below());

            if (!blockstate1.is(BlockTags.RAILS)) {
                return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }

            if (direction != Direction.DOWN && getRailShape(blockstate1).isSlope()) {
                d3 = -0.4D;
            } else {
                d3 = -0.9D;
            }
        }

        Vec3 vec31 = new Vec3(d0, d1 + d3, d2);
        AbstractMinecart abstractminecart = AbstractMinecart.createMinecart(serverlevel, vec31.x, vec31.y, vec31.z, this.entityType, EntitySpawnReason.DISPENSER, dispensed, (Player) null);

        if (abstractminecart != null) {
            serverlevel.addFreshEntity(abstractminecart);
            dispensed.shrink(1);
        }

        return dispensed;
    }

    private static RailShape getRailShape(BlockState blockFront) {
        Block block = blockFront.getBlock();
        RailShape railshape;

        if (block instanceof BaseRailBlock baserailblock) {
            railshape = (RailShape) blockFront.getValue(baserailblock.getShapeProperty());
        } else {
            railshape = RailShape.NORTH_SOUTH;
        }

        return railshape;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }
}
