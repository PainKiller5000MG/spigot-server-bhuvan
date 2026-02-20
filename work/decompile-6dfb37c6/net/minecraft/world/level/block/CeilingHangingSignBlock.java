package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CeilingHangingSignBlock extends SignBlock {

    public static final MapCodec<CeilingHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, CeilingHangingSignBlock::new);
    });
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    private static final VoxelShape SHAPE_DEFAULT = Block.column(10.0D, 0.0D, 16.0D);
    private static final Map<Integer, VoxelShape> SHAPES = (Map) Shapes.rotateHorizontal(Block.column(14.0D, 2.0D, 0.0D, 10.0D)).entrySet().stream().collect(Collectors.toMap((entry) -> {
        return RotationSegment.convertToSegment((Direction) entry.getKey());
    }, Entry::getValue));

    @Override
    public MapCodec<CeilingHangingSignBlock> codec() {
        return CeilingHangingSignBlock.CODEC;
    }

    public CeilingHangingSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties.sound(type.hangingSignSoundType()));
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(CeilingHangingSignBlock.ROTATION, 0)).setValue(CeilingHangingSignBlock.ATTACHED, false)).setValue(CeilingHangingSignBlock.WATERLOGGED, false));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof SignBlockEntity signblockentity) {
            if (this.shouldTryToChainAnotherHangingSign(player, hitResult, signblockentity, itemStack)) {
                return InteractionResult.PASS;
            }
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    private boolean shouldTryToChainAnotherHangingSign(Player player, BlockHitResult hitResult, SignBlockEntity signEntity, ItemStack itemStack) {
        return !signEntity.canExecuteClickCommands(signEntity.isFacingFrontText(player), player) && itemStack.getItem() instanceof HangingSignItem && hitResult.getDirection().equals(Direction.DOWN);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN, SupportType.CENTER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        FluidState fluidstate = level.getFluidState(context.getClickedPos());
        BlockPos blockpos = context.getClickedPos().above();
        BlockState blockstate = level.getBlockState(blockpos);
        boolean flag = blockstate.is(BlockTags.ALL_HANGING_SIGNS);
        Direction direction = Direction.fromYRot((double) context.getRotation());
        boolean flag1 = !Block.isFaceFull(blockstate.getCollisionShape(level, blockpos), Direction.DOWN) || context.isSecondaryUseActive();

        if (flag && !context.isSecondaryUseActive()) {
            if (blockstate.hasProperty(WallHangingSignBlock.FACING)) {
                Direction direction1 = (Direction) blockstate.getValue(WallHangingSignBlock.FACING);

                if (direction1.getAxis().test(direction)) {
                    flag1 = false;
                }
            } else if (blockstate.hasProperty(CeilingHangingSignBlock.ROTATION)) {
                Optional<Direction> optional = RotationSegment.convertToDirection((Integer) blockstate.getValue(CeilingHangingSignBlock.ROTATION));

                if (optional.isPresent() && ((Direction) optional.get()).getAxis().test(direction)) {
                    flag1 = false;
                }
            }
        }

        int i = !flag1 ? RotationSegment.convertToSegment(direction.getOpposite()) : RotationSegment.convertToSegment(context.getRotation() + 180.0F);

        return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(CeilingHangingSignBlock.ATTACHED, flag1)).setValue(CeilingHangingSignBlock.ROTATION, i)).setValue(CeilingHangingSignBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) CeilingHangingSignBlock.SHAPES.getOrDefault(state.getValue(CeilingHangingSignBlock.ROTATION), CeilingHangingSignBlock.SHAPE_DEFAULT);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.UP && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return RotationSegment.convertToDegrees((Integer) state.getValue(CeilingHangingSignBlock.ROTATION));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CeilingHangingSignBlock.ROTATION, rotation.rotate((Integer) state.getValue(CeilingHangingSignBlock.ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return (BlockState) state.setValue(CeilingHangingSignBlock.ROTATION, mirror.mirror((Integer) state.getValue(CeilingHangingSignBlock.ROTATION), 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CeilingHangingSignBlock.ROTATION, CeilingHangingSignBlock.ATTACHED, CeilingHangingSignBlock.WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new HangingSignBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.HANGING_SIGN, SignBlockEntity::tick);
    }
}
