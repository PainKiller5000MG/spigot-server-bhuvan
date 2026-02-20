package net.minecraft.world.phys.shapes;

import com.mojang.math.OctahedralGroup;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import org.joml.Vector3i;

public abstract class DiscreteVoxelShape {

    private static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    protected final int xSize;
    protected final int ySize;
    protected final int zSize;

    protected DiscreteVoxelShape(int xSize, int ySize, int zSize) {
        if (xSize >= 0 && ySize >= 0 && zSize >= 0) {
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
        } else {
            throw new IllegalArgumentException("Need all positive sizes: x: " + xSize + ", y: " + ySize + ", z: " + zSize);
        }
    }

    public DiscreteVoxelShape rotate(OctahedralGroup rotation) {
        if (rotation == OctahedralGroup.IDENTITY) {
            return this;
        } else {
            Vector3i vector3i = rotation.rotate(new Vector3i(this.xSize, this.ySize, this.zSize));
            int i = fixupCoordinate(vector3i, 0);
            int j = fixupCoordinate(vector3i, 1);
            int k = fixupCoordinate(vector3i, 2);
            DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(vector3i.x, vector3i.y, vector3i.z);

            for (int l = 0; l < this.xSize; ++l) {
                for (int i1 = 0; i1 < this.ySize; ++i1) {
                    for (int j1 = 0; j1 < this.zSize; ++j1) {
                        if (this.isFull(l, i1, j1)) {
                            Vector3i vector3i1 = rotation.rotate(vector3i.set(l, i1, j1));
                            int k1 = i + vector3i1.x;
                            int l1 = j + vector3i1.y;
                            int i2 = k + vector3i1.z;

                            discretevoxelshape.fill(k1, l1, i2);
                        }
                    }
                }
            }

            return discretevoxelshape;
        }
    }

    private static int fixupCoordinate(Vector3i v, int index) {
        int j = v.get(index);

        if (j < 0) {
            v.setComponent(index, -j);
            return -j - 1;
        } else {
            return 0;
        }
    }

    public boolean isFullWide(AxisCycle transform, int x, int y, int z) {
        return this.isFullWide(transform.cycle(x, y, z, Direction.Axis.X), transform.cycle(x, y, z, Direction.Axis.Y), transform.cycle(x, y, z, Direction.Axis.Z));
    }

    public boolean isFullWide(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 ? (x < this.xSize && y < this.ySize && z < this.zSize ? this.isFull(x, y, z) : false) : false;
    }

    public boolean isFull(AxisCycle transform, int x, int y, int z) {
        return this.isFull(transform.cycle(x, y, z, Direction.Axis.X), transform.cycle(x, y, z, Direction.Axis.Y), transform.cycle(x, y, z, Direction.Axis.Z));
    }

    public abstract boolean isFull(int x, int y, int z);

    public abstract void fill(int x, int y, int z);

    public boolean isEmpty() {
        for (Direction.Axis direction_axis : DiscreteVoxelShape.AXIS_VALUES) {
            if (this.firstFull(direction_axis) >= this.lastFull(direction_axis)) {
                return true;
            }
        }

        return false;
    }

    public abstract int firstFull(Direction.Axis axis);

    public abstract int lastFull(Direction.Axis axis);

    public int firstFull(Direction.Axis aAxis, int b, int c) {
        int k = this.getSize(aAxis);

        if (b >= 0 && c >= 0) {
            Direction.Axis direction_axis1 = AxisCycle.FORWARD.cycle(aAxis);
            Direction.Axis direction_axis2 = AxisCycle.BACKWARD.cycle(aAxis);

            if (b < this.getSize(direction_axis1) && c < this.getSize(direction_axis2)) {
                AxisCycle axiscycle = AxisCycle.between(Direction.Axis.X, aAxis);

                for (int l = 0; l < k; ++l) {
                    if (this.isFull(axiscycle, l, b, c)) {
                        return l;
                    }
                }

                return k;
            } else {
                return k;
            }
        } else {
            return k;
        }
    }

