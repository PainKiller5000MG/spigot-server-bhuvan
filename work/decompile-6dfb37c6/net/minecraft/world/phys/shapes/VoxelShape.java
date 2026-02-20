package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

public abstract class VoxelShape {

    protected final DiscreteVoxelShape shape;
    private @Nullable VoxelShape @Nullable [] faces;

    protected VoxelShape(DiscreteVoxelShape shape) {
        this.shape = shape;
    }

    public double min(Direction.Axis axis) {
        int i = this.shape.firstFull(axis);

        return i >= this.shape.getSize(axis) ? Double.POSITIVE_INFINITY : this.get(axis, i);
    }

    public double max(Direction.Axis axis) {
        int i = this.shape.lastFull(axis);

        return i <= 0 ? Double.NEGATIVE_INFINITY : this.get(axis, i);
    }

    public AABB bounds() {
        if (this.isEmpty()) {
            throw (UnsupportedOperationException) Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        } else {
            return new AABB(this.min(Direction.Axis.X), this.min(Direction.Axis.Y), this.min(Direction.Axis.Z), this.max(Direction.Axis.X), this.max(Direction.Axis.Y), this.max(Direction.Axis.Z));
        }
    }

    public VoxelShape singleEncompassing() {
        return this.isEmpty() ? Shapes.empty() : Shapes.box(this.min(Direction.Axis.X), this.min(Direction.Axis.Y), this.min(Direction.Axis.Z), this.max(Direction.Axis.X), this.max(Direction.Axis.Y), this.max(Direction.Axis.Z));
    }

    protected double get(Direction.Axis axis, int i) {
        return this.getCoords(axis).getDouble(i);
    }

    public abstract DoubleList getCoords(Direction.Axis axis);

    public boolean isEmpty() {
        return this.shape.isEmpty();
    }

    public VoxelShape move(Vec3 delta) {
        return this.move(delta.x, delta.y, delta.z);
    }

    public VoxelShape move(Vec3i delta) {
        return this.move((double) delta.getX(), (double) delta.getY(), (double) delta.getZ());
    }

    public VoxelShape move(double dx, double dy, double dz) {
        return (VoxelShape) (this.isEmpty() ? Shapes.empty() : new ArrayVoxelShape(this.shape, new OffsetDoubleList(this.getCoords(Direction.Axis.X), dx), new OffsetDoubleList(this.getCoords(Direction.Axis.Y), dy), new OffsetDoubleList(this.getCoords(Direction.Axis.Z), dz)));
    }

