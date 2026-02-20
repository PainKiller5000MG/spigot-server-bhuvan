package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
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
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class PointedDripstoneBlock extends Block implements SimpleWaterloggedBlock, Fallable {

    public static final MapCodec<PointedDripstoneBlock> CODEC = simpleCodec(PointedDripstoneBlock::new);
    public static final EnumProperty<Direction> TIP_DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
    public static final EnumProperty<DripstoneThickness> THICKNESS = BlockStateProperties.DRIPSTONE_THICKNESS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE = 11;
    private static final int DELAY_BEFORE_FALLING = 2;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK = 0.02F;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE = 0.12F;
    private static final int MAX_SEARCH_LENGTH_BETWEEN_STALACTITE_TIP_AND_CAULDRON = 11;
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F;
    private static final float LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F;
    private static final double MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE = 0.6D;
    private static final float STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE = 1.0F;
    private static final int STALACTITE_MAX_DAMAGE = 40;
    private static final int MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION = 6;
    private static final float STALAGMITE_FALL_DISTANCE_OFFSET = 2.5F;
    private static final int STALAGMITE_FALL_DAMAGE_MODIFIER = 2;
    private static final float AVERAGE_DAYS_PER_GROWTH = 5.0F;
    private static final float GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;
    private static final int MAX_GROWTH_LENGTH = 7;
    private static final int MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING = 10;
    private static final VoxelShape SHAPE_TIP_MERGE = Block.column(6.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_TIP_UP = Block.column(6.0D, 0.0D, 11.0D);
    private static final VoxelShape SHAPE_TIP_DOWN = Block.column(6.0D, 5.0D, 16.0D);
    private static final VoxelShape SHAPE_FRUSTUM = Block.column(8.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_MIDDLE = Block.column(10.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_BASE = Block.column(12.0D, 0.0D, 16.0D);
    private static final double STALACTITE_DRIP_START_PIXEL = PointedDripstoneBlock.SHAPE_TIP_DOWN.min(Direction.Axis.Y);
    private static final float MAX_HORIZONTAL_OFFSET = (float) PointedDripstoneBlock.SHAPE_BASE.min(Direction.Axis.X);
    private static final VoxelShape REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK = Block.column(4.0D, 0.0D, 16.0D);

    @Override
    public MapCodec<PointedDripstoneBlock> codec() {
        return PointedDripstoneBlock.CODEC;
    }

    public PointedDripstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) (this.stateDefinition.any()).setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)).setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)).setValue(PointedDripstoneBlock.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PointedDripstoneBlock.TIP_DIRECTION, PointedDripstoneBlock.THICKNESS, PointedDripstoneBlock.WATERLOGGED);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isValidPointedDripstonePlacement(level, pos, (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if ((Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED)) {
            ticks.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (directionToNeighbour != Direction.UP && directionToNeighbour != Direction.DOWN) {
            return state;
        } else {
            Direction direction1 = (Direction) state.getValue(PointedDripstoneBlock.TIP_DIRECTION);

            if (direction1 == Direction.DOWN && ticks.getBlockTicks().hasScheduledTick(pos, this)) {
                return state;
            } else if (directionToNeighbour == direction1.getOpposite() && !this.canSurvive(state, level, pos)) {
                if (direction1 == Direction.DOWN) {
                    ticks.scheduleTick(pos, (Block) this, 2);
                } else {
                    ticks.scheduleTick(pos, (Block) this, 1);
                }

                return state;
            } else {
                boolean flag = state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP_MERGE;
                DripstoneThickness dripstonethickness = calculateDripstoneThickness(level, pos, direction1, flag);

                return (BlockState) state.setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness);
            }
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
        if (!level.isClientSide()) {
            BlockPos blockpos = blockHit.getBlockPos();

            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel) level;

                if (projectile.mayInteract(serverlevel, blockpos) && projectile.mayBreak(serverlevel) && projectile instanceof ThrownTrident && projectile.getDeltaMovement().length() > 0.6D) {
                    level.destroyBlock(blockpos, true);
                }
            }

        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        if (state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP && state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP) {
            entity.causeFallDamage(fallDistance + 2.5D, 2.0F, level.damageSources().stalagmite());
        } else {
            super.fallOn(level, state, pos, entity, fallDistance);
        }

    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (canDrip(state)) {
            float f = random.nextFloat();

            if (f <= 0.12F) {
                getFluidAboveStalactite(level, pos, state).filter((pointeddripstoneblock_fluidinfo) -> {
                    return f < 0.02F || canFillCauldron(pointeddripstoneblock_fluidinfo.fluid);
                }).ifPresent((pointeddripstoneblock_fluidinfo) -> {
                    spawnDripParticle(level, pos, state, pointeddripstoneblock_fluidinfo.fluid, pointeddripstoneblock_fluidinfo.pos);
                });
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isStalagmite(state) && !this.canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        } else {
            spawnFallingStalactite(state, level, pos);
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        maybeTransferFluid(state, level, pos, random.nextFloat());
        if (random.nextFloat() < 0.011377778F && isStalactiteStartPos(state, level, pos)) {
            growStalactiteOrStalagmiteIfPossible(state, level, pos, random);
        }

    }

    @VisibleForTesting
    public static void maybeTransferFluid(BlockState state, ServerLevel level, BlockPos pos, float randomValue) {
        if (randomValue <= 0.17578125F || randomValue <= 0.05859375F) {
            if (isStalactiteStartPos(state, level, pos)) {
                Optional<PointedDripstoneBlock.FluidInfo> optional = getFluidAboveStalactite(level, pos, state);

                if (!optional.isEmpty()) {
                    Fluid fluid = ((PointedDripstoneBlock.FluidInfo) optional.get()).fluid;
                    float f1;

                    if (fluid == Fluids.WATER) {
                        f1 = 0.17578125F;
                    } else {
                        if (fluid != Fluids.LAVA) {
                            return;
                        }

                        f1 = 0.05859375F;
                    }

                    if (randomValue < f1) {
                        BlockPos blockpos1 = findTip(state, level, pos, 11, false);

                        if (blockpos1 != null) {
                            if (((PointedDripstoneBlock.FluidInfo) optional.get()).sourceState.is(Blocks.MUD) && fluid == Fluids.WATER) {
                                BlockState blockstate1 = Blocks.CLAY.defaultBlockState();

                                level.setBlockAndUpdate(((PointedDripstoneBlock.FluidInfo) optional.get()).pos, blockstate1);
                                Block.pushEntitiesUp(((PointedDripstoneBlock.FluidInfo) optional.get()).sourceState, blockstate1, level, ((PointedDripstoneBlock.FluidInfo) optional.get()).pos);
                                level.gameEvent(GameEvent.BLOCK_CHANGE, ((PointedDripstoneBlock.FluidInfo) optional.get()).pos, GameEvent.Context.of(blockstate1));
                                level.levelEvent(1504, blockpos1, 0);
                            } else {
                                BlockPos blockpos2 = findFillableCauldronBelowStalactiteTip(level, blockpos1, fluid);

                                if (blockpos2 != null) {
                                    level.levelEvent(1504, blockpos1, 0);
                                    int i = blockpos1.getY() - blockpos2.getY();
                                    int j = 50 + i;
                                    BlockState blockstate2 = level.getBlockState(blockpos2);

                                    level.scheduleTick(blockpos2, blockstate2.getBlock(), j);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelAccessor levelaccessor = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        Direction direction = context.getNearestLookingVerticalDirection().getOpposite();
        Direction direction1 = calculateTipDirection(levelaccessor, blockpos, direction);

        if (direction1 == null) {
            return null;
        } else {
            boolean flag = !context.isSecondaryUseActive();
            DripstoneThickness dripstonethickness = calculateDripstoneThickness(levelaccessor, blockpos, direction1, flag);

            return (BlockState) ((BlockState) ((BlockState) this.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, direction1)).setValue(PointedDripstoneBlock.THICKNESS, dripstonethickness)).setValue(PointedDripstoneBlock.WATERLOGGED, levelaccessor.getFluidState(blockpos).getType() == Fluids.WATER);
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return (Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape;

        switch ((DripstoneThickness) state.getValue(PointedDripstoneBlock.THICKNESS)) {
            case TIP_MERGE:
                voxelshape = PointedDripstoneBlock.SHAPE_TIP_MERGE;
                break;
            case TIP:
                voxelshape = state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN ? PointedDripstoneBlock.SHAPE_TIP_DOWN : PointedDripstoneBlock.SHAPE_TIP_UP;
                break;
            case FRUSTUM:
                voxelshape = PointedDripstoneBlock.SHAPE_FRUSTUM;
                break;
            case MIDDLE:
                voxelshape = PointedDripstoneBlock.SHAPE_MIDDLE;
                break;
            case BASE:
                voxelshape = PointedDripstoneBlock.SHAPE_BASE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        VoxelShape voxelshape1 = voxelshape;

        return voxelshape1.move(state.getOffset(pos));
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected float getMaxHorizontalOffset() {
        return PointedDripstoneBlock.MAX_HORIZONTAL_OFFSET;
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        if (!entity.isSilent()) {
            level.levelEvent(1045, pos, 0);
        }

    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().fallingStalactite(entity);
    }

    private static void spawnFallingStalactite(BlockState state, ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        for (BlockState blockstate1 = state; isStalactite(blockstate1); blockstate1 = level.getBlockState(blockpos_mutableblockpos)) {
            FallingBlockEntity fallingblockentity = FallingBlockEntity.fall(level, blockpos_mutableblockpos, blockstate1);

            if (isTip(blockstate1, true)) {
                int i = Math.max(1 + pos.getY() - blockpos_mutableblockpos.getY(), 6);
                float f = 1.0F * (float) i;

                fallingblockentity.setHurtsEntities(f, 40);
                break;
            }

            blockpos_mutableblockpos.move(Direction.DOWN);
        }

    }

    @VisibleForTesting
    public static void growStalactiteOrStalagmiteIfPossible(BlockState stalactiteStartState, ServerLevel level, BlockPos stalactiteStartPos, RandomSource random) {
        BlockState blockstate1 = level.getBlockState(stalactiteStartPos.above(1));
        BlockState blockstate2 = level.getBlockState(stalactiteStartPos.above(2));

        if (canGrow(blockstate1, blockstate2)) {
            BlockPos blockpos1 = findTip(stalactiteStartState, level, stalactiteStartPos, 7, false);

            if (blockpos1 != null) {
                BlockState blockstate3 = level.getBlockState(blockpos1);

                if (canDrip(blockstate3) && canTipGrow(blockstate3, level, blockpos1)) {
                    if (random.nextBoolean()) {
                        grow(level, blockpos1, Direction.DOWN);
                    } else {
                        growStalagmiteBelow(level, blockpos1);
                    }

                }
            }
        }
    }

    private static void growStalagmiteBelow(ServerLevel level, BlockPos posAboveStalagmite) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = posAboveStalagmite.mutable();

        for (int i = 0; i < 10; ++i) {
            blockpos_mutableblockpos.move(Direction.DOWN);
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

            if (!blockstate.getFluidState().isEmpty()) {
                return;
            }

            if (isUnmergedTipWithDirection(blockstate, Direction.UP) && canTipGrow(blockstate, level, blockpos_mutableblockpos)) {
                grow(level, blockpos_mutableblockpos, Direction.UP);
                return;
            }

            if (isValidPointedDripstonePlacement(level, blockpos_mutableblockpos, Direction.UP) && !level.isWaterAt(blockpos_mutableblockpos.below())) {
                grow(level, blockpos_mutableblockpos.below(), Direction.UP);
                return;
            }

            if (!canDripThrough(level, blockpos_mutableblockpos, blockstate)) {
                return;
            }
        }

    }

    private static void grow(ServerLevel level, BlockPos growFromPos, Direction growToDirection) {
        BlockPos blockpos1 = growFromPos.relative(growToDirection);
        BlockState blockstate = level.getBlockState(blockpos1);

        if (isUnmergedTipWithDirection(blockstate, growToDirection.getOpposite())) {
            createMergedTips(blockstate, level, blockpos1);
        } else if (blockstate.isAir() || blockstate.is(Blocks.WATER)) {
            createDripstone(level, blockpos1, growToDirection, DripstoneThickness.TIP);
        }

    }

    private static void createDripstone(LevelAccessor level, BlockPos pos, Direction direction, DripstoneThickness thickness) {
        BlockState blockstate = (BlockState) ((BlockState) ((BlockState) Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)).setValue(PointedDripstoneBlock.THICKNESS, thickness)).setValue(PointedDripstoneBlock.WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);

        level.setBlock(pos, blockstate, 3);
    }

    private static void createMergedTips(BlockState tipState, LevelAccessor level, BlockPos tipPos) {
        BlockPos blockpos1;
        BlockPos blockpos2;

        if (tipState.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP) {
            blockpos2 = tipPos;
            blockpos1 = tipPos.above();
        } else {
            blockpos1 = tipPos;
            blockpos2 = tipPos.below();
        }

        createDripstone(level, blockpos1, Direction.DOWN, DripstoneThickness.TIP_MERGE);
        createDripstone(level, blockpos2, Direction.UP, DripstoneThickness.TIP_MERGE);
    }

    public static void spawnDripParticle(Level level, BlockPos stalactiteTipPos, BlockState stalactiteTipState) {
        getFluidAboveStalactite(level, stalactiteTipPos, stalactiteTipState).ifPresent((pointeddripstoneblock_fluidinfo) -> {
            spawnDripParticle(level, stalactiteTipPos, stalactiteTipState, pointeddripstoneblock_fluidinfo.fluid, pointeddripstoneblock_fluidinfo.pos);
        });
    }

    private static void spawnDripParticle(Level level, BlockPos stalactiteTipPos, BlockState stalactiteTipState, Fluid fluidAbove, BlockPos posAbove) {
        Vec3 vec3 = stalactiteTipState.getOffset(stalactiteTipPos);
        double d0 = 0.0625D;
        double d1 = (double) stalactiteTipPos.getX() + 0.5D + vec3.x;
        double d2 = (double) stalactiteTipPos.getY() + PointedDripstoneBlock.STALACTITE_DRIP_START_PIXEL - 0.0625D;
        double d3 = (double) stalactiteTipPos.getZ() + 0.5D + vec3.z;
        ParticleOptions particleoptions = getDripParticle(level, fluidAbove, posAbove);

        level.addParticle(particleoptions, d1, d2, d3, 0.0D, 0.0D, 0.0D);
    }

    private static @Nullable BlockPos findTip(BlockState dripstoneState, LevelAccessor level, BlockPos dripstonePos, int maxSearchLength, boolean includeMergedTip) {
        if (isTip(dripstoneState, includeMergedTip)) {
            return dripstonePos;
        } else {
            Direction direction = (Direction) dripstoneState.getValue(PointedDripstoneBlock.TIP_DIRECTION);
            BiPredicate<BlockPos, BlockState> bipredicate = (blockpos1, blockstate1) -> {
                return blockstate1.is(Blocks.POINTED_DRIPSTONE) && blockstate1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == direction;
            };

            return (BlockPos) findBlockVertical(level, dripstonePos, direction.getAxisDirection(), bipredicate, (blockstate1) -> {
                return isTip(blockstate1, includeMergedTip);
            }, maxSearchLength).orElse((Object) null);
        }
    }

    private static @Nullable Direction calculateTipDirection(LevelReader level, BlockPos pos, Direction defaultTipDirection) {
        Direction direction1;

        if (isValidPointedDripstonePlacement(level, pos, defaultTipDirection)) {
            direction1 = defaultTipDirection;
        } else {
            if (!isValidPointedDripstonePlacement(level, pos, defaultTipDirection.getOpposite())) {
                return null;
            }

            direction1 = defaultTipDirection.getOpposite();
        }

        return direction1;
    }

    private static DripstoneThickness calculateDripstoneThickness(LevelReader level, BlockPos pos, Direction tipDirection, boolean mergeOpposingTips) {
        Direction direction1 = tipDirection.getOpposite();
        BlockState blockstate = level.getBlockState(pos.relative(tipDirection));

        if (isPointedDripstoneWithDirection(blockstate, direction1)) {
            return !mergeOpposingTips && blockstate.getValue(PointedDripstoneBlock.THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        } else if (!isPointedDripstoneWithDirection(blockstate, tipDirection)) {
            return DripstoneThickness.TIP;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) blockstate.getValue(PointedDripstoneBlock.THICKNESS);

            if (dripstonethickness != DripstoneThickness.TIP && dripstonethickness != DripstoneThickness.TIP_MERGE) {
                BlockState blockstate1 = level.getBlockState(pos.relative(direction1));

                return !isPointedDripstoneWithDirection(blockstate1, tipDirection) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            } else {
                return DripstoneThickness.FRUSTUM;
            }
        }
    }

    public static boolean canDrip(BlockState state) {
        return isStalactite(state) && state.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP && !(Boolean) state.getValue(PointedDripstoneBlock.WATERLOGGED);
    }

    private static boolean canTipGrow(BlockState tipState, ServerLevel level, BlockPos tipPos) {
        Direction direction = (Direction) tipState.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BlockPos blockpos1 = tipPos.relative(direction);
        BlockState blockstate1 = level.getBlockState(blockpos1);

        return !blockstate1.getFluidState().isEmpty() ? false : (blockstate1.isAir() ? true : isUnmergedTipWithDirection(blockstate1, direction.getOpposite()));
    }

    private static Optional<BlockPos> findRootBlock(Level level, BlockPos pos, BlockState dripStoneState, int maxSearchLength) {
        Direction direction = (Direction) dripStoneState.getValue(PointedDripstoneBlock.TIP_DIRECTION);
        BiPredicate<BlockPos, BlockState> bipredicate = (blockpos1, blockstate1) -> {
            return blockstate1.is(Blocks.POINTED_DRIPSTONE) && blockstate1.getValue(PointedDripstoneBlock.TIP_DIRECTION) == direction;
        };

        return findBlockVertical(level, pos, direction.getOpposite().getAxisDirection(), bipredicate, (blockstate1) -> {
            return !blockstate1.is(Blocks.POINTED_DRIPSTONE);
        }, maxSearchLength);
    }

    private static boolean isValidPointedDripstonePlacement(LevelReader level, BlockPos pos, Direction tipDirection) {
        BlockPos blockpos1 = pos.relative(tipDirection.getOpposite());
        BlockState blockstate = level.getBlockState(blockpos1);

        return blockstate.isFaceSturdy(level, blockpos1, tipDirection) || isPointedDripstoneWithDirection(blockstate, tipDirection);
    }

    private static boolean isTip(BlockState state, boolean includeMergedTip) {
        if (!state.is(Blocks.POINTED_DRIPSTONE)) {
            return false;
        } else {
            DripstoneThickness dripstonethickness = (DripstoneThickness) state.getValue(PointedDripstoneBlock.THICKNESS);

            return dripstonethickness == DripstoneThickness.TIP || includeMergedTip && dripstonethickness == DripstoneThickness.TIP_MERGE;
        }
    }

    private static boolean isUnmergedTipWithDirection(BlockState state, Direction tipDirection) {
        return isTip(state, false) && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == tipDirection;
    }

    private static boolean isStalactite(BlockState state) {
        return isPointedDripstoneWithDirection(state, Direction.DOWN);
    }

    private static boolean isStalagmite(BlockState state) {
        return isPointedDripstoneWithDirection(state, Direction.UP);
    }

    private static boolean isStalactiteStartPos(BlockState state, LevelReader level, BlockPos pos) {
        return isStalactite(state) && !level.getBlockState(pos.above()).is(Blocks.POINTED_DRIPSTONE);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    private static boolean isPointedDripstoneWithDirection(BlockState blockState, Direction tipDirection) {
        return blockState.is(Blocks.POINTED_DRIPSTONE) && blockState.getValue(PointedDripstoneBlock.TIP_DIRECTION) == tipDirection;
    }

    private static @Nullable BlockPos findFillableCauldronBelowStalactiteTip(Level level, BlockPos stalactiteTipPos, Fluid fluid) {
        Predicate<BlockState> predicate = (blockstate) -> {
            return blockstate.getBlock() instanceof AbstractCauldronBlock && ((AbstractCauldronBlock) blockstate.getBlock()).canReceiveStalactiteDrip(fluid);
        };
        BiPredicate<BlockPos, BlockState> bipredicate = (blockpos1, blockstate) -> {
            return canDripThrough(level, blockpos1, blockstate);
        };

        return (BlockPos) findBlockVertical(level, stalactiteTipPos, Direction.DOWN.getAxisDirection(), bipredicate, predicate, 11).orElse((Object) null);
    }

    public static @Nullable BlockPos findStalactiteTipAboveCauldron(Level level, BlockPos cauldronPos) {
        BiPredicate<BlockPos, BlockState> bipredicate = (blockpos1, blockstate) -> {
            return canDripThrough(level, blockpos1, blockstate);
        };

        return (BlockPos) findBlockVertical(level, cauldronPos, Direction.UP.getAxisDirection(), bipredicate, PointedDripstoneBlock::canDrip, 11).orElse((Object) null);
    }

    public static Fluid getCauldronFillFluidType(ServerLevel level, BlockPos stalactitePos) {
        return (Fluid) getFluidAboveStalactite(level, stalactitePos, level.getBlockState(stalactitePos)).map((pointeddripstoneblock_fluidinfo) -> {
            return pointeddripstoneblock_fluidinfo.fluid;
        }).filter(PointedDripstoneBlock::canFillCauldron).orElse(Fluids.EMPTY);
    }

    private static Optional<PointedDripstoneBlock.FluidInfo> getFluidAboveStalactite(Level level, BlockPos stalactitePos, BlockState stalactiteState) {
        return !isStalactite(stalactiteState) ? Optional.empty() : findRootBlock(level, stalactitePos, stalactiteState, 11).map((blockpos1) -> {
            BlockPos blockpos2 = blockpos1.above();
            BlockState blockstate1 = level.getBlockState(blockpos2);
            Fluid fluid;

            if (blockstate1.is(Blocks.MUD) && !(Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, blockpos2)) {
                fluid = Fluids.WATER;
            } else {
                fluid = level.getFluidState(blockpos2).getType();
            }

            return new PointedDripstoneBlock.FluidInfo(blockpos2, fluid, blockstate1);
        });
    }

    private static boolean canFillCauldron(Fluid fluidAbove) {
        return fluidAbove == Fluids.LAVA || fluidAbove == Fluids.WATER;
    }

    private static boolean canGrow(BlockState rootState, BlockState aboveState) {
        return rootState.is(Blocks.DRIPSTONE_BLOCK) && aboveState.is(Blocks.WATER) && aboveState.getFluidState().isSource();
    }

    private static ParticleOptions getDripParticle(Level level, Fluid fluidAbove, BlockPos posAbove) {
        return (ParticleOptions) (fluidAbove.isSame(Fluids.EMPTY) ? (ParticleOptions) level.environmentAttributes().getValue(EnvironmentAttributes.DEFAULT_DRIPSTONE_PARTICLE, posAbove) : (fluidAbove.is(FluidTags.LAVA) ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER));
    }

    private static Optional<BlockPos> findBlockVertical(LevelAccessor level, BlockPos pos, Direction.AxisDirection axisDirection, BiPredicate<BlockPos, BlockState> pathPredicate, Predicate<BlockState> targetPredicate, int maxSteps) {
        Direction direction = Direction.get(axisDirection, Direction.Axis.Y);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        for (int j = 1; j < maxSteps; ++j) {
            blockpos_mutableblockpos.move(direction);
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

            if (targetPredicate.test(blockstate)) {
                return Optional.of(blockpos_mutableblockpos.immutable());
            }

            if (level.isOutsideBuildHeight(blockpos_mutableblockpos.getY()) || !pathPredicate.test(blockpos_mutableblockpos, blockstate)) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean canDripThrough(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        } else if (state.isSolidRender()) {
            return false;
        } else if (!state.getFluidState().isEmpty()) {
            return false;
        } else {
            VoxelShape voxelshape = state.getCollisionShape(level, pos);

            return !Shapes.joinIsNotEmpty(PointedDripstoneBlock.REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK, voxelshape, BooleanOp.AND);
        }
    }

    static record FluidInfo(BlockPos pos, Fluid fluid, BlockState sourceState) {

    }
}
