package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BellBlock extends BaseEntityBlock {

    public static final MapCodec<BellBlock> CODEC = simpleCodec(BellBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BellAttachType> ATTACHMENT = BlockStateProperties.BELL_ATTACHMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape BELL_SHAPE = Shapes.or(Block.column(6.0D, 6.0D, 13.0D), Block.column(8.0D, 4.0D, 6.0D));
    private static final VoxelShape SHAPE_CEILING = Shapes.or(BellBlock.BELL_SHAPE, Block.column(2.0D, 13.0D, 16.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_FLOOR = Shapes.rotateHorizontalAxis(Block.cube(16.0D, 16.0D, 8.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_DOUBLE_WALL = Shapes.rotateHorizontalAxis(Shapes.or(BellBlock.BELL_SHAPE, Block.column(2.0D, 16.0D, 13.0D, 15.0D)));
    private static final Map<Direction, VoxelShape> SHAPE_SINGLE_WALL = Shapes.rotateHorizontal(Shapes.or(BellBlock.BELL_SHAPE, Block.boxZ(2.0D, 13.0D, 15.0D, 0.0D, 13.0D)));
    public static final int EVENT_BELL_RING = 1;

    @Override
    public MapCodec<BellBlock> codec() {
        return BellBlock.CODEC;
    }

    public BellBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BellBlock.FACING, Direction.NORTH)).setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)).setValue(BellBlock.POWERED, false));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag1 = level.hasNeighborSignal(pos);

        if (flag1 != (Boolean) state.getValue(BellBlock.POWERED)) {
            if (flag1) {
                this.attemptToRing(level, pos, (Direction) null);
            }

            level.setBlock(pos, (BlockState) state.setValue(BellBlock.POWERED, flag1), 3);
        }

    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
        Entity entity = projectile.getOwner();
        Player player;

        if (entity instanceof Player player1) {
            player = player1;
        } else {
            player = null;
        }

        Player player2 = player;

        this.onHit(level, state, hitResult, player2, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return (InteractionResult) (this.onHit(level, state, hitResult, player, true) ? InteractionResult.SUCCESS : InteractionResult.PASS);
    }

    public boolean onHit(Level level, BlockState state, BlockHitResult hitResult, @Nullable Player player, boolean requireHitFromCorrectSide) {
        Direction direction = hitResult.getDirection();
        BlockPos blockpos = hitResult.getBlockPos();
        boolean flag1 = !requireHitFromCorrectSide || this.isProperHit(state, direction, hitResult.getLocation().y - (double) blockpos.getY());

        if (flag1) {
            boolean flag2 = this.attemptToRing(player, level, blockpos, direction);

            if (flag2 && player != null) {
                player.awardStat(Stats.BELL_RING);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isProperHit(BlockState state, Direction clickedDirection, double clickY) {
        if (clickedDirection.getAxis() != Direction.Axis.Y && clickY <= (double) 0.8124F) {
            Direction direction1 = (Direction) state.getValue(BellBlock.FACING);
            BellAttachType bellattachtype = (BellAttachType) state.getValue(BellBlock.ATTACHMENT);

            switch (bellattachtype) {
                case FLOOR:
                    return direction1.getAxis() == clickedDirection.getAxis();
                case SINGLE_WALL:
                case DOUBLE_WALL:
                    return direction1.getAxis() != clickedDirection.getAxis();
                case CEILING:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public boolean attemptToRing(Level level, BlockPos pos, @Nullable Direction direction) {
        return this.attemptToRing((Entity) null, level, pos, direction);
    }

    public boolean attemptToRing(@Nullable Entity ringingEntity, Level level, BlockPos pos, @Nullable Direction direction) {
        BlockEntity blockentity = level.getBlockEntity(pos);

        if (!level.isClientSide() && blockentity instanceof BellBlockEntity) {
            if (direction == null) {
                direction = (Direction) level.getBlockState(pos).getValue(BellBlock.FACING);
            }

            ((BellBlockEntity) blockentity).onHit(direction);
            level.playSound((Entity) null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
            level.gameEvent(ringingEntity, (Holder) GameEvent.BLOCK_CHANGE, pos);
            return true;
        } else {
            return false;
        }
    }

    private VoxelShape getVoxelShape(BlockState state) {
        Direction direction = (Direction) state.getValue(BellBlock.FACING);
        VoxelShape voxelshape;

        switch ((BellAttachType) state.getValue(BellBlock.ATTACHMENT)) {
            case FLOOR:
                voxelshape = (VoxelShape) BellBlock.SHAPE_FLOOR.get(direction.getAxis());
                break;
            case SINGLE_WALL:
                voxelshape = (VoxelShape) BellBlock.SHAPE_SINGLE_WALL.get(direction);
                break;
            case DOUBLE_WALL:
                voxelshape = (VoxelShape) BellBlock.SHAPE_DOUBLE_WALL.get(direction.getAxis());
                break;
            case CEILING:
                voxelshape = BellBlock.SHAPE_CEILING;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return voxelshape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos blockpos = context.getClickedPos();
        Level level = context.getLevel();
        Direction.Axis direction_axis = direction.getAxis();

        if (direction_axis == Direction.Axis.Y) {
            BlockState blockstate = (BlockState) ((BlockState) this.defaultBlockState().setValue(BellBlock.ATTACHMENT, direction == Direction.DOWN ? BellAttachType.CEILING : BellAttachType.FLOOR)).setValue(BellBlock.FACING, context.getHorizontalDirection());

            if (blockstate.canSurvive(context.getLevel(), blockpos)) {
                return blockstate;
            }
        } else {
            boolean flag = direction_axis == Direction.Axis.X && level.getBlockState(blockpos.west()).isFaceSturdy(level, blockpos.west(), Direction.EAST) && level.getBlockState(blockpos.east()).isFaceSturdy(level, blockpos.east(), Direction.WEST) || direction_axis == Direction.Axis.Z && level.getBlockState(blockpos.north()).isFaceSturdy(level, blockpos.north(), Direction.SOUTH) && level.getBlockState(blockpos.south()).isFaceSturdy(level, blockpos.south(), Direction.NORTH);
            BlockState blockstate1 = (BlockState) ((BlockState) this.defaultBlockState().setValue(BellBlock.FACING, direction.getOpposite())).setValue(BellBlock.ATTACHMENT, flag ? BellAttachType.DOUBLE_WALL : BellAttachType.SINGLE_WALL);

            if (blockstate1.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockstate1;
            }

            boolean flag1 = level.getBlockState(blockpos.below()).isFaceSturdy(level, blockpos.below(), Direction.UP);

            blockstate1 = (BlockState) blockstate1.setValue(BellBlock.ATTACHMENT, flag1 ? BellAttachType.FLOOR : BellAttachType.CEILING);
            if (blockstate1.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockstate1;
            }
        }

        return null;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks()) {
            this.attemptToRing(level, pos, (Direction) null);
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BellAttachType bellattachtype = (BellAttachType) state.getValue(BellBlock.ATTACHMENT);
        Direction direction1 = getConnectedDirection(state).getOpposite();

        if (direction1 == directionToNeighbour && !state.canSurvive(level, pos) && bellattachtype != BellAttachType.DOUBLE_WALL) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if (directionToNeighbour.getAxis() == ((Direction) state.getValue(BellBlock.FACING)).getAxis()) {
                if (bellattachtype == BellAttachType.DOUBLE_WALL && !neighbourState.isFaceSturdy(level, neighbourPos, directionToNeighbour)) {
                    return (BlockState) ((BlockState) state.setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL)).setValue(BellBlock.FACING, directionToNeighbour.getOpposite());
                }

                if (bellattachtype == BellAttachType.SINGLE_WALL && direction1.getOpposite() == directionToNeighbour && neighbourState.isFaceSturdy(level, neighbourPos, (Direction) state.getValue(BellBlock.FACING))) {
                    return (BlockState) state.setValue(BellBlock.ATTACHMENT, BellAttachType.DOUBLE_WALL);
                }
            }

            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();

        return direction == Direction.UP ? Block.canSupportCenter(level, pos.above(), Direction.DOWN) : FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, direction);
    }

    private static Direction getConnectedDirection(BlockState state) {
        switch ((BellAttachType) state.getValue(BellBlock.ATTACHMENT)) {
            case FLOOR:
                return Direction.UP;
            case CEILING:
                return Direction.DOWN;
            default:
                return ((Direction) state.getValue(BellBlock.FACING)).getOpposite();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BellBlock.FACING, BellBlock.ATTACHMENT, BellBlock.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new BellBlockEntity(worldPosition, blockState);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.BELL, level.isClientSide() ? BellBlockEntity::clientTick : BellBlockEntity::serverTick);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(BellBlock.FACING, rotation.rotate((Direction) state.getValue(BellBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(BellBlock.FACING)));
    }
}
