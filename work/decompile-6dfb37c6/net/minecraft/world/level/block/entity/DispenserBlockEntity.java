package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DispenserBlockEntity extends RandomizableContainerBlockEntity {

    public static final int CONTAINER_SIZE = 9;
    private static final Component DEFAULT_NAME = Component.translatable("container.dispenser");
    private NonNullList<ItemStack> items;

    protected DispenserBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
    }

    public DispenserBlockEntity(BlockPos worldPosition, BlockState blockState) {
        this(BlockEntityType.DISPENSER, worldPosition, blockState);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    public int getRandomSlot(RandomSource random) {
        this.unpackLootTable((Player) null);
        int i = -1;
        int j = 1;

        for (int k = 0; k < this.items.size(); ++k) {
            if (!((ItemStack) this.items.get(k)).isEmpty() && random.nextInt(j++) == 0) {
                i = k;
            }
        }

        return i;
    }

    public ItemStack insertItem(ItemStack itemStack) {
        int i = this.getMaxStackSize(itemStack);

        for (int j = 0; j < this.items.size(); ++j) {
            ItemStack itemstack1 = this.items.get(j);

            if (itemstack1.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemstack1)) {
                int k = Math.min(itemStack.getCount(), i - itemstack1.getCount());

                if (k > 0) {
                    if (itemstack1.isEmpty()) {
                        this.setItem(j, itemStack.split(k));
                    } else {
                        itemStack.shrink(k);
                        itemstack1.grow(k);
                    }
                }

                if (itemStack.isEmpty()) {
                    break;
                }
            }
        }

        return itemStack;
    }

    @Override
    protected Component getDefaultName() {
        return DispenserBlockEntity.DEFAULT_NAME;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }

    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }

    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }
}
