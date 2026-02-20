package net.minecraft.world.level;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Spliterators;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public class ChunkPos {

    public static final Codec<ChunkPos> CODEC = Codec.INT_STREAM.comapFlatMap((intstream) -> {
        return Util.fixedSize(intstream, 2).map((aint) -> {
            return new ChunkPos(aint[0], aint[1]);
        });
    }, (chunkpos) -> {
        return IntStream.of(new int[]{chunkpos.x, chunkpos.z});
    }).stable();
    public static final StreamCodec<ByteBuf, ChunkPos> STREAM_CODEC = new StreamCodec<ByteBuf, ChunkPos>() {
        public ChunkPos decode(ByteBuf input) {
            return FriendlyByteBuf.readChunkPos(input);
        }

        public void encode(ByteBuf output, ChunkPos value) {
            FriendlyByteBuf.writeChunkPos(output, value);
        }
    };
    private static final int SAFETY_MARGIN = 1056;
    public static final long INVALID_CHUNK_POS = asLong(1875066, 1875066);
    private static final int SAFETY_MARGIN_CHUNKS = (32 + ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).accumulatedDependencies().size() + 1) * 2;
    public static final int MAX_COORDINATE_VALUE = SectionPos.blockToSectionCoord(BlockPos.MAX_HORIZONTAL_COORDINATE) - ChunkPos.SAFETY_MARGIN_CHUNKS;
    public static final ChunkPos ZERO = new ChunkPos(0, 0);
    private static final long COORD_BITS = 32L;
    private static final long COORD_MASK = 4294967295L;
    private static final int REGION_BITS = 5;
    public static final int REGION_SIZE = 32;
    private static final int REGION_MASK = 31;
    public static final int REGION_MAX_INDEX = 31;
    public final int x;
    public final int z;
    private static final int HASH_A = 1664525;
    private static final int HASH_C = 1013904223;
    private static final int HASH_Z_XOR = -559038737;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos pos) {
        this.x = SectionPos.blockToSectionCoord(pos.getX());
        this.z = SectionPos.blockToSectionCoord(pos.getZ());
    }

    public ChunkPos(long key) {
        this.x = (int) key;
        this.z = (int) (key >> 32);
    }

    public static ChunkPos minFromRegion(int regionX, int regionZ) {
        return new ChunkPos(regionX << 5, regionZ << 5);
    }

    public static ChunkPos maxFromRegion(int regionX, int regionZ) {
        return new ChunkPos((regionX << 5) + 31, (regionZ << 5) + 31);
    }

    public boolean isValid() {
        return isValid(this.x, this.z);
    }

    public static boolean isValid(int x, int z) {
        return Mth.absMax(x, z) <= ChunkPos.MAX_COORDINATE_VALUE;
    }

    public long toLong() {
        return asLong(this.x, this.z);
    }

    public static long asLong(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static long asLong(BlockPos pos) {
        return asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static int getX(long pos) {
        return (int) (pos & 4294967295L);
    }

    public static int getZ(long pos) {
        return (int) (pos >>> 32 & 4294967295L);
    }

    public int hashCode() {
        return hash(this.x, this.z);
    }

    public static int hash(int x, int z) {
        int k = 1664525 * x + 1013904223;
        int l = 1664525 * (z ^ -559038737) + 1013904223;

        return k ^ l;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof ChunkPos)) {
            return false;
        } else {
            ChunkPos chunkpos = (ChunkPos) o;

            return this.x == chunkpos.x && this.z == chunkpos.z;
        }
    }

    public int getMiddleBlockX() {
        return this.getBlockX(8);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(8);
    }

    public int getMinBlockX() {
        return SectionPos.sectionToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return SectionPos.sectionToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return this.getBlockX(15);
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(15);
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public int getRegionLocalX() {
        return this.x & 31;
    }

    public int getRegionLocalZ() {
        return this.z & 31;
    }

    public BlockPos getBlockAt(int x, int y, int z) {
        return new BlockPos(this.getBlockX(x), y, this.getBlockZ(z));
    }

    public int getBlockX(int offset) {
        return SectionPos.sectionToBlockCoord(this.x, offset);
    }

    public int getBlockZ(int offset) {
        return SectionPos.sectionToBlockCoord(this.z, offset);
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.getMinBlockX() && pos.getZ() >= this.getMinBlockZ() && pos.getX() <= this.getMaxBlockX() && pos.getZ() <= this.getMaxBlockZ();
    }

    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public BlockPos getWorldPosition() {
        return new BlockPos(this.getMinBlockX(), 0, this.getMinBlockZ());
    }

    public int getChessboardDistance(ChunkPos pos) {
        return this.getChessboardDistance(pos.x, pos.z);
    }

    public int getChessboardDistance(int x, int z) {
        return Mth.chessboardDistance(x, z, this.x, this.z);
    }

    public int distanceSquared(ChunkPos pos) {
        return this.distanceSquared(pos.x, pos.z);
    }

    public int distanceSquared(long pos) {
        return this.distanceSquared(getX(pos), getZ(pos));
    }

    private int distanceSquared(int x, int z) {
        int k = x - this.x;
        int l = z - this.z;

        return k * k + l * l;
    }

    public static Stream<ChunkPos> rangeClosed(ChunkPos center, int radius) {
        return rangeClosed(new ChunkPos(center.x - radius, center.z - radius), new ChunkPos(center.x + radius, center.z + radius));
    }

    public static Stream<ChunkPos> rangeClosed(final ChunkPos from, final ChunkPos to) {
        int i = Math.abs(from.x - to.x) + 1;
        int j = Math.abs(from.z - to.z) + 1;
        final int k = from.x < to.x ? 1 : -1;
        final int l = from.z < to.z ? 1 : -1;

        return StreamSupport.stream(new Spliterators.AbstractSpliterator<ChunkPos>((long) (i * j), 64) {
            private @Nullable ChunkPos pos;

            public boolean tryAdvance(Consumer<? super ChunkPos> action) {
                if (this.pos == null) {
                    this.pos = from;
                } else {
                    int i1 = this.pos.x;
                    int j1 = this.pos.z;

                    if (i1 == to.x) {
                        if (j1 == to.z) {
                            return false;
                        }

                        this.pos = new ChunkPos(from.x, j1 + l);
                    } else {
                        this.pos = new ChunkPos(i1 + k, j1);
                    }
                }

                action.accept(this.pos);
                return true;
            }
        }, false);
    }
}
