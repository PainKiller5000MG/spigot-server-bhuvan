package net.minecraft.world.inventory;

import java.util.List;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class StonecutterMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END = 38;
    private final ContainerLevelAccess access;
    private final DataSlot selectedRecipeIndex;
    private final Level level;
    private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipesForInput;
    private ItemStack input;
    private long lastSoundTime;
    final Slot inputSlot;
    final Slot resultSlot;
    private Runnable slotUpdateListener;
    public final Container container;
    private final ResultContainer resultContainer;

    public StonecutterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public StonecutterMenu(int containerId, Inventory inventory, final ContainerLevelAccess access) {
        super(MenuType.STONECUTTER, containerId);
        this.selectedRecipeIndex = DataSlot.standalone();
        this.recipesForInput = SelectableRecipe.SingleInputSet.<StonecutterRecipe>empty();
        this.input = ItemStack.EMPTY;
        this.slotUpdateListener = () -> {
        };
        this.container = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                StonecutterMenu.this.slotsChanged(this);
                StonecutterMenu.this.slotUpdateListener.run();
            }
        };
        this.resultContainer = new ResultContainer();
        this.access = access;
        this.level = inventory.player.level();
        this.inputSlot = this.addSlot(new Slot(this.container, 0, 20, 33));
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack carried) {
                carried.onCraftedBy(player, carried.getCount());
                StonecutterMenu.this.resultContainer.awardUsedRecipes(player, this.getRelevantItems());
                ItemStack itemstack1 = StonecutterMenu.this.inputSlot.remove(1);

                if (!itemstack1.isEmpty()) {
                    StonecutterMenu.this.setupResultSlot(StonecutterMenu.this.selectedRecipeIndex.get());
                }

                access.execute((level, blockpos) -> {
                    long j = level.getGameTime();

                    if (StonecutterMenu.this.lastSoundTime != j) {
                        level.playSound((Entity) null, blockpos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        StonecutterMenu.this.lastSoundTime = j;
                    }

                });
                super.onTake(player, carried);
            }

            private List<ItemStack> getRelevantItems() {
                return List.of(StonecutterMenu.this.inputSlot.getItem());
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.selectedRecipeIndex);
    }

    public int getSelectedRecipeIndex() {
        return this.selectedRecipeIndex.get();
    }

    public SelectableRecipe.SingleInputSet<StonecutterRecipe> getVisibleRecipes() {
        return this.recipesForInput;
    }

    public int getNumberOfVisibleRecipes() {
        return this.recipesForInput.size();
    }

    public boolean hasInputItem() {
        return this.inputSlot.hasItem() && !this.recipesForInput.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.STONECUTTER);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (this.selectedRecipeIndex.get() == buttonId) {
            return false;
        } else {
            if (this.isValidRecipeIndex(buttonId)) {
                this.selectedRecipeIndex.set(buttonId);
                this.setupResultSlot(buttonId);
            }

            return true;
        }
    }

    private boolean isValidRecipeIndex(int buttonId) {
        return buttonId >= 0 && buttonId < this.recipesForInput.size();
    }

    @Override
    public void slotsChanged(Container container) {
        ItemStack itemstack = this.inputSlot.getItem();

        if (!itemstack.is(this.input.getItem())) {
            this.input = itemstack.copy();
            this.setupRecipeList(itemstack);
        }

    }

    private void setupRecipeList(ItemStack item) {
        this.selectedRecipeIndex.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        if (!item.isEmpty()) {
            this.recipesForInput = this.level.recipeAccess().stonecutterRecipes().selectByInput(item);
        } else {
            this.recipesForInput = SelectableRecipe.SingleInputSet.<StonecutterRecipe>empty();
        }

    }

    private void setupResultSlot(int index) {
        Optional<RecipeHolder<StonecutterRecipe>> optional;

        if (!this.recipesForInput.isEmpty() && this.isValidRecipeIndex(index)) {
            SelectableRecipe.SingleInputEntry<StonecutterRecipe> selectablerecipe_singleinputentry = (SelectableRecipe.SingleInputEntry) this.recipesForInput.entries().get(index);

            optional = selectablerecipe_singleinputentry.recipe().recipe();
        } else {
            optional = Optional.empty();
        }

        optional.ifPresentOrElse((recipeholder) -> {
            this.resultContainer.setRecipeUsed(recipeholder);
            this.resultSlot.set(((StonecutterRecipe) recipeholder.value()).assemble(new SingleRecipeInput(this.container.getItem(0)), this.level.registryAccess()));
        }, () -> {
            this.resultSlot.set(ItemStack.EMPTY);
            this.resultContainer.setRecipeUsed((RecipeHolder) null);
        });
        this.broadcastChanges();
    }

    @Override
    public MenuType<?> getType() {
        return MenuType.STONECUTTER;
    }

    public void registerUpdateListener(Runnable slotUpdateListener) {
        this.slotUpdateListener = slotUpdateListener;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.resultContainer && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            Item item = itemstack1.getItem();

            itemstack = itemstack1.copy();
            if (slotIndex == 1) {
                item.onCraftedBy(itemstack1, player);
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (slotIndex == 0) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.level.recipeAccess().stonecutterRecipes().acceptsInput(itemstack1)) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 2 && slotIndex < 29) {
                if (!this.moveItemStackTo(itemstack1, 29, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 29 && slotIndex < 38 && !this.moveItemStackTo(itemstack1, 2, 29, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
            if (slotIndex == 1) {
                player.drop(itemstack1, false);
            }

            this.broadcastChanges();
        }

        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(1);
        this.access.execute((level, blockpos) -> {
            this.clearContainer(player, this.container);
        });
    }
}
