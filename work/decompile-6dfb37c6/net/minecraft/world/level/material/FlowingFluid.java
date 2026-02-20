package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FlowingFluid extends Fluid {

    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final int CACHE_SIZE = 200;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey>(200) {
            protected void rehash(int newN) {}
        };

        object2bytelinkedopenhashmap.defaultReturnValue((byte) 127);
        return object2bytelinkedopenhashmap;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    public FlowingFluid() {}

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        builder.add(FlowingFluid.FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState) {
        double d0 = 0.0D;
        double d1 = 0.0D;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            blockpos_mutableblockpos.setWithOffset(pos, direction);
            FluidState fluidstate1 = level.getFluidState(blockpos_mutableblockpos);

            if (this.affectsFlow(fluidstate1)) {
                float f = fluidstate1.getOwnHeight();
                float f1 = 0.0F;

                if (f == 0.0F) {
                    if (!level.getBlockState(blockpos_mutableblockpos).blocksMotion()) {
                        BlockPos blockpos1 = blockpos_mutableblockpos.below();
                        FluidState fluidstate2 = level.getFluidState(blockpos1);

                        if (this.affectsFlow(fluidstate2)) {
                            f = fluidstate2.getOwnHeight();
                            if (f > 0.0F) {
                                f1 = fluidState.getOwnHeight() - (f - 0.8888889F);
                            }
                        }
                    }
                } else if (f > 0.0F) {
                    f1 = fluidState.getOwnHeight() - f;
                }

                if (f1 != 0.0F) {
                    d0 += (double) ((float) direction.getStepX() * f1);
                    d1 += (double) ((float) direction.getStepZ() * f1);
                }
            }
        }

        Vec3 vec3 = new Vec3(d0, 0.0D, d1);

        if ((Boolean) fluidState.getValue(FlowingFluid.FALLING)) {
            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                blockpos_mutableblockpos.setWithOffset(pos, direction1);
                if (this.isSolidFace(level, blockpos_mutableblockpos, direction1) || this.isSolidFace(level, blockpos_mutableblockpos.above(), direction1)) {
                    vec3 = vec3.normalize().add(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }

        return vec3.normalize();
    }

    private boolean affectsFlow(FluidState neighbourFluid) {
        return neighbourFluid.isEmpty() || neighbourFluid.getType().isSame(this);
    }

    protected boolean isSolidFace(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState blockstate = level.getBlockState(pos);
        FluidState fluidstate = level.getFluidState(pos);

        return fluidstate.getType().isSame(this) ? false : (direction == Direction.UP ? true : (blockstate.getBlock() instanceof IceBlock ? false : blockstate.isFaceSturdy(level, pos, direction)));
    }

    protected void spread(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!fluidState.isEmpty()) {
            BlockPos blockpos1 = pos.below();
            BlockState blockstate1 = level.getBlockState(blockpos1);
            FluidState fluidstate1 = blockstate1.getFluidState();

            if (this.canMaybePassThrough(level, pos, state, Direction.DOWN, blockpos1, blockstate1, fluidstate1)) {
                FluidState fluidstate2 = this.getNewLiquid(level, blockpos1, blockstate1);
                Fluid fluid = fluidstate2.getType();

                if (fluidstate1.canBeReplacedWith(level, blockpos1, fluid, Direction.DOWN) && canHoldSpecificFluid(level, blockpos1, blockstate1, fluid)) {
                    this.spreadTo(level, blockpos1, blockstate1, Direction.DOWN, fluidstate2);
                    if (this.sourceNeighborCount(level, pos) >= 3) {
                        this.spreadToSides(level, pos, fluidState, state);
                    }

                    return;
                }
            }

            if (fluidState.isSource() || !this.isWaterHole(level, pos, state, blockpos1, blockstate1)) {
                this.spreadToSides(level, pos, fluidState, state);
            }

        }
    }

    private void spreadToSides(ServerLevel level, BlockPos pos, FluidState fluidState, BlockState state) {
        int i = fluidState.getAmount() - this.getDropOff(level);

        if ((Boolean) fluidState.getValue(FlowingFluid.FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread(level, pos, state);

            for (Map.Entry<Direction, FluidState> map_entry : map.entrySet()) {
                Direction direction = (Direction) map_entry.getKey();
                FluidState fluidstate1 = (FluidState) map_entry.getValue();
                BlockPos blockpos1 = pos.relative(direction);

                this.spreadTo(level, blockpos1, level.getBlockState(blockpos1), direction, fluidstate1);
            }

        }
    }

    protected FluidState getNewLiquid(ServerLevel level, BlockPos pos, BlockState state) {
        int i = 0;
        int j = 0;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = blockpos_mutableblockpos.setWithOffset(pos, direction);
            BlockState blockstate1 = level.getBlockState(blockpos1);
            FluidState fluidstate = blockstate1.getFluidState();

            if (fluidstate.getType().isSame(this) && canPassThroughWall(direction, level, pos, state, blockpos1, blockstate1)) {
                if (fluidstate.isSource()) {
                    ++j;
                }

                i = Math.max(i, fluidstate.getAmount());
            }
        }

        if (j >= 2 && this.canConvertToSource(level)) {
            BlockState blockstate2 = level.getBlockState(blockpos_mutableblockpos.setWithOffset(pos, Direction.DOWN));
            FluidState fluidstate1 = blockstate2.getFluidState();

            if (blockstate2.isSolid() || this.isSourceBlockOfThisType(fluidstate1)) {
                return this.getSource(false);
            }
        }

        BlockPos blockpos2 = blockpos_mutableblockpos.setWithOffset(pos, Direction.UP);
        BlockState blockstate3 = level.getBlockState(blockpos2);
        FluidState fluidstate2 = blockstate3.getFluidState();

        if (!fluidstate2.isEmpty() && fluidstate2.getType().isSame(this) && canPassThroughWall(Direction.UP, level, pos, state, blockpos2, blockstate3)) {
            return this.getFlowing(8, true);
        } else {
            int k = i - this.getDropOff(level);

            if (k <= 0) {
                return Fluids.EMPTY.defaultFluidState();
            } else {
                return this.getFlowing(k, false);
            }
        }
    }

    private static boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos sourcePos, BlockState sourceState, BlockPos targetPos, BlockState targetState) {
        if (!SharedConstants.DEBUG_DISABLE_LIQUID_SPREADING && (!SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD || targetPos.getZ() >= 0)) {
            VoxelShape voxelshape = targetState.getCollisionShape(level, targetPos);

            if (voxelshape == Shapes.block()) {
                return false;
            } else {
                VoxelShape voxelshape1 = sourceState.getCollisionShape(level, sourcePos);

                if (voxelshape1 == Shapes.block()) {
                    return false;
                } else if (voxelshape1 == Shapes.empty() && voxelshape == Shapes.empty()) {
                    return true;
                } else {
                    Object2ByteLinkedOpenHashMap<FlowingFluid.BlockStatePairKey> object2bytelinkedopenhashmap;

                    if (!sourceState.getBlock().hasDynamicShape() && !targetState.getBlock().hasDynamicShape()) {
                        object2bytelinkedopenhashmap = (Object2ByteLinkedOpenHashMap) FlowingFluid.OCCLUSION_CACHE.get();
                    } else {
                        object2bytelinkedopenhashmap = null;
                    }

                    FlowingFluid.BlockStatePairKey flowingfluid_blockstatepairkey;

                    if (object2bytelinkedopenhashmap != null) {
                        flowingfluid_blockstatepairkey = new FlowingFluid.BlockStatePairKey(sourceState, targetState, direction);
                        byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(flowingfluid_blockstatepairkey);

                        if (b0 != 127) {
                            return b0 != 0;
                        }
                    } else {
                        flowingfluid_blockstatepairkey = null;
                    }

                    boolean flag = !Shapes.mergedFaceOccludes(voxelshape1, voxelshape, direction);

                    if (object2bytelinkedopenhashmap != null) {
                        if (object2bytelinkedopenhashmap.size() == 200) {
                            object2bytelinkedopenhashmap.removeLastByte();
                        }

                        object2bytelinkedopenhashmap.putAndMoveToFirst(flowingfluid_blockstatepairkey, (byte) (flag ? 1 : 0));
                    }

                    return flag;
                }
            }
        } else {
            return false;
        }
    }

    public abstract Fluid getFlowing();

    public FluidState getFlowing(int amount, boolean falling) {
        return (FluidState) ((FluidState) this.getFlowing().defaultFluidState().setValue(FlowingFluid.LEVEL, amount)).setValue(FlowingFluid.FALLING, falling);
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean falling) {
        return (FluidState) this.getSource().defaultFluidState().setValue(FlowingFluid.FALLING, falling);
    }

    protected abstract boolean canConvertToSource(ServerLevel level);

    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
        Block block = state.getBlock();

        if (block instanceof LiquidBlockContainer liquidblockcontainer) {
            liquidblockcontainer.placeLiquid(level, pos, state, target);
        } else {
            if (!state.isAir()) {
                this.beforeDestroyingBlock(level, pos, state);
            }

            level.setBlock(pos, target.createLegacyBlock(), 3);
        }

    }

    protected abstract void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state);

    protected int getSlopeDistance(LevelReader level, BlockPos pos, int pass, Direction from, BlockState state, FlowingFluid.SpreadContext context) {
        int j = 1000;

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            if (direction1 != from) {
                BlockPos blockpos1 = pos.relative(direction1);
                BlockState blockstate1 = context.getBlockState(blockpos1);
                FluidState fluidstate = blockstate1.getFluidState();

                if (this.canPassThrough(level, this.getFlowing(), pos, state, direction1, blockpos1, blockstate1, fluidstate)) {
                    if (context.isHole(blockpos1)) {
                        return pass;
                    }

                    if (pass < this.getSlopeFindDistance(level)) {
                        int k = this.getSlopeDistance(level, blockpos1, pass + 1, direction1.getOpposite(), blockstate1, context);

                        if (k < j) {
                            j = k;
                        }
                    }
                }
            }
        }

        return j;
    }

    private boolean isWaterHole(BlockGetter level, BlockPos topPos, BlockState topState, BlockPos bottomPos, BlockState bottomState) {
        return !canPassThroughWall(Direction.DOWN, level, topPos, topState, bottomPos, bottomState) ? false : (bottomState.getFluidState().getType().isSame(this) ? true : canHoldFluid(level, bottomPos, bottomState, this.getFlowing()));
    }

    private boolean canPassThrough(BlockGetter level, Fluid fluid, BlockPos sourcePos, BlockState sourceState, Direction direction, BlockPos testPos, BlockState testState, FluidState testFluidState) {
        return this.canMaybePassThrough(level, sourcePos, sourceState, direction, testPos, testState, testFluidState) && canHoldSpecificFluid(level, testPos, testState, fluid);
    }

    private boolean canMaybePassThrough(BlockGetter level, BlockPos sourcePos, BlockState sourceState, Direction direction, BlockPos testPos, BlockState testState, FluidState testFluidState) {
        return !this.isSourceBlockOfThisType(testFluidState) && canHoldAnyFluid(testState) && canPassThroughWall(direction, level, sourcePos, sourceState, testPos, testState);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader level);

    private int sourceNeighborCount(LevelReader level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);
            FluidState fluidstate = level.getFluidState(blockpos1);

            if (this.isSourceBlockOfThisType(fluidstate)) {
                ++i;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        FlowingFluid.SpreadContext flowingfluid_spreadcontext = null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos1 = pos.relative(direction);
            BlockState blockstate1 = level.getBlockState(blockpos1);
            FluidState fluidstate = blockstate1.getFluidState();

            if (this.canMaybePassThrough(level, pos, state, direction, blockpos1, blockstate1, fluidstate)) {
                FluidState fluidstate1 = this.getNewLiquid(level, blockpos1, blockstate1);

                if (canHoldSpecificFluid(level, blockpos1, blockstate1, fluidstate1.getType())) {
                    if (flowingfluid_spreadcontext == null) {
                        flowingfluid_spreadcontext = new FlowingFluid.SpreadContext(level, pos);
                    }

                    int j;

                    if (flowingfluid_spreadcontext.isHole(blockpos1)) {
                        j = 0;
                    } else {
                        j = this.getSlopeDistance(level, blockpos1, 1, direction.getOpposite(), blockstate1, flowingfluid_spreadcontext);
                    }

                    if (j < i) {
                        map.clear();
                    }

                    if (j <= i) {
                        if (fluidstate.canBeReplacedWith(level, blockpos1, fluidstate1.getType(), direction)) {
                            map.put(direction, fluidstate1);
                        }

                        i = j;
                    }
                }
            }
        }

        return map;
    }

    private static boolean canHoldAnyFluid(BlockState state) {
        Block block = state.getBlock();

        return block instanceof LiquidBlockContainer ? true : (state.blocksMotion() ? false : !(block instanceof DoorBlock) && !state.is(BlockTags.SIGNS) && !state.is(Blocks.LADDER) && !state.is(Blocks.SUGAR_CANE) && !state.is(Blocks.BUBBLE_COLUMN) && !state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_GATEWAY) && !state.is(Blocks.STRUCTURE_VOID));
    }

    private static boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid newFluid) {
        return canHoldAnyFluid(state) && canHoldSpecificFluid(level, pos, state, newFluid);
    }

    private static boolean canHoldSpecificFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid newFluid) {
        Block block = state.getBlock();

        if (block instanceof LiquidBlockContainer liquidblockcontainer) {
            return liquidblockcontainer.canPlaceLiquid((LivingEntity) null, level, pos, state, newFluid);
        } else {
            return true;
        }
    }

    protected abstract int getDropOff(LevelReader level);

    protected int getSpreadDelay(Level level, BlockPos pos, FluidState oldFluidState, FluidState newFluidState) {
        return this.getTickDelay(level);
    }

    @Override
    public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (!fluidState.isSource()) {
            FluidState fluidstate1 = this.getNewLiquid(level, pos, level.getBlockState(pos));
            int i = this.getSpreadDelay(level, pos, fluidState, fluidstate1);

            if (fluidstate1.isEmpty()) {
                fluidState = fluidstate1;
                blockState = Blocks.AIR.defaultBlockState();
                level.setBlock(pos, blockState, 3);
            } else if (fluidstate1 != fluidState) {
                fluidState = fluidstate1;
                blockState = fluidstate1.createLegacyBlock();
                level.setBlock(pos, blockState, 3);
                level.scheduleTick(pos, fluidstate1.getType(), i);
            }
        }

        this.spread(level, pos, blockState, fluidState);
    }

    protected static int getLegacyLevel(FluidState fluidState) {
        return fluidState.isSource() ? 0 : 8 - Math.min(fluidState.getAmount(), 8) + ((Boolean) fluidState.getValue(FlowingFluid.FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState fluidState, BlockGetter level, BlockPos pos) {
        return fluidState.getType().isSame(level.getFluidState(pos.above()).getType());
    }

    @Override
    public float getHeight(FluidState fluidState, BlockGetter level, BlockPos pos) {
        return hasSameAbove(fluidState, level, pos) ? 1.0F : fluidState.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState fluidState) {
        return (float) fluidState.getAmount() / 9.0F;
    }

    @Override
    public abstract int getAmount(FluidState fluidState);

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return state.getAmount() == 9 && hasSameAbove(state, level, pos) ? Shapes.block() : (VoxelShape) this.shapes.computeIfAbsent(state, (fluidstate1) -> {
            return Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, (double) fluidstate1.getHeight(level, pos), 1.0D);
        });
    }

    private static record BlockStatePairKey(BlockState first, BlockState second, Direction direction) {

        public boolean equals(Object o) {
            boolean flag;

            if (o instanceof FlowingFluid.BlockStatePairKey flowingfluid_blockstatepairkey) {
                if (this.first == flowingfluid_blockstatepairkey.first && this.second == flowingfluid_blockstatepairkey.second && this.direction == flowingfluid_blockstatepairkey.direction) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }

        public int hashCode() {
            int i = System.identityHashCode(this.first);

            i = 31 * i + System.identityHashCode(this.second);
            i = 31 * i + this.direction.hashCode();
            return i;
        }
    }

    protected class SpreadContext {

        private final BlockGetter level;
        private final BlockPos origin;
        private final Short2ObjectMap<BlockState> stateCache = new Short2ObjectOpenHashMap();
        private final Short2BooleanMap holeCache = new Short2BooleanOpenHashMap();

        private SpreadContext(BlockGetter level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        public BlockState getBlockState(BlockPos pos) {
            return this.getBlockState(pos, this.getCacheKey(pos));
        }

        private BlockState getBlockState(BlockPos pos, short key) {
            return (BlockState) this.stateCache.computeIfAbsent(key, (short1) -> {
                return this.level.getBlockState(pos);
            });
        }

        public boolean isHole(BlockPos pos) {
            return this.holeCache.computeIfAbsent(this.getCacheKey(pos), (short0) -> {
                BlockState blockstate = this.getBlockState(pos, short0);
                BlockPos blockpos1 = pos.below();
                BlockState blockstate1 = this.level.getBlockState(blockpos1);

                return FlowingFluid.this.isWaterHole(this.level, pos, blockstate, blockpos1, blockstate1);
            });
        }

        private short getCacheKey(BlockPos pos) {
            int i = pos.getX() - this.origin.getX();
            int j = pos.getZ() - this.origin.getZ();

            return (short) ((i + 128 & 255) << 8 | j + 128 & 255);
        }
    }
}
