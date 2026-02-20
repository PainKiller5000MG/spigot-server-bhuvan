package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BubbleColumnBlock extends Block implements BucketPickup {

    public static final MapCodec<BubbleColumnBlock> CODEC = simpleCodec(BubbleColumnBlock::new);
    public static final BooleanProperty DRAG_DOWN = BlockStateProperties.DRAG;
    private static final int CHECK_PERIOD = 5;

    @Override
    public MapCodec<BubbleColumnBlock> codec() {
        return BubbleColumnBlock.CODEC;
    }

    public BubbleColumnBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(BubbleColumnBlock.DRAG_DOWN, true));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (isPrecise) {
            BlockState blockstate1 = level.getBlockState(pos.above());
            boolean flag1 = blockstate1.getCollisionShape(level, pos).isEmpty() && blockstate1.getFluidState().isEmpty();

            if (flag1) {
                entity.onAboveBubbleColumn((Boolean) state.getValue(BubbleColumnBlock.DRAG_DOWN), pos);
            } else {
                entity.onInsideBubbleColumn((Boolean) state.getValue(BubbleColumnBlock.DRAG_DOWN));
            }
        }

    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateColumn(level, pos, state, level.getBlockState(pos.below()));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    public static void updateColumn(LevelAccessor level, BlockPos origin, BlockState belowState) {
        updateColumn(level, origin, level.getBlockState(origin), belowState);
    }

    public static void updateColumn(LevelAccessor level, BlockPos origin, BlockState originState, BlockState belowState) {
        if (canExistIn(originState)) {
            BlockState blockstate2 = getColumnState(belowState);

            level.setBlock(origin, blockstate2, 2);
            BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable().move(Direction.UP);

            while (canExistIn(level.getBlockState(blockpos_mutableblockpos))) {
                if (!level.setBlock(blockpos_mutableblockpos, blockstate2, 2)) {
                    return;
                }

                blockpos_mutableblockpos.move(Direction.UP);
            }

        }
    }

    private static boolean canExistIn(BlockState state) {
        return state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.WATER) && state.getFluidState().getAmount() >= 8 && state.getFluidState().isSource();
    }

    private static BlockState getColumnState(BlockState belowState) {
        return belowState.is(Blocks.BUBBLE_COLUMN) ? belowState : (belowState.is(Blocks.SOUL_SAND) ? (BlockState) Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false) : (belowState.is(Blocks.MAGMA_BLOCK) ? (BlockState) Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true) : Blocks.WATER.defaultBlockState()));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d0 = (double) pos.getX();
        double d1 = (double) pos.getY();
        double d2 = (double) pos.getZ();

        if ((Boolean) state.getValue(BubbleColumnBlock.DRAG_DOWN)) {
            level.addAlwaysVisibleParticle(ParticleTypes.CURRENT_DOWN, d0 + 0.5D, d1 + 0.8D, d2, 0.0D, 0.0D, 0.0D);
            if (random.nextInt(200) == 0) {
                level.playLocalSound(d0, d1, d2, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        } else {
            level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, d0 + 0.5D, d1, d2 + 0.5D, 0.0D, 0.04D, 0.0D);
            level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, d0 + (double) random.nextFloat(), d1 + (double) random.nextFloat(), d2 + (double) random.nextFloat(), 0.0D, 0.04D, 0.0D);
            if (random.nextInt(200) == 0) {
                level.playLocalSound(d0, d1, d2, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (!state.canSurvive(level, pos) || directionToNeighbour == Direction.DOWN || directionToNeighbour == Direction.UP && !neighbourState.is(Blocks.BUBBLE_COLUMN) && canExistIn(neighbourState)) {
            ticks.scheduleTick(pos, (Block) this, 5);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate1 = level.getBlockState(pos.below());

        return blockstate1.is(Blocks.BUBBLE_COLUMN) || blockstate1.is(Blocks.MAGMA_BLOCK) || blockstate1.is(Blocks.SOUL_SAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BubbleColumnBlock.DRAG_DOWN);
    }

    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.WATER.getPickupSound();
    }
}
