package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class RegionFileStorage implements AutoCloseable {

    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    RegionFileStorage(RegionStorageInfo info, Path folder, boolean sync) {
        this.folder = folder;
        this.sync = sync;
        this.info = info;
    }

    private RegionFile getRegionFile(ChunkPos pos) throws IOException {
        long i = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        RegionFile regionfile = (RegionFile) this.regionCache.getAndMoveToFirst(i);

        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.regionCache.size() >= 256) {
                ((RegionFile) this.regionCache.removeLast()).close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = this.folder;
            int j = pos.getRegionX();
            Path path1 = path.resolve("r." + j + "." + pos.getRegionZ() + ".mca");
            RegionFile regionfile1 = new RegionFile(this.info, path1, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    public @Nullable CompoundTag read(ChunkPos pos) throws IOException {
        RegionFile regionfile = this.getRegionFile(pos);

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos)) {
            if (datainputstream == null) {
                return null;
            } else {
                return NbtIo.read((DataInput) datainputstream);
            }
        }
    }

    public void scanChunk(ChunkPos pos, StreamTagVisitor scanner) throws IOException {
        RegionFile regionfile = this.getRegionFile(pos);

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos)) {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, scanner, NbtAccounter.unlimitedHeap());
            }
        }

    }

    protected void write(ChunkPos pos, @Nullable CompoundTag value) throws IOException {
        if (!SharedConstants.DEBUG_DONT_SAVE_WORLD) {
            RegionFile regionfile = this.getRegionFile(pos);

            if (value == null) {
                regionfile.clear(pos);
            } else {
                try (DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(pos)) {
                    NbtIo.write(value, (DataOutput) dataoutputstream);
                }
            }

        }
    }

    public void close() throws IOException {
        ExceptionCollector<IOException> exceptioncollector = new ExceptionCollector<IOException>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptioncollector.add(ioexception);
            }
        }

        exceptioncollector.throwIfPresent();
    }

    public void flush() throws IOException {
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.flush();
        }

    }

    public RegionStorageInfo info() {
        return this.info;
    }
}
