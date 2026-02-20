package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ChestBlock extends AbstractChestBlock<ChestBlockEntity> implements SimpleWaterloggedBlock {

    public static final MapCodec<ChestBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("open_sound").forGetter(ChestBlock::getOpenChestSound), BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("close_sound").forGetter(ChestBlock::getCloseChestSound), propertiesCodec()).apply(instance, (soundevent, soundevent1, blockbehaviour_properties) -> {
            return new ChestBlock(() -> {
                return BlockEntityType.CHEST;
            }, soundevent, soundevent1, blockbehaviour_properties);
        });
    });
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int EVENT_SET_OPEN_COUNT = 1;
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 14.0D);
    private static final Map<Direction, VoxelShape> HALF_SHAPES = Shapes.rotateHorizontal(Block.boxZ(14.0D, 0.0D, 14.0D, 0.0D, 15.0D));
    private final SoundEvent openSound;
    private final SoundEvent closeSound;
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>> CHEST_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<Container>>() {
        public Optional<Container> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
            return Optional.of(new CompoundContainer(first, second));
        }

        public Optional<Container> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<Container> acceptNone() {
            return Optional.empty();
        }
    };
    private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER_COMBINER = new DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>>() {
        public Optional<MenuProvider> acceptDouble(final ChestBlockEntity first, final ChestBlockEntity second) {
            final Container container = new CompoundContainer(first, second);

            return Optional.of(new MenuProvider() {
                @Override
                public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                    if (first.canOpen(player) && second.canOpen(player)) {
                        first.unpackLootTable(inventory.player);
                        second.unpackLootTable(inventory.player);
                        return ChestMenu.sixRows(containerId, inventory, container);
                    } else {
                        Direction direction = ChestBlock.getConnectedDirection(first.getBlockState());
                        Vec3 vec3 = first.getBlockPos().getCenter();
                        Vec3 vec31 = vec3.add((double) direction.getStepX() / 2.0D, 0.0D, (double) direction.getStepZ() / 2.0D);

                        BaseContainerBlockEntity.sendChestLockedNotifications(vec31, player, this.getDisplayName());
                        return null;
                    }
                }

                @Override
                public Component getDisplayName() {
                    return (Component) (first.hasCustomName() ? first.getDisplayName() : (second.hasCustomName() ? second.getDisplayName() : Component.translatable("container.chestDouble")));
                }
            });
        }

        public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
            return Optional.of(single);
        }

        @Override
        public Optional<MenuProvider> acceptNone() {
            return Optional.empty();
        }
    };

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return ChestBlock.CODEC;
    }

    protected ChestBlock(Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityType, SoundEvent openSound, SoundEvent closeSound, BlockBehaviour.Properties properties) {
        super(properties, blockEntityType);
        this.openSound = openSound;
        this.closeSound = closeSound;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(ChestBlock.FACING, Direction.NORTH)).setValue(ChestBlock.TYPE, ChestType.SINGLE)).setValue(ChestBlock.WATERLOGGED, false));
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        ChestType chesttype = (ChestType) state.getValue(ChestBlock.TYPE);

        return chesttype == ChestType.SINGLE ? DoubleBlockCombiner.BlockType.SINGLE : (chesttype == ChestType.RIGHT ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(ChestBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (this.chestCanConnectTo(neighbourState) && directionToNeighbour.getAxis().isHorizontal()) {
            ChestType chesttype = (ChestType) neighbourState.getValue(ChestBlock.TYPE);

            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE && chesttype != ChestType.SINGLE && state.getValue(ChestBlock.FACING) == neighbourState.getValue(ChestBlock.FACING) && getConnectedDirection(neighbourState) == directionToNeighbour.getOpposite()) {
                return (BlockState) state.setValue(ChestBlock.TYPE, chesttype.getOpposite());
            }
        } else if (getConnectedDirection(state) == directionToNeighbour) {
            return (BlockState) state.setValue(ChestBlock.TYPE, ChestType.SINGLE);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    public boolean chestCanConnectTo(BlockState blockState) {
        return blockState.is(this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape;

        switch ((ChestType) state.getValue(ChestBlock.TYPE)) {
            case SINGLE:
                voxelshape = ChestBlock.SHAPE;
                break;
            case LEFT:
            case RIGHT:
                voxelshape = (VoxelShape) ChestBlock.HALF_SHAPES.get(getConnectedDirection(state));
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return voxelshape;
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction direction = (Direction) state.getValue(ChestBlock.FACING);

        return state.getValue(ChestBlock.TYPE) == ChestType.LEFT ? direction.getClockWise() : direction.getCounterClockWise();
    }

    public static BlockPos getConnectedBlockPos(BlockPos pos, BlockState state) {
        Direction direction = getConnectedDirection(state);

        return pos.relative(direction);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ChestType chesttype = ChestType.SINGLE;
        Direction direction = context.getHorizontalDirection().getOpposite();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = context.isSecondaryUseActive();
        Direction direction1 = context.getClickedFace();

        if (direction1.getAxis().isHorizontal() && flag) {
            Direction direction2 = this.candidatePartnerFacing(context.getLevel(), context.getClickedPos(), direction1.getOpposite());

            if (direction2 != null && direction2.getAxis() != direction1.getAxis()) {
                direction = direction2;
                chesttype = direction2.getCounterClockWise() == direction1.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
            }
        }

        if (chesttype == ChestType.SINGLE && !flag) {
            chesttype = this.getChestType(context.getLevel(), context.getClickedPos(), direction);
        }

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(ChestBlock.FACING, direction)).setValue(ChestBlock.TYPE, chesttype)).setValue(ChestBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    protected ChestType getChestType(Level level, BlockPos pos, Direction facingDirection) {
        return facingDirection == this.candidatePartnerFacing(level, pos, facingDirection.getClockWise()) ? ChestType.LEFT : (facingDirection == this.candidatePartnerFacing(level, pos, facingDirection.getCounterClockWise()) ? ChestType.RIGHT : ChestType.SINGLE);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(ChestBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private @Nullable Direction candidatePartnerFacing(Level level, BlockPos pos, Direction neighbourDirection) {
        BlockState blockstate = level.getBlockState(pos.relative(neighbourDirection));

        return this.chestCanConnectTo(blockstate) && blockstate.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? (Direction) blockstate.getValue(ChestBlock.FACING) : null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverlevel) {
            MenuProvider menuprovider = this.getMenuProvider(state, level, pos);

            if (menuprovider != null) {
                player.openMenu(menuprovider);
                player.awardStat(this.getOpenChestStat());
                PiglinAi.angerNearbyPiglins(serverlevel, player, true);
            }
        }

        return InteractionResult.SUCCESS;
    }

    protected Stat<Identifier> getOpenChestStat() {
        return Stats.CUSTOM.get(Stats.OPEN_CHEST);
    }

    public BlockEntityType<? extends ChestBlockEntity> blockEntityType() {
        return (BlockEntityType) this.blockEntityType.get();
    }

    public static @Nullable Container getContainer(ChestBlock block, BlockState state, Level level, BlockPos pos, boolean ignoreBeingBlocked) {
        return (Container) ((Optional) block.combine(state, level, pos, ignoreBeingBlocked).apply(ChestBlock.CHEST_COMBINER)).orElse((Object) null);
    }

    @Override
    public DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combine(BlockState state, Level level, BlockPos pos, boolean ignoreBeingBlocked) {
        BiPredicate<LevelAccessor, BlockPos> bipredicate;

        if (ignoreBeingBlocked) {
            bipredicate = (levelaccessor, blockpos1) -> {
                return false;
            };
        } else {
            bipredicate = ChestBlock::isChestBlockedAt;
        }

        return DoubleBlockCombiner.<ChestBlockEntity>combineWithNeigbour((BlockEntityType) this.blockEntityType.get(), ChestBlock::getBlockType, ChestBlock::getConnectedDirection, ChestBlock.FACING, state, level, pos, bipredicate);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return (MenuProvider) ((Optional) this.combine(state, level, pos, false).apply(ChestBlock.MENU_PROVIDER_COMBINER)).orElse((Object) null);
    }

    public static DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction> opennessCombiner(final LidBlockEntity entity) {
        return new DoubleBlockCombiner.Combiner<ChestBlockEntity, Float2FloatFunction>() {
            public Float2FloatFunction acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                return (f) -> {
                    return Math.max(first.getOpenNess(f), second.getOpenNess(f));
                };
            }

            public Float2FloatFunction acceptSingle(ChestBlockEntity single) {
                Objects.requireNonNull(single);
                return single::getOpenNess;
            }

            @Override
            public Float2FloatFunction acceptNone() {
                LidBlockEntity lidblockentity1 = entity;

                Objects.requireNonNull(entity);
                return lidblockentity1::getOpenNess;
            }
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new ChestBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, this.blockEntityType(), ChestBlockEntity::lidAnimateTick) : null;
    }

    public static boolean isChestBlockedAt(LevelAccessor level, BlockPos pos) {
        return isBlockedChestByBlock(level, pos) || isCatSittingOnChest(level, pos);
    }

    private static boolean isBlockedChestByBlock(BlockGetter level, BlockPos pos) {
        BlockPos blockpos1 = pos.above();

        return level.getBlockState(blockpos1).isRedstoneConductor(level, blockpos1);
    }

    private static boolean isCatSittingOnChest(LevelAccessor level, BlockPos pos) {
        List<Cat> list = level.<Cat>getEntitiesOfClass(Cat.class, new AABB((double) pos.getX(), (double) (pos.getY() + 1), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 2), (double) (pos.getZ() + 1)));

        if (!list.isEmpty()) {
            for (Cat cat : list) {
                if (cat.isInSittingPose()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(getContainer(this, state, level, pos, false));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(ChestBlock.FACING, rotation.rotate((Direction) state.getValue(ChestBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(ChestBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ChestBlock.FACING, ChestBlock.TYPE, ChestBlock.WATERLOGGED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof ChestBlockEntity) {
            ((ChestBlockEntity) blockentity).recheckOpen();
        }

    }

    public SoundEvent getOpenChestSound() {
        return this.openSound;
    }

    public SoundEvent getCloseChestSound() {
        return this.closeSound;
    }
}
