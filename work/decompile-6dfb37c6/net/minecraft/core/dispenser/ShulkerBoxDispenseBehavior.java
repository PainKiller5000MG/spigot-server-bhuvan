package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.DispenserBlock;
import org.slf4j.Logger;

public class ShulkerBoxDispenseBehavior extends OptionalDispenseItemBehavior {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ShulkerBoxDispenseBehavior() {}

    @Override
    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        this.setSuccess(false);
        Item item = dispensed.getItem();

        if (item instanceof BlockItem) {
            Direction direction = (Direction) source.state().getValue(DispenserBlock.FACING);
            BlockPos blockpos = source.pos().relative(direction);
            Direction direction1 = source.level().isEmptyBlock(blockpos.below()) ? direction : Direction.UP;

            try {
                this.setSuccess(((BlockItem) item).place(new DirectionalPlaceContext(source.level(), blockpos, direction, dispensed, direction1)).consumesAction());
            } catch (Exception exception) {
                ShulkerBoxDispenseBehavior.LOGGER.error("Error trying to place shulker box at {}", blockpos, exception);
            }
        }

        return dispensed;
    }
}
