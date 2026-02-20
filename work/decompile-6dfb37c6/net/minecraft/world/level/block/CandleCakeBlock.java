package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleCakeBlock extends AbstractCandleBlock {

    public static final MapCodec<CandleCakeBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("candle").forGetter((candlecakeblock) -> {
            return candlecakeblock.candleBlock;
        }), propertiesCodec()).apply(instance, CandleCakeBlock::new);
    });
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.column(2.0D, 8.0D, 14.0D), Block.column(14.0D, 0.0D, 8.0D));
    private static final Map<CandleBlock, CandleCakeBlock> BY_CANDLE = Maps.newHashMap();
    private static final Iterable<Vec3> PARTICLE_OFFSETS = List.of((new Vec3(8.0D, 16.0D, 8.0D)).scale(0.0625D));
    private final CandleBlock candleBlock;

    @Override
    public MapCodec<CandleCakeBlock> codec() {
        return CandleCakeBlock.CODEC;
    }

    protected CandleCakeBlock(Block block, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(CandleCakeBlock.LIT, false));
        if (block instanceof CandleBlock candleblock) {
            CandleCakeBlock.BY_CANDLE.put(candleblock, this);
            this.candleBlock = candleblock;
        } else {
            String s = String.valueOf(CandleBlock.class);

            throw new IllegalArgumentException("Expected block to be of " + s + " was " + String.valueOf(block.getClass()));
        }
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState state) {
        return CandleCakeBlock.PARTICLE_OFFSETS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CandleCakeBlock.SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!itemStack.is(Items.FLINT_AND_STEEL) && !itemStack.is(Items.FIRE_CHARGE)) {
            if (candleHit(hitResult) && itemStack.isEmpty() && (Boolean) state.getValue(CandleCakeBlock.LIT)) {
                extinguish(player, state, level, pos);
                return InteractionResult.SUCCESS;
            } else {
                return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        InteractionResult interactionresult = CakeBlock.eat(level, pos, Blocks.CAKE.defaultBlockState(), player);

        if (interactionresult.consumesAction()) {
            dropResources(state, level, pos);
        }

        return interactionresult;
    }

    private static boolean candleHit(BlockHitResult hitResult) {
        return hitResult.getLocation().y - (double) hitResult.getBlockPos().getY() > 0.5D;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CandleCakeBlock.LIT);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Blocks.CAKE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return CakeBlock.FULL_CAKE_SIGNAL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    public static BlockState byCandle(CandleBlock block) {
        return ((CandleCakeBlock) CandleCakeBlock.BY_CANDLE.get(block)).defaultBlockState();
    }

    public static boolean canLight(BlockState state) {
        return state.is(BlockTags.CANDLE_CAKES, (blockbehaviour_blockstatebase) -> {
            return blockbehaviour_blockstatebase.hasProperty(CandleCakeBlock.LIT) && !(Boolean) state.getValue(CandleCakeBlock.LIT);
        });
    }
}
