package net.minecraft.util.eventlog;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class EventLogDirectory {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMPRESS_BUFFER_SIZE = 4096;
    private static final String COMPRESSED_EXTENSION = ".gz";
    private final Path root;
    private final String extension;

    private EventLogDirectory(Path root, String extension) {
        this.root = root;
        this.extension = extension;
    }

    public static EventLogDirectory open(Path root, String extension) throws IOException {
        Files.createDirectories(root);
        return new EventLogDirectory(root, extension);
    }

    public EventLogDirectory.FileList listFiles() throws IOException {
        try (Stream<Path> stream = Files.list(this.root)) {
            return new EventLogDirectory.FileList(stream.filter((path) -> {
                return Files.isRegularFile(path, new LinkOption[0]);
            }).map(this::parseFile).filter(Objects::nonNull).toList());
        }
    }

    private EventLogDirectory.@Nullable File parseFile(Path path) {
        String s = path.getFileName().toString();
        int i = s.indexOf(46);

        if (i == -1) {
            return null;
        } else {
            EventLogDirectory.FileId eventlogdirectory_fileid = EventLogDirectory.FileId.parse(s.substring(0, i));

            if (eventlogdirectory_fileid != null) {
                String s1 = s.substring(i);

                if (s1.equals(this.extension)) {
                    return new EventLogDirectory.RawFile(path, eventlogdirectory_fileid);
                }

                if (s1.equals(this.extension + ".gz")) {
                    return new EventLogDirectory.CompressedFile(path, eventlogdirectory_fileid);
                }
            }

            return null;
        }
    }

    private static void tryCompress(Path raw, Path compressed) throws IOException {
        if (Files.exists(compressed, new LinkOption[0])) {
            throw new IOException("Compressed target file already exists: " + String.valueOf(compressed));
        } else {
            try (FileChannel filechannel = FileChannel.open(raw, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                FileLock filelock = filechannel.tryLock();

                if (filelock == null) {
                    throw new IOException("Raw log file is already locked, cannot compress: " + String.valueOf(raw));
                }

                writeCompressed(filechannel, compressed);
                filechannel.truncate(0L);
            }

            Files.delete(raw);
        }
    }

    private static void writeCompressed(ReadableByteChannel channel, Path target) throws IOException {
        try (OutputStream outputstream = new GZIPOutputStream(Files.newOutputStream(target))) {
            byte[] abyte = new byte[4096];
            ByteBuffer bytebuffer = ByteBuffer.wrap(abyte);

            while (channel.read(bytebuffer) >= 0) {
                bytebuffer.flip();
                outputstream.write(abyte, 0, bytebuffer.limit());
                bytebuffer.clear();
            }
        }

    }

    public EventLogDirectory.RawFile createNewFile(LocalDate date) throws IOException {
        int i = 1;
        Set<EventLogDirectory.FileId> set = this.listFiles().ids();

        EventLogDirectory.FileId eventlogdirectory_fileid;

        do {
            eventlogdirectory_fileid = new EventLogDirectory.FileId(date, i++);
        } while (set.contains(eventlogdirectory_fileid));

        EventLogDirectory.RawFile eventlogdirectory_rawfile = new EventLogDirectory.RawFile(this.root.resolve(eventlogdirectory_fileid.toFileName(this.extension)), eventlogdirectory_fileid);

        Files.createFile(eventlogdirectory_rawfile.path());
        return eventlogdirectory_rawfile;
    }

    public static class FileList implements Iterable<EventLogDirectory.File> {

        private final List<EventLogDirectory.File> files;

        private FileList(List<EventLogDirectory.File> files) {
            this.files = new ArrayList(files);
        }

        public EventLogDirectory.FileList prune(LocalDate date, int expiryDays) {
            this.files.removeIf((eventlogdirectory_file) -> {
                EventLogDirectory.FileId eventlogdirectory_fileid = eventlogdirectory_file.id();
                LocalDate localdate1 = eventlogdirectory_fileid.date().plusDays((long) expiryDays);

                if (!date.isBefore(localdate1)) {
                    try {
                        Files.delete(eventlogdirectory_file.path());
                        return true;
                    } catch (IOException ioexception) {
                        EventLogDirectory.LOGGER.warn("Failed to delete expired event log file: {}", eventlogdirectory_file.path(), ioexception);
                    }
                }

                return false;
            });
            return this;
        }

        public EventLogDirectory.FileList compressAll() {
            ListIterator<EventLogDirectory.File> listiterator = this.files.listIterator();

            while (listiterator.hasNext()) {
                EventLogDirectory.File eventlogdirectory_file = (EventLogDirectory.File) listiterator.next();

                try {
                    listiterator.set(eventlogdirectory_file.compress());
                } catch (IOException ioexception) {
                    EventLogDirectory.LOGGER.warn("Failed to compress event log file: {}", eventlogdirectory_file.path(), ioexception);
                }
            }

            return this;
        }

        public Iterator<EventLogDirectory.File> iterator() {
            return this.files.iterator();
        }

        public Stream<EventLogDirectory.File> stream() {
            return this.files.stream();
        }

        public Set<EventLogDirectory.FileId> ids() {
            return (Set) this.files.stream().map(EventLogDirectory.File::id).collect(Collectors.toSet());
        }
    }

    public static record RawFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {

        public FileChannel openChannel() throws IOException {
            return FileChannel.open(this.path, StandardOpenOption.WRITE, StandardOpenOption.READ);
        }

        @Override
        public @Nullable Reader openReader() throws IOException {
            return Files.exists(this.path, new LinkOption[0]) ? Files.newBufferedReader(this.path) : null;
        }

        @Override
        public EventLogDirectory.CompressedFile compress() throws IOException {
            Path path = this.path.resolveSibling(this.path.getFileName().toString() + ".gz");

            EventLogDirectory.tryCompress(this.path, path);
            return new EventLogDirectory.CompressedFile(path, this.id);
        }
    }

    public static record CompressedFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {

        @Override
        public @Nullable Reader openReader() throws IOException {
            return !Files.exists(this.path, new LinkOption[0]) ? null : new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(this.path)), StandardCharsets.UTF_8));
        }

        @Override
        public EventLogDirectory.CompressedFile compress() {
            return this;
        }
    }

    public static record FileId(LocalDate date, int index) {

        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

        public static EventLogDirectory.@Nullable FileId parse(String name) {
            int i = name.indexOf("-");

            if (i == -1) {
                return null;
            } else {
                String s1 = name.substring(0, i);
                String s2 = name.substring(i + 1);

                try {
                    return new EventLogDirectory.FileId(LocalDate.parse(s1, EventLogDirectory.FileId.DATE_FORMATTER), Integer.parseInt(s2));
                } catch (DateTimeParseException | NumberFormatException numberformatexception) {
                    return null;
                }
            }
        }

        public String toString() {
            String s = EventLogDirectory.FileId.DATE_FORMATTER.format(this.date);

            return s + "-" + this.index;
        }

        public String toFileName(String extension) {
            String s1 = String.valueOf(this);

            return s1 + extension;
        }
    }

    public interface File {

        Path path();

        EventLogDirectory.FileId id();

        @Nullable
        Reader openReader() throws IOException;

        EventLogDirectory.CompressedFile compress() throws IOException;
    }
}
