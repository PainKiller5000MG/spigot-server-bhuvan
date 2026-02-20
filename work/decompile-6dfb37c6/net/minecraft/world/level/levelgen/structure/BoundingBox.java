package net.minecraft.world.level.levelgen.structure;

import com.google.common.base.MoreObjects;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class BoundingBox {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<BoundingBox> CODEC = Codec.INT_STREAM.comapFlatMap((intstream) -> {
        return Util.fixedSize(intstream, 6).map((aint) -> {
            return new BoundingBox(aint[0], aint[1], aint[2], aint[3], aint[4], aint[5]);
        });
    }, (boundingbox) -> {
        return IntStream.of(new int[]{boundingbox.minX, boundingbox.minY, boundingbox.minZ, boundingbox.maxX, boundingbox.maxY, boundingbox.maxZ});
    }).stable();
    public static final StreamCodec<ByteBuf, BoundingBox> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, (boundingbox) -> {
        return new BlockPos(boundingbox.minX, boundingbox.minY, boundingbox.minZ);
    }, BlockPos.STREAM_CODEC, (boundingbox) -> {
        return new BlockPos(boundingbox.maxX, boundingbox.maxY, boundingbox.maxZ);
    }, (blockpos, blockpos1) -> {
        return new BoundingBox(blockpos.getX(), blockpos.getY(), blockpos.getZ(), blockpos1.getX(), blockpos1.getY(), blockpos1.getZ());
    });
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BoundingBox(BlockPos content) {
        this(content.getX(), content.getY(), content.getZ(), content.getX(), content.getY(), content.getZ());
    }

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            Util.logAndPauseIfInIde("Invalid bounding box data, inverted bounds for: " + String.valueOf(this));
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

    }

    public static BoundingBox fromCorners(Vec3i pos0, Vec3i pos1) {
        return new BoundingBox(Math.min(pos0.getX(), pos1.getX()), Math.min(pos0.getY(), pos1.getY()), Math.min(pos0.getZ(), pos1.getZ()), Math.max(pos0.getX(), pos1.getX()), Math.max(pos0.getY(), pos1.getY()), Math.max(pos0.getZ(), pos1.getZ()));
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static BoundingBox orientBox(int footX, int footY, int footZ, int offX, int offY, int offZ, int width, int height, int depth, Direction direction) {
        switch (direction) {
            case SOUTH:
            default:
                return new BoundingBox(footX + offX, footY + offY, footZ + offZ, footX + width - 1 + offX, footY + height - 1 + offY, footZ + depth - 1 + offZ);
            case NORTH:
                return new BoundingBox(footX + offX, footY + offY, footZ - depth + 1 + offZ, footX + width - 1 + offX, footY + height - 1 + offY, footZ + offZ);
            case WEST:
                return new BoundingBox(footX - depth + 1 + offZ, footY + offY, footZ + offX, footX + offZ, footY + height - 1 + offY, footZ + width - 1 + offX);
            case EAST:
                return new BoundingBox(footX + offZ, footY + offY, footZ + offX, footX + depth - 1 + offZ, footY + height - 1 + offY, footZ + width - 1 + offX);
        }
    }

    public Stream<ChunkPos> intersectingChunks() {
        int i = SectionPos.blockToSectionCoord(this.minX());
        int j = SectionPos.blockToSectionCoord(this.minZ());
        int k = SectionPos.blockToSectionCoord(this.maxX());
        int l = SectionPos.blockToSectionCoord(this.maxZ());

        return ChunkPos.rangeClosed(new ChunkPos(i, j), new ChunkPos(k, l));
    }

    public boolean intersects(BoundingBox other) {
        return this.maxX >= other.minX && this.minX <= other.maxX && this.maxZ >= other.minZ && this.minZ <= other.maxZ && this.maxY >= other.minY && this.minY <= other.maxY;
    }

    public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
        return this.maxX >= minX && this.minX <= maxX && this.maxZ >= minZ && this.minZ <= maxZ;
    }

    public static Optional<BoundingBox> encapsulatingPositions(Iterable<BlockPos> iterable) {
        Iterator<BlockPos> iterator = iterable.iterator();

        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingbox = new BoundingBox((BlockPos) iterator.next());

            Objects.requireNonNull(boundingbox);
            iterator.forEachRemaining(boundingbox::encapsulate);
            return Optional.of(boundingbox);
        }
    }

    public static Optional<BoundingBox> encapsulatingBoxes(Iterable<BoundingBox> iterable) {
        Iterator<BoundingBox> iterator = iterable.iterator();

        if (!iterator.hasNext()) {
            return Optional.empty();
        } else {
            BoundingBox boundingbox = (BoundingBox) iterator.next();
            BoundingBox boundingbox1 = new BoundingBox(boundingbox.minX, boundingbox.minY, boundingbox.minZ, boundingbox.maxX, boundingbox.maxY, boundingbox.maxZ);

            Objects.requireNonNull(boundingbox1);
            iterator.forEachRemaining(boundingbox1::encapsulate);
            return Optional.of(boundingbox1);
        }
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox encapsulate(BoundingBox other) {
        this.minX = Math.min(this.minX, other.minX);
        this.minY = Math.min(this.minY, other.minY);
        this.minZ = Math.min(this.minZ, other.minZ);
        this.maxX = Math.max(this.maxX, other.maxX);
        this.maxY = Math.max(this.maxY, other.maxY);
        this.maxZ = Math.max(this.maxZ, other.maxZ);
        return this;
    }

    public static BoundingBox encapsulating(BoundingBox a, BoundingBox b) {
        return new BoundingBox(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ), Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox encapsulate(BlockPos pos) {
        this.minX = Math.min(this.minX, pos.getX());
        this.minY = Math.min(this.minY, pos.getY());
        this.minZ = Math.min(this.minZ, pos.getZ());
        this.maxX = Math.max(this.maxX, pos.getX());
        this.maxY = Math.max(this.maxY, pos.getY());
        this.maxZ = Math.max(this.maxZ, pos.getZ());
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox move(int dx, int dy, int dz) {
        this.minX += dx;
        this.minY += dy;
        this.minZ += dz;
        this.maxX += dx;
        this.maxY += dy;
        this.maxZ += dz;
        return this;
    }

    /** @deprecated */
    @Deprecated
    public BoundingBox move(Vec3i amount) {
        return this.move(amount.getX(), amount.getY(), amount.getZ());
    }

    public BoundingBox moved(int dx, int dy, int dz) {
        return new BoundingBox(this.minX + dx, this.minY + dy, this.minZ + dz, this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public BoundingBox inflatedBy(int amountToAddAllDirections) {
        return this.inflatedBy(amountToAddAllDirections, amountToAddAllDirections, amountToAddAllDirections);
    }

    public BoundingBox inflatedBy(int inflateX, int inflateY, int inflateZ) {
        return new BoundingBox(this.minX() - inflateX, this.minY() - inflateY, this.minZ() - inflateZ, this.maxX() + inflateX, this.maxY() + inflateY, this.maxZ() + inflateZ);
    }

    public boolean isInside(Vec3i pos) {
        return this.isInside(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isInside(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ && y >= this.minY && y <= this.maxY;
    }

    public Vec3i getLength() {
        return new Vec3i(this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ);
    }

    public int getXSpan() {
        return this.maxX - this.minX + 1;
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public int getZSpan() {
        return this.maxZ - this.minZ + 1;
    }

    public BlockPos getCenter() {
        return new BlockPos(this.minX + (this.maxX - this.minX + 1) / 2, this.minY + (this.maxY - this.minY + 1) / 2, this.minZ + (this.maxZ - this.minZ + 1) / 2);
    }

    public void forAllCorners(Consumer<BlockPos> consumer) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        consumer.accept(blockpos_mutableblockpos.set(this.maxX, this.maxY, this.maxZ));
        consumer.accept(blockpos_mutableblockpos.set(this.minX, this.maxY, this.maxZ));
        consumer.accept(blockpos_mutableblockpos.set(this.maxX, this.minY, this.maxZ));
        consumer.accept(blockpos_mutableblockpos.set(this.minX, this.minY, this.maxZ));
        consumer.accept(blockpos_mutableblockpos.set(this.maxX, this.maxY, this.minZ));
        consumer.accept(blockpos_mutableblockpos.set(this.minX, this.maxY, this.minZ));
        consumer.accept(blockpos_mutableblockpos.set(this.maxX, this.minY, this.minZ));
        consumer.accept(blockpos_mutableblockpos.set(this.minX, this.minY, this.minZ));
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("minX", this.minX).add("minY", this.minY).add("minZ", this.minZ).add("maxX", this.maxX).add("maxY", this.maxY).add("maxZ", this.maxZ).toString();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof BoundingBox)) {
            return false;
        } else {
            BoundingBox boundingbox = (BoundingBox) o;

            return this.minX == boundingbox.minX && this.minY == boundingbox.minY && this.minZ == boundingbox.minZ && this.maxX == boundingbox.maxX && this.maxY == boundingbox.maxY && this.maxZ == boundingbox.maxZ;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ});
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }
}
