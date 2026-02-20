package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class FenceGateBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<FenceGateBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WoodType.CODEC.fieldOf("wood_type").forGetter((fencegateblock) -> {
            return fencegateblock.type;
        }), propertiesCodec()).apply(instance, FenceGateBlock::new);
    });
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty IN_WALL = BlockStateProperties.IN_WALL;
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.cube(16.0D, 16.0D, 4.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPES_WALL = Maps.newEnumMap(Util.mapValues(FenceGateBlock.SHAPES, (voxelshape) -> {
        return Shapes.join(voxelshape, Block.column(16.0D, 13.0D, 16.0D), BooleanOp.ONLY_FIRST);
    }));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_COLLISION = Shapes.rotateHorizontalAxis(Block.column(16.0D, 4.0D, 0.0D, 24.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_SUPPORT = Shapes.rotateHorizontalAxis(Block.column(16.0D, 4.0D, 5.0D, 24.0D));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION = Shapes.rotateHorizontalAxis(Shapes.or(Block.box(0.0D, 5.0D, 7.0D, 2.0D, 16.0D, 9.0D), Block.box(14.0D, 5.0D, 7.0D, 16.0D, 16.0D, 9.0D)));
    private static final Map<Direction.Axis, VoxelShape> SHAPE_OCCLUSION_WALL = Maps.newEnumMap(Util.mapValues(FenceGateBlock.SHAPE_OCCLUSION, (voxelshape) -> {
        return voxelshape.move(0.0D, -0.1875D, 0.0D).optimize();
    }));
    private final WoodType type;

    @Override
    public MapCodec<FenceGateBlock> codec() {
        return FenceGateBlock.CODEC;
    }

    public FenceGateBlock(WoodType type, BlockBehaviour.Properties properties) {
        super(properties.sound(type.soundType()));
        this.type = type;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(FenceGateBlock.OPEN, false)).setValue(FenceGateBlock.POWERED, false)).setValue(FenceGateBlock.IN_WALL, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis direction_axis = ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis();

        return (VoxelShape) ((Boolean) state.getValue(FenceGateBlock.IN_WALL) ? FenceGateBlock.SHAPES_WALL : FenceGateBlock.SHAPES).get(direction_axis);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Direction.Axis direction_axis = directionToNeighbour.getAxis();

        if (((Direction) state.getValue(FenceGateBlock.FACING)).getClockWise().getAxis() != direction_axis) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        } else {
            boolean flag = this.isWall(neighbourState) || this.isWall(level.getBlockState(pos.relative(directionToNeighbour.getOpposite())));

            return (BlockState) state.setValue(FenceGateBlock.IN_WALL, flag);
        }
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction.Axis direction_axis = ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis();

        return (Boolean) state.getValue(FenceGateBlock.OPEN) ? Shapes.empty() : (VoxelShape) FenceGateBlock.SHAPE_SUPPORT.get(direction_axis);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction.Axis direction_axis = ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis();

        return (Boolean) state.getValue(FenceGateBlock.OPEN) ? Shapes.empty() : (VoxelShape) FenceGateBlock.SHAPE_COLLISION.get(direction_axis);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        Direction.Axis direction_axis = ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis();

        return (VoxelShape) ((Boolean) state.getValue(FenceGateBlock.IN_WALL) ? FenceGateBlock.SHAPE_OCCLUSION_WALL : FenceGateBlock.SHAPE_OCCLUSION).get(direction_axis);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        switch (type) {
            case LAND:
                return (Boolean) state.getValue(FenceGateBlock.OPEN);
            case WATER:
                return false;
            case AIR:
                return (Boolean) state.getValue(FenceGateBlock.OPEN);
            default:
                return false;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        boolean flag = level.hasNeighborSignal(blockpos);
        Direction direction = context.getHorizontalDirection();
        Direction.Axis direction_axis = direction.getAxis();
        boolean flag1 = direction_axis == Direction.Axis.Z && (this.isWall(level.getBlockState(blockpos.west())) || this.isWall(level.getBlockState(blockpos.east()))) || direction_axis == Direction.Axis.X && (this.isWall(level.getBlockState(blockpos.north())) || this.isWall(level.getBlockState(blockpos.south())));

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(FenceGateBlock.FACING, direction)).setValue(FenceGateBlock.OPEN, flag)).setValue(FenceGateBlock.POWERED, flag)).setValue(FenceGateBlock.IN_WALL, flag1);
    }

    private boolean isWall(BlockState state) {
        return state.is(BlockTags.WALLS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if ((Boolean) state.getValue(FenceGateBlock.OPEN)) {
            state = (BlockState) state.setValue(FenceGateBlock.OPEN, false);
            level.setBlock(pos, state, 10);
        } else {
            Direction direction = player.getDirection();

            if (state.getValue(FenceGateBlock.FACING) == direction.getOpposite()) {
                state = (BlockState) state.setValue(FenceGateBlock.FACING, direction);
            }

            state = (BlockState) state.setValue(FenceGateBlock.OPEN, true);
            level.setBlock(pos, state, 10);
        }

        boolean flag = (Boolean) state.getValue(FenceGateBlock.OPEN);

        level.playSound(player, pos, flag ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(player, (Holder) (flag ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
        if (explosion.canTriggerBlocks() && !(Boolean) state.getValue(FenceGateBlock.POWERED)) {
            boolean flag = (Boolean) state.getValue(FenceGateBlock.OPEN);

            level.setBlockAndUpdate(pos, (BlockState) state.setValue(FenceGateBlock.OPEN, !flag));
            level.playSound((Entity) null, pos, flag ? this.type.fenceGateClose() : this.type.fenceGateOpen(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(flag ? GameEvent.BLOCK_CLOSE : GameEvent.BLOCK_OPEN, pos, GameEvent.Context.of(state));
        }

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            boolean flag1 = level.hasNeighborSignal(pos);

            if ((Boolean) state.getValue(FenceGateBlock.POWERED) != flag1) {
                level.setBlock(pos, (BlockState) ((BlockState) state.setValue(FenceGateBlock.POWERED, flag1)).setValue(FenceGateBlock.OPEN, flag1), 2);
                if ((Boolean) state.getValue(FenceGateBlock.OPEN) != flag1) {
                    level.playSound((Entity) null, pos, flag1 ? this.type.fenceGateOpen() : this.type.fenceGateClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
                    level.gameEvent((Entity) null, (Holder) (flag1 ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE), pos);
                }
            }

        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FenceGateBlock.FACING, FenceGateBlock.OPEN, FenceGateBlock.POWERED, FenceGateBlock.IN_WALL);
    }

    public static boolean connectsToDirection(BlockState state, Direction direction) {
        return ((Direction) state.getValue(FenceGateBlock.FACING)).getAxis() == direction.getClockWise().getAxis();
    }
}
