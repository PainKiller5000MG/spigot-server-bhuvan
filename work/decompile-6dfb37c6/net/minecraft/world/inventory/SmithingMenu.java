package net.minecraft.world.inventory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SmithingMenu extends ItemCombinerMenu {

    public static final int TEMPLATE_SLOT = 0;
    public static final int BASE_SLOT = 1;
    public static final int ADDITIONAL_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    public static final int TEMPLATE_SLOT_X_PLACEMENT = 8;
    public static final int BASE_SLOT_X_PLACEMENT = 26;
    public static final int ADDITIONAL_SLOT_X_PLACEMENT = 44;
    private static final int RESULT_SLOT_X_PLACEMENT = 98;
    public static final int SLOT_Y_PLACEMENT = 48;
    private final Level level;
    private final RecipePropertySet baseItemTest;
    private final RecipePropertySet templateItemTest;
    private final RecipePropertySet additionItemTest;
    private final DataSlot hasRecipeError;

    public SmithingMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public SmithingMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        this(containerId, inventory, access, inventory.player.level());
    }

    private SmithingMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Level level) {
        super(MenuType.SMITHING, containerId, inventory, access, createInputSlotDefinitions(level.recipeAccess()));
        this.hasRecipeError = DataSlot.standalone();
        this.level = level;
        this.baseItemTest = level.recipeAccess().propertySet(RecipePropertySet.SMITHING_BASE);
        this.templateItemTest = level.recipeAccess().propertySet(RecipePropertySet.SMITHING_TEMPLATE);
        this.additionItemTest = level.recipeAccess().propertySet(RecipePropertySet.SMITHING_ADDITION);
        this.addDataSlot(this.hasRecipeError).set(0);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions(RecipeAccess recipes) {
        RecipePropertySet recipepropertyset = recipes.propertySet(RecipePropertySet.SMITHING_BASE);
        RecipePropertySet recipepropertyset1 = recipes.propertySet(RecipePropertySet.SMITHING_TEMPLATE);
        RecipePropertySet recipepropertyset2 = recipes.propertySet(RecipePropertySet.SMITHING_ADDITION);
        ItemCombinerMenuSlotDefinition.Builder itemcombinermenuslotdefinition_builder = ItemCombinerMenuSlotDefinition.create();

        Objects.requireNonNull(recipepropertyset1);
        itemcombinermenuslotdefinition_builder = itemcombinermenuslotdefinition_builder.withSlot(0, 8, 48, recipepropertyset1::test);
        Objects.requireNonNull(recipepropertyset);
        itemcombinermenuslotdefinition_builder = itemcombinermenuslotdefinition_builder.withSlot(1, 26, 48, recipepropertyset::test);
        Objects.requireNonNull(recipepropertyset2);
        return itemcombinermenuslotdefinition_builder.withSlot(2, 44, 48, recipepropertyset2::test).withResultSlot(3, 98, 48).build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(Blocks.SMITHING_TABLE);
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        carried.onCraftedBy(player, carried.getCount());
        this.resultSlots.awardUsedRecipes(player, this.getRelevantItems());
        this.shrinkStackInSlot(0);
        this.shrinkStackInSlot(1);
        this.shrinkStackInSlot(2);
        this.access.execute((level, blockpos) -> {
            level.levelEvent(1044, blockpos, 0);
        });
    }

    private List<ItemStack> getRelevantItems() {
        return List.of(this.inputSlots.getItem(0), this.inputSlots.getItem(1), this.inputSlots.getItem(2));
    }

    private SmithingRecipeInput createRecipeInput() {
        return new SmithingRecipeInput(this.inputSlots.getItem(0), this.inputSlots.getItem(1), this.inputSlots.getItem(2));
    }

    private void shrinkStackInSlot(int slot) {
        ItemStack itemstack = this.inputSlots.getItem(slot);

        if (!itemstack.isEmpty()) {
            itemstack.shrink(1);
            this.inputSlots.setItem(slot, itemstack);
        }

    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (this.level instanceof ServerLevel) {
            boolean flag = this.getSlot(0).hasItem() && this.getSlot(1).hasItem() && this.getSlot(2).hasItem() && !this.getSlot(this.getResultSlot()).hasItem();

            this.hasRecipeError.set(flag ? 1 : 0);
        }

    }

    @Override
    public void createResult() {
        SmithingRecipeInput smithingrecipeinput = this.createRecipeInput();
        Level level = this.level;
        Optional<RecipeHolder<SmithingRecipe>> optional;

        if (level instanceof ServerLevel serverlevel) {
            optional = serverlevel.recipeAccess().getRecipeFor(RecipeType.SMITHING, smithingrecipeinput, serverlevel);
        } else {
            optional = Optional.empty();
        }

        optional.ifPresentOrElse((recipeholder) -> {
            ItemStack itemstack = ((SmithingRecipe) recipeholder.value()).assemble(smithingrecipeinput, this.level.registryAccess());

            this.resultSlots.setRecipeUsed(recipeholder);
            this.resultSlots.setItem(0, itemstack);
        }, () -> {
            this.resultSlots.setRecipeUsed((RecipeHolder) null);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        });
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canMoveIntoInputSlots(ItemStack stack) {
        return this.templateItemTest.test(stack) && !this.getSlot(0).hasItem() ? true : (this.baseItemTest.test(stack) && !this.getSlot(1).hasItem() ? true : this.additionItemTest.test(stack) && !this.getSlot(2).hasItem());
    }

    public boolean hasRecipeError() {
        return this.hasRecipeError.get() > 0;
    }
}
