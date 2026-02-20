package net.minecraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class Containers {

    public Containers() {}

    public static void dropContents(Level level, BlockPos pos, Container container) {
        dropContents(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), container);
    }

    public static void dropContents(Level level, Entity entity, Container container) {
        dropContents(level, entity.getX(), entity.getY(), entity.getZ(), container);
    }

    private static void dropContents(Level level, double x, double y, double z, Container container) {
        for (int i = 0; i < container.getContainerSize(); ++i) {
            dropItemStack(level, x, y, z, container.getItem(i));
        }

    }

    public static void dropContents(Level level, BlockPos pos, NonNullList<ItemStack> list) {
        list.forEach((itemstack) -> {
            dropItemStack(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), itemstack);
        });
    }

    public static void dropItemStack(Level level, double x, double y, double z, ItemStack itemStack) {
        double d3 = (double) EntityType.ITEM.getWidth();
        double d4 = 1.0D - d3;
        double d5 = d3 / 2.0D;
        double d6 = Math.floor(x) + level.random.nextDouble() * d4 + d5;
        double d7 = Math.floor(y) + level.random.nextDouble() * d4;
        double d8 = Math.floor(z) + level.random.nextDouble() * d4 + d5;

        while (!itemStack.isEmpty()) {
            ItemEntity itementity = new ItemEntity(level, d6, d7, d8, itemStack.split(level.random.nextInt(21) + 10));
            float f = 0.05F;

            itementity.setDeltaMovement(level.random.triangle(0.0D, 0.11485000171139836D), level.random.triangle(0.2D, 0.11485000171139836D), level.random.triangle(0.0D, 0.11485000171139836D));
            level.addFreshEntity(itementity);
        }

    }

    public static void updateNeighboursAfterDestroy(BlockState state, Level level, BlockPos pos) {
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
}
