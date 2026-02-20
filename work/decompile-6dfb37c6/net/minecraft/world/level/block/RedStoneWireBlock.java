package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.DefaultRedstoneWireEvaluator;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.ExperimentalRedstoneWireEvaluator;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class RedStoneWireBlock extends Block {

    public static final MapCodec<RedStoneWireBlock> CODEC = simpleCodec(RedStoneWireBlock::new);
    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final Map<Direction, EnumProperty<RedstoneSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(Maps.newEnumMap(Map.of(Direction.NORTH, RedStoneWireBlock.NORTH, Direction.EAST, RedStoneWireBlock.EAST, Direction.SOUTH, RedStoneWireBlock.SOUTH, Direction.WEST, RedStoneWireBlock.WEST)));
    private static final int[] COLORS = (int[]) Util.make(new int[16], (aint) -> {
        for (int i = 0; i <= 15; ++i) {
            float f = (float) i / 15.0F;
            float f1 = f * 0.6F + (f > 0.0F ? 0.4F : 0.3F);
            float f2 = Mth.clamp(f * f * 0.7F - 0.5F, 0.0F, 1.0F);
            float f3 = Mth.clamp(f * f * 0.6F - 0.7F, 0.0F, 1.0F);

            aint[i] = ARGB.colorFromFloat(1.0F, f1, f2, f3);
        }

    });
    private static final float PARTICLE_DENSITY = 0.2F;
    private final Function<BlockState, VoxelShape> shapes;
    private final BlockState crossState;
    private final RedstoneWireEvaluator evaluator = new DefaultRedstoneWireEvaluator(this);
    private boolean shouldSignal = true;

    @Override
    public MapCodec<RedStoneWireBlock> codec() {
        return RedStoneWireBlock.CODEC;
    }

    public RedStoneWireBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(RedStoneWireBlock.NORTH, RedstoneSide.NONE)).setValue(RedStoneWireBlock.EAST, RedstoneSide.NONE)).setValue(RedStoneWireBlock.SOUTH, RedstoneSide.NONE)).setValue(RedStoneWireBlock.WEST, RedstoneSide.NONE)).setValue(RedStoneWireBlock.POWER, 0));
        this.shapes = this.makeShapes();
        this.crossState = (BlockState) ((BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE)).setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        int i = 1;
        int j = 10;
        VoxelShape voxelshape = Block.column(10.0D, 0.0D, 1.0D);
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(10.0D, 0.0D, 1.0D, 0.0D, 8.0D));
        Map<Direction, VoxelShape> map1 = Shapes.rotateHorizontal(Block.boxZ(10.0D, 16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((blockstate) -> {
            VoxelShape voxelshape1 = voxelshape;

            for (Map.Entry<Direction, EnumProperty<RedstoneSide>> map_entry : RedStoneWireBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                VoxelShape voxelshape2;

                switch ((RedstoneSide) blockstate.getValue((Property) map_entry.getValue())) {
                    case UP:
                        voxelshape2 = Shapes.or(voxelshape1, (VoxelShape) map.get(map_entry.getKey()), (VoxelShape) map1.get(map_entry.getKey()));
                        break;
                    case SIDE:
                        voxelshape2 = Shapes.or(voxelshape1, (VoxelShape) map.get(map_entry.getKey()));
                        break;
                    case NONE:
                        voxelshape2 = voxelshape1;
                        break;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }

                voxelshape1 = voxelshape2;
            }

            return voxelshape1;
        }, new Property[]{RedStoneWireBlock.POWER});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.getConnectionState(context.getLevel(), this.crossState, context.getClickedPos());
    }

    private BlockState getConnectionState(BlockGetter level, BlockState state, BlockPos pos) {
        boolean flag = isDot(state);

        state = this.getMissingConnections(level, (BlockState) this.defaultBlockState().setValue(RedStoneWireBlock.POWER, (Integer) state.getValue(RedStoneWireBlock.POWER)), pos);
        if (flag && isDot(state)) {
            return state;
        } else {
            boolean flag1 = ((RedstoneSide) state.getValue(RedStoneWireBlock.NORTH)).isConnected();
            boolean flag2 = ((RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH)).isConnected();
            boolean flag3 = ((RedstoneSide) state.getValue(RedStoneWireBlock.EAST)).isConnected();
            boolean flag4 = ((RedstoneSide) state.getValue(RedStoneWireBlock.WEST)).isConnected();
            boolean flag5 = !flag1 && !flag2;
            boolean flag6 = !flag3 && !flag4;

            if (!flag4 && flag5) {
                state = (BlockState) state.setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE);
            }

            if (!flag3 && flag5) {
                state = (BlockState) state.setValue(RedStoneWireBlock.EAST, RedstoneSide.SIDE);
            }

            if (!flag1 && flag6) {
                state = (BlockState) state.setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE);
            }

            if (!flag2 && flag6) {
                state = (BlockState) state.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
            }

            return state;
        }
    }

    private BlockState getMissingConnections(BlockGetter level, BlockState state, BlockPos pos) {
        boolean flag = !level.getBlockState(pos.above()).isRedstoneConductor(level, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!((RedstoneSide) state.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction))).isConnected()) {
                RedstoneSide redstoneside = this.getConnectingSide(level, pos, direction, flag);

                state = (BlockState) state.setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction), redstoneside);
            }
        }

        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == Direction.DOWN) {
            return !this.canSurviveOn(level, neighbourPos, neighbourState) ? Blocks.AIR.defaultBlockState() : state;
        } else if (directionToNeighbour == Direction.UP) {
            return this.getConnectionState(level, state, pos);
        } else {
            RedstoneSide redstoneside = this.getConnectingSide(level, pos, directionToNeighbour);

            return redstoneside.isConnected() == ((RedstoneSide) state.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour))).isConnected() && !isCross(state) ? (BlockState) state.setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), redstoneside) : this.getConnectionState(level, (BlockState) ((BlockState) this.crossState.setValue(RedStoneWireBlock.POWER, (Integer) state.getValue(RedStoneWireBlock.POWER))).setValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), redstoneside), pos);
        }
    }

    private static boolean isCross(BlockState state) {
        return ((RedstoneSide) state.getValue(RedStoneWireBlock.NORTH)).isConnected() && ((RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH)).isConnected() && ((RedstoneSide) state.getValue(RedStoneWireBlock.EAST)).isConnected() && ((RedstoneSide) state.getValue(RedStoneWireBlock.WEST)).isConnected();
    }

    private static boolean isDot(BlockState state) {
        return !((RedstoneSide) state.getValue(RedStoneWireBlock.NORTH)).isConnected() && !((RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH)).isConnected() && !((RedstoneSide) state.getValue(RedStoneWireBlock.EAST)).isConnected() && !((RedstoneSide) state.getValue(RedStoneWireBlock.WEST)).isConnected();
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int updateFlags, int updateLimit) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            RedstoneSide redstoneside = (RedstoneSide) state.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction));

            if (redstoneside != RedstoneSide.NONE && !level.getBlockState(blockpos_mutableblockpos.setWithOffset(pos, direction)).is(this)) {
                blockpos_mutableblockpos.move(Direction.DOWN);
                BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos);

                if (blockstate1.is(this)) {
                    BlockPos blockpos1 = blockpos_mutableblockpos.relative(direction.getOpposite());

                    level.neighborShapeChanged(direction.getOpposite(), blockpos_mutableblockpos, blockpos1, level.getBlockState(blockpos1), updateFlags, updateLimit);
                }

                blockpos_mutableblockpos.setWithOffset(pos, direction).move(Direction.UP);
                BlockState blockstate2 = level.getBlockState(blockpos_mutableblockpos);

                if (blockstate2.is(this)) {
                    BlockPos blockpos2 = blockpos_mutableblockpos.relative(direction.getOpposite());

                    level.neighborShapeChanged(direction.getOpposite(), blockpos_mutableblockpos, blockpos2, level.getBlockState(blockpos2), updateFlags, updateLimit);
                }
            }
        }

    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction direction) {
        return this.getConnectingSide(level, pos, direction, !level.getBlockState(pos.above()).isRedstoneConductor(level, pos));
    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction direction, boolean canConnectUp) {
        BlockPos blockpos1 = pos.relative(direction);
        BlockState blockstate = level.getBlockState(blockpos1);

        if (canConnectUp) {
            boolean flag1 = blockstate.getBlock() instanceof TrapDoorBlock || this.canSurviveOn(level, blockpos1, blockstate);

            if (flag1 && shouldConnectTo(level.getBlockState(blockpos1.above()))) {
                if (blockstate.isFaceSturdy(level, blockpos1, direction.getOpposite())) {
                    return RedstoneSide.UP;
                }

                return RedstoneSide.SIDE;
            }
        }

        return !shouldConnectTo(blockstate, direction) && (blockstate.isRedstoneConductor(level, blockpos1) || !shouldConnectTo(level.getBlockState(blockpos1.below()))) ? RedstoneSide.NONE : RedstoneSide.SIDE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        return this.canSurviveOn(level, blockpos1, blockstate1);
    }

    private boolean canSurviveOn(BlockGetter level, BlockPos relativePos, BlockState relativeState) {
        return relativeState.isFaceSturdy(level, relativePos, Direction.UP) || relativeState.is(Blocks.HOPPER);
    }

    private void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean shapeUpdateWiresAroundInitialPosition) {
        if (useExperimentalEvaluator(level)) {
            (new ExperimentalRedstoneWireEvaluator(this)).updatePowerStrength(level, pos, state, orientation, shapeUpdateWiresAroundInitialPosition);
        } else {
            this.evaluator.updatePowerStrength(level, pos, state, orientation, shapeUpdateWiresAroundInitialPosition);
        }

    }

    public int getBlockSignal(Level level, BlockPos pos) {
        this.shouldSignal = false;
        int i = level.getBestNeighborSignal(pos);

        this.shouldSignal = true;
        return i;
    }

    private void checkCornerChangeAt(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(this)) {
            level.updateNeighborsAt(pos, this);

            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            this.updatePowerStrength(level, pos, state, (Orientation) null, true);

            for (Direction direction : Direction.Plane.VERTICAL) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

            this.updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }

            this.updatePowerStrength(level, pos, state, (Orientation) null, false);
            this.updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    private void updateNeighborsOfNeighboringWires(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            this.checkCornerChangeAt(level, pos.relative(direction));
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction1);

            if (level.getBlockState(blockpos1).isRedstoneConductor(level, blockpos1)) {
                this.checkCornerChangeAt(level, blockpos1.above());
            } else {
                this.checkCornerChangeAt(level, blockpos1.below());
            }
        }

    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            if (block != this || !useExperimentalEvaluator(level)) {
                if (state.canSurvive(level, pos)) {
                    this.updatePowerStrength(level, pos, state, orientation, false);
                } else {
                    dropResources(state, level, pos);
                    level.removeBlock(pos, false);
                }

            }
        }
    }

    private static boolean useExperimentalEvaluator(Level level) {
        return level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !this.shouldSignal ? 0 : state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (this.shouldSignal && direction != Direction.DOWN) {
            int i = (Integer) state.getValue(RedStoneWireBlock.POWER);

            return i == 0 ? 0 : (direction != Direction.UP && !((RedstoneSide) this.getConnectionState(level, state, pos).getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite()))).isConnected() ? 0 : i);
        } else {
            return 0;
        }
    }

    protected static boolean shouldConnectTo(BlockState blockState) {
        return shouldConnectTo(blockState, (Direction) null);
    }

    protected static boolean shouldConnectTo(BlockState blockState, @Nullable Direction direction) {
        if (blockState.is(Blocks.REDSTONE_WIRE)) {
            return true;
        } else if (blockState.is(Blocks.REPEATER)) {
            Direction direction1 = (Direction) blockState.getValue(RepeaterBlock.FACING);

            return direction1 == direction || direction1.getOpposite() == direction;
        } else {
            return blockState.is(Blocks.OBSERVER) ? direction == blockState.getValue(ObserverBlock.FACING) : blockState.isSignalSource() && direction != null;
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.shouldSignal;
    }

    public static int getColorForPower(int power) {
        return RedStoneWireBlock.COLORS[power];
    }

    private static void spawnParticlesAlongLine(Level level, RandomSource random, BlockPos pos, int color, Direction side, Direction along, float from, float to) {
        float f2 = to - from;

        if (random.nextFloat() < 0.2F * f2) {
            float f3 = 0.4375F;
            float f4 = from + f2 * random.nextFloat();
            double d0 = 0.5D + (double) (0.4375F * (float) side.getStepX()) + (double) (f4 * (float) along.getStepX());
            double d1 = 0.5D + (double) (0.4375F * (float) side.getStepY()) + (double) (f4 * (float) along.getStepY());
            double d2 = 0.5D + (double) (0.4375F * (float) side.getStepZ()) + (double) (f4 * (float) along.getStepZ());

            level.addParticle(new DustParticleOptions(color, 1.0F), (double) pos.getX() + d0, (double) pos.getY() + d1, (double) pos.getZ() + d2, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int i = (Integer) state.getValue(RedStoneWireBlock.POWER);

        if (i != 0) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                RedstoneSide redstoneside = (RedstoneSide) state.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction));

                switch (redstoneside) {
                    case UP:
                        spawnParticlesAlongLine(level, random, pos, RedStoneWireBlock.COLORS[i], direction, Direction.UP, -0.5F, 0.5F);
                    case SIDE:
                        spawnParticlesAlongLine(level, random, pos, RedStoneWireBlock.COLORS[i], Direction.DOWN, direction, 0.0F, 0.5F);
                        break;
                    case NONE:
                    default:
                        spawnParticlesAlongLine(level, random, pos, RedStoneWireBlock.COLORS[i], Direction.DOWN, direction, 0.0F, 0.3F);
                }
            }

        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(RedStoneWireBlock.NORTH, (RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.EAST, (RedstoneSide) state.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.SOUTH, (RedstoneSide) state.getValue(RedStoneWireBlock.NORTH))).setValue(RedStoneWireBlock.WEST, (RedstoneSide) state.getValue(RedStoneWireBlock.EAST));
            case COUNTERCLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(RedStoneWireBlock.NORTH, (RedstoneSide) state.getValue(RedStoneWireBlock.EAST))).setValue(RedStoneWireBlock.EAST, (RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.SOUTH, (RedstoneSide) state.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.WEST, (RedstoneSide) state.getValue(RedStoneWireBlock.NORTH));
            case CLOCKWISE_90:
                return (BlockState) ((BlockState) ((BlockState) ((BlockState) state.setValue(RedStoneWireBlock.NORTH, (RedstoneSide) state.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.EAST, (RedstoneSide) state.getValue(RedStoneWireBlock.NORTH))).setValue(RedStoneWireBlock.SOUTH, (RedstoneSide) state.getValue(RedStoneWireBlock.EAST))).setValue(RedStoneWireBlock.WEST, (RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH));
            default:
                return state;
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return (BlockState) ((BlockState) state.setValue(RedStoneWireBlock.NORTH, (RedstoneSide) state.getValue(RedStoneWireBlock.SOUTH))).setValue(RedStoneWireBlock.SOUTH, (RedstoneSide) state.getValue(RedStoneWireBlock.NORTH));
            case FRONT_BACK:
                return (BlockState) ((BlockState) state.setValue(RedStoneWireBlock.EAST, (RedstoneSide) state.getValue(RedStoneWireBlock.WEST))).setValue(RedStoneWireBlock.WEST, (RedstoneSide) state.getValue(RedStoneWireBlock.EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedStoneWireBlock.NORTH, RedStoneWireBlock.EAST, RedStoneWireBlock.SOUTH, RedStoneWireBlock.WEST, RedStoneWireBlock.POWER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            if (isCross(state) || isDot(state)) {
                BlockState blockstate1 = isCross(state) ? this.defaultBlockState() : this.crossState;

                blockstate1 = (BlockState) blockstate1.setValue(RedStoneWireBlock.POWER, (Integer) state.getValue(RedStoneWireBlock.POWER));
                blockstate1 = this.getConnectionState(level, blockstate1, pos);
                if (blockstate1 != state) {
                    level.setBlock(pos, blockstate1, 3);
                    this.updatesOnShapeChange(level, pos, state, blockstate1);
                    return InteractionResult.SUCCESS;
                }
            }

            return InteractionResult.PASS;
        }
    }

    private void updatesOnShapeChange(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, (Direction) null, Direction.UP);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);

            if (((RedstoneSide) oldState.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction))).isConnected() != ((RedstoneSide) newState.getValue((Property) RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction))).isConnected() && level.getBlockState(blockpos1).isRedstoneConductor(level, blockpos1)) {
                level.updateNeighborsAtExceptFromFacing(blockpos1, newState.getBlock(), direction.getOpposite(), ExperimentalRedstoneUtils.withFront(orientation, direction));
            }
        }

    }
}