    public int lastFull(Direction.Axis aAxis, int b, int c) {
        if (b >= 0 && c >= 0) {
            Direction.Axis direction_axis1 = AxisCycle.FORWARD.cycle(aAxis);
            Direction.Axis direction_axis2 = AxisCycle.BACKWARD.cycle(aAxis);

            if (b < this.getSize(direction_axis1) && c < this.getSize(direction_axis2)) {
                int k = this.getSize(aAxis);
                AxisCycle axiscycle = AxisCycle.between(Direction.Axis.X, aAxis);

                for (int l = k - 1; l >= 0; --l) {
                    if (this.isFull(axiscycle, l, b, c)) {
                        return l + 1;
                    }
                }

                return 0;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public int getSize(Direction.Axis axis) {
        return axis.choose(this.xSize, this.ySize, this.zSize);
    }

    public int getXSize() {
        return this.getSize(Direction.Axis.X);
    }

    public int getYSize() {
        return this.getSize(Direction.Axis.Y);
    }

    public int getZSize() {
        return this.getSize(Direction.Axis.Z);
    }

    public void forAllEdges(DiscreteVoxelShape.IntLineConsumer consumer, boolean mergeNeighbors) {
        this.forAllAxisEdges(consumer, AxisCycle.NONE, mergeNeighbors);
        this.forAllAxisEdges(consumer, AxisCycle.FORWARD, mergeNeighbors);
        this.forAllAxisEdges(consumer, AxisCycle.BACKWARD, mergeNeighbors);
    }

    private void forAllAxisEdges(DiscreteVoxelShape.IntLineConsumer consumer, AxisCycle transform, boolean mergeNeighbors) {
        AxisCycle axiscycle1 = transform.inverse();
        int i = this.getSize(axiscycle1.cycle(Direction.Axis.X));
        int j = this.getSize(axiscycle1.cycle(Direction.Axis.Y));
        int k = this.getSize(axiscycle1.cycle(Direction.Axis.Z));

        for (int l = 0; l <= i; ++l) {
            for (int i1 = 0; i1 <= j; ++i1) {
                int j1 = -1;

                for (int k1 = 0; k1 <= k; ++k1) {
                    int l1 = 0;
                    int i2 = 0;

                    for (int j2 = 0; j2 <= 1; ++j2) {
                        for (int k2 = 0; k2 <= 1; ++k2) {
                            if (this.isFullWide(axiscycle1, l + j2 - 1, i1 + k2 - 1, k1)) {
                                ++l1;
                                i2 ^= j2 ^ k2;
                            }
                        }
                    }

                    if (l1 == 1 || l1 == 3 || l1 == 2 && (i2 & 1) == 0) {
                        if (mergeNeighbors) {
                            if (j1 == -1) {
                                j1 = k1;
                            }
                        } else {
                            consumer.consume(axiscycle1.cycle(l, i1, k1, Direction.Axis.X), axiscycle1.cycle(l, i1, k1, Direction.Axis.Y), axiscycle1.cycle(l, i1, k1, Direction.Axis.Z), axiscycle1.cycle(l, i1, k1 + 1, Direction.Axis.X), axiscycle1.cycle(l, i1, k1 + 1, Direction.Axis.Y), axiscycle1.cycle(l, i1, k1 + 1, Direction.Axis.Z));
                        }
                    } else if (j1 != -1) {
                        consumer.consume(axiscycle1.cycle(l, i1, j1, Direction.Axis.X), axiscycle1.cycle(l, i1, j1, Direction.Axis.Y), axiscycle1.cycle(l, i1, j1, Direction.Axis.Z), axiscycle1.cycle(l, i1, k1, Direction.Axis.X), axiscycle1.cycle(l, i1, k1, Direction.Axis.Y), axiscycle1.cycle(l, i1, k1, Direction.Axis.Z));
                        j1 = -1;
                    }
                }
            }
        }

    }

    public void forAllBoxes(DiscreteVoxelShape.IntLineConsumer consumer, boolean mergeNeighbors) {
        BitSetDiscreteVoxelShape.forAllBoxes(this, consumer, mergeNeighbors);
    }

    public void forAllFaces(DiscreteVoxelShape.IntFaceConsumer consumer) {
        this.forAllAxisFaces(consumer, AxisCycle.NONE);
        this.forAllAxisFaces(consumer, AxisCycle.FORWARD);
        this.forAllAxisFaces(consumer, AxisCycle.BACKWARD);
    }

    private void forAllAxisFaces(DiscreteVoxelShape.IntFaceConsumer consumer, AxisCycle transform) {
        AxisCycle axiscycle1 = transform.inverse();
        Direction.Axis direction_axis = axiscycle1.cycle(Direction.Axis.Z);
        int i = this.getSize(axiscycle1.cycle(Direction.Axis.X));
        int j = this.getSize(axiscycle1.cycle(Direction.Axis.Y));
        int k = this.getSize(direction_axis);
        Direction direction = Direction.fromAxisAndDirection(direction_axis, Direction.AxisDirection.NEGATIVE);
        Direction direction1 = Direction.fromAxisAndDirection(direction_axis, Direction.AxisDirection.POSITIVE);

        for (int l = 0; l < i; ++l) {
            for (int i1 = 0; i1 < j; ++i1) {
                boolean flag = false;

                for (int j1 = 0; j1 <= k; ++j1) {
                    boolean flag1 = j1 != k && this.isFull(axiscycle1, l, i1, j1);

                    if (!flag && flag1) {
                        consumer.consume(direction, axiscycle1.cycle(l, i1, j1, Direction.Axis.X), axiscycle1.cycle(l, i1, j1, Direction.Axis.Y), axiscycle1.cycle(l, i1, j1, Direction.Axis.Z));
                    }

                    if (flag && !flag1) {
                        consumer.consume(direction1, axiscycle1.cycle(l, i1, j1 - 1, Direction.Axis.X), axiscycle1.cycle(l, i1, j1 - 1, Direction.Axis.Y), axiscycle1.cycle(l, i1, j1 - 1, Direction.Axis.Z));
                    }

                    flag = flag1;
                }
            }
        }

    }

    public interface IntFaceConsumer {

        void consume(Direction direction, int x, int y, int z);
    }

    public interface IntLineConsumer {

        void consume(int x1, int y1, int z1, int x2, int y2, int z2);
    }
}
