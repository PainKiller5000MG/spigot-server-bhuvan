package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public class BoatDispenseItemBehavior extends DefaultDispenseItemBehavior {

    private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();
    private final EntityType<? extends AbstractBoat> type;

    public BoatDispenseItemBehavior(EntityType<? extends AbstractBoat> type) {
        this.type = type;
    }

    @Override
    public ItemStack execute(BlockSource source, ItemStack dispensed) {
        Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
        ServerLevel serverlevel = source.level();
        Vec3 vec3 = source.center();
        double d0 = 0.5625D + (double) this.type.getWidth() / 2.0D;
        double d1 = vec3.x() + (double) direction.getStepX() * d0;
        double d2 = vec3.y() + (double) ((float) direction.getStepY() * 1.125F);
        double d3 = vec3.z() + (double) direction.getStepZ() * d0;
        BlockPos blockpos = source.pos().relative(direction);
        double d4;

        if (serverlevel.getFluidState(blockpos).is(FluidTags.WATER)) {
            d4 = 1.0D;
        } else {
            if (!serverlevel.getBlockState(blockpos).isAir() || !serverlevel.getFluidState(blockpos.below()).is(FluidTags.WATER)) {
                return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }

            d4 = 0.0D;
        }

        AbstractBoat abstractboat = this.type.create(serverlevel, EntitySpawnReason.DISPENSER);

        if (abstractboat != null) {
            abstractboat.setInitialPos(d1, d2 + d4, d3);
            EntityType.createDefaultStackConfig(serverlevel, dispensed, (LivingEntity) null).accept(abstractboat);
            abstractboat.setYRot(direction.toYRot());
            serverlevel.addFreshEntity(abstractboat);
            dispensed.shrink(1);
        }

        return dispensed;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }
}
