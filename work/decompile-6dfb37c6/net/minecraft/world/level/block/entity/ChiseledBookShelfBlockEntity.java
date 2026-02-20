package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements ListBackedContainer {

    public static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_LAST_INTERACTED_SLOT = -1;
    private final NonNullList<ItemStack> items;
    public int lastInteractedSlot;

    public ChiseledBookShelfBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.CHISELED_BOOKSHELF, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(6, ItemStack.EMPTY);
        this.lastInteractedSlot = -1;
    }

    private void updateState(int interactedSlot) {
        if (interactedSlot >= 0 && interactedSlot < 6) {
            this.lastInteractedSlot = interactedSlot;
            BlockState blockstate = this.getBlockState();

            for (int j = 0; j < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); ++j) {
                boolean flag = !this.getItem(j).isEmpty();
                BooleanProperty booleanproperty = (BooleanProperty) ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(j);

                blockstate = (BlockState) blockstate.setValue(booleanproperty, flag);
            }

            ((Level) Objects.requireNonNull(this.level)).setBlock(this.worldPosition, blockstate, 3);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, GameEvent.Context.of(blockstate));
        } else {
            ChiseledBookShelfBlockEntity.LOGGER.error("Expected slot 0-5, got {}", interactedSlot);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.lastInteractedSlot = input.getIntOr("last_interacted_slot", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putInt("last_interacted_slot", this.lastInteractedSlot);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean acceptsItemType(ItemStack itemStack) {
        return itemStack.is(ItemTags.BOOKSHELF_BOOKS);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack itemstack = (ItemStack) Objects.requireNonNullElse((ItemStack) this.getItems().get(slot), ItemStack.EMPTY);

        this.getItems().set(slot, ItemStack.EMPTY);
        if (!itemstack.isEmpty()) {
            this.updateState(slot);
        }

        return itemstack;
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        if (this.acceptsItemType(itemStack)) {
            this.getItems().set(slot, itemStack);
            this.updateState(slot);
        } else if (itemStack.isEmpty()) {
            this.removeItem(slot, this.getMaxStackSize());
        }

    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack itemStack) {
        return into.hasAnyMatching((itemstack1) -> {
            return itemstack1.isEmpty() ? true : ItemStack.isSameItemSameComponents(itemStack, itemstack1) && itemstack1.getCount() + itemStack.getCount() <= into.getMaxStackSize(itemstack1);
        });
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    public int getLastInteractedSlot() {
        return this.lastInteractedSlot;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ((ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyInto(this.items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }
}
