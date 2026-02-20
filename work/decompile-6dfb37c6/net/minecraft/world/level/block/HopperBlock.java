package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class HopperBlock extends BaseEntityBlock {

    public static final MapCodec<HopperBlock> CODEC = simpleCodec(HopperBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
    private final Function<BlockState, VoxelShape> shapes;
    private final Map<Direction, VoxelShape> interactionShapes;

    @Override
    public MapCodec<HopperBlock> codec() {
        return HopperBlock.CODEC;
    }

    public HopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(HopperBlock.FACING, Direction.DOWN)).setValue(HopperBlock.ENABLED, true));
        VoxelShape voxelshape = Block.column(12.0D, 11.0D, 16.0D);

        this.shapes = this.makeShapes(voxelshape);
        this.interactionShapes = ImmutableMap.builderWithExpectedSize(5).putAll(Shapes.rotateHorizontal(Shapes.or(voxelshape, Block.boxZ(4.0D, 8.0D, 10.0D, 0.0D, 4.0D)))).put(Direction.DOWN, voxelshape).build();
    }

    private Function<BlockState, VoxelShape> makeShapes(VoxelShape inside) {
        VoxelShape voxelshape1 = Shapes.or(Block.column(16.0D, 10.0D, 16.0D), Block.column(8.0D, 4.0D, 10.0D));
        VoxelShape voxelshape2 = Shapes.join(voxelshape1, inside, BooleanOp.ONLY_FIRST);
        Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(4.0D, 4.0D, 8.0D, 0.0D, 8.0D), (new Vec3(8.0D, 6.0D, 8.0D)).scale(0.0625D));

        return this.getShapeForEachState((blockstate) -> {
            return Shapes.or(voxelshape2, Shapes.join((VoxelShape) map.get(blockstate.getValue(HopperBlock.FACING)), Shapes.block(), BooleanOp.AND));
        }, new Property[]{HopperBlock.ENABLED});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return (VoxelShape) this.interactionShapes.get(state.getValue(HopperBlock.FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace().getOpposite();

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(HopperBlock.FACING, direction.getAxis() == Direction.Axis.Y ? Direction.DOWN : direction)).setValue(HopperBlock.ENABLED, true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new HopperBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, BlockEntityType.HOPPER, HopperBlockEntity::pushItemsTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.checkPoweredState(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockentity = level.getBlockEntity(pos);

            if (blockentity instanceof HopperBlockEntity) {
                HopperBlockEntity hopperblockentity = (HopperBlockEntity) blockentity;

                player.openMenu(hopperblockentity);
                player.awardStat(Stats.INSPECT_HOPPER);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        this.checkPoweredState(level, pos, state);
    }

    private void checkPoweredState(Level level, BlockPos pos, BlockState state) {
        boolean flag = !level.hasNeighborSignal(pos);

        if (flag != (Boolean) state.getValue(HopperBlock.ENABLED)) {
            level.setBlock(pos, (BlockState) state.setValue(HopperBlock.ENABLED, flag), 2);
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(HopperBlock.FACING, rotation.rotate((Direction) state.getValue(HopperBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(HopperBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HopperBlock.FACING, HopperBlock.ENABLED);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof HopperBlockEntity) {
            HopperBlockEntity.entityInside(level, pos, state, entity, (HopperBlockEntity) blockentity);
        }

    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
