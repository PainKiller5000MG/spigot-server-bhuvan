package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleBlock extends AbstractCandleBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<CandleBlock> CODEC = simpleCodec(CandleBlock::new);
    public static final int MIN_CANDLES = 1;
    public static final int MAX_CANDLES = 4;
    public static final IntegerProperty CANDLES = BlockStateProperties.CANDLES;
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final ToIntFunction<BlockState> LIGHT_EMISSION = (blockstate) -> {
        return (Boolean) blockstate.getValue(CandleBlock.LIT) ? 3 * (Integer) blockstate.getValue(CandleBlock.CANDLES) : 0;
    };
    private static final Int2ObjectMap<List<Vec3>> PARTICLE_OFFSETS = (Int2ObjectMap) Util.make(new Int2ObjectOpenHashMap(4), (int2objectopenhashmap) -> {
        float f = 0.0625F;

        int2objectopenhashmap.put(1, List.of((new Vec3(8.0D, 8.0D, 8.0D)).scale(0.0625D)));
        int2objectopenhashmap.put(2, List.of((new Vec3(6.0D, 7.0D, 8.0D)).scale(0.0625D), (new Vec3(10.0D, 8.0D, 7.0D)).scale(0.0625D)));
        int2objectopenhashmap.put(3, List.of((new Vec3(8.0D, 5.0D, 10.0D)).scale(0.0625D), (new Vec3(6.0D, 7.0D, 8.0D)).scale(0.0625D), (new Vec3(9.0D, 8.0D, 7.0D)).scale(0.0625D)));
        int2objectopenhashmap.put(4, List.of((new Vec3(7.0D, 5.0D, 9.0D)).scale(0.0625D), (new Vec3(10.0D, 7.0D, 9.0D)).scale(0.0625D), (new Vec3(6.0D, 7.0D, 6.0D)).scale(0.0625D), (new Vec3(9.0D, 8.0D, 6.0D)).scale(0.0625D)));
    });
    private static final VoxelShape[] SHAPES = new VoxelShape[]{Block.column(2.0D, 0.0D, 6.0D), Block.box(5.0D, 0.0D, 6.0D, 11.0D, 6.0D, 9.0D), Block.box(5.0D, 0.0D, 6.0D, 10.0D, 6.0D, 11.0D), Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 10.0D)};

    @Override
    public MapCodec<CandleBlock> codec() {
        return CandleBlock.CODEC;
    }

    public CandleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(CandleBlock.CANDLES, 1)).setValue(CandleBlock.LIT, false)).setValue(CandleBlock.WATERLOGGED, false));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (itemStack.isEmpty() && player.getAbilities().mayBuild && (Boolean) state.getValue(CandleBlock.LIT)) {
            extinguish(player, state, level, pos);
            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().getItem() == this.asItem() && (Integer) state.getValue(CandleBlock.CANDLES) < 4 ? true : super.canBeReplaced(state, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());

        if (blockstate.is(this)) {
            return (BlockState) blockstate.cycle(CandleBlock.CANDLES);
        } else {
            FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
            boolean flag = fluidstate.getType() == Fluids.WATER;

            return (BlockState) super.getStateForPlacement(context).setValue(CandleBlock.WATERLOGGED, flag);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(CandleBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(CandleBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CandleBlock.SHAPES[(Integer) state.getValue(CandleBlock.CANDLES) - 1];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CandleBlock.CANDLES, CandleBlock.LIT, CandleBlock.WATERLOGGED);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!(Boolean) state.getValue(CandleBlock.WATERLOGGED) && fluidState.getType() == Fluids.WATER) {
            BlockState blockstate1 = (BlockState) state.setValue(CandleBlock.WATERLOGGED, true);

            if ((Boolean) state.getValue(CandleBlock.LIT)) {
                extinguish((Player) null, blockstate1, level, pos);
            } else {
                level.setBlock(pos, blockstate1, 3);
            }

            level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
            return true;
        } else {
            return false;
        }
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CANDLES, (blockbehaviour_blockstatebase) -> {
            return blockbehaviour_blockstatebase.hasProperty(CandleBlock.LIT) && blockbehaviour_blockstatebase.hasProperty(CandleBlock.WATERLOGGED);
        }) && !(Boolean) state.getValue(CandleBlock.LIT) && !(Boolean) state.getValue(CandleBlock.WATERLOGGED);
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState state) {
        return (Iterable) CandleBlock.PARTICLE_OFFSETS.get((Integer) state.getValue(CandleBlock.CANDLES));
    }

    @Override
    protected boolean canBeLit(BlockState state) {
        return !(Boolean) state.getValue(CandleBlock.WATERLOGGED) && super.canBeLit(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }
}
