package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CrafterBlock extends BaseEntityBlock {

    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    private static final int MAX_CRAFTING_TICKS = 6;
    private static final int CRAFTING_TICK_DELAY = 4;
    private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);
    private static final int CRAFTER_ADVANCEMENT_DIAMETER = 17;

    public CrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(CrafterBlock.ORIENTATION, FrontAndTop.NORTH_UP)).setValue(CrafterBlock.TRIGGERED, false)).setValue(CrafterBlock.CRAFTING, false));
    }

    @Override
    protected MapCodec<CrafterBlock> codec() {
        return CrafterBlock.CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CrafterBlockEntity crafterblockentity) {
            return crafterblockentity.getRedstoneSignal();
        } else {
            return 0;
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag1 = level.hasNeighborSignal(pos);
        boolean flag2 = (Boolean) state.getValue(CrafterBlock.TRIGGERED);
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (flag1 && !flag2) {
            level.scheduleTick(pos, (Block) this, 4);
            level.setBlock(pos, (BlockState) state.setValue(CrafterBlock.TRIGGERED, true), 2);
            this.setBlockEntityTriggered(blockentity, true);
        } else if (!flag1 && flag2) {
            level.setBlock(pos, (BlockState) ((BlockState) state.setValue(CrafterBlock.TRIGGERED, false)).setValue(CrafterBlock.CRAFTING, false), 2);
            this.setBlockEntityTriggered(blockentity, false);
        }

    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.dispenseFrom(state, level, pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, BlockEntityType.CRAFTER, CrafterBlockEntity::serverTick);
    }

    private void setBlockEntityTriggered(@Nullable BlockEntity blockEntity, boolean triggered) {
        if (blockEntity instanceof CrafterBlockEntity crafterblockentity) {
            crafterblockentity.setTriggered(triggered);
        }

    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        CrafterBlockEntity crafterblockentity = new CrafterBlockEntity(worldPosition, blockState);

        crafterblockentity.setTriggered(blockState.hasProperty(CrafterBlock.TRIGGERED) && (Boolean) blockState.getValue(CrafterBlock.TRIGGERED));
        return crafterblockentity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection().getOpposite();
        Direction direction1;

        switch (direction) {
            case DOWN:
                direction1 = context.getHorizontalDirection().getOpposite();
                break;
            case UP:
                direction1 = context.getHorizontalDirection();
                break;
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                direction1 = Direction.UP;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Direction direction2 = direction1;

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(CrafterBlock.ORIENTATION, FrontAndTop.fromFrontAndTop(direction, direction2))).setValue(CrafterBlock.TRIGGERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if ((Boolean) state.getValue(CrafterBlock.TRIGGERED)) {
            level.scheduleTick(pos, (Block) this, 4);
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof CrafterBlockEntity) {
                CrafterBlockEntity crafterblockentity = (CrafterBlockEntity) blockentity;

                player.openMenu(crafterblockentity);
            }
        }

        return InteractionResult.SUCCESS;
    }

    protected void dispenseFrom(BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof CrafterBlockEntity crafterblockentity) {
            CraftingInput craftinginput = crafterblockentity.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = getPotentialResults(level, craftinginput);

            if (optional.isEmpty()) {
                level.levelEvent(1050, pos, 0);
            } else {
                RecipeHolder<CraftingRecipe> recipeholder = (RecipeHolder) optional.get();
                ItemStack itemstack = (recipeholder.value()).assemble(craftinginput, level.registryAccess());

                if (itemstack.isEmpty()) {
                    level.levelEvent(1050, pos, 0);
                } else {
                    crafterblockentity.setCraftingTicksRemaining(6);
                    level.setBlock(pos, (BlockState) state.setValue(CrafterBlock.CRAFTING, true), 2);
                    itemstack.onCraftedBySystem(level);
                    this.dispenseItem(level, pos, crafterblockentity, itemstack, state, recipeholder);

                    for (ItemStack itemstack1 : (recipeholder.value()).getRemainingItems(craftinginput)) {
                        if (!itemstack1.isEmpty()) {
                            this.dispenseItem(level, pos, crafterblockentity, itemstack1, state, recipeholder);
                        }
                    }

                    crafterblockentity.getItems().forEach((itemstack2) -> {
                        if (!itemstack2.isEmpty()) {
                            itemstack2.shrink(1);
                        }
                    });
                    crafterblockentity.setChanged();
                }
            }
        }
    }

    public static Optional<RecipeHolder<CraftingRecipe>> getPotentialResults(ServerLevel level, CraftingInput input) {
        return CrafterBlock.RECIPE_CACHE.get(level, input);
    }

    private void dispenseItem(ServerLevel level, BlockPos pos, CrafterBlockEntity blockEntity, ItemStack results, BlockState blockState, RecipeHolder<?> recipe) {
        Direction direction = ((FrontAndTop) blockState.getValue(CrafterBlock.ORIENTATION)).front();
        Container container = HopperBlockEntity.getContainerAt(level, pos.relative(direction));
        ItemStack itemstack1 = results.copy();

        if (container != null && (container instanceof CrafterBlockEntity || results.getCount() > container.getMaxStackSize(results))) {
            while (!itemstack1.isEmpty()) {
                ItemStack itemstack2 = itemstack1.copyWithCount(1);
                ItemStack itemstack3 = HopperBlockEntity.addItem(blockEntity, container, itemstack2, direction.getOpposite());

                if (!itemstack3.isEmpty()) {
                    break;
                }

                itemstack1.shrink(1);
            }
        } else if (container != null) {
            while (!itemstack1.isEmpty()) {
                int i = itemstack1.getCount();

                itemstack1 = HopperBlockEntity.addItem(blockEntity, container, itemstack1, direction.getOpposite());
                if (i == itemstack1.getCount()) {
                    break;
                }
            }
        }

        if (!itemstack1.isEmpty()) {
            Vec3 vec3 = Vec3.atCenterOf(pos);
            Vec3 vec31 = vec3.relative(direction, 0.7D);

            DefaultDispenseItemBehavior.spawnItem(level, itemstack1, 6, direction, vec31);

            for (ServerPlayer serverplayer : level.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(vec3, 17.0D, 17.0D, 17.0D))) {
                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(serverplayer, recipe.id(), blockEntity.getItems());
            }

            level.levelEvent(1049, pos, 0);
            level.levelEvent(2010, pos, direction.get3DDataValue());
        }

    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CrafterBlock.ORIENTATION, rotation.rotation().rotate((FrontAndTop) state.getValue(CrafterBlock.ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(CrafterBlock.ORIENTATION, mirror.rotation().rotate((FrontAndTop) state.getValue(CrafterBlock.ORIENTATION)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CrafterBlock.ORIENTATION, CrafterBlock.TRIGGERED, CrafterBlock.CRAFTING);
    }
}
