package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DoorBlock extends Block {

    public static final MapCodec<DoorBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::type), propertiesCodec()).apply(instance, DoorBlock::new);
    });
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(16.0D, 13.0D, 16.0D));
    private final BlockSetType type;

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return DoorBlock.CODEC;
    }

    protected DoorBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(DoorBlock.FACING, Direction.NORTH)).setValue(DoorBlock.OPEN, false)).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)).setValue(DoorBlock.POWERED, false)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    }

    public BlockSetType type() {
        return this.type;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = (Direction) state.getValue(DoorBlock.FACING);
        Direction direction1 = (Boolean) state.getValue(DoorBlock.OPEN) ? (state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? direction.getCounterClockWise() : direction.getClockWise()) : direction;

        return (VoxelShape) DoorBlock.SHAPES.get(direction1);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf doubleblockhalf = (DoubleBlockHalf) state.getValue(DoorBlock.HALF);

        return directionToNeighbour.getAxis() == Direction.Axis.Y && doubleblockhalf == DoubleBlockHalf.LOWER == (directionToNeighbour == Direction.UP) ? (neighbourState.getBlock() instanceof DoorBlock && neighbourState.getValue(DoorBlock.HALF) != doubleblockhalf ? (BlockState) neighbourState.setValue(DoorBlock.HALF, doubleblockhalf) : Blocks.AIR.defaultBlockState()) : (doubleblockhalf == DoubleBlockHalf.LOWER && directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random));
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && this.type.canOpenByWindCharge() && !(Boolean) state.getValue(DoorBlock.POWERED)) {
            this.setOpen((Entity) null, level, state, pos, !this.isOpen(state));
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && (player.preventsBlockDrops() || !player.hasCorrectToolForDrops(state))) {
            DoublePlantBlock.preventDropFromBottomPart(level, pos, state, player);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        boolean flag;

        switch (type) {
            case LAND:
            case AIR:
                flag = (Boolean) state.getValue(DoorBlock.OPEN);
                break;
            case WATER:
                flag = false;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return flag;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos blockpos = context.getClickedPos();
        Level level = context.getLevel();

        if (blockpos.getY() < level.getMaxY() && level.getBlockState(blockpos.above()).canBeReplaced(context)) {
            boolean flag = level.hasNeighborSignal(blockpos) || level.hasNeighborSignal(blockpos.above());

            return (BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(DoorBlock.FACING, context.getHorizontalDirection())).setValue(DoorBlock.HINGE, this.getHinge(context))).setValue(DoorBlock.POWERED, flag)).setValue(DoorBlock.OPEN, flag)).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        } else {
            return null;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        level.setBlock(pos.above(), (BlockState) state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private DoorHingeSide getHinge(BlockPlaceContext context) {
        BlockGetter blockgetter = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        Direction direction = context.getHorizontalDirection();
        BlockPos blockpos1 = blockpos.above();
        Direction direction1 = direction.getCounterClockWise();
        BlockPos blockpos2 = blockpos.relative(direction1);
        BlockState blockstate = blockgetter.getBlockState(blockpos2);
        BlockPos blockpos3 = blockpos1.relative(direction1);
        BlockState blockstate1 = blockgetter.getBlockState(blockpos3);
        Direction direction2 = direction.getClockWise();
        BlockPos blockpos4 = blockpos.relative(direction2);
        BlockState blockstate2 = blockgetter.getBlockState(blockpos4);
        BlockPos blockpos5 = blockpos1.relative(direction2);
        BlockState blockstate3 = blockgetter.getBlockState(blockpos5);
        int i = (blockstate.isCollisionShapeFullBlock(blockgetter, blockpos2) ? -1 : 0) + (blockstate1.isCollisionShapeFullBlock(blockgetter, blockpos3) ? -1 : 0) + (blockstate2.isCollisionShapeFullBlock(blockgetter, blockpos4) ? 1 : 0) + (blockstate3.isCollisionShapeFullBlock(blockgetter, blockpos5) ? 1 : 0);
        boolean flag = blockstate.getBlock() instanceof DoorBlock && blockstate.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        boolean flag1 = blockstate2.getBlock() instanceof DoorBlock && blockstate2.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;

        if ((!flag || flag1) && i <= 0) {
            if ((!flag1 || flag) && i >= 0) {
                int j = direction.getStepX();
                int k = direction.getStepZ();
                Vec3 vec3 = context.getClickLocation();
                double d0 = vec3.x - (double) blockpos.getX();
                double d1 = vec3.z - (double) blockpos.getZ();

                return (j >= 0 || d1 >= 0.5D) && (j <= 0 || d1 <= 0.5D) && (k >= 0 || d0 <= 0.5D) && (k <= 0 || d0 >= 0.5D) ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
            } else {
                return DoorHingeSide.LEFT;
            }
        } else {
            return DoorHingeSide.RIGHT;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!this.type.canOpenByHand()) {
            return InteractionResult.PASS;
        } else {
            state = (BlockState) state.cycle(DoorBlock.OPEN);
            level.setBlock(pos, state, 10);
            this.playSound(player, level, pos, (Boolean) state.getValue(DoorBlock.OPEN));
            level.gameEvent(player, (Holder) (this.isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
            return InteractionResult.SUCCESS;
        }
    }

    public boolean isOpen(BlockState state) {
        return (Boolean) state.getValue(DoorBlock.OPEN);
    }

    public void setOpen(@Nullable Entity sourceEntity, Level level, BlockState state, BlockPos pos, boolean shouldOpen) {
        if (state.is(this) && (Boolean) state.getValue(DoorBlock.OPEN) != shouldOpen) {
            level.setBlock(pos, (BlockState) state.setValue(DoorBlock.OPEN, shouldOpen), 10);
            this.playSound(sourceEntity, level, pos, shouldOpen);
            level.gameEvent(sourceEntity, (Holder) (shouldOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean flag1 = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.relative(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));

        if (!this.defaultBlockState().is(block) && flag1 != (Boolean) state.getValue(DoorBlock.POWERED)) {
            if (flag1 != (Boolean) state.getValue(DoorBlock.OPEN)) {
                this.playSound((Entity) null, level, pos, flag1);
                level.gameEvent((Entity) null, (Holder) (flag1 ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
            }

            level.setBlock(pos, (BlockState) ((BlockState) state.setValue(DoorBlock.POWERED, flag1)).setValue(DoorBlock.OPEN, flag1), 2);
        }

    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? blockstate1.isFaceSturdy(level, blockpos1, Direction.UP) : blockstate1.is(this);
    }

    private void playSound(@Nullable Entity entity, Level level, BlockPos pos, boolean open) {
        level.playSound(entity, pos, open ? this.type.doorOpen() : this.type.doorClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(DoorBlock.FACING, rotation.rotate((Direction) state.getValue(DoorBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : (BlockState) state.rotate(mirror.getRotation((Direction) state.getValue(DoorBlock.FACING))).cycle(DoorBlock.HINGE);
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.below(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DoorBlock.HALF, DoorBlock.FACING, DoorBlock.OPEN, DoorBlock.HINGE, DoorBlock.POWERED);
    }

    public static boolean isWoodenDoor(Level level, BlockPos pos) {
        return isWoodenDoor(level.getBlockState(pos));
    }

    public static boolean isWoodenDoor(BlockState state) {
        Block block = state.getBlock();
        boolean flag;

        if (block instanceof DoorBlock doorblock) {
            if (doorblock.type().canOpenByHand()) {
                flag = true;
                return flag;
            }
        }

        flag = false;
        return flag;
    }
}
