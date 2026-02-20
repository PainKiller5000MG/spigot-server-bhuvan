package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SideChainPart;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ShelfBlock extends BaseEntityBlock implements SelectableSlotContainer, SideChainPartBlock, SimpleWaterloggedBlock {

    public static final MapCodec<ShelfBlock> CODEC = simpleCodec(ShelfBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<SideChainPart> SIDE_CHAIN_PART = BlockStateProperties.SIDE_CHAIN_PART;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(Block.box(0.0D, 12.0D, 11.0D, 16.0D, 16.0D, 13.0D), Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D), Block.box(0.0D, 0.0D, 11.0D, 16.0D, 4.0D, 13.0D)));

    @Override
    public MapCodec<ShelfBlock> codec() {
        return ShelfBlock.CODEC;
    }

    public ShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ShelfBlock.FACING, Direction.NORTH)).setValue(ShelfBlock.POWERED, false)).setValue(ShelfBlock.SIDE_CHAIN_PART, SideChainPart.UNCONNECTED)).setValue(ShelfBlock.WATERLOGGED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) ShelfBlock.SHAPES.get(state.getValue(ShelfBlock.FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.WATER && state.getFluidState().is(FluidTags.WATER);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ShelfBlockEntity(worldPosition, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShelfBlock.FACING, ShelfBlock.POWERED, ShelfBlock.SIDE_CHAIN_PART, ShelfBlock.WATERLOGGED);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
        this.updateNeighborsAfterPoweringDown(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean flag1 = level.hasNeighborSignal(pos);

            if ((Boolean) state.getValue(ShelfBlock.POWERED) != flag1) {
                BlockState blockstate1 = (BlockState) state.setValue(ShelfBlock.POWERED, flag1);

                if (!flag1) {
                    blockstate1 = (BlockState) blockstate1.setValue(ShelfBlock.SIDE_CHAIN_PART, SideChainPart.UNCONNECTED);
                }

                level.setBlock(pos, blockstate1, 3);
                this.playSound(level, pos, flag1 ? SoundEvents.SHELF_ACTIVATE : SoundEvents.SHELF_DEACTIVATE);
                level.gameEvent(flag1 ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(blockstate1));
            }

        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(ShelfBlock.FACING, context.getHorizontalDirection().getOpposite())).setValue(ShelfBlock.POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))).setValue(ShelfBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ShelfBlock.FACING, rotation.rotate((Direction) state.getValue(ShelfBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ShelfBlock.FACING)));
    }

    @Override
    public int getRows() {
        return 1;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ShelfBlockEntity shelfblockentity) {
            if (!hand.equals(InteractionHand.OFF_HAND)) {
                OptionalInt optionalint = this.getHitSlot(hitResult, (Direction) state.getValue(ShelfBlock.FACING));

                if (optionalint.isEmpty()) {
                    return InteractionResult.PASS;
                }

                Inventory inventory = player.getInventory();

                if (level.isClientSide()) {
                    return (InteractionResult) (inventory.getSelectedItem().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS);
                }

                if (!(Boolean) state.getValue(ShelfBlock.POWERED)) {
                    boolean flag = swapSingleItem(itemStack, player, shelfblockentity, optionalint.getAsInt(), inventory);

                    if (flag) {
                        this.playSound(level, pos, itemStack.isEmpty() ? SoundEvents.SHELF_TAKE_ITEM : SoundEvents.SHELF_SINGLE_SWAP);
                    } else {
                        if (itemStack.isEmpty()) {
                            return InteractionResult.PASS;
                        }

                        this.playSound(level, pos, SoundEvents.SHELF_PLACE_ITEM);
                    }

                    return InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
                }

                ItemStack itemstack1 = inventory.getSelectedItem();
                boolean flag1 = this.swapHotbar(level, pos, inventory);

                if (!flag1) {
                    return InteractionResult.CONSUME;
                }

                this.playSound(level, pos, SoundEvents.SHELF_MULTI_SWAP);
                if (itemstack1 == inventory.getSelectedItem()) {
                    return InteractionResult.SUCCESS;
                }

                return InteractionResult.SUCCESS.heldItemTransformedTo(inventory.getSelectedItem());
            }
        }

        return InteractionResult.PASS;
    }

    private static boolean swapSingleItem(ItemStack itemStack, Player player, ShelfBlockEntity shelfBlockEntity, int hitSlot, Inventory inventory) {
        ItemStack itemstack1 = shelfBlockEntity.swapItemNoUpdate(hitSlot, itemStack);
        ItemStack itemstack2 = player.hasInfiniteMaterials() && itemstack1.isEmpty() ? itemStack.copy() : itemstack1;

        inventory.setItem(inventory.getSelectedSlot(), itemstack2);
        inventory.setChanged();
        shelfBlockEntity.setChanged(itemstack2.has(DataComponents.USE_EFFECTS) && !((UseEffects) itemstack2.get(DataComponents.USE_EFFECTS)).interactVibrations() ? null : GameEvent.ITEM_INTERACT_FINISH);
        return !itemstack1.isEmpty();
    }

    private boolean swapHotbar(Level level, BlockPos pos, Inventory inventory) {
        List<BlockPos> list = this.getAllBlocksConnectedTo(level, pos);

        if (list.isEmpty()) {
            return false;
        } else {
            boolean flag = false;

            for (int i = 0; i < list.size(); ++i) {
                ShelfBlockEntity shelfblockentity = (ShelfBlockEntity) level.getBlockEntity((BlockPos) list.get(i));

                if (shelfblockentity != null) {
                    for (int j = 0; j < shelfblockentity.getContainerSize(); ++j) {
                        int k = 9 - (list.size() - i) * shelfblockentity.getContainerSize() + j;

                        if (k >= 0 && k <= inventory.getContainerSize()) {
                            ItemStack itemstack = inventory.removeItemNoUpdate(k);
                            ItemStack itemstack1 = shelfblockentity.swapItemNoUpdate(j, itemstack);

                            if (!itemstack.isEmpty() || !itemstack1.isEmpty()) {
                                inventory.setItem(k, itemstack1);
                                flag = true;
                            }
                        }
                    }

                    inventory.setChanged();
                    shelfblockentity.setChanged(GameEvent.ENTITY_INTERACT);
                }
            }

            return flag;
        }
    }

    @Override
    public SideChainPart getSideChainPart(BlockState state) {
        return (SideChainPart) state.getValue(ShelfBlock.SIDE_CHAIN_PART);
    }

    @Override
    public BlockState setSideChainPart(BlockState state, SideChainPart newPart) {
        return (BlockState) state.setValue(ShelfBlock.SIDE_CHAIN_PART, newPart);
    }

    @Override
    public Direction getFacing(BlockState state) {
        return (Direction) state.getValue(ShelfBlock.FACING);
    }

    @Override
    public boolean isConnectable(BlockState state) {
        return state.is(BlockTags.WOODEN_SHELVES) && state.hasProperty(ShelfBlock.POWERED) && (Boolean) state.getValue(ShelfBlock.POWERED);
    }

    @Override
    public int getMaxChainLength() {
        return 3;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if ((Boolean) state.getValue(ShelfBlock.POWERED)) {
            this.updateSelfAndNeighborsOnPoweringUp(level, pos, state, oldState);
        } else {
            this.updateNeighborsAfterPoweringDown(level, pos, state);
        }

    }

    private void playSound(LevelAccessor level, BlockPos pos, SoundEvent sound) {
        level.playSound((Entity) null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(ShelfBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(ShelfBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide()) {
            return 0;
        } else if (direction != ((Direction) state.getValue(ShelfBlock.FACING)).getOpposite()) {
            return 0;
        } else {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof ShelfBlockEntity) {
                ShelfBlockEntity shelfblockentity = (ShelfBlockEntity) blockentity;
                int i = shelfblockentity.getItem(0).isEmpty() ? 0 : 1;
                int j = shelfblockentity.getItem(1).isEmpty() ? 0 : 1;
                int k = shelfblockentity.getItem(2).isEmpty() ? 0 : 1;

                return i | j << 1 | k << 2;
            } else {
                return 0;
            }
        }
    }
}
