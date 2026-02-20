package net.minecraft.world.phys.shapes;

import java.util.BitSet;
import net.minecraft.core.Direction;

public final class BitSetDiscreteVoxelShape extends DiscreteVoxelShape {

    private final BitSet storage;
    private int xMin;
    private int yMin;
    private int zMin;
    private int xMax;
    private int yMax;
    private int zMax;

    public BitSetDiscreteVoxelShape(int xSize, int ySize, int zSize) {
        super(xSize, ySize, zSize);
        this.storage = new BitSet(xSize * ySize * zSize);
        this.xMin = xSize;
        this.yMin = ySize;
        this.zMin = zSize;
    }

    public static BitSetDiscreteVoxelShape withFilledBounds(int xSize, int ySize, int zSize, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(xSize, ySize, zSize);

        bitsetdiscretevoxelshape.xMin = xMin;
        bitsetdiscretevoxelshape.yMin = yMin;
        bitsetdiscretevoxelshape.zMin = zMin;
        bitsetdiscretevoxelshape.xMax = xMax;
        bitsetdiscretevoxelshape.yMax = yMax;
        bitsetdiscretevoxelshape.zMax = zMax;

        for (int j2 = xMin; j2 < xMax; ++j2) {
            for (int k2 = yMin; k2 < yMax; ++k2) {
                for (int l2 = zMin; l2 < zMax; ++l2) {
                    bitsetdiscretevoxelshape.fillUpdateBounds(j2, k2, l2, false);
                }
            }
        }

        return bitsetdiscretevoxelshape;
    }

    public BitSetDiscreteVoxelShape(DiscreteVoxelShape voxelShape) {
        super(voxelShape.xSize, voxelShape.ySize, voxelShape.zSize);
        if (voxelShape instanceof BitSetDiscreteVoxelShape) {
            this.storage = (BitSet) ((BitSetDiscreteVoxelShape) voxelShape).storage.clone();
        } else {
            this.storage = new BitSet(this.xSize * this.ySize * this.zSize);

            for (int i = 0; i < this.xSize; ++i) {
                for (int j = 0; j < this.ySize; ++j) {
                    for (int k = 0; k < this.zSize; ++k) {
                        if (voxelShape.isFull(i, j, k)) {
                            this.storage.set(this.getIndex(i, j, k));
                        }
                    }
                }
            }
        }

        this.xMin = voxelShape.firstFull(Direction.Axis.X);
        this.yMin = voxelShape.firstFull(Direction.Axis.Y);
        this.zMin = voxelShape.firstFull(Direction.Axis.Z);
        this.xMax = voxelShape.lastFull(Direction.Axis.X);
        this.yMax = voxelShape.lastFull(Direction.Axis.Y);
        this.zMax = voxelShape.lastFull(Direction.Axis.Z);
    }

    protected int getIndex(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.storage.get(this.getIndex(x, y, z));
    }

    private void fillUpdateBounds(int x, int y, int z, boolean updateBounds) {
        this.storage.set(this.getIndex(x, y, z));
        if (updateBounds) {
            this.xMin = Math.min(this.xMin, x);
            this.yMin = Math.min(this.yMin, y);
            this.zMin = Math.min(this.zMin, z);
            this.xMax = Math.max(this.xMax, x + 1);
            this.yMax = Math.max(this.yMax, y + 1);
            this.zMax = Math.max(this.zMax, z + 1);
        }

    }

    @Override
    public void fill(int x, int y, int z) {
        this.fillUpdateBounds(x, y, z, true);
    }

