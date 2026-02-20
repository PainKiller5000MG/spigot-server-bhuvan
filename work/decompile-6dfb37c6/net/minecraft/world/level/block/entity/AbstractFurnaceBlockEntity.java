package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, StackedContentsCompatible, RecipeCraftingHolder {

    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);
    private static final short DEFAULT_COOKING_TIMER = 0;
    private static final short DEFAULT_COOKING_TOTAL_TIME = 0;
    private static final short DEFAULT_LIT_TIME_REMAINING = 0;
    private static final short DEFAULT_LIT_TOTAL_TIME = 0;
    protected NonNullList<ItemStack> items;
    public int litTimeRemaining;
    private int litTotalTime;
    public int cookingTimer;
    public int cookingTotalTime;
    protected final ContainerData dataAccess;
    public final Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> recipesUsed;
    private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    protected AbstractFurnaceBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState, RecipeType<? extends AbstractCookingRecipe> recipeType) {
        super(type, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(3, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int dataId) {
                switch (dataId) {
                    case 0:
                        return AbstractFurnaceBlockEntity.this.litTimeRemaining;
                    case 1:
                        return AbstractFurnaceBlockEntity.this.litTotalTime;
                    case 2:
                        return AbstractFurnaceBlockEntity.this.cookingTimer;
                    case 3:
                        return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int dataId, int value) {
                switch (dataId) {
                    case 0:
                        AbstractFurnaceBlockEntity.this.litTimeRemaining = value;
                        break;
                    case 1:
                        AbstractFurnaceBlockEntity.this.litTotalTime = value;
                        break;
                    case 2:
                        AbstractFurnaceBlockEntity.this.cookingTimer = value;
                        break;
                    case 3:
                        AbstractFurnaceBlockEntity.this.cookingTotalTime = value;
                }

            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        this.recipesUsed = new Reference2IntOpenHashMap();
        this.quickCheck = RecipeManager.<SingleRecipeInput, AbstractCookingRecipe>createCheck(recipeType);
    }

    private boolean isLit() {
        return this.litTimeRemaining > 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.cookingTimer = input.getShortOr("cooking_time_spent", (short) 0);
        this.cookingTotalTime = input.getShortOr("cooking_total_time", (short) 0);
        this.litTimeRemaining = input.getShortOr("lit_time_remaining", (short) 0);
        this.litTotalTime = input.getShortOr("lit_total_time", (short) 0);
        this.recipesUsed.clear();
        this.recipesUsed.putAll((Map) input.read("RecipesUsed", AbstractFurnaceBlockEntity.RECIPES_USED_CODEC).orElse(Map.of()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putShort("cooking_time_spent", (short) this.cookingTimer);
        output.putShort("cooking_total_time", (short) this.cookingTotalTime);
        output.putShort("lit_time_remaining", (short) this.litTimeRemaining);
        output.putShort("lit_total_time", (short) this.litTotalTime);
        ContainerHelper.saveAllItems(output, this.items);
        output.store("RecipesUsed", AbstractFurnaceBlockEntity.RECIPES_USED_CODEC, this.recipesUsed);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
        boolean flag = entity.isLit();
        boolean flag1 = false;

        if (entity.isLit()) {
            --entity.litTimeRemaining;
        }

        ItemStack itemstack = entity.items.get(1);
        ItemStack itemstack1 = entity.items.get(0);
        boolean flag2 = !itemstack1.isEmpty();
        boolean flag3 = !itemstack.isEmpty();

        if (entity.isLit() || flag3 && flag2) {
            SingleRecipeInput singlerecipeinput = new SingleRecipeInput(itemstack1);
            RecipeHolder<? extends AbstractCookingRecipe> recipeholder;

            if (flag2) {
                recipeholder = (RecipeHolder) entity.quickCheck.getRecipeFor(singlerecipeinput, level).orElse((Object) null);
            } else {
                recipeholder = null;
            }

            int i = entity.getMaxStackSize();

            if (!entity.isLit() && canBurn(level.registryAccess(), recipeholder, singlerecipeinput, entity.items, i)) {
                entity.litTimeRemaining = entity.getBurnDuration(level.fuelValues(), itemstack);
                entity.litTotalTime = entity.litTimeRemaining;
                if (entity.isLit()) {
                    flag1 = true;
                    if (flag3) {
                        Item item = itemstack.getItem();

                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            entity.items.set(1, item.getCraftingRemainder());
                        }
                    }
                }
            }

            if (entity.isLit() && canBurn(level.registryAccess(), recipeholder, singlerecipeinput, entity.items, i)) {
                ++entity.cookingTimer;
                if (entity.cookingTimer == entity.cookingTotalTime) {
                    entity.cookingTimer = 0;
                    entity.cookingTotalTime = getTotalCookTime(level, entity);
                    if (burn(level.registryAccess(), recipeholder, singlerecipeinput, entity.items, i)) {
                        entity.setRecipeUsed(recipeholder);
                    }

                    flag1 = true;
                }
            } else {
                entity.cookingTimer = 0;
            }
        } else if (!entity.isLit() && entity.cookingTimer > 0) {
            entity.cookingTimer = Mth.clamp(entity.cookingTimer - 2, 0, entity.cookingTotalTime);
        }

        if (flag != entity.isLit()) {
            flag1 = true;
            state = (BlockState) state.setValue(AbstractFurnaceBlock.LIT, entity.isLit());
            level.setBlock(pos, state, 3);
        }

        if (flag1) {
            setChanged(level, pos, state);
        }

    }

    private static boolean canBurn(RegistryAccess registryAccess, @Nullable RecipeHolder<? extends AbstractCookingRecipe> recipe, SingleRecipeInput input, NonNullList<ItemStack> items, int maxStackSize) {
        if (!((ItemStack) items.get(0)).isEmpty() && recipe != null) {
            ItemStack itemstack = ((AbstractCookingRecipe) recipe.value()).assemble(input, registryAccess);

            if (itemstack.isEmpty()) {
                return false;
            } else {
                ItemStack itemstack1 = items.get(2);

                return itemstack1.isEmpty() ? true : (!ItemStack.isSameItemSameComponents(itemstack1, itemstack) ? false : (itemstack1.getCount() < maxStackSize && itemstack1.getCount() < itemstack1.getMaxStackSize() ? true : itemstack1.getCount() < itemstack.getMaxStackSize()));
            }
        } else {
            return false;
        }
    }

    private static boolean burn(RegistryAccess registryAccess, @Nullable RecipeHolder<? extends AbstractCookingRecipe> recipe, SingleRecipeInput input, NonNullList<ItemStack> items, int maxStackSize) {
        if (recipe != null && canBurn(registryAccess, recipe, input, items, maxStackSize)) {
            ItemStack itemstack = items.get(0);
            ItemStack itemstack1 = ((AbstractCookingRecipe) recipe.value()).assemble(input, registryAccess);
            ItemStack itemstack2 = items.get(2);

            if (itemstack2.isEmpty()) {
                items.set(2, itemstack1.copy());
            } else if (ItemStack.isSameItemSameComponents(itemstack2, itemstack1)) {
                itemstack2.grow(1);
            }

            if (itemstack.is(Blocks.WET_SPONGE.asItem()) && !((ItemStack) items.get(1)).isEmpty() && ((ItemStack) items.get(1)).is(Items.BUCKET)) {
                items.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(FuelValues fuelValues, ItemStack itemStack) {
        return fuelValues.burnDuration(itemStack);
    }

    private static int getTotalCookTime(ServerLevel level, AbstractFurnaceBlockEntity entity) {
        SingleRecipeInput singlerecipeinput = new SingleRecipeInput(entity.getItem(0));

        return (Integer) entity.quickCheck.getRecipeFor(singlerecipeinput, level).map((recipeholder) -> {
            return ((AbstractCookingRecipe) recipeholder.value()).cookingTime();
        }).orElse(200);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? AbstractFurnaceBlockEntity.SLOTS_FOR_DOWN : (direction == Direction.UP ? AbstractFurnaceBlockEntity.SLOTS_FOR_UP : AbstractFurnaceBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return direction == Direction.DOWN && slot == 1 ? itemStack.is(Items.WATER_BUCKET) || itemStack.is(Items.BUCKET) : true;
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

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        ItemStack itemstack1 = this.items.get(slot);
        boolean flag = !itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemstack1, itemStack);

        this.items.set(slot, itemStack);
        itemStack.limitSize(this.getMaxStackSize(itemStack));
        if (slot == 0 && !flag) {
            Level level = this.level;

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                this.cookingTotalTime = getTotalCookTime(serverlevel, this);
                this.cookingTimer = 0;
                this.setChanged();
            }
        }

    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        if (slot == 2) {
            return false;
        } else if (slot != 1) {
            return true;
        } else {
            ItemStack itemstack1 = this.items.get(1);

            return this.level.fuelValues().isFuel(itemStack) || itemStack.is(Items.BUCKET) && !itemstack1.is(Items.BUCKET);
        }
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipeUsed) {
        if (recipeUsed != null) {
            ResourceKey<Recipe<?>> resourcekey = recipeUsed.id();

            this.recipesUsed.addTo(resourcekey, 1);
        }

    }

    @Override
    public @Nullable RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(Player player, List<ItemStack> itemStacks) {}

    public void awardUsedRecipesAndPopExperience(ServerPlayer player) {
        List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(player.level(), player.position());

        player.awardRecipes(list);

        for (RecipeHolder<?> recipeholder : list) {
            player.triggerRecipeCrafted(recipeholder, this.items);
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel level, Vec3 position) {
        List<RecipeHolder<?>> list = Lists.newArrayList();
        ObjectIterator objectiterator = this.recipesUsed.reference2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Reference2IntMap.Entry<ResourceKey<Recipe<?>>> reference2intmap_entry = (Entry) objectiterator.next();

            level.recipeAccess().byKey((ResourceKey) reference2intmap_entry.getKey()).ifPresent((recipeholder) -> {
                list.add(recipeholder);
                createExperience(level, position, reference2intmap_entry.getIntValue(), ((AbstractCookingRecipe) recipeholder.value()).experience());
            });
        }

        return list;
    }

    private static void createExperience(ServerLevel level, Vec3 position, int amount, float value) {
        int j = Mth.floor((float) amount * value);
        float f1 = Mth.frac((float) amount * value);

        if (f1 != 0.0F && level.random.nextFloat() < f1) {
            ++j;
        }

        ExperienceOrb.award(level, position, j);
    }

    @Override
    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemstack : this.items) {
            contents.accountStack(itemstack);
        }

    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;

        if (level instanceof ServerLevel serverlevel) {
            this.getRecipesToAwardAndPopExperience(serverlevel, Vec3.atCenterOf(pos));
        }

    }
}
