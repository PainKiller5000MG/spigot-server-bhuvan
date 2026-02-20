package net.minecraft.server.packs.linkfs;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class LinkFileSystem extends FileSystem {

    private static final Set<String> VIEWS = Set.of("basic");
    public static final String PATH_SEPARATOR = "/";
    private static final Splitter PATH_SPLITTER = Splitter.on('/');
    private final FileStore store;
    private final FileSystemProvider provider = new LinkFSProvider();
    private final LinkFSPath root;

    private LinkFileSystem(String name, LinkFileSystem.DirectoryEntry rootEntry) {
        this.store = new LinkFSFileStore(name);
        this.root = buildPath(rootEntry, this, "", (LinkFSPath) null);
    }

    private static LinkFSPath buildPath(LinkFileSystem.DirectoryEntry entry, LinkFileSystem fileSystem, String selfName, @Nullable LinkFSPath parent) {
        Object2ObjectOpenHashMap<String, LinkFSPath> object2objectopenhashmap = new Object2ObjectOpenHashMap();
        LinkFSPath linkfspath1 = new LinkFSPath(fileSystem, selfName, parent, new PathContents.DirectoryContents(object2objectopenhashmap));

        entry.files.forEach((s1, path) -> {
            object2objectopenhashmap.put(s1, new LinkFSPath(fileSystem, s1, linkfspath1, new PathContents.FileContents(path)));
        });
        entry.children.forEach((s1, linkfilesystem_directoryentry1) -> {
            object2objectopenhashmap.put(s1, buildPath(linkfilesystem_directoryentry1, fileSystem, s1, linkfspath1));
        });
        object2objectopenhashmap.trim();
        return linkfspath1;
    }

    public FileSystemProvider provider() {
        return this.provider;
    }

    public void close() {}

    public boolean isOpen() {
        return true;
    }

    public boolean isReadOnly() {
        return true;
    }

    public String getSeparator() {
        return "/";
    }

    public Iterable<Path> getRootDirectories() {
        return List.of(this.root);
    }

    public Iterable<FileStore> getFileStores() {
        return List.of(this.store);
    }

    public Set<String> supportedFileAttributeViews() {
        return LinkFileSystem.VIEWS;
    }

    public Path getPath(String first, String... more) {
        Stream<String> stream = Stream.of(first);

        if (more.length > 0) {
            stream = Stream.concat(stream, Stream.of(more));
        }

        String s1 = (String) stream.collect(Collectors.joining("/"));

        if (s1.equals("/")) {
            return this.root;
        } else if (s1.startsWith("/")) {
            LinkFSPath linkfspath = this.root;

            for (String s2 : LinkFileSystem.PATH_SPLITTER.split(s1.substring(1))) {
                if (s2.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkfspath = linkfspath.resolveName(s2);
            }

            return linkfspath;
        } else {
            LinkFSPath linkfspath1 = null;

            for (String s3 : LinkFileSystem.PATH_SPLITTER.split(s1)) {
                if (s3.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkfspath1 = new LinkFSPath(this, s3, linkfspath1, PathContents.RELATIVE);
            }

            if (linkfspath1 == null) {
                throw new IllegalArgumentException("Empty paths not allowed");
            } else {
                return linkfspath1;
            }
        }
    }

    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    public FileStore store() {
        return this.store;
    }

    public LinkFSPath rootPath() {
        return this.root;
    }

    public static LinkFileSystem.Builder builder() {
        return new LinkFileSystem.Builder();
    }

    private static record DirectoryEntry(Map<String, LinkFileSystem.DirectoryEntry> children, Map<String, Path> files) {

        public DirectoryEntry() {
            this(new HashMap(), new HashMap());
        }
    }

    public static class Builder {

        private final LinkFileSystem.DirectoryEntry root = new LinkFileSystem.DirectoryEntry();

        public Builder() {}

        public LinkFileSystem.Builder put(List<String> path, String name, Path target) {
            LinkFileSystem.DirectoryEntry linkfilesystem_directoryentry = this.root;

            for (String s1 : path) {
                linkfilesystem_directoryentry = (LinkFileSystem.DirectoryEntry) linkfilesystem_directoryentry.children.computeIfAbsent(s1, (s2) -> {
                    return new LinkFileSystem.DirectoryEntry();
                });
            }

            linkfilesystem_directoryentry.files.put(name, target);
            return this;
        }

        public LinkFileSystem.Builder put(List<String> path, Path target) {
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Path can't be empty");
            } else {
                int i = path.size() - 1;

                return this.put(path.subList(0, i), (String) path.get(i), target);
            }
        }

        public FileSystem build(String name) {
            return new LinkFileSystem(name, this.root);
        }
    }
}
