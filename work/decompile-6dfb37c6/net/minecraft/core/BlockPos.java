package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

@Immutable
public class BlockPos extends Vec3i {

    public static final Codec<BlockPos> CODEC = Codec.INT_STREAM.comapFlatMap((intstream) -> {
        return Util.fixedSize(intstream, 3).map((aint) -> {
            return new BlockPos(aint[0], aint[1], aint[2]);
        });
    }, (blockpos) -> {
        return IntStream.of(new int[]{blockpos.getX(), blockpos.getY(), blockpos.getZ()});
    }).stable();
    public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
        public BlockPos decode(ByteBuf input) {
            return FriendlyByteBuf.readBlockPos(input);
        }

        public void encode(ByteBuf output, BlockPos value) {
            FriendlyByteBuf.writeBlockPos(output, value);
        }
    };
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    public static final int PACKED_HORIZONTAL_LENGTH = 1 + Mth.log2(Mth.smallestEncompassingPowerOfTwo(30000000));
    public static final int PACKED_Y_LENGTH = 64 - 2 * BlockPos.PACKED_HORIZONTAL_LENGTH;
    private static final long PACKED_X_MASK = (1L << BlockPos.PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << BlockPos.PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << BlockPos.PACKED_HORIZONTAL_LENGTH) - 1L;
    private static final int Y_OFFSET = 0;
    private static final int Z_OFFSET = BlockPos.PACKED_Y_LENGTH;
    private static final int X_OFFSET = BlockPos.PACKED_Y_LENGTH + BlockPos.PACKED_HORIZONTAL_LENGTH;
    public static final int MAX_HORIZONTAL_COORDINATE = (1 << BlockPos.PACKED_HORIZONTAL_LENGTH) / 2 - 1;

    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public BlockPos(Vec3i vec3i) {
        this(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }

    public static long offset(long blockNode, Direction offset) {
        return offset(blockNode, offset.getStepX(), offset.getStepY(), offset.getStepZ());
    }

    public static long offset(long blockNode, int stepX, int stepY, int stepZ) {
        return asLong(getX(blockNode) + stepX, getY(blockNode) + stepY, getZ(blockNode) + stepZ);
    }

    public static int getX(long blockNode) {
        return (int) (blockNode << 64 - BlockPos.X_OFFSET - BlockPos.PACKED_HORIZONTAL_LENGTH >> 64 - BlockPos.PACKED_HORIZONTAL_LENGTH);
    }

    public static int getY(long blockNode) {
        return (int) (blockNode << 64 - BlockPos.PACKED_Y_LENGTH >> 64 - BlockPos.PACKED_Y_LENGTH);
    }

    public static int getZ(long blockNode) {
        return (int) (blockNode << 64 - BlockPos.Z_OFFSET - BlockPos.PACKED_HORIZONTAL_LENGTH >> 64 - BlockPos.PACKED_HORIZONTAL_LENGTH);
    }

    public static BlockPos of(long blockNode) {
        return new BlockPos(getX(blockNode), getY(blockNode), getZ(blockNode));
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    public static BlockPos containing(Position pos) {
        return containing(pos.x(), pos.y(), pos.z());
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public long asLong() {
        return asLong(this.getX(), this.getY(), this.getZ());
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;

        l |= ((long) x & BlockPos.PACKED_X_MASK) << BlockPos.X_OFFSET;
        l |= ((long) y & BlockPos.PACKED_Y_MASK) << 0;
        l |= ((long) z & BlockPos.PACKED_Z_MASK) << BlockPos.Z_OFFSET;
        return l;
    }

    public static long getFlatIndex(long neighborBlockNode) {
        return neighborBlockNode & -16L;
    }

    @Override
    public BlockPos offset(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3 getCenter() {
        return Vec3.atCenterOf(this);
    }

    public Vec3 getBottomCenter() {
        return Vec3.atBottomCenterOf(this);
    }

    @Override
    public BlockPos offset(Vec3i vec) {
        return this.offset(vec.getX(), vec.getY(), vec.getZ());
    }

    @Override
    public BlockPos subtract(Vec3i vec) {
        return this.offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    @Override
    public BlockPos multiply(int scale) {
        return scale == 1 ? this : (scale == 0 ? BlockPos.ZERO : new BlockPos(this.getX() * scale, this.getY() * scale, this.getZ() * scale));
    }

    @Override
    public BlockPos above() {
        return this.relative(Direction.UP);
    }

    @Override
    public BlockPos above(int steps) {
        return this.relative(Direction.UP, steps);
    }

    @Override
    public BlockPos below() {
        return this.relative(Direction.DOWN);
    }

    @Override
    public BlockPos below(int steps) {
        return this.relative(Direction.DOWN, steps);
    }

    @Override
    public BlockPos north() {
        return this.relative(Direction.NORTH);
    }

    @Override
    public BlockPos north(int steps) {
        return this.relative(Direction.NORTH, steps);
    }

    @Override
    public BlockPos south() {
        return this.relative(Direction.SOUTH);
    }

    @Override
    public BlockPos south(int steps) {
        return this.relative(Direction.SOUTH, steps);
    }

    @Override
    public BlockPos west() {
        return this.relative(Direction.WEST);
    }

    @Override
    public BlockPos west(int steps) {
        return this.relative(Direction.WEST, steps);
    }

    @Override
    public BlockPos east() {
        return this.relative(Direction.EAST);
    }

    @Override
    public BlockPos east(int steps) {
        return this.relative(Direction.EAST, steps);
    }

    @Override
    public BlockPos relative(Direction direction) {
        return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
    }

    @Override
    public BlockPos relative(Direction direction, int steps) {
        return steps == 0 ? this : new BlockPos(this.getX() + direction.getStepX() * steps, this.getY() + direction.getStepY() * steps, this.getZ() + direction.getStepZ() * steps);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int steps) {
        if (steps == 0) {
            return this;
        } else {
            int j = axis == Direction.Axis.X ? steps : 0;
            int k = axis == Direction.Axis.Y ? steps : 0;
            int l = axis == Direction.Axis.Z ? steps : 0;

            return new BlockPos(this.getX() + j, this.getY() + k, this.getZ() + l);
        }
    }

    public BlockPos rotate(Rotation rotation) {
        BlockPos blockpos;

        switch (rotation) {
            case CLOCKWISE_90:
                blockpos = new BlockPos(-this.getZ(), this.getY(), this.getX());
                break;
            case CLOCKWISE_180:
                blockpos = new BlockPos(-this.getX(), this.getY(), -this.getZ());
                break;
            case COUNTERCLOCKWISE_90:
                blockpos = new BlockPos(this.getZ(), this.getY(), -this.getX());
                break;
            case NONE:
                blockpos = this;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return blockpos;
    }

    @Override
    public BlockPos cross(Vec3i upVector) {
        return new BlockPos(this.getY() * upVector.getZ() - this.getZ() * upVector.getY(), this.getZ() * upVector.getX() - this.getX() * upVector.getZ(), this.getX() * upVector.getY() - this.getY() * upVector.getX());
    }

    public BlockPos atY(int y) {
        return new BlockPos(this.getX(), y, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public Vec3 clampLocationWithin(Vec3 location) {
        return new Vec3(Mth.clamp(location.x, (double) ((float) this.getX() + 1.0E-5F), (double) this.getX() + 1.0D - (double) 1.0E-5F), Mth.clamp(location.y, (double) ((float) this.getY() + 1.0E-5F), (double) this.getY() + 1.0D - (double) 1.0E-5F), Mth.clamp(location.z, (double) ((float) this.getZ() + 1.0E-5F), (double) this.getZ() + 1.0D - (double) 1.0E-5F));
    }

    public static Iterable<BlockPos> randomInCube(RandomSource random, int limit, BlockPos center, int sizeToScanInAllDirections) {
        return randomBetweenClosed(random, limit, center.getX() - sizeToScanInAllDirections, center.getY() - sizeToScanInAllDirections, center.getZ() - sizeToScanInAllDirections, center.getX() + sizeToScanInAllDirections, center.getY() + sizeToScanInAllDirections, center.getZ() + sizeToScanInAllDirections);
    }

    /** @deprecated */
    @Deprecated
    public static Stream<BlockPos> squareOutSouthEast(BlockPos from) {
        return Stream.of(from, from.south(), from.east(), from.south().east());
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource random, int limit, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int l1 = maxX - minX + 1;
        int i2 = maxY - minY + 1;
        int j2 = maxZ - minZ + 1;

        return () -> {
            return new AbstractIterator<BlockPos>() {
                final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
                int counter = limit;

                protected BlockPos computeNext() {
                    if (this.counter <= 0) {
                        return (BlockPos) this.endOfData();
                    } else {
                        BlockPos blockpos = this.nextPos.set(minX + random.nextInt(l1), minY + random.nextInt(i2), minZ + random.nextInt(j2));

                        --this.counter;
                        return blockpos;
                    }
                }
            };
        };
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos origin, int reachX, int reachY, int reachZ) {
        int l = reachX + reachY + reachZ;
        int i1 = origin.getX();
        int j1 = origin.getY();
        int k1 = origin.getZ();

        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int currentDepth;
                private int maxX;
                private int maxY;
                private int x;
                private int y;
                private boolean zMirror;

                protected BlockPos computeNext() {
                    if (this.zMirror) {
                        this.zMirror = false;
                        this.cursor.setZ(k1 - (this.cursor.getZ() - k1));
                        return this.cursor;
                    } else {
                        BlockPos blockpos1;

                        for (blockpos1 = null; blockpos1 == null; ++this.y) {
                            if (this.y > this.maxY) {
                                ++this.x;
                                if (this.x > this.maxX) {
                                    ++this.currentDepth;
                                    if (this.currentDepth > l) {
                                        return (BlockPos) this.endOfData();
                                    }

                                    this.maxX = Math.min(reachX, this.currentDepth);
                                    this.x = -this.maxX;
                                }

                                this.maxY = Math.min(reachY, this.currentDepth - Math.abs(this.x));
                                this.y = -this.maxY;
                            }

                            int l1 = this.x;
                            int i2 = this.y;
                            int j2 = this.currentDepth - Math.abs(l1) - Math.abs(i2);

                            if (j2 <= reachZ) {
                                this.zMirror = j2 != 0;
                                blockpos1 = this.cursor.set(i1 + l1, j1 + i2, k1 + j2);
                            }
                        }

                        return blockpos1;
                    }
                }
            };
        };
    }

    public static Optional<BlockPos> findClosestMatch(BlockPos startPos, int horizontalSearchRadius, int verticalSearchRadius, Predicate<BlockPos> predicate) {
        for (BlockPos blockpos1 : withinManhattan(startPos, horizontalSearchRadius, verticalSearchRadius, horizontalSearchRadius)) {
            if (predicate.test(blockpos1)) {
                return Optional.of(blockpos1);
            }
        }

        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos origin, int reachX, int reachY, int reachZ) {
        return StreamSupport.stream(withinManhattan(origin, reachX, reachY, reachZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(AABB box) {
        BlockPos blockpos = containing(box.minX, box.minY, box.minZ);
        BlockPos blockpos1 = containing(box.maxX, box.maxY, box.maxZ);

        return betweenClosed(blockpos, blockpos1);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        return betweenClosed(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos a, BlockPos b) {
        return StreamSupport.stream(betweenClosed(a, b).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox boundingBox) {
        return betweenClosedStream(Math.min(boundingBox.minX(), boundingBox.maxX()), Math.min(boundingBox.minY(), boundingBox.maxY()), Math.min(boundingBox.minZ(), boundingBox.maxZ()), Math.max(boundingBox.minX(), boundingBox.maxX()), Math.max(boundingBox.minY(), boundingBox.maxY()), Math.max(boundingBox.minZ(), boundingBox.maxZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(AABB box) {
        return betweenClosedStream(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ));
    }

    public static Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int k1 = maxX - minX + 1;
        int l1 = maxY - minY + 1;
        int i2 = maxZ - minZ + 1;
        int j2 = k1 * l1 * i2;

        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int index;

                protected BlockPos computeNext() {
                    if (this.index == j2) {
                        return (BlockPos) this.endOfData();
                    } else {
                        int k2 = this.index % k1;
                        int l2 = this.index / k1;
                        int i3 = l2 % l1;
                        int j3 = l2 / l1;

                        ++this.index;
                        return this.cursor.set(minX + k2, minY + i3, minZ + j3);
                    }
                }
            };
        };
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos center, int radius, Direction firstDirection, Direction secondDirection) {
        Validate.validState(firstDirection.getAxis() != secondDirection.getAxis(), "The two directions cannot be on the same axis", new Object[0]);
        return () -> {
            return new AbstractIterator<BlockPos.MutableBlockPos>() {
                private final Direction[] directions = new Direction[]{firstDirection, secondDirection, firstDirection.getOpposite(), secondDirection.getOpposite()};
                private final BlockPos.MutableBlockPos cursor = center.mutable().move(secondDirection);
                private final int legs = 4 * radius;
                private int leg = -1;
                private int legSize;
                private int legIndex;
                private int lastX;
                private int lastY;
                private int lastZ;

                {
                    this.lastX = this.cursor.getX();
                    this.lastY = this.cursor.getY();
                    this.lastZ = this.cursor.getZ();
                }

                protected BlockPos.MutableBlockPos computeNext() {
                    this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                    this.lastX = this.cursor.getX();
                    this.lastY = this.cursor.getY();
                    this.lastZ = this.cursor.getZ();
                    if (this.legIndex >= this.legSize) {
                        if (this.leg >= this.legs) {
                            return (BlockPos.MutableBlockPos) this.endOfData();
                        }

                        ++this.leg;
                        this.legIndex = 0;
                        this.legSize = this.leg / 2 + 1;
                    }

                    ++this.legIndex;
                    return this.cursor;
                }
            };
        };
    }

    public static int breadthFirstTraversal(BlockPos startPos, int maxDepth, int maxCount, BiConsumer<BlockPos, Consumer<BlockPos>> neighbourProvider, Function<BlockPos, BlockPos.TraversalNodeStatus> nodeProcessor) {
        Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque();
        LongSet longset = new LongOpenHashSet();

        queue.add(Pair.of(startPos, 0));
        int k = 0;

        while (!((Queue) queue).isEmpty()) {
            Pair<BlockPos, Integer> pair = (Pair) queue.poll();
            BlockPos blockpos1 = (BlockPos) pair.getLeft();
            int l = (Integer) pair.getRight();
            long i1 = blockpos1.asLong();

            if (longset.add(i1)) {
                BlockPos.TraversalNodeStatus blockpos_traversalnodestatus = (BlockPos.TraversalNodeStatus) nodeProcessor.apply(blockpos1);

                if (blockpos_traversalnodestatus != BlockPos.TraversalNodeStatus.SKIP) {
                    if (blockpos_traversalnodestatus == BlockPos.TraversalNodeStatus.STOP) {
                        break;
                    }

                    ++k;
                    if (k >= maxCount) {
                        return k;
                    }

                    if (l < maxDepth) {
                        neighbourProvider.accept(blockpos1, (Consumer) (blockpos2) -> {
                            queue.add(Pair.of(blockpos2, l + 1));
                        });
                    }
                }
            }
        }

        return k;
    }

    public static Iterable<BlockPos> betweenCornersInDirection(AABB aabb, Vec3 direction) {
        Vec3 vec31 = aabb.getMinPosition();
        int i = Mth.floor(vec31.x());
        int j = Mth.floor(vec31.y());
        int k = Mth.floor(vec31.z());
        Vec3 vec32 = aabb.getMaxPosition();
        int l = Mth.floor(vec32.x());
        int i1 = Mth.floor(vec32.y());
        int j1 = Mth.floor(vec32.z());

        return betweenCornersInDirection(i, j, k, l, i1, j1, direction);
    }

    public static Iterable<BlockPos> betweenCornersInDirection(BlockPos firstCorner, BlockPos secondCorner, Vec3 direction) {
        return betweenCornersInDirection(firstCorner.getX(), firstCorner.getY(), firstCorner.getZ(), secondCorner.getX(), secondCorner.getY(), secondCorner.getZ(), direction);
    }

    public static Iterable<BlockPos> betweenCornersInDirection(int firstCornerX, int firstCornerY, int firstCornerZ, int secondCornerX, int secondCornerY, int secondCornerZ, Vec3 direction) {
        int k1 = Math.min(firstCornerX, secondCornerX);
        int l1 = Math.min(firstCornerY, secondCornerY);
        int i2 = Math.min(firstCornerZ, secondCornerZ);
        int j2 = Math.max(firstCornerX, secondCornerX);
        int k2 = Math.max(firstCornerY, secondCornerY);
        int l2 = Math.max(firstCornerZ, secondCornerZ);
        int i3 = j2 - k1;
        int j3 = k2 - l1;
        int k3 = l2 - i2;
        int l3 = direction.x >= 0.0D ? k1 : j2;
        int i4 = direction.y >= 0.0D ? l1 : k2;
        int j4 = direction.z >= 0.0D ? i2 : l2;
        List<Direction.Axis> list = Direction.axisStepOrder(direction);
        Direction.Axis direction_axis = (Direction.Axis) list.get(0);
        Direction.Axis direction_axis1 = (Direction.Axis) list.get(1);
        Direction.Axis direction_axis2 = (Direction.Axis) list.get(2);
        Direction direction1 = direction.get(direction_axis) >= 0.0D ? direction_axis.getPositive() : direction_axis.getNegative();
        Direction direction2 = direction.get(direction_axis1) >= 0.0D ? direction_axis1.getPositive() : direction_axis1.getNegative();
        Direction direction3 = direction.get(direction_axis2) >= 0.0D ? direction_axis2.getPositive() : direction_axis2.getNegative();
        int k4 = direction_axis.choose(i3, j3, k3);
        int l4 = direction_axis1.choose(i3, j3, k3);
        int i5 = direction_axis2.choose(i3, j3, k3);

        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int firstIndex;
                private int secondIndex;
                private int thirdIndex;
                private boolean end;
                private final int firstDirX = direction1.getStepX();
                private final int firstDirY = direction1.getStepY();
                private final int firstDirZ = direction1.getStepZ();
                private final int secondDirX = direction2.getStepX();
                private final int secondDirY = direction2.getStepY();
                private final int secondDirZ = direction2.getStepZ();
                private final int thirdDirX = direction3.getStepX();
                private final int thirdDirY = direction3.getStepY();
                private final int thirdDirZ = direction3.getStepZ();

                protected BlockPos computeNext() {
                    if (this.end) {
                        return (BlockPos) this.endOfData();
                    } else {
                        this.cursor.set(l3 + this.firstDirX * this.firstIndex + this.secondDirX * this.secondIndex + this.thirdDirX * this.thirdIndex, i4 + this.firstDirY * this.firstIndex + this.secondDirY * this.secondIndex + this.thirdDirY * this.thirdIndex, j4 + this.firstDirZ * this.firstIndex + this.secondDirZ * this.secondIndex + this.thirdDirZ * this.thirdIndex);
                        if (this.thirdIndex < i5) {
                            ++this.thirdIndex;
                        } else if (this.secondIndex < l4) {
                            ++this.secondIndex;
                            this.thirdIndex = 0;
                        } else if (this.firstIndex < k4) {
                            ++this.firstIndex;
                            this.thirdIndex = 0;
                            this.secondIndex = 0;
                        } else {
                            this.end = true;
                        }

                        return this.cursor;
                    }
                }
            };
        };
    }

    public static class MutableBlockPos extends BlockPos {

        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            super(x, y, z);
        }

        public MutableBlockPos(double x, double y, double z) {
            this(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        @Override
        public BlockPos offset(int x, int y, int z) {
            return super.offset(x, y, z).immutable();
        }

        @Override
        public BlockPos multiply(int scale) {
            return super.multiply(scale).immutable();
        }

        @Override
        public BlockPos relative(Direction direction, int steps) {
            return super.relative(direction, steps).immutable();
        }

        @Override
        public BlockPos relative(Direction.Axis axis, int steps) {
            return super.relative(axis, steps).immutable();
        }

        @Override
        public BlockPos rotate(Rotation rotation) {
            return super.rotate(rotation).immutable();
        }

        public BlockPos.MutableBlockPos set(int x, int y, int z) {
            this.setX(x);
            this.setY(y);
            this.setZ(z);
            return this;
        }

        public BlockPos.MutableBlockPos set(double x, double y, double z) {
            return this.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        }

        public BlockPos.MutableBlockPos set(Vec3i vec) {
            return this.set(vec.getX(), vec.getY(), vec.getZ());
        }

        public BlockPos.MutableBlockPos set(long pos) {
            return this.set(getX(pos), getY(pos), getZ(pos));
        }

        public BlockPos.MutableBlockPos set(AxisCycle transform, int x, int y, int z) {
            return this.set(transform.cycle(x, y, z, Direction.Axis.X), transform.cycle(x, y, z, Direction.Axis.Y), transform.cycle(x, y, z, Direction.Axis.Z));
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Direction direction) {
            return this.set(pos.getX() + direction.getStepX(), pos.getY() + direction.getStepY(), pos.getZ() + direction.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, int x, int y, int z) {
            return this.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            return this.set(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
        }

        public BlockPos.MutableBlockPos move(Direction direction) {
            return this.move(direction, 1);
        }

        public BlockPos.MutableBlockPos move(Direction direction, int steps) {
            return this.set(this.getX() + direction.getStepX() * steps, this.getY() + direction.getStepY() * steps, this.getZ() + direction.getStepZ() * steps);
        }

        public BlockPos.MutableBlockPos move(int x, int y, int z) {
            return this.set(this.getX() + x, this.getY() + y, this.getZ() + z);
        }

        public BlockPos.MutableBlockPos move(Vec3i pos) {
            return this.set(this.getX() + pos.getX(), this.getY() + pos.getY(), this.getZ() + pos.getZ());
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int minimum, int maximum) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos;

            switch (axis) {
                case X:
                    blockpos_mutableblockpos = this.set(Mth.clamp(this.getX(), minimum, maximum), this.getY(), this.getZ());
                    break;
                case Y:
                    blockpos_mutableblockpos = this.set(this.getX(), Mth.clamp(this.getY(), minimum, maximum), this.getZ());
                    break;
                case Z:
                    blockpos_mutableblockpos = this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), minimum, maximum));
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return blockpos_mutableblockpos;
        }

        @Override
        public BlockPos.MutableBlockPos setX(int x) {
            super.setX(x);
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setY(int y) {
            super.setY(y);
            return this;
        }

        @Override
        public BlockPos.MutableBlockPos setZ(int z) {
            super.setZ(z);
            return this;
        }

        @Override
        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }

    public static enum TraversalNodeStatus {

        ACCEPT, SKIP, STOP;

        private TraversalNodeStatus() {}
    }
}
