package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {

    private static final int DEFAULT_ACCURACY = 6;

    public DefaultDispenseItemBehavior() {}

    @Override
    public final ItemStack dispense(BlockSource source, ItemStack dispensed) {
        ItemStack itemstack1 = this.execute(source, dispensed);

        this.playSound(source);
        this.playAnimation(source, (Direction) source.state().getValue(DispenserBlock.FACING));
        return itemstack1;
    }

    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(source);
        ItemStack itemstack1 = dispensed.split(1);

        spawnItem(source.level(), itemstack1, 6, direction, position);
        return dispensed;
    }

    public static void spawnItem(Level level, ItemStack itemStack, int accuracy, Direction direction, Position position) {
        double d0 = position.x();
        double d1 = position.y();
        double d2 = position.z();

        if (direction.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125D;
        } else {
            d1 -= 0.15625D;
        }

        ItemEntity itementity = new ItemEntity(level, d0, d1, d2, itemStack);
        double d3 = level.random.nextDouble() * 0.1D + 0.2D;

        itementity.setDeltaMovement(level.random.triangle((double) direction.getStepX() * d3, 0.0172275D * (double) accuracy), level.random.triangle(0.2D, 0.0172275D * (double) accuracy), level.random.triangle((double) direction.getStepZ() * d3, 0.0172275D * (double) accuracy));
        level.addFreshEntity(itementity);
    }

    protected void playSound(BlockSource source) {
        playDefaultSound(source);
    }

    protected void playAnimation(BlockSource source, Direction direction) {
        playDefaultAnimation(source, direction);
    }

    private static void playDefaultSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }

    private static void playDefaultAnimation(BlockSource source, Direction direction) {
        source.level().levelEvent(2000, source.pos(), direction.get3DDataValue());
    }

    protected ItemStack consumeWithRemainder(BlockSource source, ItemStack dispensed, ItemStack remainder) {
        dispensed.shrink(1);
        if (dispensed.isEmpty()) {
            return remainder;
        } else {
            this.addToInventoryOrDispense(source, remainder);
            return dispensed;
        }
    }

    private void addToInventoryOrDispense(BlockSource source, ItemStack itemStack) {
        ItemStack itemstack1 = source.blockEntity().insertItem(itemStack);

        if (!itemstack1.isEmpty()) {
            Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);

            spawnItem(source.level(), itemstack1, 6, direction, DispenserBlock.getDispensePosition(source));
            playDefaultSound(source);
            playDefaultAnimation(source, direction);
        }
    }
}
