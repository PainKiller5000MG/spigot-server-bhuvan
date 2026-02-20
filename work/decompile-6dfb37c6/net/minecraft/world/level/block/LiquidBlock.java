package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LiquidBlock extends Block implements BucketPickup {

    private static final Codec<FlowingFluid> FLOWING_FLUID = BuiltInRegistries.FLUID.byNameCodec().comapFlatMap((fluid) -> {
        DataResult dataresult;

        if (fluid instanceof FlowingFluid flowingfluid) {
            dataresult = DataResult.success(flowingfluid);
        } else {
            dataresult = DataResult.error(() -> {
                return "Not a flowing fluid: " + String.valueOf(fluid);
            });
        }

        return dataresult;
    }, (flowingfluid) -> {
        return flowingfluid;
    });
    public static final MapCodec<LiquidBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LiquidBlock.FLOWING_FLUID.fieldOf("fluid").forGetter((liquidblock) -> {
            return liquidblock.fluid;
        }), propertiesCodec()).apply(instance, LiquidBlock::new);
    });
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    protected final FlowingFluid fluid;
    private final List<FluidState> stateCache;
    public static final VoxelShape SHAPE_STABLE = Block.column(16.0D, 0.0D, 8.0D);
    public static final ImmutableList<Direction> POSSIBLE_FLOW_DIRECTIONS = ImmutableList.of(Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST);

    @Override
    public MapCodec<LiquidBlock> codec() {
        return LiquidBlock.CODEC;
    }

    protected LiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(properties);
        this.fluid = fluid;
        this.stateCache = Lists.newArrayList();
        this.stateCache.add(fluid.getSource(false));

        for (int i = 1; i < 8; ++i) {
            this.stateCache.add(fluid.getFlowing(8 - i, false));
        }

        this.stateCache.add(fluid.getFlowing(8, true));
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(LiquidBlock.LEVEL, 0));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.alwaysCollideWithFluid() ? Shapes.block() : (context.isAbove(LiquidBlock.SHAPE_STABLE, pos, true) && (Integer) state.getValue(LiquidBlock.LEVEL) == 0 && context.canStandOnFluid(level.getFluidState(pos.above()), state.getFluidState()) ? LiquidBlock.SHAPE_STABLE : Shapes.empty());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getFluidState().isRandomlyTicking();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        state.getFluidState().randomTick(level, pos, random);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !this.fluid.is(FluidTags.LAVA);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        int i = (Integer) state.getValue(LiquidBlock.LEVEL);

        return (FluidState) this.stateCache.get(Math.min(i, 8));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.getFluidState().getType().isSame(this.fluid);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.emptyList();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (this.shouldSpreadLiquid(level, pos, state)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getFluidState().isSource() || neighbourState.getFluidState().isSource()) {
            ticks.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (this.shouldSpreadLiquid(level, pos, state)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }

    }

    private boolean shouldSpreadLiquid(Level level, BlockPos pos, BlockState state) {
        if (this.fluid.is(FluidTags.LAVA)) {
            boolean flag = level.getBlockState(pos.below()).is(Blocks.SOUL_SOIL);
            UnmodifiableIterator unmodifiableiterator = LiquidBlock.POSSIBLE_FLOW_DIRECTIONS.iterator();

            while (unmodifiableiterator.hasNext()) {
                Direction direction = (Direction) unmodifiableiterator.next();
                BlockPos blockpos1 = pos.relative(direction.getOpposite());

                if (level.getFluidState(blockpos1).is(FluidTags.WATER)) {
                    Block block = level.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;

                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                    this.fizz(level, pos);
                    return false;
                }

                if (flag && level.getBlockState(blockpos1).is(Blocks.BLUE_ICE)) {
                    level.setBlockAndUpdate(pos, Blocks.BASALT.defaultBlockState());
                    this.fizz(level, pos);
                    return false;
                }
            }
        }

        return true;
    }

    private void fizz(LevelAccessor level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LiquidBlock.LEVEL);
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        if ((Integer) state.getValue(LiquidBlock.LEVEL) == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            return new ItemStack(this.fluid.getBucket());
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return this.fluid.getPickupSound();
    }
}
