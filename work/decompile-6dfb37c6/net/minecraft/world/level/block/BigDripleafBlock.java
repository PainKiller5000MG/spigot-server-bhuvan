package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.Tilt;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BigDripleafBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, BonemealableBlock {

    public static final MapCodec<BigDripleafBlock> CODEC = simpleCodec(BigDripleafBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final EnumProperty<Tilt> TILT = BlockStateProperties.TILT;
    private static final int NO_TICK = -1;
    private static final Object2IntMap<Tilt> DELAY_UNTIL_NEXT_TILT_STATE = (Object2IntMap) Util.make(new Object2IntArrayMap(), (object2intarraymap) -> {
        object2intarraymap.defaultReturnValue(-1);
        object2intarraymap.put(Tilt.UNSTABLE, 10);
        object2intarraymap.put(Tilt.PARTIAL, 10);
        object2intarraymap.put(Tilt.FULL, 100);
    });
    private static final int MAX_GEN_HEIGHT = 5;
    private static final int ENTITY_DETECTION_MIN_Y = 11;
    private static final int LOWEST_LEAF_TOP = 13;
    private static final Map<Tilt, VoxelShape> SHAPE_LEAF = Maps.newEnumMap(Map.of(Tilt.NONE, Block.column(16.0D, 11.0D, 15.0D), Tilt.UNSTABLE, Block.column(16.0D, 11.0D, 15.0D), Tilt.PARTIAL, Block.column(16.0D, 11.0D, 13.0D), Tilt.FULL, Shapes.empty()));
    private final Function<BlockState, VoxelShape> shapes;

    @Override
    public MapCodec<BigDripleafBlock> codec() {
        return BigDripleafBlock.CODEC;
    }

    protected BigDripleafBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(BigDripleafBlock.WATERLOGGED, false)).setValue(BigDripleafBlock.FACING, Direction.NORTH)).setValue(BigDripleafBlock.TILT, Tilt.NONE));
        this.shapes = this.makeShapes();
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.column(6.0D, 0.0D, 13.0D).move(0.0D, 0.0D, 0.25D).optimize());

        return this.getShapeForEachState((blockstate) -> {
            return Shapes.or((VoxelShape) BigDripleafBlock.SHAPE_LEAF.get(blockstate.getValue(BigDripleafBlock.TILT)), (VoxelShape) map.get(blockstate.getValue(BigDripleafBlock.FACING)));
        }, new Property[]{BigDripleafBlock.WATERLOGGED});
    }

    public static void placeWithRandomHeight(LevelAccessor level, RandomSource random, BlockPos stemBottomPos, Direction facing) {
        int i = Mth.nextInt(random, 2, 5);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = stemBottomPos.mutable();
        int j = 0;

        while (j < i && canPlaceAt(level, blockpos_mutableblockpos, level.getBlockState(blockpos_mutableblockpos))) {
            ++j;
            blockpos_mutableblockpos.move(Direction.UP);
        }

        int k = stemBottomPos.getY() + j - 1;

        blockpos_mutableblockpos.setY(stemBottomPos.getY());

        while (blockpos_mutableblockpos.getY() < k) {
            BigDripleafStemBlock.place(level, blockpos_mutableblockpos, level.getFluidState(blockpos_mutableblockpos), facing);
            blockpos_mutableblockpos.move(Direction.UP);
        }

        place(level, blockpos_mutableblockpos, level.getFluidState(blockpos_mutableblockpos), facing);
    }

    private static boolean canReplace(BlockState oldState) {
        return oldState.isAir() || oldState.is(Blocks.WATER) || oldState.is(Blocks.SMALL_DRIPLEAF);
    }

    protected static boolean canPlaceAt(LevelHeightAccessor level, BlockPos pos, BlockState oldState) {
        return !level.isOutsideBuildHeight(pos) && canReplace(oldState);
    }

    protected static boolean place(LevelAccessor level, BlockPos pos, FluidState fluidState, Direction facing) {
        BlockState blockstate = (BlockState) ((BlockState) Blocks.BIG_DRIPLEAF.defaultBlockState().setValue(BigDripleafBlock.WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))).setValue(BigDripleafBlock.FACING, facing);

        return level.setBlock(pos, blockstate, 3);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
        this.setTiltAndScheduleTick(state, level, blockHit.getBlockPos(), Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(BigDripleafBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.below();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        return blockstate1.is(this) || blockstate1.is(Blocks.BIG_DRIPLEAF_STEM) || blockstate1.is(BlockTags.BIG_DRIPLEAF_PLACEABLE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            if ((Boolean) state.getValue(BigDripleafBlock.WATERLOGGED)) {
                ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            return directionToNeighbour == Direction.UP && neighbourState.is(this) ? Blocks.BIG_DRIPLEAF_STEM.withPropertiesOf(state) : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockState blockstate1 = level.getBlockState(pos.above());

        return canReplace(blockstate1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate1 = level.getBlockState(blockpos1);

        if (canPlaceAt(level, blockpos1, blockstate1)) {
            Direction direction = (Direction) state.getValue(BigDripleafBlock.FACING);

            BigDripleafStemBlock.place(level, pos, state.getFluidState(), direction);
            place(level, blockpos1, blockstate1.getFluidState(), direction);
        }

    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!level.isClientSide()) {
            if (state.getValue(BigDripleafBlock.TILT) == Tilt.NONE && canEntityTilt(pos, entity) && !level.hasNeighborSignal(pos)) {
                this.setTiltAndScheduleTick(state, level, pos, Tilt.UNSTABLE, (SoundEvent) null);
            }

        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.hasNeighborSignal(pos)) {
            resetTilt(state, level, pos);
        } else {
            Tilt tilt = (Tilt) state.getValue(BigDripleafBlock.TILT);

            if (tilt == Tilt.UNSTABLE) {
                this.setTiltAndScheduleTick(state, level, pos, Tilt.PARTIAL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
            } else if (tilt == Tilt.PARTIAL) {
                this.setTiltAndScheduleTick(state, level, pos, Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
            } else if (tilt == Tilt.FULL) {
                resetTilt(state, level, pos);
            }

        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) {
            resetTilt(state, level, pos);
        }

    }

    private static void playTiltSound(Level level, BlockPos pos, SoundEvent tiltSound) {
        float f = Mth.randomBetween(level.random, 0.8F, 1.2F);

        level.playSound((Entity) null, pos, tiltSound, SoundSource.BLOCKS, 1.0F, f);
    }

    private static boolean canEntityTilt(BlockPos pos, Entity entity) {
        return entity.onGround() && entity.position().y > (double) ((float) pos.getY() + 0.6875F);
    }

    private void setTiltAndScheduleTick(BlockState state, Level level, BlockPos pos, Tilt tilt, @Nullable SoundEvent sound) {
        setTilt(state, level, pos, tilt);
        if (sound != null) {
            playTiltSound(level, pos, sound);
        }

        int i = BigDripleafBlock.DELAY_UNTIL_NEXT_TILT_STATE.getInt(tilt);

        if (i != -1) {
            level.scheduleTick(pos, (Block) this, i);
        }

    }

    private static void resetTilt(BlockState state, Level level, BlockPos pos) {
        setTilt(state, level, pos, Tilt.NONE);
        if (state.getValue(BigDripleafBlock.TILT) != Tilt.NONE) {
            playTiltSound(level, pos, SoundEvents.BIG_DRIPLEAF_TILT_UP);
        }

    }

    private static void setTilt(BlockState state, Level level, BlockPos pos, Tilt tilt) {
        Tilt tilt1 = (Tilt) state.getValue(BigDripleafBlock.TILT);

        level.setBlock(pos, (BlockState) state.setValue(BigDripleafBlock.TILT, tilt), 2);
        if (tilt.causesVibration() && tilt != tilt1) {
            level.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_CHANGE, pos);
        }

    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) BigDripleafBlock.SHAPE_LEAF.get(state.getValue(BigDripleafBlock.TILT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (VoxelShape) this.shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos().below());
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = blockstate.is(Blocks.BIG_DRIPLEAF) || blockstate.is(Blocks.BIG_DRIPLEAF_STEM);

        return (BlockState) ((BlockState) this.defaultBlockState().setValue(BigDripleafBlock.WATERLOGGED, fluidstate.isSourceOfType(Fluids.WATER))).setValue(BigDripleafBlock.FACING, flag ? (Direction) blockstate.getValue(BigDripleafBlock.FACING) : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BigDripleafBlock.WATERLOGGED, BigDripleafBlock.FACING, BigDripleafBlock.TILT);
    }
}
