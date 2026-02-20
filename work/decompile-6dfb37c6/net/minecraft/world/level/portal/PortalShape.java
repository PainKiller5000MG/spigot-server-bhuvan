package net.minecraft.world.level.portal;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

public class PortalShape {

    private static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;
    private static final BlockBehaviour.StatePredicate FRAME = (blockstate, blockgetter, blockpos) -> {
        return blockstate.is(Blocks.OBSIDIAN);
    };
    private static final float SAFE_TRAVEL_MAX_ENTITY_XY = 4.0F;
    private static final double SAFE_TRAVEL_MAX_VERTICAL_DELTA = 1.0D;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private final int numPortalBlocks;
    private final BlockPos bottomLeft;
    private final int height;
    private final int width;

    private PortalShape(Direction.Axis axis, int portalBlockCount, Direction rightDir, BlockPos bottomLeft, int width, int height) {
        this.axis = axis;
        this.numPortalBlocks = portalBlockCount;
        this.rightDir = rightDir;
        this.bottomLeft = bottomLeft;
        this.width = width;
        this.height = height;
    }

    public static Optional<PortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis preferredAxis) {
        return findPortalShape(level, pos, (portalshape) -> {
            return portalshape.isValid() && portalshape.numPortalBlocks == 0;
        }, preferredAxis);
    }

    public static Optional<PortalShape> findPortalShape(LevelAccessor level, BlockPos pos, Predicate<PortalShape> isValid, Direction.Axis preferredAxis) {
        Optional<PortalShape> optional = Optional.of(findAnyShape(level, pos, preferredAxis)).filter(isValid);

        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis direction_axis1 = preferredAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;

            return Optional.of(findAnyShape(level, pos, direction_axis1)).filter(isValid);
        }
    }

    public static PortalShape findAnyShape(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        Direction direction = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        BlockPos blockpos1 = calculateBottomLeft(level, direction, pos);

        if (blockpos1 == null) {
            return new PortalShape(axis, 0, direction, pos, 0, 0);
        } else {
            int i = calculateWidth(level, blockpos1, direction);

            if (i == 0) {
                return new PortalShape(axis, 0, direction, blockpos1, 0, 0);
            } else {
                MutableInt mutableint = new MutableInt();
                int j = calculateHeight(level, blockpos1, direction, i, mutableint);

                return new PortalShape(axis, mutableint.intValue(), direction, blockpos1, i, j);
            }
        }
    }

    private static @Nullable BlockPos calculateBottomLeft(BlockGetter level, Direction rightDir, BlockPos pos) {
        for (int i = Math.max(level.getMinY(), pos.getY() - 21); pos.getY() > i && isEmpty(level.getBlockState(pos.below())); pos = pos.below()) {
            ;
        }

        Direction direction1 = rightDir.getOpposite();
        int j = getDistanceUntilEdgeAboveFrame(level, pos, direction1) - 1;

        return j < 0 ? null : pos.relative(direction1, j);
    }

    private static int calculateWidth(BlockGetter level, BlockPos bottomLeft, Direction rightDir) {
        int i = getDistanceUntilEdgeAboveFrame(level, bottomLeft, rightDir);

        return i >= 2 && i <= 21 ? i : 0;
    }

    private static int getDistanceUntilEdgeAboveFrame(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= 21; ++i) {
            blockpos_mutableblockpos.set(pos).move(direction, i);
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

            if (!isEmpty(blockstate)) {
                if (PortalShape.FRAME.test(blockstate, level, blockpos_mutableblockpos)) {
                    return i;
                }
                break;
            }

            BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos.move(Direction.DOWN));

            if (!PortalShape.FRAME.test(blockstate1, level, blockpos_mutableblockpos)) {
                break;
            }
        }

        return 0;
    }

    private static int calculateHeight(BlockGetter level, BlockPos bottomLeft, Direction rightDir, int width, MutableInt portalBlockCount) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int j = getDistanceUntilTop(level, bottomLeft, rightDir, blockpos_mutableblockpos, width, portalBlockCount);

        return j >= 3 && j <= 21 && hasTopFrame(level, bottomLeft, rightDir, blockpos_mutableblockpos, width, j) ? j : 0;
    }

    private static boolean hasTopFrame(BlockGetter level, BlockPos bottomLeft, Direction rightDir, BlockPos.MutableBlockPos pos, int width, int height) {
        for (int k = 0; k < width; ++k) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = pos.set(bottomLeft).move(Direction.UP, height).move(rightDir, k);

            if (!PortalShape.FRAME.test(level.getBlockState(blockpos_mutableblockpos1), level, blockpos_mutableblockpos1)) {
                return false;
            }
        }

        return true;
    }

    private static int getDistanceUntilTop(BlockGetter level, BlockPos bottomLeft, Direction rightDir, BlockPos.MutableBlockPos pos, int width, MutableInt portalBlockCount) {
        for (int j = 0; j < 21; ++j) {
            pos.set(bottomLeft).move(Direction.UP, j).move(rightDir, -1);
            if (!PortalShape.FRAME.test(level.getBlockState(pos), level, pos)) {
                return j;
            }

            pos.set(bottomLeft).move(Direction.UP, j).move(rightDir, width);
            if (!PortalShape.FRAME.test(level.getBlockState(pos), level, pos)) {
                return j;
            }

            for (int k = 0; k < width; ++k) {
                pos.set(bottomLeft).move(Direction.UP, j).move(rightDir, k);
                BlockState blockstate = level.getBlockState(pos);

                if (!isEmpty(blockstate)) {
                    return j;
                }

                if (blockstate.is(Blocks.NETHER_PORTAL)) {
                    portalBlockCount.increment();
                }
            }
        }

        return 21;
    }

    private static boolean isEmpty(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    public boolean isValid() {
        return this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
    }

    public void createPortalBlocks(LevelAccessor level) {
        BlockState blockstate = (BlockState) Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);

        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1)).forEach((blockpos) -> {
            level.setBlock(blockpos, blockstate, 18);
        });
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    public static Vec3 getRelativePosition(BlockUtil.FoundRectangle largestRectangleAround, Direction.Axis axis, Vec3 position, EntityDimensions dimensions) {
        double d0 = (double) largestRectangleAround.axis1Size - (double) dimensions.width();
        double d1 = (double) largestRectangleAround.axis2Size - (double) dimensions.height();
        BlockPos blockpos = largestRectangleAround.minCorner;
        double d2;

        if (d0 > 0.0D) {
            double d3 = (double) blockpos.get(axis) + (double) dimensions.width() / 2.0D;

            d2 = Mth.clamp(Mth.inverseLerp(position.get(axis) - d3, 0.0D, d0), 0.0D, 1.0D);
        } else {
            d2 = 0.5D;
        }

        double d4;

        if (d1 > 0.0D) {
            Direction.Axis direction_axis1 = Direction.Axis.Y;

            d4 = Mth.clamp(Mth.inverseLerp(position.get(direction_axis1) - (double) blockpos.get(direction_axis1), 0.0D, d1), 0.0D, 1.0D);
        } else {
            d4 = 0.0D;
        }

        Direction.Axis direction_axis2 = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        double d5 = position.get(direction_axis2) - ((double) blockpos.get(direction_axis2) + 0.5D);

        return new Vec3(d2, d4, d5);
    }

    public static Vec3 findCollisionFreePosition(Vec3 bottomCenter, ServerLevel serverLevel, Entity entity, EntityDimensions dimensions) {
        if (dimensions.width() <= 4.0F && dimensions.height() <= 4.0F) {
            double d0 = (double) dimensions.height() / 2.0D;
            Vec3 vec31 = bottomCenter.add(0.0D, d0, 0.0D);
            VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec31, (double) dimensions.width(), 0.0D, (double) dimensions.width()).expandTowards(0.0D, 1.0D, 0.0D).inflate(1.0E-6D));
            Optional<Vec3> optional = serverLevel.findFreePosition(entity, voxelshape, vec31, (double) dimensions.width(), (double) dimensions.height(), (double) dimensions.width());
            Optional<Vec3> optional1 = optional.map((vec32) -> {
                return vec32.subtract(0.0D, d0, 0.0D);
            });

            return (Vec3) optional1.orElse(bottomCenter);
        } else {
            return bottomCenter;
        }
    }
}
