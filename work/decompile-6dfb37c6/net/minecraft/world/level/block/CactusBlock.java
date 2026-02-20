package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusBlock extends Block {

    public static final MapCodec<CactusBlock> CODEC = simpleCodec(CactusBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final int MAX_AGE = 15;
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_COLLISION = Block.column(14.0D, 0.0D, 15.0D);
    private static final int MAX_CACTUS_GROWING_HEIGHT = 3;
    private static final int ATTEMPT_GROW_CACTUS_FLOWER_AGE = 8;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_SMALL_CACTUS_CHANCE = 0.1D;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_TALL_CACTUS_CHANCE = 0.25D;

    @Override
    public MapCodec<CactusBlock> codec() {
        return CactusBlock.CODEC;
    }

    protected CactusBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) (this.stateDefinition.any()).setValue(CactusBlock.AGE, 0));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockpos1 = pos.above();

        if (level.isEmptyBlock(blockpos1)) {
            int i = 1;
            int j = (Integer) state.getValue(CactusBlock.AGE);

            while (level.getBlockState(pos.below(i)).is(this)) {
                ++i;
                if (i == 3 && j == 15) {
                    return;
                }
            }

            if (j == 8 && this.canSurvive(this.defaultBlockState(), level, pos.above())) {
                double d0 = i >= 3 ? 0.25D : 0.1D;

                if (random.nextDouble() <= d0) {
                    level.setBlockAndUpdate(blockpos1, Blocks.CACTUS_FLOWER.defaultBlockState());
                }
            } else if (j == 15 && i < 3) {
                level.setBlockAndUpdate(blockpos1, this.defaultBlockState());
                BlockState blockstate1 = (BlockState) state.setValue(CactusBlock.AGE, 0);

                level.setBlock(pos, blockstate1, 260);
                level.neighborChanged(blockstate1, blockpos1, this, (Orientation) null, false);
            }

            if (j < 15) {
                level.setBlock(pos, (BlockState) state.setValue(CactusBlock.AGE, j + 1), 260);
            }

        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CactusBlock.SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CactusBlock.SHAPE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, (Block) this, 1);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockstate1 = level.getBlockState(pos.relative(direction));

            if (blockstate1.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return false;
            }
        }

        BlockState blockstate2 = level.getBlockState(pos.below());

        return (blockstate2.is(Blocks.CACTUS) || blockstate2.is(BlockTags.SAND)) && !level.getBlockState(pos.above()).liquid();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        entity.hurt(level.damageSources().cactus(), 1.0F);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CactusBlock.AGE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
