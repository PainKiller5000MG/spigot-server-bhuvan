package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LecternBlock extends BaseEntityBlock {

    public static final MapCodec<LecternBlock> CODEC = simpleCodec(LecternBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty HAS_BOOK = BlockStateProperties.HAS_BOOK;
    private static final VoxelShape SHAPE_COLLISION = Shapes.or(Block.column(16.0D, 0.0D, 2.0D), Block.column(8.0D, 2.0D, 14.0D));
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(Block.boxZ(16.0D, 10.0D, 14.0D, 1.0D, 5.333333D), Block.boxZ(16.0D, 12.0D, 16.0D, 5.333333D, 9.666667D), Block.boxZ(16.0D, 14.0D, 18.0D, 9.666667D, 14.0D), LecternBlock.SHAPE_COLLISION));
    private static final int PAGE_CHANGE_IMPULSE_TICKS = 2;

    @Override
    public MapCodec<LecternBlock> codec() {
        return LecternBlock.CODEC;
    }

    protected LecternBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(LecternBlock.FACING, Direction.NORTH)).setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, false));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return LecternBlock.SHAPE_COLLISION;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        ItemStack itemstack = context.getItemInHand();
        Player player = context.getPlayer();
        boolean flag = false;

        if (!level.isClientSide() && player != null && player.canUseGameMasterBlocks()) {
            TypedEntityData<BlockEntityType<?>> typedentitydata = (TypedEntityData) itemstack.get(DataComponents.BLOCK_ENTITY_DATA);

            if (typedentitydata != null && typedentitydata.contains("Book")) {
                flag = true;
            }
        }

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(LecternBlock.FACING, context.getHorizontalDirection().getOpposite())).setValue(LecternBlock.HAS_BOOK, flag);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LecternBlock.SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) LecternBlock.SHAPES.get(state.getValue(LecternBlock.FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(LecternBlock.FACING, rotation.rotate((Direction) state.getValue(LecternBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(LecternBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LecternBlock.FACING, LecternBlock.POWERED, LecternBlock.HAS_BOOK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new LecternBlockEntity(worldPosition, blockState);
    }

    public static boolean tryPlaceBook(@Nullable LivingEntity sourceEntity, Level level, BlockPos pos, BlockState state, ItemStack item) {
        if (!(Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            if (!level.isClientSide()) {
                placeBook(sourceEntity, level, pos, state, item);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBook(@Nullable LivingEntity sourceEntity, Level level, BlockPos pos, BlockState state, ItemStack book) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof LecternBlockEntity lecternblockentity) {
            lecternblockentity.setBook(book.consumeAndReturn(1, sourceEntity));
            resetBookState(sourceEntity, level, pos, state, true);
            level.playSound((Entity) null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

    }

    public static void resetBookState(@Nullable Entity sourceEntity, Level level, BlockPos pos, BlockState state, boolean hasBook) {
        BlockState blockstate1 = (BlockState) ((BlockState) state.setValue(LecternBlock.POWERED, false)).setValue(LecternBlock.HAS_BOOK, hasBook);

        level.setBlock(pos, blockstate1, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(sourceEntity, blockstate1));
        updateBelow(level, pos, state);
    }

    public static void signalPageChange(Level level, BlockPos pos, BlockState state) {
        changePowered(level, pos, state, true);
        level.scheduleTick(pos, state.getBlock(), 2);
        level.levelEvent(1043, pos, 0);
    }

    private static void changePowered(Level level, BlockPos pos, BlockState state, boolean isPowered) {
        level.setBlock(pos, (BlockState) state.setValue(LecternBlock.POWERED, isPowered), 3);
        updateBelow(level, pos, state);
    }

    private static void updateBelow(Level level, BlockPos pos, BlockState state) {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, ((Direction) state.getValue(LecternBlock.FACING)).getOpposite(), Direction.UP);

        level.updateNeighborsAt(pos.below(), state.getBlock(), orientation);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        changePowered(level, pos, state, false);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if ((Boolean) state.getValue(LecternBlock.POWERED)) {
            updateBelow(level, pos, state);
        }

    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP && (Boolean) state.getValue(LecternBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if ((Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof LecternBlockEntity) {
                return ((LecternBlockEntity) blockentity).getRedstoneSignal();
            }
        }

        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return (InteractionResult) ((Boolean) state.getValue(LecternBlock.HAS_BOOK) ? InteractionResult.TRY_WITH_EMPTY_HAND : (itemStack.is(ItemTags.LECTERN_BOOKS) ? (tryPlaceBook(player, level, pos, state, itemStack) ? InteractionResult.SUCCESS : InteractionResult.PASS) : (itemStack.isEmpty() && hand == InteractionHand.MAIN_HAND ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Boolean) state.getValue(LecternBlock.HAS_BOOK)) {
            if (!level.isClientSide()) {
                this.openScreen(level, pos, player);
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return !(Boolean) state.getValue(LecternBlock.HAS_BOOK) ? null : super.getMenuProvider(state, level, pos);
    }

    private void openScreen(Level level, BlockPos pos, Player player) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof LecternBlockEntity) {
            player.openMenu((LecternBlockEntity) blockentity);
            player.awardStat(Stats.INTERACT_WITH_LECTERN);
        }

    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
