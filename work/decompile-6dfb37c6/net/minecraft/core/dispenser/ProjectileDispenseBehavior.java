package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.block.DispenserBlock;

public class ProjectileDispenseBehavior extends DefaultDispenseItemBehavior {

    private final ProjectileItem projectileItem;
    private final ProjectileItem.DispenseConfig dispenseConfig;

    public ProjectileDispenseBehavior(Item item) {
        if (item instanceof ProjectileItem projectileitem) {
            this.projectileItem = projectileitem;
            this.dispenseConfig = projectileitem.createDispenseConfig();
        } else {
            String s = String.valueOf(item);

            throw new IllegalArgumentException(s + " not instance of " + ProjectileItem.class.getSimpleName());
        }
    }

    @Override
    public ItemStack execute(BlockSource source, ItemStack dispensed) {
        ServerLevel serverlevel = source.level();
        Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
        Position position = this.dispenseConfig.positionFunction().getDispensePosition(source, direction);

        Projectile.spawnProjectileUsingShoot(this.projectileItem.asProjectile(serverlevel, position, dispensed, direction), serverlevel, dispensed, (double) direction.getStepX(), (double) direction.getStepY(), (double) direction.getStepZ(), this.dispenseConfig.power(), this.dispenseConfig.uncertainty());
        dispensed.shrink(1);
        return dispensed;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(this.dispenseConfig.overrideDispenseEvent().orElse(1002), source.pos(), 0);
    }
}