    @Override
    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return axis.choose(this.xMin, this.yMin, this.zMin);
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return axis.choose(this.xMax, this.yMax, this.zMax);
    }

    static BitSetDiscreteVoxelShape join(DiscreteVoxelShape first, DiscreteVoxelShape second, IndexMerger xMerger, IndexMerger yMerger, IndexMerger zMerger, BooleanOp op) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(xMerger.size() - 1, yMerger.size() - 1, zMerger.size() - 1);
        int[] aint = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

        xMerger.forMergedIndexes((i, j, k) -> {
            boolean[] aboolean = new boolean[]{false};

            yMerger.forMergedIndexes((l, i1, j1) -> {
                boolean[] aboolean1 = new boolean[]{false};

                zMerger.forMergedIndexes((k1, l1, i2) -> {
                    if (op.apply(first.isFullWide(i, l, k1), second.isFullWide(j, i1, l1))) {
                        bitsetdiscretevoxelshape.storage.set(bitsetdiscretevoxelshape.getIndex(k, j1, i2));
                        aint[2] = Math.min(aint[2], i2);
                        aint[5] = Math.max(aint[5], i2);
                        aboolean1[0] = true;
                    }

                    return true;
                });
                if (aboolean1[0]) {
                    aint[1] = Math.min(aint[1], j1);
                    aint[4] = Math.max(aint[4], j1);
                    aboolean[0] = true;
                }

                return true;
            });
            if (aboolean[0]) {
                aint[0] = Math.min(aint[0], k);
                aint[3] = Math.max(aint[3], k);
            }

            return true;
        });
        bitsetdiscretevoxelshape.xMin = aint[0];
        bitsetdiscretevoxelshape.yMin = aint[1];
        bitsetdiscretevoxelshape.zMin = aint[2];
        bitsetdiscretevoxelshape.xMax = aint[3] + 1;
        bitsetdiscretevoxelshape.yMax = aint[4] + 1;
        bitsetdiscretevoxelshape.zMax = aint[5] + 1;
        return bitsetdiscretevoxelshape;
    }

    protected static void forAllBoxes(DiscreteVoxelShape voxelShape, DiscreteVoxelShape.IntLineConsumer consumer, boolean mergeNeighbors) {
        BitSetDiscreteVoxelShape bitsetdiscretevoxelshape = new BitSetDiscreteVoxelShape(voxelShape);

        for (int i = 0; i < bitsetdiscretevoxelshape.ySize; ++i) {
            for (int j = 0; j < bitsetdiscretevoxelshape.xSize; ++j) {
                int k = -1;

                for (int l = 0; l <= bitsetdiscretevoxelshape.zSize; ++l) {
                    if (bitsetdiscretevoxelshape.isFullWide(j, i, l)) {
                        if (mergeNeighbors) {
                            if (k == -1) {
                                k = l;
                            }
                        } else {
                            consumer.consume(j, i, l, j + 1, i + 1, l + 1);
                        }
                    } else if (k != -1) {
                        int i1 = j;
                        int j1 = i;

                        bitsetdiscretevoxelshape.clearZStrip(k, l, j, i);

                        while (bitsetdiscretevoxelshape.isZStripFull(k, l, i1 + 1, i)) {
                            bitsetdiscretevoxelshape.clearZStrip(k, l, i1 + 1, i);
                            ++i1;
                        }

                        while (bitsetdiscretevoxelshape.isXZRectangleFull(j, i1 + 1, k, l, j1 + 1)) {
                            for (int k1 = j; k1 <= i1; ++k1) {
                                bitsetdiscretevoxelshape.clearZStrip(k, l, k1, j1 + 1);
                            }

                            ++j1;
                        }

                        consumer.consume(j, i, k, i1 + 1, j1 + 1, l);
                        k = -1;
                    }
                }
            }
        }

    }

    private boolean isZStripFull(int startZ, int endZ, int x, int y) {
        return x < this.xSize && y < this.ySize ? this.storage.nextClearBit(this.getIndex(x, y, startZ)) >= this.getIndex(x, y, endZ) : false;
    }

    private boolean isXZRectangleFull(int startX, int endX, int startZ, int endZ, int y) {
        for (int j1 = startX; j1 < endX; ++j1) {
            if (!this.isZStripFull(startZ, endZ, j1, y)) {
                return false;
            }
        }

        return true;
    }

    private void clearZStrip(int startZ, int endZ, int x, int y) {
        this.storage.clear(this.getIndex(x, y, startZ), this.getIndex(x, y, endZ));
    }

    public boolean isInterior(int x, int y, int z) {
        boolean flag = x > 0 && x < this.xSize - 1 && y > 0 && y < this.ySize - 1 && z > 0 && z < this.zSize - 1;

        return flag && this.isFull(x, y, z) && this.isFull(x - 1, y, z) && this.isFull(x + 1, y, z) && this.isFull(x, y - 1, z) && this.isFull(x, y + 1, z) && this.isFull(x, y, z - 1) && this.isFull(x, y, z + 1);
    }
}
