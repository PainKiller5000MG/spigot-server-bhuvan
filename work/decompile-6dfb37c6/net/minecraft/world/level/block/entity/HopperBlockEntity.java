package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper {

    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private static final int[][] CACHED_SLOTS = new int[54][];
    private static final int NO_COOLDOWN_TIME = -1;
    private static final Component DEFAULT_NAME = Component.translatable("container.hopper");
    private NonNullList<ItemStack> items;
    private int cooldownTime;
    private long tickedGameTime;
    private Direction facing;

    public HopperBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.HOPPER, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(5, ItemStack.EMPTY);
        this.cooldownTime = -1;
        this.facing = (Direction) blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }

        this.cooldownTime = input.getIntOr("TransferCooldown", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }

        output.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        this.unpackLootTable((Player) null);
        return ContainerHelper.removeItem(this.getItems(), slot, count);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        this.unpackLootTable((Player) null);
        this.getItems().set(slot, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
    }

    @Override
    public void setBlockState(BlockState blockState) {
        super.setBlockState(blockState);
        this.facing = (Direction) blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected Component getDefaultName() {
        return HopperBlockEntity.DEFAULT_NAME;
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity entity) {
        --entity.cooldownTime;
        entity.tickedGameTime = level.getGameTime();
        if (!entity.isOnCooldown()) {
            entity.setCooldown(0);
            tryMoveItems(level, pos, state, entity, () -> {
                return suckInItems(level, entity);
            });
        }

    }

    private static boolean tryMoveItems(Level level, BlockPos pos, BlockState state, HopperBlockEntity entity, BooleanSupplier action) {
        if (level.isClientSide()) {
            return false;
        } else {
            if (!entity.isOnCooldown() && (Boolean) state.getValue(HopperBlock.ENABLED)) {
                boolean flag = false;

                if (!entity.isEmpty()) {
                    flag = ejectItems(level, pos, entity);
                }

                if (!entity.inventoryFull()) {
                    flag |= action.getAsBoolean();
                }

                if (flag) {
                    entity.setCooldown(8);
                    setChanged(level, pos, state);
                    return true;
                }
            }

            return false;
        }
    }

    private boolean inventoryFull() {
        for (ItemStack itemstack : this.items) {
            if (itemstack.isEmpty() || itemstack.getCount() != itemstack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    private static boolean ejectItems(Level level, BlockPos blockPos, HopperBlockEntity self) {
        Container container = getAttachedContainer(level, blockPos, self);

        if (container == null) {
            return false;
        } else {
            Direction direction = self.facing.getOpposite();

            if (isFullContainer(container, direction)) {
                return false;
            } else {
                for (int i = 0; i < self.getContainerSize(); ++i) {
                    ItemStack itemstack = self.getItem(i);

                    if (!itemstack.isEmpty()) {
                        int j = itemstack.getCount();
                        ItemStack itemstack1 = addItem(self, container, self.removeItem(i, 1), direction);

                        if (itemstack1.isEmpty()) {
                            container.setChanged();
                            return true;
                        }

                        itemstack.setCount(j);
                        if (j == 1) {
                            self.setItem(i, itemstack);
                        }
                    }
                }

                return false;
            }
        }
    }

    private static int[] getSlots(Container container, Direction direction) {
        if (container instanceof WorldlyContainer worldlycontainer) {
            return worldlycontainer.getSlotsForFace(direction);
        } else {
            int i = container.getContainerSize();

            if (i < HopperBlockEntity.CACHED_SLOTS.length) {
                int[] aint = HopperBlockEntity.CACHED_SLOTS[i];

                if (aint != null) {
                    return aint;
                } else {
                    int[] aint1 = createFlatSlots(i);

                    HopperBlockEntity.CACHED_SLOTS[i] = aint1;
                    return aint1;
                }
            } else {
                return createFlatSlots(i);
            }
        }
    }

    private static int[] createFlatSlots(int containerSize) {
        int[] aint = new int[containerSize];

        for (int j = 0; j < aint.length; aint[j] = j++) {
            ;
        }

        return aint;
    }

    private static boolean isFullContainer(Container container, Direction direction) {
        int[] aint = getSlots(container, direction);

        for (int i : aint) {
            ItemStack itemstack = container.getItem(i);

            if (itemstack.getCount() < itemstack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    public static boolean suckInItems(Level level, Hopper hopper) {
        BlockPos blockpos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        BlockState blockstate = level.getBlockState(blockpos);
        Container container = getSourceContainer(level, hopper, blockpos, blockstate);

        if (container != null) {
            Direction direction = Direction.DOWN;

            for (int i : getSlots(container, direction)) {
                if (tryTakeInItemFromSlot(hopper, container, i, direction)) {
                    return true;
                }
            }

            return false;
        } else {
            boolean flag = hopper.isGridAligned() && blockstate.isCollisionShapeFullBlock(level, blockpos) && !blockstate.is(BlockTags.DOES_NOT_BLOCK_HOPPERS);

            if (!flag) {
                for (ItemEntity itementity : getItemsAtAndAbove(level, hopper)) {
                    if (addItem(hopper, itementity)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static boolean tryTakeInItemFromSlot(Hopper hopper, Container container, int slot, Direction direction) {
        ItemStack itemstack = container.getItem(slot);

        if (!itemstack.isEmpty() && canTakeItemFromContainer(hopper, container, itemstack, slot, direction)) {
            int j = itemstack.getCount();
            ItemStack itemstack1 = addItem(container, hopper, container.removeItem(slot, 1), (Direction) null);

            if (itemstack1.isEmpty()) {
                container.setChanged();
                return true;
            }

            itemstack.setCount(j);
            if (j == 1) {
                container.setItem(slot, itemstack);
            }
        }

        return false;
    }

    public static boolean addItem(Container container, ItemEntity entity) {
        boolean flag = false;
        ItemStack itemstack = entity.getItem().copy();
        ItemStack itemstack1 = addItem((Container) null, container, itemstack, (Direction) null);

        if (itemstack1.isEmpty()) {
            flag = true;
            entity.setItem(ItemStack.EMPTY);
            entity.discard();
        } else {
            entity.setItem(itemstack1);
        }

        return flag;
    }

    public static ItemStack addItem(@Nullable Container from, Container container, ItemStack itemStack, @Nullable Direction direction) {
        if (container instanceof WorldlyContainer worldlycontainer) {
            if (direction != null) {
                int[] aint = worldlycontainer.getSlotsForFace(direction);

                for (int i = 0; i < aint.length && !itemStack.isEmpty(); ++i) {
                    itemStack = tryMoveInItem(from, container, itemStack, aint[i], direction);
                }

                return itemStack;
            }
        }

        int j = container.getContainerSize();

        for (int k = 0; k < j && !itemStack.isEmpty(); ++k) {
            itemStack = tryMoveInItem(from, container, itemStack, k, direction);
        }

        return itemStack;
    }

    private static boolean canPlaceItemInContainer(Container container, ItemStack itemStack, int slot, @Nullable Direction direction) {
        if (!container.canPlaceItem(slot, itemStack)) {
            return false;
        } else {
            boolean flag;

            if (container instanceof WorldlyContainer) {
                WorldlyContainer worldlycontainer = (WorldlyContainer) container;

                if (!worldlycontainer.canPlaceItemThroughFace(slot, itemStack, direction)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static boolean canTakeItemFromContainer(Container into, Container from, ItemStack itemStack, int slot, Direction direction) {
        if (!from.canTakeItem(into, slot, itemStack)) {
            return false;
        } else {
            boolean flag;

            if (from instanceof WorldlyContainer) {
                WorldlyContainer worldlycontainer = (WorldlyContainer) from;

                if (!worldlycontainer.canTakeItemThroughFace(slot, itemStack, direction)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static ItemStack tryMoveInItem(@Nullable Container from, Container container, ItemStack itemStack, int slot, @Nullable Direction direction) {
        ItemStack itemstack1 = container.getItem(slot);

        if (canPlaceItemInContainer(container, itemStack, slot, direction)) {
            boolean flag = false;
            boolean flag1 = container.isEmpty();

            if (itemstack1.isEmpty()) {
                container.setItem(slot, itemStack);
                itemStack = ItemStack.EMPTY;
                flag = true;
            } else if (canMergeItems(itemstack1, itemStack)) {
                int j = itemStack.getMaxStackSize() - itemstack1.getCount();
                int k = Math.min(itemStack.getCount(), j);

                itemStack.shrink(k);
                itemstack1.grow(k);
                flag = k > 0;
            }

            if (flag) {
                if (flag1 && container instanceof HopperBlockEntity) {
                    HopperBlockEntity hopperblockentity = (HopperBlockEntity) container;

                    if (!hopperblockentity.isOnCustomCooldown()) {
                        int l = 0;

                        if (from instanceof HopperBlockEntity) {
                            HopperBlockEntity hopperblockentity1 = (HopperBlockEntity) from;

                            if (hopperblockentity.tickedGameTime >= hopperblockentity1.tickedGameTime) {
                                l = 1;
                            }
                        }

                        hopperblockentity.setCooldown(8 - l);
                    }
                }

                container.setChanged();
            }
        }

        return itemStack;
    }

    private static @Nullable Container getAttachedContainer(Level level, BlockPos blockPos, HopperBlockEntity self) {
        return getContainerAt(level, blockPos.relative(self.facing));
    }

    private static @Nullable Container getSourceContainer(Level level, Hopper hopper, BlockPos pos, BlockState state) {
        return getContainerAt(level, pos, state, hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
    }

    public static List<ItemEntity> getItemsAtAndAbove(Level level, Hopper hopper) {
        AABB aabb = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5D, hopper.getLevelY() - 0.5D, hopper.getLevelZ() - 0.5D);

        return level.<ItemEntity>getEntitiesOfClass(ItemEntity.class, aabb, EntitySelector.ENTITY_STILL_ALIVE);
    }

    public static @Nullable Container getContainerAt(Level level, BlockPos pos) {
        return getContainerAt(level, pos, level.getBlockState(pos), (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D);
    }

    private static @Nullable Container getContainerAt(Level level, BlockPos pos, BlockState state, double x, double y, double z) {
        Container container = getBlockContainer(level, pos, state);

        if (container == null) {
            container = getEntityContainer(level, x, y, z);
        }

        return container;
    }

    private static @Nullable Container getBlockContainer(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof WorldlyContainerHolder) {
            return ((WorldlyContainerHolder) block).getContainer(state, level, pos);
        } else {
            if (state.hasBlockEntity()) {
                BlockEntity blockentity = level.getBlockEntity(pos);

                if (blockentity instanceof Container) {
                    Container container = (Container) blockentity;

                    if (container instanceof ChestBlockEntity && block instanceof ChestBlock) {
                        container = ChestBlock.getContainer((ChestBlock) block, state, level, pos, true);
                    }

                    return container;
                }
            }

            return null;
        }
    }

    private static @Nullable Container getEntityContainer(Level level, double x, double y, double z) {
        List<Entity> list = level.getEntities((Entity) null, new AABB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D), EntitySelector.CONTAINER_ENTITY_SELECTOR);

        return !list.isEmpty() ? (Container) list.get(level.random.nextInt(list.size())) : null;
    }

    private static boolean canMergeItems(ItemStack a, ItemStack b) {
        return a.getCount() <= a.getMaxStackSize() && ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public double getLevelX() {
        return (double) this.worldPosition.getX() + 0.5D;
    }

    @Override
    public double getLevelY() {
        return (double) this.worldPosition.getY() + 0.5D;
    }

    @Override
    public double getLevelZ() {
        return (double) this.worldPosition.getZ() + 0.5D;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    private void setCooldown(int time) {
        this.cooldownTime = time;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    public static void entityInside(Level level, BlockPos pos, BlockState blockState, Entity entity, HopperBlockEntity hopper) {
        if (entity instanceof ItemEntity itementity) {
            if (!itementity.getItem().isEmpty() && entity.getBoundingBox().move((double) (-pos.getX()), (double) (-pos.getY()), (double) (-pos.getZ())).intersects(hopper.getSuckAabb())) {
                tryMoveItems(level, pos, blockState, hopper, () -> {
                    return addItem(hopper, itementity);
                });
            }
        }

    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new HopperMenu(containerId, inventory, this);
    }
}
