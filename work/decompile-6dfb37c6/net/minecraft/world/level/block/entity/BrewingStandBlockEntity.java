package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int[] SLOTS_FOR_UP = new int[]{3};
    private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
    private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
    public static final int FUEL_USES = 20;
    public static final int DATA_BREW_TIME = 0;
    public static final int DATA_FUEL_USES = 1;
    public static final int NUM_DATA_VALUES = 2;
    private static final short DEFAULT_BREW_TIME = 0;
    private static final byte DEFAULT_FUEL = 0;
    private static final Component DEFAULT_NAME = Component.translatable("container.brewing");
    private NonNullList<ItemStack> items;
    public int brewTime;
    private boolean[] lastPotionCount;
    private Item ingredient;
    public int fuel;
    protected final ContainerData dataAccess;

    public BrewingStandBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.BREWING_STAND, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(5, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int dataId) {
                int j;

                switch (dataId) {
                    case 0:
                        j = BrewingStandBlockEntity.this.brewTime;
                        break;
                    case 1:
                        j = BrewingStandBlockEntity.this.fuel;
                        break;
                    default:
                        j = 0;
                }

                return j;
            }

            @Override
            public void set(int dataId, int value) {
                switch (dataId) {
                    case 0:
                        BrewingStandBlockEntity.this.brewTime = value;
                        break;
                    case 1:
                        BrewingStandBlockEntity.this.fuel = value;
                }

            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    protected Component getDefaultName() {
        return BrewingStandBlockEntity.DEFAULT_NAME;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState selfState, BrewingStandBlockEntity entity) {
        ItemStack itemstack = entity.items.get(4);

        if (entity.fuel <= 0 && itemstack.is(ItemTags.BREWING_FUEL)) {
            entity.fuel = 20;
            itemstack.shrink(1);
            setChanged(level, pos, selfState);
        }

        boolean flag = isBrewable(level.potionBrewing(), entity.items);
        boolean flag1 = entity.brewTime > 0;
        ItemStack itemstack1 = entity.items.get(3);

        if (flag1) {
            --entity.brewTime;
            boolean flag2 = entity.brewTime == 0;

            if (flag2 && flag) {
                doBrew(level, pos, entity.items);
            } else if (!flag || !itemstack1.is(entity.ingredient)) {
                entity.brewTime = 0;
            }

            setChanged(level, pos, selfState);
        } else if (flag && entity.fuel > 0) {
            --entity.fuel;
            entity.brewTime = 400;
            entity.ingredient = itemstack1.getItem();
            setChanged(level, pos, selfState);
        }

        boolean[] aboolean = entity.getPotionBits();

        if (!Arrays.equals(aboolean, entity.lastPotionCount)) {
            entity.lastPotionCount = aboolean;
            BlockState blockstate1 = selfState;

            if (!(selfState.getBlock() instanceof BrewingStandBlock)) {
                return;
            }

            for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; ++i) {
                blockstate1 = (BlockState) blockstate1.setValue(BrewingStandBlock.HAS_BOTTLE[i], aboolean[i]);
            }

            level.setBlock(pos, blockstate1, 2);
        }

    }

    private boolean[] getPotionBits() {
        boolean[] aboolean = new boolean[3];

        for (int i = 0; i < 3; ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                aboolean[i] = true;
            }
        }

        return aboolean;
    }

    private static boolean isBrewable(PotionBrewing potionBrewing, NonNullList<ItemStack> items) {
        ItemStack itemstack = items.get(3);

        if (itemstack.isEmpty()) {
            return false;
        } else if (!potionBrewing.isIngredient(itemstack)) {
            return false;
        } else {
            for (int i = 0; i < 3; ++i) {
                ItemStack itemstack1 = items.get(i);

                if (!itemstack1.isEmpty() && potionBrewing.hasMix(itemstack1, itemstack)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static void doBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
        ItemStack itemstack = items.get(3);
        PotionBrewing potionbrewing = level.potionBrewing();

        for (int i = 0; i < 3; ++i) {
            items.set(i, potionbrewing.mix(itemstack, items.get(i)));
        }

        itemstack.shrink(1);
        ItemStack itemstack1 = itemstack.getItem().getCraftingRemainder();

        if (!itemstack1.isEmpty()) {
            if (itemstack.isEmpty()) {
                itemstack = itemstack1;
            } else {
                Containers.dropItemStack(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), itemstack1);
            }
        }

        items.set(3, itemstack);
        level.levelEvent(1035, pos, 0);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.brewTime = input.getShortOr("BrewTime", (short) 0);
        if (this.brewTime > 0) {
            this.ingredient = ((ItemStack) this.items.get(3)).getItem();
        }

        this.fuel = input.getByteOr("Fuel", (byte) 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putShort("BrewTime", (short) this.brewTime);
        ContainerHelper.saveAllItems(output, this.items);
        output.putByte("Fuel", (byte) this.fuel);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        if (slot == 3) {
            PotionBrewing potionbrewing = this.level != null ? this.level.potionBrewing() : PotionBrewing.EMPTY;

            return potionbrewing.isIngredient(itemStack);
        } else {
            return slot == 4 ? itemStack.is(ItemTags.BREWING_FUEL) : (itemStack.is(Items.POTION) || itemStack.is(Items.SPLASH_POTION) || itemStack.is(Items.LINGERING_POTION) || itemStack.is(Items.GLASS_BOTTLE)) && this.getItem(slot).isEmpty();
        }
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.UP ? BrewingStandBlockEntity.SLOTS_FOR_UP : (direction == Direction.DOWN ? BrewingStandBlockEntity.SLOTS_FOR_DOWN : BrewingStandBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return slot == 3 ? itemStack.is(Items.GLASS_BOTTLE) : true;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BrewingStandMenu(containerId, inventory, this, this.dataAccess);
    }
}
