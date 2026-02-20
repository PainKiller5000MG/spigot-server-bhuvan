package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TripWireBlock extends Block {

    public static final MapCodec<TripWireBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("hook").forGetter((tripwireblock) -> {
            return tripwireblock.hook;
        }), propertiesCodec()).apply(instance, TripWireBlock::new);
    });
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    public static final BooleanProperty DISARMED = BlockStateProperties.DISARMED;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = CrossCollisionBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape SHAPE_ATTACHED = Block.column(16.0D, 1.0D, 2.5D);
    private static final VoxelShape SHAPE_NOT_ATTACHED = Block.column(16.0D, 0.0D, 8.0D);
    private static final int RECHECK_PERIOD = 10;
    private final Block hook;

    @Override
    public MapCodec<TripWireBlock> codec() {
        return TripWireBlock.CODEC;
    }

    public TripWireBlock(Block hook, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(TripWireBlock.POWERED, false)).setValue(TripWireBlock.ATTACHED, false)).setValue(TripWireBlock.DISARMED, false)).setValue(TripWireBlock.NORTH, false)).setValue(TripWireBlock.EAST, false)).setValue(TripWireBlock.SOUTH, false)).setValue(TripWireBlock.WEST, false));
        this.hook = hook;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (Boolean) state.getValue(TripWireBlock.ATTACHED) ? TripWireBlock.SHAPE_ATTACHED : TripWireBlock.SHAPE_NOT_ATTACHED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter blockgetter = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        return (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(TripWireBlock.NORTH, this.shouldConnectTo(blockgetter.getBlockState(blockpos.north()), Direction.NORTH))).setValue(TripWireBlock.EAST, this.shouldConnectTo(blockgetter.getBlockState(blockpos.east()), Direction.EAST))).setValue(TripWireBlock.SOUTH, this.shouldConnectTo(blockgetter.getBlockState(blockpos.south()), Direction.SOUTH))).setValue(TripWireBlock.WEST, this.shouldConnectTo(blockgetter.getBlockState(blockpos.west()), Direction.WEST));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getAxis().isHorizontal() ? (BlockState) state.setValue((Property) TripWireBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), this.shouldConnectTo(neighbourState, directionToNeighbour)) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            this.updateSource(level, pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            this.updateSource(level, pos, (BlockState) state.setValue(TripWireBlock.POWERED, true));
        }

    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getMainHandItem().isEmpty() && player.getMainHandItem().is(Items.SHEARS)) {
            level.setBlock(pos, (BlockState) state.setValue(TripWireBlock.DISARMED, true), 260);
            level.gameEvent(player, (Holder) GameEvent.SHEAR, pos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private void updateSource(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : new Direction[]{Direction.SOUTH, Direction.WEST}) {
            for (int i = 1; i < 42; ++i) {
                BlockPos blockpos1 = pos.relative(direction, i);
                BlockState blockstate1 = level.getBlockState(blockpos1);

                if (blockstate1.is(this.hook)) {
                    if (blockstate1.getValue(TripWireHookBlock.FACING) == direction.getOpposite()) {
                        TripWireHookBlock.calculateState(level, blockpos1, blockstate1, false, true, i, state);
                    }
                    break;
                }

                if (!blockstate1.is(this)) {
                    break;
                }
            }
        }

    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getShape(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide()) {
            if (!(Boolean) state.getValue(TripWireBlock.POWERED)) {
                this.checkPressed(level, pos, List.of(entity));
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean) level.getBlockState(pos).getValue(TripWireBlock.POWERED)) {
            this.checkPressed(level, pos);
        }
    }

    private void checkPressed(Level level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);
        List<? extends Entity> list = level.getEntities((Entity) null, blockstate.getShape(level, pos).bounds().move(pos));

        this.checkPressed(level, pos, list);
    }

    private void checkPressed(Level level, BlockPos pos, List<? extends Entity> entities) {
        BlockState blockstate = level.getBlockState(pos);
        boolean flag = (Boolean) blockstate.getValue(TripWireBlock.POWERED);
        boolean flag1 = false;

        if (!entities.isEmpty()) {
            for (Entity entity : entities) {
                if (!entity.isIgnoringBlockTriggers()) {
                    flag1 = true;
                    break;
                }
            }
        }

        if (flag1 != flag) {
            blockstate = (BlockState) blockstate.setValue(TripWireBlock.POWERED, flag1);
            level.setBlock(pos, blockstate, 3);
            this.updateSource(level, pos, blockstate);
        }

        if (flag1) {
            level.scheduleTick(new BlockPos(pos), (Block) this, 10);
        }

    }

    public boolean shouldConnectTo(BlockState blockState, Direction direction) {
        return blockState.is(this.hook) ? blockState.getValue(TripWireHookBlock.FACING) == direction.getOpposite() : blockState.is(this);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.NORTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.EAST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(TripWireBlock.NORTH, (Boolean) state.getValue(TripWireBlock.SOUTH))).setValue(TripWireBlock.SOUTH, (Boolean) state.getValue(TripWireBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(TripWireBlock.EAST, (Boolean) state.getValue(TripWireBlock.WEST))).setValue(TripWireBlock.WEST, (Boolean) state.getValue(TripWireBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TripWireBlock.POWERED, TripWireBlock.ATTACHED, TripWireBlock.DISARMED, TripWireBlock.NORTH, TripWireBlock.EAST, TripWireBlock.WEST, TripWireBlock.SOUTH);
    }
}
