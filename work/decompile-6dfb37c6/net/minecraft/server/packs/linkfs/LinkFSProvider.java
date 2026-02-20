package net.minecraft.server.packs.linkfs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class LinkFSProvider extends FileSystemProvider {

    public static final String SCHEME = "x-mc-link";

    LinkFSProvider() {}

    public String getScheme() {
        return "x-mc-link";
    }

    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new UnsupportedOperationException();
    }

    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException();
    }

    public Path getPath(URI uri) {
        throw new UnsupportedOperationException();
    }

    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (!options.contains(StandardOpenOption.CREATE_NEW) && !options.contains(StandardOpenOption.CREATE) && !options.contains(StandardOpenOption.APPEND) && !options.contains(StandardOpenOption.WRITE)) {
            Path path1 = toLinkPath(path).toAbsolutePath().getTargetPath();

            if (path1 == null) {
                throw new NoSuchFileException(path.toString());
            } else {
                return Files.newByteChannel(path1, options, attrs);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public DirectoryStream<Path> newDirectoryStream(Path dir, final DirectoryStream.Filter<? super Path> filter) throws IOException {
        final PathContents.DirectoryContents pathcontents_directorycontents = toLinkPath(dir).toAbsolutePath().getDirectoryContents();

        if (pathcontents_directorycontents == null) {
            throw new NotDirectoryException(dir.toString());
        } else {
            return new DirectoryStream<Path>() {
                public Iterator<Path> iterator() {
                    return pathcontents_directorycontents.children().values().stream().filter((linkfspath) -> {
                        try {
                            return filter.accept(linkfspath);
                        } catch (IOException ioexception) {
                            throw new DirectoryIteratorException(ioexception);
                        }
                    }).map((linkfspath) -> {
                        return linkfspath;
                    }).iterator();
                }

                public void close() {}
            };
        }
    }

    public void createDirectory(Path dir, FileAttribute<?>... attrs) {
        throw new ReadOnlyFileSystemException();
    }

    public void delete(Path path) {
        throw new ReadOnlyFileSystemException();
    }

    public void copy(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    public void move(Path source, Path target, CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    public boolean isSameFile(Path path, Path path2) {
        return path instanceof LinkFSPath && path2 instanceof LinkFSPath && path.equals(path2);
    }

    public boolean isHidden(Path path) {
        return false;
    }

    public FileStore getFileStore(Path path) {
        return toLinkPath(path).getFileSystem().store();
    }

    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        if (modes.length == 0 && !toLinkPath(path).exists()) {
            throw new NoSuchFileException(path.toString());
        } else {
            AccessMode[] aaccessmode1 = modes;
            int i = modes.length;
            int j = 0;

            while (j < i) {
                AccessMode accessmode = aaccessmode1[j];

                switch (accessmode) {
                    case READ:
                        if (!toLinkPath(path).exists()) {
                            throw new NoSuchFileException(path.toString());
                        }
                    default:
                        ++j;
                        break;
                    case EXECUTE:
                    case WRITE:
                        throw new AccessDeniedException(accessmode.toString());
                }
            }

        }
    }

    public <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        LinkFSPath linkfspath = toLinkPath(path);

        return (V) (type == BasicFileAttributeView.class ? linkfspath.getBasicAttributeView() : null);
    }

    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        LinkFSPath linkfspath = toLinkPath(path).toAbsolutePath();

        if (type == BasicFileAttributes.class) {
            return (A) linkfspath.getBasicAttributes();
        } else {
            throw new UnsupportedOperationException("Attributes of type " + type.getName() + " not supported");
        }
    }

    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    private static LinkFSPath toLinkPath(@Nullable Path path) {
        if (path == null) {
            throw new NullPointerException();
        } else if (path instanceof LinkFSPath) {
            LinkFSPath linkfspath = (LinkFSPath) path;

            return linkfspath;
        } else {
            throw new ProviderMismatchException();
        }
    }
}
