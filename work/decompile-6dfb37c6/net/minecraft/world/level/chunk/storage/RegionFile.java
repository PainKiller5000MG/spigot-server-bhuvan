package net.minecraft.world.level.chunk.storage;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class RegionFile implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECTOR_BYTES = 4096;
    @VisibleForTesting
    protected static final int SECTOR_INTS = 1024;
    private static final int CHUNK_HEADER_SIZE = 5;
    private static final int HEADER_OFFSET = 0;
    private static final ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(1);
    private static final String EXTERNAL_FILE_EXTENSION = ".mcc";
    private static final int EXTERNAL_STREAM_FLAG = 128;
    private static final int EXTERNAL_CHUNK_THRESHOLD = 256;
    private static final int CHUNK_NOT_PRESENT = 0;
    private final RegionStorageInfo info;
    private final Path path;
    private final FileChannel file;
    private final Path externalFileDir;
    private final RegionFileVersion version;
    private final ByteBuffer header;
    private final IntBuffer offsets;
    private final IntBuffer timestamps;
    @VisibleForTesting
    protected final RegionBitmap usedSectors;

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, boolean sync) throws IOException {
        this(info, path, externalFileDir, RegionFileVersion.getSelected(), sync);
    }

    public RegionFile(RegionStorageInfo info, Path path, Path externalFileDir, RegionFileVersion version, boolean sync) throws IOException {
        this.header = ByteBuffer.allocateDirect(8192);
        this.usedSectors = new RegionBitmap();
        this.info = info;
        this.path = path;
        this.version = version;
        if (!Files.isDirectory(externalFileDir, new LinkOption[0])) {
            throw new IllegalArgumentException("Expected directory, got " + String.valueOf(externalFileDir.toAbsolutePath()));
        } else {
            this.externalFileDir = externalFileDir;
            this.offsets = this.header.asIntBuffer();
            this.offsets.limit(1024);
            this.header.position(4096);
            this.timestamps = this.header.asIntBuffer();
            if (sync) {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } else {
                this.file = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            }

            this.usedSectors.force(0, 2);
            this.header.position(0);
            int i = this.file.read(this.header, 0L);

            if (i != -1) {
                if (i != 8192) {
                    RegionFile.LOGGER.warn("Region file {} has truncated header: {}", path, i);
                }

                long j = Files.size(path);

                for (int k = 0; k < 1024; ++k) {
                    int l = this.offsets.get(k);

                    if (l != 0) {
                        int i1 = getSectorNumber(l);
                        int j1 = getNumSectors(l);

                        if (i1 < 2) {
                            RegionFile.LOGGER.warn("Region file {} has invalid sector at index: {}; sector {} overlaps with header", new Object[]{path, k, i1});
                            this.offsets.put(k, 0);
                        } else if (j1 == 0) {
                            RegionFile.LOGGER.warn("Region file {} has an invalid sector at index: {}; size has to be > 0", path, k);
                            this.offsets.put(k, 0);
                        } else if ((long) i1 * 4096L > j) {
                            RegionFile.LOGGER.warn("Region file {} has an invalid sector at index: {}; sector {} is out of bounds", new Object[]{path, k, i1});
                            this.offsets.put(k, 0);
                        } else {
                            this.usedSectors.force(i1, j1);
                        }
                    }
                }
            }

        }
    }

    public Path getPath() {
        return this.path;
    }

    private Path getExternalChunkPath(ChunkPos pos) {
        String s = "c." + pos.x + "." + pos.z + ".mcc";

        return this.externalFileDir.resolve(s);
    }

    public synchronized @Nullable DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException {
        int i = this.getOffset(pos);

        if (i == 0) {
            return null;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            int l = k * 4096;
            ByteBuffer bytebuffer = ByteBuffer.allocate(l);

            this.file.read(bytebuffer, (long) (j * 4096));
            bytebuffer.flip();
            if (bytebuffer.remaining() < 5) {
                RegionFile.LOGGER.error("Chunk {} header is truncated: expected {} but read {}", new Object[]{pos, l, bytebuffer.remaining()});
                return null;
            } else {
                int i1 = bytebuffer.getInt();
                byte b0 = bytebuffer.get();

                if (i1 == 0) {
                    RegionFile.LOGGER.warn("Chunk {} is allocated, but stream is missing", pos);
                    return null;
                } else {
                    int j1 = i1 - 1;

                    if (isExternalStreamChunk(b0)) {
                        if (j1 != 0) {
                            RegionFile.LOGGER.warn("Chunk has both internal and external streams");
                        }

                        return this.createExternalChunkInputStream(pos, getExternalChunkVersion(b0));
                    } else if (j1 > bytebuffer.remaining()) {
                        RegionFile.LOGGER.error("Chunk {} stream is truncated: expected {} but read {}", new Object[]{pos, j1, bytebuffer.remaining()});
                        return null;
                    } else if (j1 < 0) {
                        RegionFile.LOGGER.error("Declared size {} of chunk {} is negative", i1, pos);
                        return null;
                    } else {
                        JvmProfiler.INSTANCE.onRegionFileRead(this.info, pos, this.version, j1);
                        return this.createChunkInputStream(pos, b0, createStream(bytebuffer, j1));
                    }
                }
            }
        }
    }

    private static int getTimestamp() {
        return (int) (Util.getEpochMillis() / 1000L);
    }

    private static boolean isExternalStreamChunk(byte version) {
        return (version & 128) != 0;
    }

    private static byte getExternalChunkVersion(byte version) {
        return (byte) (version & -129);
    }

    private @Nullable DataInputStream createChunkInputStream(ChunkPos pos, byte versionId, InputStream chunkStream) throws IOException {
        RegionFileVersion regionfileversion = RegionFileVersion.fromId(versionId);

        if (regionfileversion == RegionFileVersion.VERSION_CUSTOM) {
            String s = (new DataInputStream(chunkStream)).readUTF();
            Identifier identifier = Identifier.tryParse(s);

            if (identifier != null) {
                RegionFile.LOGGER.error("Unrecognized custom compression {}", identifier);
                return null;
            } else {
                RegionFile.LOGGER.error("Invalid custom compression id {}", s);
                return null;
            }
        } else if (regionfileversion == null) {
            RegionFile.LOGGER.error("Chunk {} has invalid chunk stream version {}", pos, versionId);
            return null;
        } else {
            return new DataInputStream(regionfileversion.wrap(chunkStream));
        }
    }

    private @Nullable DataInputStream createExternalChunkInputStream(ChunkPos pos, byte versionId) throws IOException {
        Path path = this.getExternalChunkPath(pos);

        if (!Files.isRegularFile(path, new LinkOption[0])) {
            RegionFile.LOGGER.error("External chunk path {} is not file", path);
            return null;
        } else {
            return this.createChunkInputStream(pos, versionId, Files.newInputStream(path));
        }
    }

    private static ByteArrayInputStream createStream(ByteBuffer buffer, int length) {
        return new ByteArrayInputStream(buffer.array(), buffer.position(), length);
    }

    private int packSectorOffset(int index, int size) {
        return index << 8 | size;
    }

    private static int getNumSectors(int offset) {
        return offset & 255;
    }

    private static int getSectorNumber(int offset) {
        return offset >> 8 & 16777215;
    }

    private static int sizeToSectors(int size) {
        return (size + 4096 - 1) / 4096;
    }

    public boolean doesChunkExist(ChunkPos pos) {
        int i = this.getOffset(pos);

        if (i == 0) {
            return false;
        } else {
            int j = getSectorNumber(i);
            int k = getNumSectors(i);
            ByteBuffer bytebuffer = ByteBuffer.allocate(5);

            try {
                this.file.read(bytebuffer, (long) (j * 4096));
                bytebuffer.flip();
                if (bytebuffer.remaining() != 5) {
                    return false;
                } else {
                    int l = bytebuffer.getInt();
                    byte b0 = bytebuffer.get();

                    if (isExternalStreamChunk(b0)) {
                        if (!RegionFileVersion.isValidVersion(getExternalChunkVersion(b0))) {
                            return false;
                        }

                        if (!Files.isRegularFile(this.getExternalChunkPath(pos), new LinkOption[0])) {
                            return false;
                        }
                    } else {
                        if (!RegionFileVersion.isValidVersion(b0)) {
                            return false;
                        }

                        if (l == 0) {
                            return false;
                        }

                        int i1 = l - 1;

                        if (i1 < 0 || i1 > 4096 * k) {
                            return false;
                        }
                    }

                    return true;
                }
            } catch (IOException ioexception) {
                return false;
            }
        }
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException {
        return new DataOutputStream(this.version.wrap((OutputStream) (new RegionFile.ChunkBuffer(pos))));
    }

    public void flush() throws IOException {
        this.file.force(true);
    }

    public void clear(ChunkPos pos) throws IOException {
        int i = getOffsetIndex(pos);
        int j = this.offsets.get(i);

        if (j != 0) {
            this.offsets.put(i, 0);
            this.timestamps.put(i, getTimestamp());
            this.writeHeader();
            Files.deleteIfExists(this.getExternalChunkPath(pos));
            this.usedSectors.free(getSectorNumber(j), getNumSectors(j));
        }
    }

    protected synchronized void write(ChunkPos pos, ByteBuffer data) throws IOException {
        int i = getOffsetIndex(pos);
        int j = this.offsets.get(i);
        int k = getSectorNumber(j);
        int l = getNumSectors(j);
        int i1 = data.remaining();
        int j1 = sizeToSectors(i1);
        int k1;
        RegionFile.CommitOp regionfile_commitop;

        if (j1 >= 256) {
            Path path = this.getExternalChunkPath(pos);

            RegionFile.LOGGER.warn("Saving oversized chunk {} ({} bytes} to external file {}", new Object[]{pos, i1, path});
            j1 = 1;
            k1 = this.usedSectors.allocate(j1);
            regionfile_commitop = this.writeToExternalFile(path, data);
            ByteBuffer bytebuffer1 = this.createExternalStub();

            this.file.write(bytebuffer1, (long) (k1 * 4096));
        } else {
            k1 = this.usedSectors.allocate(j1);
            regionfile_commitop = () -> {
                Files.deleteIfExists(this.getExternalChunkPath(pos));
            };
            this.file.write(data, (long) (k1 * 4096));
        }

        this.offsets.put(i, this.packSectorOffset(k1, j1));
        this.timestamps.put(i, getTimestamp());
        this.writeHeader();
        regionfile_commitop.run();
        if (k != 0) {
            this.usedSectors.free(k, l);
        }

    }

    private ByteBuffer createExternalStub() {
        ByteBuffer bytebuffer = ByteBuffer.allocate(5);

        bytebuffer.putInt(1);
        bytebuffer.put((byte) (this.version.getId() | 128));
        bytebuffer.flip();
        return bytebuffer;
    }

    private RegionFile.CommitOp writeToExternalFile(Path path, ByteBuffer data) throws IOException {
        Path path1 = Files.createTempFile(this.externalFileDir, "tmp", (String) null);

        try (FileChannel filechannel = FileChannel.open(path1, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            data.position(5);
            filechannel.write(data);
        }

        return () -> {
            Files.move(path1, path, StandardCopyOption.REPLACE_EXISTING);
        };
    }

    private void writeHeader() throws IOException {
        this.header.position(0);
        this.file.write(this.header, 0L);
    }

    private int getOffset(ChunkPos pos) {
        return this.offsets.get(getOffsetIndex(pos));
    }

    public boolean hasChunk(ChunkPos pos) {
        return this.getOffset(pos) != 0;
    }

    private static int getOffsetIndex(ChunkPos pos) {
        return pos.getRegionLocalX() + pos.getRegionLocalZ() * 32;
    }

    public void close() throws IOException {
        try {
            this.padToFullSector();
        } finally {
            try {
                this.file.force(true);
            } finally {
                this.file.close();
            }
        }

    }

    private void padToFullSector() throws IOException {
        int i = (int) this.file.size();
        int j = sizeToSectors(i) * 4096;

        if (i != j) {
            ByteBuffer bytebuffer = RegionFile.PADDING_BUFFER.duplicate();

            bytebuffer.position(0);
            this.file.write(bytebuffer, (long) (j - 1));
        }

    }

    private class ChunkBuffer extends ByteArrayOutputStream {

        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos pos) {
            super(8096);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(0);
            super.write(RegionFile.this.version.getId());
            this.pos = pos;
        }

        public void close() throws IOException {
            ByteBuffer bytebuffer = ByteBuffer.wrap(this.buf, 0, this.count);
            int i = this.count - 5 + 1;

            JvmProfiler.INSTANCE.onRegionFileWrite(RegionFile.this.info, this.pos, RegionFile.this.version, i);
            bytebuffer.putInt(0, i);
            RegionFile.this.write(this.pos, bytebuffer);
        }
    }

    private interface CommitOp {

        void run() throws IOException;
    }
}