    public VoxelShape optimize() {
        VoxelShape[] avoxelshape = new VoxelShape[]{Shapes.empty()};

        this.forAllBoxes((d0, d1, d2, d3, d4, d5) -> {
            avoxelshape[0] = Shapes.joinUnoptimized(avoxelshape[0], Shapes.box(d0, d1, d2, d3, d4, d5), BooleanOp.OR);
        });
        return avoxelshape[0];
    }

    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        this.shape.forAllEdges((i, j, k, l, i1, j1) -> {
            consumer.consume(this.get(Direction.Axis.X, i), this.get(Direction.Axis.Y, j), this.get(Direction.Axis.Z, k), this.get(Direction.Axis.X, l), this.get(Direction.Axis.Y, i1), this.get(Direction.Axis.Z, j1));
        }, true);
    }

    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        DoubleList doublelist = this.getCoords(Direction.Axis.X);
        DoubleList doublelist1 = this.getCoords(Direction.Axis.Y);
        DoubleList doublelist2 = this.getCoords(Direction.Axis.Z);

        this.shape.forAllBoxes((i, j, k, l, i1, j1) -> {
            consumer.consume(doublelist.getDouble(i), doublelist1.getDouble(j), doublelist2.getDouble(k), doublelist.getDouble(l), doublelist1.getDouble(i1), doublelist2.getDouble(j1));
        }, true);
    }

    public List<AABB> toAabbs() {
        List<AABB> list = Lists.newArrayList();

        this.forAllBoxes((d0, d1, d2, d3, d4, d5) -> {
            list.add(new AABB(d0, d1, d2, d3, d4, d5));
        });
        return list;
    }

    public double min(Direction.Axis aAxis, double b, double c) {
        Direction.Axis direction_axis1 = AxisCycle.FORWARD.cycle(aAxis);
        Direction.Axis direction_axis2 = AxisCycle.BACKWARD.cycle(aAxis);
        int i = this.findIndex(direction_axis1, b);
        int j = this.findIndex(direction_axis2, c);
        int k = this.shape.firstFull(aAxis, i, j);

        return k >= this.shape.getSize(aAxis) ? Double.POSITIVE_INFINITY : this.get(aAxis, k);
    }

    public double max(Direction.Axis aAxis, double b, double c) {
        Direction.Axis direction_axis1 = AxisCycle.FORWARD.cycle(aAxis);
        Direction.Axis direction_axis2 = AxisCycle.BACKWARD.cycle(aAxis);
        int i = this.findIndex(direction_axis1, b);
        int j = this.findIndex(direction_axis2, c);
        int k = this.shape.lastFull(aAxis, i, j);

        return k <= 0 ? Double.NEGATIVE_INFINITY : this.get(aAxis, k);
    }

    protected int findIndex(Direction.Axis axis, double coord) {
        return Mth.binarySearch(0, this.shape.getSize(axis) + 1, (i) -> {
            return coord < this.get(axis, i);
        }) - 1;
    }

    public @Nullable BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
        if (this.isEmpty()) {
            return null;
        } else {
            Vec3 vec32 = to.subtract(from);

            if (vec32.lengthSqr() < 1.0E-7D) {
                return null;
            } else {
                Vec3 vec33 = from.add(vec32.scale(0.001D));

                return this.shape.isFullWide(this.findIndex(Direction.Axis.X, vec33.x - (double) pos.getX()), this.findIndex(Direction.Axis.Y, vec33.y - (double) pos.getY()), this.findIndex(Direction.Axis.Z, vec33.z - (double) pos.getZ())) ? new BlockHitResult(vec33, Direction.getApproximateNearest(vec32.x, vec32.y, vec32.z).getOpposite(), pos, true) : AABB.clip(this.toAabbs(), from, to, pos);
            }
        }
    }

    public Optional<Vec3> closestPointTo(Vec3 point) {
        if (this.isEmpty()) {
            return Optional.empty();
        } else {
            MutableObject<Vec3> mutableobject = new MutableObject();

            this.forAllBoxes((d0, d1, d2, d3, d4, d5) -> {
                double d6 = Mth.clamp(point.x(), d0, d3);
                double d7 = Mth.clamp(point.y(), d1, d4);
                double d8 = Mth.clamp(point.z(), d2, d5);
                Vec3 vec31 = (Vec3) mutableobject.get();

                if (vec31 == null || point.distanceToSqr(d6, d7, d8) < point.distanceToSqr(vec31)) {
                    mutableobject.setValue(new Vec3(d6, d7, d8));
                }

            });
            return Optional.of((Vec3) Objects.requireNonNull((Vec3) mutableobject.get()));
        }
    }

    public VoxelShape getFaceShape(Direction direction) {
        if (!this.isEmpty() && this != Shapes.block()) {
            if (this.faces != null) {
                VoxelShape voxelshape = this.faces[direction.ordinal()];

                if (voxelshape != null) {
                    return voxelshape;
                }
            } else {
                this.faces = new VoxelShape[6];
            }

            VoxelShape voxelshape1 = this.calculateFace(direction);

            this.faces[direction.ordinal()] = voxelshape1;
            return voxelshape1;
        } else {
            return this;
        }
    }

    private VoxelShape calculateFace(Direction direction) {
        Direction.Axis direction_axis = direction.getAxis();

        if (this.isCubeLikeAlong(direction_axis)) {
            return this;
        } else {
            Direction.AxisDirection direction_axisdirection = direction.getAxisDirection();
            int i = this.findIndex(direction_axis, direction_axisdirection == Direction.AxisDirection.POSITIVE ? 0.9999999D : 1.0E-7D);
            SliceShape sliceshape = new SliceShape(this, direction_axis, i);

            return (VoxelShape) (sliceshape.isEmpty() ? Shapes.empty() : (sliceshape.isCubeLike() ? Shapes.block() : sliceshape));
        }
    }

    protected boolean isCubeLike() {
        for (Direction.Axis direction_axis : Direction.Axis.VALUES) {
            if (!this.isCubeLikeAlong(direction_axis)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCubeLikeAlong(Direction.Axis axis) {
        DoubleList doublelist = this.getCoords(axis);

        return doublelist.size() == 2 && DoubleMath.fuzzyEquals(doublelist.getDouble(0), 0.0D, 1.0E-7D) && DoubleMath.fuzzyEquals(doublelist.getDouble(1), 1.0D, 1.0E-7D);
    }

    public double collide(Direction.Axis axis, AABB moving, double distance) {
        return this.collideX(AxisCycle.between(axis, Direction.Axis.X), moving, distance);
    }

    protected double collideX(AxisCycle transform, AABB moving, double distance) {
        if (this.isEmpty()) {
            return distance;
        } else if (Math.abs(distance) < 1.0E-7D) {
            return 0.0D;
        } else {
            AxisCycle axiscycle1 = transform.inverse();
            Direction.Axis direction_axis = axiscycle1.cycle(Direction.Axis.X);
            Direction.Axis direction_axis1 = axiscycle1.cycle(Direction.Axis.Y);
            Direction.Axis direction_axis2 = axiscycle1.cycle(Direction.Axis.Z);
            double d1 = moving.max(direction_axis);
            double d2 = moving.min(direction_axis);
            int i = this.findIndex(direction_axis, d2 + 1.0E-7D);
            int j = this.findIndex(direction_axis, d1 - 1.0E-7D);
            int k = Math.max(0, this.findIndex(direction_axis1, moving.min(direction_axis1) + 1.0E-7D));
            int l = Math.min(this.shape.getSize(direction_axis1), this.findIndex(direction_axis1, moving.max(direction_axis1) - 1.0E-7D) + 1);
            int i1 = Math.max(0, this.findIndex(direction_axis2, moving.min(direction_axis2) + 1.0E-7D));
            int j1 = Math.min(this.shape.getSize(direction_axis2), this.findIndex(direction_axis2, moving.max(direction_axis2) - 1.0E-7D) + 1);
            int k1 = this.shape.getSize(direction_axis);

            if (distance > 0.0D) {
                for (int l1 = j + 1; l1 < k1; ++l1) {
                    for (int i2 = k; i2 < l; ++i2) {
                        for (int j2 = i1; j2 < j1; ++j2) {
                            if (this.shape.isFullWide(axiscycle1, l1, i2, j2)) {
                                double d3 = this.get(direction_axis, l1) - d1;

                                if (d3 >= -1.0E-7D) {
                                    distance = Math.min(distance, d3);
                                }

                                return distance;
                            }
                        }
                    }
                }
            } else if (distance < 0.0D) {
                for (int k2 = i - 1; k2 >= 0; --k2) {
                    for (int l2 = k; l2 < l; ++l2) {
                        for (int i3 = i1; i3 < j1; ++i3) {
                            if (this.shape.isFullWide(axiscycle1, k2, l2, i3)) {
                                double d4 = this.get(direction_axis, k2 + 1) - d2;

                                if (d4 <= 1.0E-7D) {
                                    distance = Math.max(distance, d4);
                                }

                                return distance;
                            }
                        }
                    }
                }
            }

            return distance;
        }
    }

    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public String toString() {
        return this.isEmpty() ? "EMPTY" : "VoxelShape[" + String.valueOf(this.bounds()) + "]";
    }
}
