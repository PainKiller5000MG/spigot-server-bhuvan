package net.minecraft.world.item;

import java.util.Map;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public class BlockItem extends Item {

    /** @deprecated */
    @Deprecated
    private final Block block;

    public BlockItem(Block block, Item.Properties properties) {
        super(properties);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult interactionresult = this.place(new BlockPlaceContext(context));

        return !interactionresult.consumesAction() && context.getItemInHand().has(DataComponents.CONSUMABLE) ? super.use(context.getLevel(), context.getPlayer(), context.getHand()) : interactionresult;
    }

    public InteractionResult place(BlockPlaceContext placeContext) {
        if (!this.getBlock().isEnabled(placeContext.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!placeContext.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockplacecontext1 = this.updatePlacementContext(placeContext);

            if (blockplacecontext1 == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState blockstate = this.getPlacementState(blockplacecontext1);

                if (blockstate == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockplacecontext1, blockstate)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos blockpos = blockplacecontext1.getClickedPos();
                    Level level = blockplacecontext1.getLevel();
                    Player player = blockplacecontext1.getPlayer();
                    ItemStack itemstack = blockplacecontext1.getItemInHand();
                    BlockState blockstate1 = level.getBlockState(blockpos);

                    if (blockstate1.is(blockstate.getBlock())) {
                        blockstate1 = this.updateBlockStateFromTag(blockpos, level, itemstack, blockstate1);
                        this.updateCustomBlockEntityTag(blockpos, level, player, itemstack, blockstate1);
                        updateBlockEntityComponents(level, blockpos, itemstack);
                        blockstate1.getBlock().setPlacedBy(level, blockpos, blockstate1, player, itemstack);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) player, blockpos, itemstack);
                        }
                    }

                    SoundType soundtype = blockstate1.getSoundType();

                    level.playSound(player, blockpos, this.getPlaceSound(blockstate1), SoundSource.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F);
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(player, blockstate1));
                    itemstack.consume(1, player);
                    return InteractionResult.SUCCESS;
                }
            }
        }
    }

    protected SoundEvent getPlaceSound(BlockState blockState) {
        return blockState.getSoundType().getPlaceSound();
    }

    public @Nullable BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return context;
    }

    private static void updateBlockEntityComponents(Level level, BlockPos pos, ItemStack itemStack) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity != null) {
            blockentity.applyComponentsFromItemStack(itemStack);
            blockentity.setChanged();
        }

    }

    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack itemStack, BlockState placedState) {
        return updateCustomBlockEntityTag(level, player, pos, itemStack);
    }

    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        BlockState blockstate = this.getBlock().getStateForPlacement(context);

        return blockstate != null && this.canPlace(context, blockstate) ? blockstate : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos pos, Level level, ItemStack itemStack, BlockState placedState) {
        BlockItemStateProperties blockitemstateproperties = (BlockItemStateProperties) itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);

        if (blockitemstateproperties.isEmpty()) {
            return placedState;
        } else {
            BlockState blockstate1 = blockitemstateproperties.apply(placedState);

            if (blockstate1 != placedState) {
                level.setBlock(pos, blockstate1, 2);
            }

            return blockstate1;
        }
    }

    protected boolean canPlace(BlockPlaceContext context, BlockState stateForPlacement) {
        Player player = context.getPlayer();

        return (!this.mustSurvive() || stateForPlacement.canSurvive(context.getLevel(), context.getClickedPos())) && context.getLevel().isUnobstructed(stateForPlacement, context.getClickedPos(), CollisionContext.placementContext(player));
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState placementState) {
        return context.getLevel().setBlock(context.getClickedPos(), placementState, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level level, @Nullable Player player, BlockPos pos, ItemStack itemStack) {
        if (level.isClientSide()) {
            return false;
        } else {
            TypedEntityData<BlockEntityType<?>> typedentitydata = (TypedEntityData) itemStack.get(DataComponents.BLOCK_ENTITY_DATA);

            if (typedentitydata != null) {
                BlockEntity blockentity = level.getBlockEntity(pos);

                if (blockentity != null) {
                    BlockEntityType<?> blockentitytype = blockentity.getType();

                    if (blockentitytype != typedentitydata.type()) {
                        return false;
                    }

                    if (!blockentitytype.onlyOpCanSetNbt() || player != null && player.canUseGameMasterBlocks()) {
                        return typedentitydata.loadInto(blockentity, level.registryAccess());
                    }

                    return false;
                }
            }

            return false;
        }
    }

    @Override
    public boolean shouldPrintOpWarning(ItemStack stack, @Nullable Player player) {
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            TypedEntityData<BlockEntityType<?>> typedentitydata = (TypedEntityData) stack.get(DataComponents.BLOCK_ENTITY_DATA);

            if (typedentitydata != null) {
                return ((BlockEntityType) typedentitydata.type()).onlyOpCanSetNbt();
            }
        }

        return false;
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> map, Item item) {
        map.put(this.getBlock(), item);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.getBlock() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemContainerContents itemcontainercontents = (ItemContainerContents) entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        if (itemcontainercontents != null) {
            ItemUtils.onContainerDestroyed(entity, itemcontainercontents.nonEmptyItemsCopy());
        }

    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> type, TagValueOutput output) {
        output.discard("id");
        if (output.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            BlockEntity.addEntityType(output, type);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, output.buildResult()));
        }

    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
