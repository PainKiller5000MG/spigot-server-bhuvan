package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class ChiseledBookShelfBlock extends BaseEntityBlock implements SelectableSlotContainer {

    public static final MapCodec<ChiseledBookShelfBlock> CODEC = simpleCodec(ChiseledBookShelfBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty SLOT_0_OCCUPIED = BlockStateProperties.SLOT_0_OCCUPIED;
    public static final BooleanProperty SLOT_1_OCCUPIED = BlockStateProperties.SLOT_1_OCCUPIED;
    public static final BooleanProperty SLOT_2_OCCUPIED = BlockStateProperties.SLOT_2_OCCUPIED;
    public static final BooleanProperty SLOT_3_OCCUPIED = BlockStateProperties.SLOT_3_OCCUPIED;
    public static final BooleanProperty SLOT_4_OCCUPIED = BlockStateProperties.SLOT_4_OCCUPIED;
    public static final BooleanProperty SLOT_5_OCCUPIED = BlockStateProperties.SLOT_5_OCCUPIED;
    private static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final int BOOKS_PER_ROW = 3;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = List.of(ChiseledBookShelfBlock.SLOT_0_OCCUPIED, ChiseledBookShelfBlock.SLOT_1_OCCUPIED, ChiseledBookShelfBlock.SLOT_2_OCCUPIED, ChiseledBookShelfBlock.SLOT_3_OCCUPIED, ChiseledBookShelfBlock.SLOT_4_OCCUPIED, ChiseledBookShelfBlock.SLOT_5_OCCUPIED);

    @Override
    public MapCodec<ChiseledBookShelfBlock> codec() {
        return ChiseledBookShelfBlock.CODEC;
    }

    @Override
    public int getRows() {
        return 2;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    public ChiseledBookShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState blockstate = (BlockState) (this.stateDefinition.any()).setValue(ChiseledBookShelfBlock.FACING, Direction.NORTH);

        for (BooleanProperty booleanproperty : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            blockstate = (BlockState) blockstate.setValue(booleanproperty, false);
        }

        this.registerDefaultState(blockstate);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ChiseledBookShelfBlockEntity chiseledbookshelfblockentity) {
            if (!itemStack.is(ItemTags.BOOKSHELF_BOOKS)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            } else {
                OptionalInt optionalint = this.getHitSlot(hitResult, (Direction) state.getValue(ChiseledBookShelfBlock.FACING));

                if (optionalint.isEmpty()) {
                    return InteractionResult.PASS;
                } else if ((Boolean) state.getValue((Property) ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(optionalint.getAsInt()))) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                } else {
                    addBook(level, pos, player, chiseledbookshelfblockentity, itemStack, optionalint.getAsInt());
                    return InteractionResult.SUCCESS;
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ChiseledBookShelfBlockEntity chiseledbookshelfblockentity) {
            OptionalInt optionalint = this.getHitSlot(hitResult, (Direction) state.getValue(ChiseledBookShelfBlock.FACING));

            if (optionalint.isEmpty()) {
                return InteractionResult.PASS;
            } else if (!(Boolean) state.getValue((Property) ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(optionalint.getAsInt()))) {
                return InteractionResult.CONSUME;
            } else {
                removeBook(level, pos, player, chiseledbookshelfblockentity, optionalint.getAsInt());
                return InteractionResult.SUCCESS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private static void addBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity bookshelfBlock, ItemStack itemStack, int slot) {
        if (!level.isClientSide()) {
            player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
            SoundEvent soundevent = itemStack.is(Items.ENCHANTED_BOOK) ? SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED : SoundEvents.CHISELED_BOOKSHELF_INSERT;

            bookshelfBlock.setItem(slot, itemStack.consumeAndReturn(1, player));
            level.playSound((Entity) null, pos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void removeBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity bookshelfBlock, int slot) {
        if (!level.isClientSide()) {
            ItemStack itemstack = bookshelfBlock.removeItem(slot, 1);
            SoundEvent soundevent = itemstack.is(Items.ENCHANTED_BOOK) ? SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED : SoundEvents.CHISELED_BOOKSHELF_PICKUP;

            level.playSound((Entity) null, pos, soundevent, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(itemstack)) {
                player.drop(itemstack, false);
            }

            level.gameEvent(player, (Holder) GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ChiseledBookShelfBlockEntity(worldPosition, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChiseledBookShelfBlock.FACING);
        List list = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES;

        Objects.requireNonNull(builder);
        list.forEach((property) -> {
            builder.add(property);
        });
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState) this.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ChiseledBookShelfBlock.FACING, rotation.rotate((Direction) state.getValue(ChiseledBookShelfBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ChiseledBookShelfBlock.FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide()) {
            return 0;
        } else {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof ChiseledBookShelfBlockEntity) {
                ChiseledBookShelfBlockEntity chiseledbookshelfblockentity = (ChiseledBookShelfBlockEntity) blockentity;

                return chiseledbookshelfblockentity.getLastInteractedSlot() + 1;
            } else {
                return 0;
            }
        }
    }
}
