package net.minecraft.world.level.levelgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public class Heightmap {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Predicate<BlockState> NOT_AIR = (blockstate) -> {
        return !blockstate.isAir();
    };
    private static final Predicate<BlockState> MATERIAL_MOTION_BLOCKING = BlockBehaviour.BlockStateBase::blocksMotion;
    private final BitStorage data;
    private final Predicate<BlockState> isOpaque;
    private final ChunkAccess chunk;

    public Heightmap(ChunkAccess chunk, Heightmap.Types heightmapType) {
        this.isOpaque = heightmapType.isOpaque();
        this.chunk = chunk;
        int i = Mth.ceillog2(chunk.getHeight() + 1);

        this.data = new SimpleBitStorage(i, 256);
    }

    public static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types) {
        if (!types.isEmpty()) {
            int i = types.size();
            ObjectList<Heightmap> objectlist = new ObjectArrayList(i);
            ObjectListIterator<Heightmap> objectlistiterator = objectlist.iterator();
            int j = chunk.getHighestSectionPosition() + 16;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    for (Heightmap.Types heightmap_types : types) {
                        objectlist.add(chunk.getOrCreateHeightmapUnprimed(heightmap_types));
                    }

                    for (int i1 = j - 1; i1 >= chunk.getMinY(); --i1) {
                        blockpos_mutableblockpos.set(k, i1, l);
                        BlockState blockstate = chunk.getBlockState(blockpos_mutableblockpos);

                        if (!blockstate.is(Blocks.AIR)) {
                            while (objectlistiterator.hasNext()) {
                                Heightmap heightmap = (Heightmap) objectlistiterator.next();

                                if (heightmap.isOpaque.test(blockstate)) {
                                    heightmap.setHeight(k, l, i1 + 1);
                                    objectlistiterator.remove();
                                }
                            }

                            if (objectlist.isEmpty()) {
                                break;
                            }

                            objectlistiterator.back(i);
                        }
                    }
                }
            }

        }
    }

    public boolean update(int localX, int localY, int localZ, BlockState state) {
        int l = this.getFirstAvailable(localX, localZ);

        if (localY <= l - 2) {
            return false;
        } else {
            if (this.isOpaque.test(state)) {
                if (localY >= l) {
                    this.setHeight(localX, localZ, localY + 1);
                    return true;
                }
            } else if (l - 1 == localY) {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

                for (int i1 = localY - 1; i1 >= this.chunk.getMinY(); --i1) {
                    blockpos_mutableblockpos.set(localX, i1, localZ);
                    if (this.isOpaque.test(this.chunk.getBlockState(blockpos_mutableblockpos))) {
                        this.setHeight(localX, localZ, i1 + 1);
                        return true;
                    }
                }

                this.setHeight(localX, localZ, this.chunk.getMinY());
                return true;
            }

            return false;
        }
    }

    public int getFirstAvailable(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z));
    }

    public int getHighestTaken(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z)) - 1;
    }

    private int getFirstAvailable(int index) {
        return this.data.get(index) + this.chunk.getMinY();
    }

    private void setHeight(int x, int z, int height) {
        this.data.set(getIndex(x, z), height - this.chunk.getMinY());
    }

    public void setRawData(ChunkAccess chunk, Heightmap.Types type, long[] data) {
        long[] along1 = this.data.getRaw();

        if (along1.length == data.length) {
            System.arraycopy(data, 0, along1, 0, data.length);
        } else {
            Heightmap.LOGGER.warn("Ignoring heightmap data for chunk {}, size does not match; expected: {}, got: {}", new Object[]{chunk.getPos(), along1.length, data.length});
            primeHeightmaps(chunk, EnumSet.of(type));
        }
    }

    public long[] getRawData() {
        return this.data.getRaw();
    }

    private static int getIndex(int x, int z) {
        return x + z * 16;
    }

    public static enum Usage {

        WORLDGEN, LIVE_WORLD, CLIENT;

        private Usage() {}
    }

    public static enum Types implements StringRepresentable {

        WORLD_SURFACE_WG(0, "WORLD_SURFACE_WG", Heightmap.Usage.WORLDGEN, Heightmap.NOT_AIR), WORLD_SURFACE(1, "WORLD_SURFACE", Heightmap.Usage.CLIENT, Heightmap.NOT_AIR), OCEAN_FLOOR_WG(2, "OCEAN_FLOOR_WG", Heightmap.Usage.WORLDGEN, Heightmap.MATERIAL_MOTION_BLOCKING), OCEAN_FLOOR(3, "OCEAN_FLOOR", Heightmap.Usage.LIVE_WORLD, Heightmap.MATERIAL_MOTION_BLOCKING), MOTION_BLOCKING(4, "MOTION_BLOCKING", Heightmap.Usage.CLIENT, (blockstate) -> {
            return blockstate.blocksMotion() || !blockstate.getFluidState().isEmpty();
        }), MOTION_BLOCKING_NO_LEAVES(5, "MOTION_BLOCKING_NO_LEAVES", Heightmap.Usage.CLIENT, (blockstate) -> {
            return (blockstate.blocksMotion() || !blockstate.getFluidState().isEmpty()) && !(blockstate.getBlock() instanceof LeavesBlock);
        });

        public static final Codec<Heightmap.Types> CODEC = StringRepresentable.<Heightmap.Types>fromEnum(Heightmap.Types::values);
        private static final IntFunction<Heightmap.Types> BY_ID = ByIdMap.<Heightmap.Types>continuous((heightmap_types) -> {
            return heightmap_types.id;
        }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Heightmap.Types> STREAM_CODEC = ByteBufCodecs.idMapper(Heightmap.Types.BY_ID, (heightmap_types) -> {
            return heightmap_types.id;
        });
        private final int id;
        private final String serializationKey;
        private final Heightmap.Usage usage;
        private final Predicate<BlockState> isOpaque;

        private Types(int id, String serializationKey, Heightmap.Usage usage, Predicate<BlockState> isOpaque) {
            this.id = id;
            this.serializationKey = serializationKey;
            this.usage = usage;
            this.isOpaque = isOpaque;
        }

        public String getSerializationKey() {
            return this.serializationKey;
        }

        public boolean sendToClient() {
            return this.usage == Heightmap.Usage.CLIENT;
        }

        public boolean keepAfterWorldgen() {
            return this.usage != Heightmap.Usage.WORLDGEN;
        }

        public Predicate<BlockState> isOpaque() {
            return this.isOpaque;
        }

        @Override
        public String getSerializedName() {
            return this.serializationKey;
        }
    }
}
