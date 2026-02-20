package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CampfireBlockEntity extends BlockEntity implements Clearable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BURN_COOL_SPEED = 2;
    private static final int NUM_SLOTS = 4;
    private final NonNullList<ItemStack> items;
    public final int[] cookingProgress;
    public final int[] cookingTime;

    public CampfireBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityType.CAMPFIRE, worldPosition, blockState);
        this.items = NonNullList.<ItemStack>withSize(4, ItemStack.EMPTY);
        this.cookingProgress = new int[4];
        this.cookingTime = new int[4];
    }

    public static void cookTick(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity entity, RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache) {
        boolean flag = false;

        for (int i = 0; i < entity.items.size(); ++i) {
            ItemStack itemstack = entity.items.get(i);

            if (!itemstack.isEmpty()) {
                flag = true;
                int j = entity.cookingProgress[i]++;

                if (entity.cookingProgress[i] >= entity.cookingTime[i]) {
                    SingleRecipeInput singlerecipeinput = new SingleRecipeInput(itemstack);
                    ItemStack itemstack1 = (ItemStack) recipeCache.getRecipeFor(singlerecipeinput, level).map((recipeholder) -> {
                        return ((CampfireCookingRecipe) recipeholder.value()).assemble(singlerecipeinput, level.registryAccess());
                    }).orElse(itemstack);

                    if (itemstack1.isItemEnabled(level.enabledFeatures())) {
                        Containers.dropItemStack(level, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), itemstack1);
                        entity.items.set(i, ItemStack.EMPTY);
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                    }
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }

    }

    public static void cooldownTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity) {
        boolean flag = false;

        for (int i = 0; i < entity.items.size(); ++i) {
            if (entity.cookingProgress[i] > 0) {
                flag = true;
                entity.cookingProgress[i] = Mth.clamp(entity.cookingProgress[i] - 2, 0, entity.cookingTime[i]);
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }

    }

    public static void particleTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity) {
        RandomSource randomsource = level.random;

        if (randomsource.nextFloat() < 0.11F) {
            for (int i = 0; i < randomsource.nextInt(2) + 2; ++i) {
                CampfireBlock.makeParticles(level, pos, (Boolean) state.getValue(CampfireBlock.SIGNAL_FIRE), false);
            }
        }

        int j = ((Direction) state.getValue(CampfireBlock.FACING)).get2DDataValue();

        for (int k = 0; k < entity.items.size(); ++k) {
            if (!((ItemStack) entity.items.get(k)).isEmpty() && randomsource.nextFloat() < 0.2F) {
                Direction direction = Direction.from2DDataValue(Math.floorMod(k + j, 4));
                float f = 0.3125F;
                double d0 = (double) pos.getX() + 0.5D - (double) ((float) direction.getStepX() * 0.3125F) + (double) ((float) direction.getClockWise().getStepX() * 0.3125F);
                double d1 = (double) pos.getY() + 0.5D;
                double d2 = (double) pos.getZ() + 0.5D - (double) ((float) direction.getStepZ() * 0.3125F) + (double) ((float) direction.getClockWise().getStepZ() * 0.3125F);

                for (int l = 0; l < 4; ++l) {
                    level.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 5.0E-4D, 0.0D);
                }
            }
        }

    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        input.getIntArray("CookingTimes").ifPresentOrElse((aint) -> {
            System.arraycopy(aint, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, aint.length));
        }, () -> {
            Arrays.fill(this.cookingProgress, 0);
        });
        input.getIntArray("CookingTotalTimes").ifPresentOrElse((aint) -> {
            System.arraycopy(aint, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, aint.length));
        }, () -> {
            Arrays.fill(this.cookingTime, 0);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putIntArray("CookingTimes", this.cookingProgress);
        output.putIntArray("CookingTotalTimes", this.cookingTime);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(this.problemPath(), CampfireBlockEntity.LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector, registries);

            ContainerHelper.saveAllItems(tagvalueoutput, this.items, true);
            return tagvalueoutput.buildResult();
        }
    }

    public boolean placeFood(ServerLevel serverLevel, @Nullable LivingEntity sourceEntity, ItemStack placeItem) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.items.get(i);

            if (itemstack1.isEmpty()) {
                Optional<RecipeHolder<CampfireCookingRecipe>> optional = serverLevel.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(placeItem), serverLevel);

                if (optional.isEmpty()) {
                    return false;
                }

                this.cookingTime[i] = ((CampfireCookingRecipe) ((RecipeHolder) optional.get()).value()).cookingTime();
                this.cookingProgress[i] = 0;
                this.items.set(i, placeItem.consumeAndReturn(1, sourceEntity));
                serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(sourceEntity, this.getBlockState()));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.getItems());
        }

    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ((ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }
}
