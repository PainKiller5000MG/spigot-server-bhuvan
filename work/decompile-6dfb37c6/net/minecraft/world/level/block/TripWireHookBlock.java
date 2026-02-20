package net.minecraft.world.level.block;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class TripWireHookBlock extends Block {

    public static final MapCodec<TripWireHookBlock> CODEC = simpleCodec(TripWireHookBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;
    protected static final int WIRE_DIST_MIN = 1;
    protected static final int WIRE_DIST_MAX = 42;
    private static final int RECHECK_PERIOD = 10;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(6.0D, 0.0D, 10.0D, 10.0D, 16.0D));

    @Override
    public MapCodec<TripWireHookBlock> codec() {
        return TripWireHookBlock.CODEC;
    }

    public TripWireHookBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(TripWireHookBlock.FACING, Direction.NORTH)).setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) TripWireHookBlock.SHAPES.get(state.getValue(TripWireHookBlock.FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = (Direction) state.getValue(TripWireHookBlock.FACING);
        BlockPos blockpos1 = pos.relative(direction.getOpposite());
        BlockState blockstate1 = level.getBlockState(blockpos1);

        return direction.getAxis().isHorizontal() && blockstate1.isFaceSturdy(level, blockpos1, direction);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getOpposite() == state.getValue(TripWireHookBlock.FACING) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = (BlockState) ((BlockState) this.defaultBlockState().setValue(TripWireHookBlock.POWERED, false)).setValue(TripWireHookBlock.ATTACHED, false);
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        Direction[] adirection = context.getNearestLookingDirections();

        for (Direction direction : adirection) {
            if (direction.getAxis().isHorizontal()) {
                Direction direction1 = direction.getOpposite();

                blockstate = (BlockState) blockstate.setValue(TripWireHookBlock.FACING, direction1);
                if (blockstate.canSurvive(levelreader, blockpos)) {
                    return blockstate;
                }
            }
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        calculateState(level, pos, state, false, false, -1, (BlockState) null);
    }

    public static void calculateState(Level level, BlockPos pos, BlockState state, boolean isBeingDestroyed, boolean canUpdate, int wireSource, @Nullable BlockState wireSourceState) {
        Optional<Direction> optional = state.<Direction>getOptionalValue(TripWireHookBlock.FACING);

        if (optional.isPresent()) {
            Direction direction = (Direction) optional.get();
            boolean flag2 = (Boolean) state.getOptionalValue(TripWireHookBlock.ATTACHED).orElse(false);
            boolean flag3 = (Boolean) state.getOptionalValue(TripWireHookBlock.POWERED).orElse(false);
            Block block = state.getBlock();
            boolean flag4 = !isBeingDestroyed;
            boolean flag5 = false;
            int j = 0;
            BlockState[] ablockstate = new BlockState[42];

            for (int k = 1; k < 42; ++k) {
                BlockPos blockpos1 = pos.relative(direction, k);
                BlockState blockstate2 = level.getBlockState(blockpos1);

                if (blockstate2.is(Blocks.TRIPWIRE_HOOK)) {
                    if (blockstate2.getValue(TripWireHookBlock.FACING) == direction.getOpposite()) {
                        j = k;
                    }
                    break;
                }

                if (!blockstate2.is(Blocks.TRIPWIRE) && k != wireSource) {
                    ablockstate[k] = null;
                    flag4 = false;
                } else {
                    if (k == wireSource) {
                        blockstate2 = (BlockState) MoreObjects.firstNonNull(wireSourceState, blockstate2);
                    }

                    boolean flag6 = !(Boolean) blockstate2.getValue(TripWireBlock.DISARMED);
                    boolean flag7 = (Boolean) blockstate2.getValue(TripWireBlock.POWERED);

                    flag5 |= flag6 && flag7;
                    ablockstate[k] = blockstate2;
                    if (k == wireSource) {
                        level.scheduleTick(pos, block, 10);
                        flag4 &= flag6;
                    }
                }
            }

            flag4 &= j > 1;
            flag5 &= flag4;
            BlockState blockstate3 = (BlockState) ((BlockState) block.defaultBlockState().trySetValue(TripWireHookBlock.ATTACHED, flag4)).trySetValue(TripWireHookBlock.POWERED, flag5);

            if (j > 0) {
                BlockPos blockpos2 = pos.relative(direction, j);
                Direction direction1 = direction.getOpposite();

                level.setBlock(blockpos2, (BlockState) blockstate3.setValue(TripWireHookBlock.FACING, direction1), 3);
                notifyNeighbors(block, level, blockpos2, direction1);
                emitState(level, blockpos2, flag4, flag5, flag2, flag3);
            }

            emitState(level, pos, flag4, flag5, flag2, flag3);
            if (!isBeingDestroyed) {
                level.setBlock(pos, (BlockState) blockstate3.setValue(TripWireHookBlock.FACING, direction), 3);
                if (canUpdate) {
                    notifyNeighbors(block, level, pos, direction);
                }
            }

            if (flag2 != flag4) {
                for (int l = 1; l < j; ++l) {
                    BlockPos blockpos3 = pos.relative(direction, l);
                    BlockState blockstate4 = ablockstate[l];

                    if (blockstate4 != null) {
                        BlockState blockstate5 = level.getBlockState(blockpos3);

                        if (blockstate5.is(Blocks.TRIPWIRE) || blockstate5.is(Blocks.TRIPWIRE_HOOK)) {
                            level.setBlock(blockpos3, (BlockState) blockstate4.trySetValue(TripWireHookBlock.ATTACHED, flag4), 3);
                        }
                    }
                }
            }

        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        calculateState(level, pos, state, false, true, -1, (BlockState) null);
    }

    private static void emitState(Level level, BlockPos pos, boolean attached, boolean powered, boolean wasAttached, boolean wasPowered) {
        if (powered && !wasPowered) {
            level.playSound((Entity) null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 0.6F);
            level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_ACTIVATE, pos);
        } else if (!powered && wasPowered) {
            level.playSound((Entity) null, pos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.5F);
            level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_DEACTIVATE, pos);
        } else if (attached && !wasAttached) {
            level.playSound((Entity) null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4F, 0.7F);
            level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_ATTACH, pos);
        } else if (!attached && wasAttached) {
            level.playSound((Entity) null, pos, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 0.4F, 1.2F / (level.random.nextFloat() * 0.2F + 0.9F));
            level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_DETACH, pos);
        }

    }

    private static void notifyNeighbors(Block block, Level level, BlockPos pos, Direction direction) {
        Direction direction1 = direction.getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction1, Direction.UP);

        level.updateNeighborsAt(pos, block, orientation);
        level.updateNeighborsAt(pos.relative(direction1), block, orientation);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            boolean flag1 = (Boolean) state.getValue(TripWireHookBlock.ATTACHED);
            boolean flag2 = (Boolean) state.getValue(TripWireHookBlock.POWERED);

            if (flag1 || flag2) {
                calculateState(level, pos, state, true, false, -1, (BlockState) null);
            }

            if (flag2) {
                notifyNeighbors(this, level, pos, (Direction) state.getValue(TripWireHookBlock.FACING));
            }

        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(TripWireHookBlock.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !(Boolean) state.getValue(TripWireHookBlock.POWERED) ? 0 : (state.getValue(TripWireHookBlock.FACING) == direction ? 15 : 0);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(TripWireHookBlock.FACING, rotation.rotate((Direction) state.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(TripWireHookBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TripWireHookBlock.FACING, TripWireHookBlock.POWERED, TripWireHookBlock.ATTACHED);
    }
}
