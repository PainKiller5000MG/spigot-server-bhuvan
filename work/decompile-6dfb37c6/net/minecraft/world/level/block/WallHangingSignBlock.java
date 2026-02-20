package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class WallHangingSignBlock extends SignBlock {

    public static final MapCodec<WallHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter(SignBlock::type), propertiesCodec()).apply(instance, WallHangingSignBlock::new);
    });
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction.Axis, VoxelShape> SHAPES_PLANK = Shapes.rotateHorizontalAxis(Block.column(16.0D, 4.0D, 14.0D, 16.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Shapes.or((VoxelShape) WallHangingSignBlock.SHAPES_PLANK.get(Direction.Axis.Z), Block.column(14.0D, 2.0D, 0.0D, 10.0D)));

    @Override
    public MapCodec<WallHangingSignBlock> codec() {
        return WallHangingSignBlock.CODEC;
    }

    public WallHangingSignBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(type, properties.sound(type.hangingSignSoundType()));
        this.registerDefaultState((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(WallHangingSignBlock.FACING, Direction.NORTH)).setValue(WallHangingSignBlock.WATERLOGGED, false));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (blockentity instanceof SignBlockEntity signblockentity) {
            if (this.shouldTryToChainAnotherHangingSign(state, player, hitResult, signblockentity, itemStack)) {
                return InteractionResult.PASS;
            }
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    private boolean shouldTryToChainAnotherHangingSign(BlockState state, Player player, BlockHitResult hitResult, SignBlockEntity signEntity, ItemStack itemStack) {
        return !signEntity.canExecuteClickCommands(signEntity.isFacingFrontText(player), player) && itemStack.getItem() instanceof HangingSignItem && !this.isHittingEditableSide(hitResult, state);
    }

    private boolean isHittingEditableSide(BlockHitResult hitResult, BlockState state) {
        return hitResult.getDirection().getAxis() == ((Direction) state.getValue(WallHangingSignBlock.FACING)).getAxis();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) WallHangingSignBlock.SHAPES.get(((Direction) state.getValue(WallHangingSignBlock.FACING)).getAxis());
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) WallHangingSignBlock.SHAPES_PLANK.get(((Direction) state.getValue(WallHangingSignBlock.FACING)).getAxis());
    }

    public boolean canPlace(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = ((Direction) state.getValue(WallHangingSignBlock.FACING)).getClockWise();
        Direction direction1 = ((Direction) state.getValue(WallHangingSignBlock.FACING)).getCounterClockWise();

        return this.canAttachTo(level, state, pos.relative(direction), direction1) || this.canAttachTo(level, state, pos.relative(direction1), direction);
    }

    public boolean canAttachTo(LevelReader level, BlockState state, BlockPos attachPos, Direction attachFace) {
        BlockState blockstate1 = level.getBlockState(attachPos);

        return blockstate1.is(BlockTags.WALL_HANGING_SIGNS) ? ((Direction) blockstate1.getValue(WallHangingSignBlock.FACING)).getAxis().test((Direction) state.getValue(WallHangingSignBlock.FACING)) : blockstate1.isFaceSturdy(level, attachPos, attachFace, SupportType.FULL);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = this.defaultBlockState();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal() && !direction.getAxis().test(context.getClickedFace())) {
                Direction direction1 = direction.getOpposite();

                blockstate = (BlockState) blockstate.setValue(WallHangingSignBlock.FACING, direction1);
                if (blockstate.canSurvive(levelreader, blockpos) && this.canPlace(blockstate, levelreader, blockpos)) {
                    return (BlockState) blockstate.setValue(WallHangingSignBlock.WATERLOGGED, fluidstate.getType() == Fluids.WATER);
                }
            }
        }

        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getAxis() == ((Direction) state.getValue(WallHangingSignBlock.FACING)).getClockWise().getAxis() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return ((Direction) state.getValue(WallHangingSignBlock.FACING)).toYRot();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(WallHangingSignBlock.FACING, rotation.rotate((Direction) state.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WallHangingSignBlock.FACING, WallHangingSignBlock.WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new HangingSignBlockEntity(worldPosition, blockState);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.HANGING_SIGN, SignBlockEntity::tick);
    }
}
