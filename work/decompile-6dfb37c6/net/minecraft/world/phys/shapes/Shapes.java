package net.minecraft.world.phys.shapes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import com.mojang.math.OctahedralGroup;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Shapes {

    public static final double EPSILON = 1.0E-7D;
    public static final double BIG_EPSILON = 1.0E-6D;
    private static final VoxelShape BLOCK = (VoxelShape) Util.make(() -> {
        DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(1, 1, 1);

        discretevoxelshape.fill(0, 0, 0);
        return new CubeVoxelShape(discretevoxelshape);
    });
    private static final Vec3 BLOCK_CENTER = new Vec3(0.5D, 0.5D, 0.5D);
    public static final VoxelShape INFINITY = box(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    private static final VoxelShape EMPTY = new ArrayVoxelShape(new BitSetDiscreteVoxelShape(0, 0, 0), new DoubleArrayList(new double[]{0.0D}), new DoubleArrayList(new double[]{0.0D}), new DoubleArrayList(new double[]{0.0D}));

    public Shapes() {}

    public static VoxelShape empty() {
        return Shapes.EMPTY;
    }

    public static VoxelShape block() {
        return Shapes.BLOCK;
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
            return create(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
        }
    }

    public static VoxelShape create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX - minX >= 1.0E-7D && maxY - minY >= 1.0E-7D && maxZ - minZ >= 1.0E-7D) {
            int i = findBits(minX, maxX);
            int j = findBits(minY, maxY);
            int k = findBits(minZ, maxZ);

            if (i >= 0 && j >= 0 && k >= 0) {
                if (i == 0 && j == 0 && k == 0) {
                    return block();
                } else {
                    int l = 1 << i;
                    int i1 = 1 << j;
                    int j1 = 1 << k;
                    BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = BitSetDiscreteVoxelShape.withFilledBounds(l, i1, j1, (int) Math.round(minX * (double) l), (int) Math.round(minY * (double) i1), (int) Math.round(minZ * (double) j1), (int) Math.round(maxX * (double) l), (int) Math.round(maxY * (double) i1), (int) Math.round(maxZ * (double) j1));

                    return new CubeVoxelShape(bitsetdiscretevoxelshape);
                }
            } else {
                return new ArrayVoxelShape(Shapes.BLOCK.shape, DoubleArrayList.wrap(new double[]{minX, maxX}), DoubleArrayList.wrap(new double[]{minY, maxY}), DoubleArrayList.wrap(new double[]{minZ, maxZ}));
            }
        } else {
            return empty();
        }
    }

    public static VoxelShape create(AABB aabb) {
        return create(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    @VisibleForTesting
    protected static int findBits(double min, double max) {
        if (min >= -1.0E-7D && max <= 1.0000001D) {
            for (int i = 0; i <= 3; ++i) {
                int j = 1 << i;
                double d2 = min * (double) j;
                double d3 = max * (double) j;
                boolean flag = Math.abs(d2 - (double) Math.round(d2)) < 1.0E-7D * (double) j;
                boolean flag1 = Math.abs(d3 - (double) Math.round(d3)) < 1.0E-7D * (double) j;

                if (flag && flag1) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    protected static long lcm(int first, int second) {
        return (long) first * (long) (second / IntMath.gcd(first, second));
    }

    public static VoxelShape or(VoxelShape first, VoxelShape second) {
        return join(first, second, BooleanOp.OR);
    }

    public static VoxelShape or(VoxelShape first, VoxelShape... tail) {
        return (VoxelShape) Arrays.stream(tail).reduce(first, Shapes::or);
    }

    public static VoxelShape join(VoxelShape first, VoxelShape second, BooleanOp op) {
        return joinUnoptimized(first, second, op).optimize();
    }

    public static VoxelShape joinUnoptimized(VoxelShape first, VoxelShape second, BooleanOp op) {
        if (op.apply(false, false)) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException());
        } else if (first == second) {
            return op.apply(true, true) ? first : empty();
        } else {
            boolean flag = op.apply(true, false);
            boolean flag1 = op.apply(false, true);

            if (first.isEmpty()) {
                return flag1 ? second : empty();
            } else if (second.isEmpty()) {
                return flag ? first : empty();
            } else {
                IndexMerger indexmerger = createIndexMerger(1, first.getCoords(Direction.Axis.X), second.getCoords(Direction.Axis.X), flag, flag1);
                IndexMerger indexmerger1 = createIndexMerger(indexmerger.size() - 1, first.getCoords(Direction.Axis.Y), second.getCoords(Direction.Axis.Y), flag, flag1);
                IndexMerger indexmerger2 = createIndexMerger((indexmerger.size() - 1) * (indexmerger1.size() - 1), first.getCoords(Direction.Axis.Z), second.getCoords(Direction.Axis.Z), flag, flag1);
                BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = BitSetDiscreteVoxelShape.join(first.shape, second.shape, indexmerger, indexmerger1, indexmerger2, op);

                return (VoxelShape) (indexmerger instanceof DiscreteCubeMerger && indexmerger1 instanceof DiscreteCubeMerger && indexmerger2 instanceof DiscreteCubeMerger ? new CubeVoxelShape(bitsetdiscretevoxelshape) : new ArrayVoxelShape(bitsetdiscretevoxelshape, indexmerger.getList(), indexmerger1.getList(), indexmerger2.getList()));
            }
        }
    }

    public static boolean joinIsNotEmpty(VoxelShape first, VoxelShape second, BooleanOp op) {
        if (op.apply(false, false)) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException());
        } else {
            boolean flag = first.isEmpty();
            boolean flag1 = second.isEmpty();

            if (!flag && !flag1) {
                if (first == second) {
                    return op.apply(true, true);
                } else {
                    boolean flag2 = op.apply(true, false);
                    boolean flag3 = op.apply(false, true);

                    for (Direction.Axis direction_axis : AxisCycle.AXIS_VALUES) {
                        if (first.max(direction_axis) < second.min(direction_axis) - 1.0E-7D) {
                            return flag2 || flag3;
                        }

                        if (second.max(direction_axis) < first.min(direction_axis) - 1.0E-7D) {
                            return flag2 || flag3;
                        }
                    }

                    IndexMerger indexmerger = createIndexMerger(1, first.getCoords(Direction.Axis.X), second.getCoords(Direction.Axis.X), flag2, flag3);
                    IndexMerger indexmerger1 = createIndexMerger(indexmerger.size() - 1, first.getCoords(Direction.Axis.Y), second.getCoords(Direction.Axis.Y), flag2, flag3);
                    IndexMerger indexmerger2 = createIndexMerger((indexmerger.size() - 1) * (indexmerger1.size() - 1), first.getCoords(Direction.Axis.Z), second.getCoords(Direction.Axis.Z), flag2, flag3);

                    return joinIsNotEmpty(indexmerger, indexmerger1, indexmerger2, first.shape, second.shape, op);
                }
            } else {
                return op.apply(!flag, !flag1);
            }
        }
    }

    private static boolean joinIsNotEmpty(IndexMerger xMerger, IndexMerger yMerger, IndexMerger zMerger, DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp op) {
        return !xMerger.forMergedIndexes((i, j, k) -> {
            return yMerger.forMergedIndexes((l, i1, j1) -> {
                return zMerger.forMergedIndexes((k1, l1, i2) -> {
                    return !op.apply(first.isFullWide(i, l, k1), second.isFullWide(j, i1, l1));
                });
            });
        });
    }

    public static double collide(Direction.Axis axis, AABB moving, Iterable<VoxelShape> shapes, double distance) {
        for (VoxelShape voxelshape : shapes) {
            if (Math.abs(distance) < 1.0E-7D) {
                return 0.0D;
            }

            distance = voxelshape.collide(axis, moving, distance);
        }

        return distance;
    }

    public static boolean blockOccludes(VoxelShape shape, VoxelShape occluder, Direction direction) {
        if (shape == block() && occluder == block()) {
            return true;
        } else if (occluder.isEmpty()) {
            return false;
        } else {
            Direction.Axis direction_axis = direction.getAxis();
            Direction.AxisDirection direction_axisdirection = direction.getAxisDirection();
            VoxelShape voxelshape2 = direction_axisdirection == Direction.AxisDirection.POSITIVE ? shape : occluder;
            VoxelShape voxelshape3 = direction_axisdirection == Direction.AxisDirection.POSITIVE ? occluder : shape;
            BooleanOp booleanop = direction_axisdirection == Direction.AxisDirection.POSITIVE ? BooleanOp.ONLY_FIRST : BooleanOp.ONLY_SECOND;

            return DoubleMath.fuzzyEquals(voxelshape2.max(direction_axis), 1.0D, 1.0E-7D) && DoubleMath.fuzzyEquals(voxelshape3.min(direction_axis), 0.0D, 1.0E-7D) && !joinIsNotEmpty(new SliceShape(voxelshape2, direction_axis, voxelshape2.shape.getSize(direction_axis) - 1), new SliceShape(voxelshape3, direction_axis, 0), booleanop);
        }
    }

    public static boolean mergedFaceOccludes(VoxelShape shape, VoxelShape occluder, Direction direction) {
        if (shape != block() && occluder != block()) {
            Direction.Axis direction_axis = direction.getAxis();
            Direction.AxisDirection direction_axisdirection = direction.getAxisDirection();
            VoxelShape voxelshape2 = direction_axisdirection == Direction.AxisDirection.POSITIVE ? shape : occluder;
            VoxelShape voxelshape3 = direction_axisdirection == Direction.AxisDirection.POSITIVE ? occluder : shape;

            if (!DoubleMath.fuzzyEquals(voxelshape2.max(direction_axis), 1.0D, 1.0E-7D)) {
                voxelshape2 = empty();
            }

            if (!DoubleMath.fuzzyEquals(voxelshape3.min(direction_axis), 0.0D, 1.0E-7D)) {
                voxelshape3 = empty();
            }

            return !joinIsNotEmpty(block(), joinUnoptimized(new SliceShape(voxelshape2, direction_axis, voxelshape2.shape.getSize(direction_axis) - 1), new SliceShape(voxelshape3, direction_axis, 0), BooleanOp.OR), BooleanOp.ONLY_FIRST);
        } else {
            return true;
        }
    }

    public static boolean faceShapeOccludes(VoxelShape shape, VoxelShape occluder) {
        return shape != block() && occluder != block() ? (shape.isEmpty() && occluder.isEmpty() ? false : !joinIsNotEmpty(block(), joinUnoptimized(shape, occluder, BooleanOp.OR), BooleanOp.ONLY_FIRST)) : true;
    }

    @VisibleForTesting
    protected static IndexMerger createIndexMerger(int cost, DoubleList first, DoubleList second, boolean firstOnlyMatters, boolean secondOnlyMatters) {
        int j = first.size() - 1;
        int k = second.size() - 1;

        if (first instanceof CubePointRange && second instanceof CubePointRange) {
            long l = lcm(j, k);

            if ((long) cost * l <= 256L) {
                return new DiscreteCubeMerger(j, k);
            }
        }

        return (IndexMerger) (first.getDouble(j) < second.getDouble(0) - 1.0E-7D ? new NonOverlappingMerger(first, second, false) : (second.getDouble(k) < first.getDouble(0) - 1.0E-7D ? new NonOverlappingMerger(second, first, true) : (j == k && Objects.equals(first, second) ? new IdenticalMerger(first) : new IndirectMerger(first, second, firstOnlyMatters, secondOnlyMatters))));
    }

    public static VoxelShape rotate(VoxelShape shape, OctahedralGroup rotation) {
        return rotate(shape, rotation, Shapes.BLOCK_CENTER);
    }

    public static VoxelShape rotate(VoxelShape shape, OctahedralGroup rotation, Vec3 rotationPoint) {
        if (rotation == OctahedralGroup.IDENTITY) {
            return shape;
        } else {
            DiscreteVoxelShape discretevoxelshape = shape.shape.rotate(rotation);

            if (shape instanceof CubeVoxelShape && Shapes.BLOCK_CENTER.equals(rotationPoint)) {
                return new CubeVoxelShape(discretevoxelshape);
            } else {
                Direction.Axis direction_axis = rotation.permutation().permuteAxis(Direction.Axis.X);
                Direction.Axis direction_axis1 = rotation.permutation().permuteAxis(Direction.Axis.Y);
                Direction.Axis direction_axis2 = rotation.permutation().permuteAxis(Direction.Axis.Z);
                DoubleList doublelist = shape.getCoords(direction_axis);
                DoubleList doublelist1 = shape.getCoords(direction_axis1);
                DoubleList doublelist2 = shape.getCoords(direction_axis2);
                boolean flag = rotation.inverts(Direction.Axis.X);
                boolean flag1 = rotation.inverts(Direction.Axis.Y);
                boolean flag2 = rotation.inverts(Direction.Axis.Z);

                return new ArrayVoxelShape(discretevoxelshape, flipAxisIfNeeded(doublelist, flag, rotationPoint.get(direction_axis), rotationPoint.x), flipAxisIfNeeded(doublelist1, flag1, rotationPoint.get(direction_axis1), rotationPoint.y), flipAxisIfNeeded(doublelist2, flag2, rotationPoint.get(direction_axis2), rotationPoint.z));
            }
        }
    }

    @VisibleForTesting
    static DoubleList flipAxisIfNeeded(DoubleList newAxis, boolean flip, double newRelative, double oldRelative) {
        if (!flip && newRelative == oldRelative) {
            return newAxis;
        } else {
            int i = newAxis.size();
            DoubleList doublelist1 = new DoubleArrayList(i);

            if (flip) {
                for (int j = i - 1; j >= 0; --j) {
                    doublelist1.add(-(newAxis.getDouble(j) - newRelative) + oldRelative);
                }
            } else {
                for (int k = 0; k >= 0 && k < i; ++k) {
                    doublelist1.add(newAxis.getDouble(k) - newRelative + oldRelative);
                }
            }

            return doublelist1;
        }
    }

    public static boolean equal(VoxelShape first, VoxelShape second) {
        return !joinIsNotEmpty(first, second, BooleanOp.NOT_SAME);
    }

    public static Map<Direction.Axis, VoxelShape> rotateHorizontalAxis(VoxelShape zAxis) {
        return rotateHorizontalAxis(zAxis, Shapes.BLOCK_CENTER);
    }

    public static Map<Direction.Axis, VoxelShape> rotateHorizontalAxis(VoxelShape zAxis, Vec3 rotationCenter) {
        return Maps.newEnumMap(Map.of(Direction.Axis.Z, zAxis, Direction.Axis.X, rotate(zAxis, OctahedralGroup.BLOCK_ROT_Y_90, rotationCenter)));
    }

    public static Map<Direction.Axis, VoxelShape> rotateAllAxis(VoxelShape north) {
        return rotateAllAxis(north, Shapes.BLOCK_CENTER);
    }

    public static Map<Direction.Axis, VoxelShape> rotateAllAxis(VoxelShape north, Vec3 rotationCenter) {
        return Maps.newEnumMap(Map.of(Direction.Axis.Z, north, Direction.Axis.X, rotate(north, OctahedralGroup.BLOCK_ROT_Y_90, rotationCenter), Direction.Axis.Y, rotate(north, OctahedralGroup.BLOCK_ROT_X_90, rotationCenter)));
    }

    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north) {
        return rotateHorizontal(north, OctahedralGroup.IDENTITY, Shapes.BLOCK_CENTER);
    }

    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north, OctahedralGroup initial) {
        return rotateHorizontal(north, initial, Shapes.BLOCK_CENTER);
    }

    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north, OctahedralGroup initial, Vec3 rotationCenter) {
        return Maps.newEnumMap(Map.of(Direction.NORTH, rotate(north, initial), Direction.EAST, rotate(north, OctahedralGroup.BLOCK_ROT_Y_90.compose(initial), rotationCenter), Direction.SOUTH, rotate(north, OctahedralGroup.BLOCK_ROT_Y_180.compose(initial), rotationCenter), Direction.WEST, rotate(north, OctahedralGroup.BLOCK_ROT_Y_270.compose(initial), rotationCenter)));
    }

    public static Map<Direction, VoxelShape> rotateAll(VoxelShape north) {
        return rotateAll(north, OctahedralGroup.IDENTITY, Shapes.BLOCK_CENTER);
    }

    public static Map<Direction, VoxelShape> rotateAll(VoxelShape north, Vec3 rotationCenter) {
        return rotateAll(north, OctahedralGroup.IDENTITY, rotationCenter);
    }

    public static Map<Direction, VoxelShape> rotateAll(VoxelShape north, OctahedralGroup initial, Vec3 rotationCenter) {
        return Maps.newEnumMap(Map.of(Direction.NORTH, rotate(north, initial), Direction.EAST, rotate(north, OctahedralGroup.BLOCK_ROT_Y_90.compose(initial), rotationCenter), Direction.SOUTH, rotate(north, OctahedralGroup.BLOCK_ROT_Y_180.compose(initial), rotationCenter), Direction.WEST, rotate(north, OctahedralGroup.BLOCK_ROT_Y_270.compose(initial), rotationCenter), Direction.UP, rotate(north, OctahedralGroup.BLOCK_ROT_X_270.compose(initial), rotationCenter), Direction.DOWN, rotate(north, OctahedralGroup.BLOCK_ROT_X_90.compose(initial), rotationCenter)));
    }

    public static Map<AttachFace, Map<Direction, VoxelShape>> rotateAttachFace(VoxelShape north) {
        return rotateAttachFace(north, OctahedralGroup.IDENTITY);
    }

    public static Map<AttachFace, Map<Direction, VoxelShape>> rotateAttachFace(VoxelShape north, OctahedralGroup initial) {
        return Map.of(AttachFace.WALL, rotateHorizontal(north, initial), AttachFace.FLOOR, rotateHorizontal(north, OctahedralGroup.BLOCK_ROT_X_270.compose(initial)), AttachFace.CEILING, rotateHorizontal(north, OctahedralGroup.BLOCK_ROT_Y_180.compose(OctahedralGroup.BLOCK_ROT_X_90).compose(initial)));
    }

    public interface DoubleLineConsumer {

        void consume(double x1, double y1, double z1, double x2, double y2, double z2);
    }
}
