package net.minecraft.server.packs.linkfs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

class LinkFSPath implements Path {

    private static final BasicFileAttributes DIRECTORY_ATTRIBUTES = new DummyFileAttributes() {
        public boolean isRegularFile() {
            return false;
        }

        public boolean isDirectory() {
            return true;
        }
    };
    private static final BasicFileAttributes FILE_ATTRIBUTES = new DummyFileAttributes() {
        public boolean isRegularFile() {
            return true;
        }

        public boolean isDirectory() {
            return false;
        }
    };
    private static final Comparator<LinkFSPath> PATH_COMPARATOR = Comparator.comparing(LinkFSPath::pathToString);
    private final String name;
    private final LinkFileSystem fileSystem;
    private final @Nullable LinkFSPath parent;
    private @Nullable List<String> pathToRoot;
    private @Nullable String pathString;
    private final PathContents pathContents;

    public LinkFSPath(LinkFileSystem fileSystem, String name, @Nullable LinkFSPath parent, PathContents pathContents) {
        this.fileSystem = fileSystem;
        this.name = name;
        this.parent = parent;
        this.pathContents = pathContents;
    }

    private LinkFSPath createRelativePath(@Nullable LinkFSPath parent, String name) {
        return new LinkFSPath(this.fileSystem, name, parent, PathContents.RELATIVE);
    }

    public LinkFileSystem getFileSystem() {
        return this.fileSystem;
    }

    public boolean isAbsolute() {
        return this.pathContents != PathContents.RELATIVE;
    }

    public File toFile() {
        PathContents pathcontents = this.pathContents;

        if (pathcontents instanceof PathContents.FileContents pathcontents_filecontents) {
            return pathcontents_filecontents.contents().toFile();
        } else {
            throw new UnsupportedOperationException("Path " + this.pathToString() + " does not represent file");
        }
    }

    public @Nullable LinkFSPath getRoot() {
        return this.isAbsolute() ? this.fileSystem.rootPath() : null;
    }

    public LinkFSPath getFileName() {
        return this.createRelativePath((LinkFSPath) null, this.name);
    }

    public @Nullable LinkFSPath getParent() {
        return this.parent;
    }

    public int getNameCount() {
        return this.pathToRoot().size();
    }

    private List<String> pathToRoot() {
        if (this.name.isEmpty()) {
            return List.of();
        } else {
            if (this.pathToRoot == null) {
                ImmutableList.Builder<String> immutablelist_builder = ImmutableList.builder();

                if (this.parent != null) {
                    immutablelist_builder.addAll(this.parent.pathToRoot());
                }

                immutablelist_builder.add(this.name);
                this.pathToRoot = immutablelist_builder.build();
            }

            return this.pathToRoot;
        }
    }

    public LinkFSPath getName(int index) {
        List<String> list = this.pathToRoot();

        if (index >= 0 && index < list.size()) {
            return this.createRelativePath((LinkFSPath) null, (String) list.get(index));
        } else {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
    }

    public LinkFSPath subpath(int beginIndex, int endIndex) {
        List<String> list = this.pathToRoot();

        if (beginIndex >= 0 && endIndex <= list.size() && beginIndex < endIndex) {
            LinkFSPath linkfspath = null;

            for (int k = beginIndex; k < endIndex; ++k) {
                linkfspath = this.createRelativePath(linkfspath, (String) list.get(k));
            }

            return linkfspath;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public boolean startsWith(Path other) {
        if (other.isAbsolute() != this.isAbsolute()) {
            return false;
        } else if (other instanceof LinkFSPath) {
            LinkFSPath linkfspath = (LinkFSPath) other;

            if (linkfspath.fileSystem != this.fileSystem) {
                return false;
            } else {
                List<String> list = this.pathToRoot();
                List<String> list1 = linkfspath.pathToRoot();
                int i = list1.size();

                if (i > list.size()) {
                    return false;
                } else {
                    for (int j = 0; j < i; ++j) {
                        if (!((String) list1.get(j)).equals(list.get(j))) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public boolean endsWith(Path other) {
        if (other.isAbsolute() && !this.isAbsolute()) {
            return false;
        } else if (other instanceof LinkFSPath) {
            LinkFSPath linkfspath = (LinkFSPath) other;

            if (linkfspath.fileSystem != this.fileSystem) {
                return false;
            } else {
                List<String> list = this.pathToRoot();
                List<String> list1 = linkfspath.pathToRoot();
                int i = list1.size();
                int j = list.size() - i;

                if (j < 0) {
                    return false;
                } else {
                    for (int k = i - 1; k >= 0; --k) {
                        if (!((String) list1.get(k)).equals(list.get(j + k))) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public LinkFSPath normalize() {
        return this;
    }

    public LinkFSPath resolve(Path other) {
        LinkFSPath linkfspath = this.toLinkPath(other);

        return other.isAbsolute() ? linkfspath : this.resolve(linkfspath.pathToRoot());
    }

    private LinkFSPath resolve(List<String> names) {
        LinkFSPath linkfspath = this;

        for (String s : names) {
            linkfspath = linkfspath.resolveName(s);
        }

        return linkfspath;
    }

    LinkFSPath resolveName(String name) {
        if (isRelativeOrMissing(this.pathContents)) {
            return new LinkFSPath(this.fileSystem, name, this, this.pathContents);
        } else {
            PathContents pathcontents = this.pathContents;

            if (pathcontents instanceof PathContents.DirectoryContents) {
                PathContents.DirectoryContents pathcontents_directorycontents = (PathContents.DirectoryContents) pathcontents;
                LinkFSPath linkfspath = (LinkFSPath) pathcontents_directorycontents.children().get(name);

                return linkfspath != null ? linkfspath : new LinkFSPath(this.fileSystem, name, this, PathContents.MISSING);
            } else if (this.pathContents instanceof PathContents.FileContents) {
                return new LinkFSPath(this.fileSystem, name, this, PathContents.MISSING);
            } else {
                throw new AssertionError("All content types should be already handled");
            }
        }
    }

    private static boolean isRelativeOrMissing(PathContents contents) {
        return contents == PathContents.MISSING || contents == PathContents.RELATIVE;
    }

    public LinkFSPath relativize(Path other) {
        LinkFSPath linkfspath = this.toLinkPath(other);

        if (this.isAbsolute() != linkfspath.isAbsolute()) {
            throw new IllegalArgumentException("absolute mismatch");
        } else {
            List<String> list = this.pathToRoot();
            List<String> list1 = linkfspath.pathToRoot();

            if (list.size() >= list1.size()) {
                throw new IllegalArgumentException();
            } else {
                for (int i = 0; i < list.size(); ++i) {
                    if (!((String) list.get(i)).equals(list1.get(i))) {
                        throw new IllegalArgumentException();
                    }
                }

                return linkfspath.subpath(list.size(), list1.size());
            }
        }
    }

    public URI toUri() {
        try {
            return new URI("x-mc-link", this.fileSystem.store().name(), this.pathToString(), (String) null);
        } catch (URISyntaxException urisyntaxexception) {
            throw new AssertionError("Failed to create URI", urisyntaxexception);
        }
    }

    public LinkFSPath toAbsolutePath() {
        return this.isAbsolute() ? this : this.fileSystem.rootPath().resolve(this);
    }

    public LinkFSPath toRealPath(LinkOption... options) {
        return this.toAbsolutePath();
    }

    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, Modifier... modifiers) {
        throw new UnsupportedOperationException();
    }

    public int compareTo(Path other) {
        LinkFSPath linkfspath = this.toLinkPath(other);

        return LinkFSPath.PATH_COMPARATOR.compare(this, linkfspath);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof LinkFSPath) {
            LinkFSPath linkfspath = (LinkFSPath) other;

            if (this.fileSystem != linkfspath.fileSystem) {
                return false;
            } else {
                boolean flag = this.hasRealContents();

                return flag != linkfspath.hasRealContents() ? false : (flag ? this.pathContents == linkfspath.pathContents : Objects.equals(this.parent, linkfspath.parent) && Objects.equals(this.name, linkfspath.name));
            }
        } else {
            return false;
        }
    }

    private boolean hasRealContents() {
        return !isRelativeOrMissing(this.pathContents);
    }

    public int hashCode() {
        return this.hasRealContents() ? this.pathContents.hashCode() : this.name.hashCode();
    }

    public String toString() {
        return this.pathToString();
    }

    private String pathToString() {
        if (this.pathString == null) {
            StringBuilder stringbuilder = new StringBuilder();

            if (this.isAbsolute()) {
                stringbuilder.append("/");
            }

            Joiner.on("/").appendTo(stringbuilder, this.pathToRoot());
            this.pathString = stringbuilder.toString();
        }

        return this.pathString;
    }

    private LinkFSPath toLinkPath(@Nullable Path path) {
        if (path == null) {
            throw new NullPointerException();
        } else {
            if (path instanceof LinkFSPath) {
                LinkFSPath linkfspath = (LinkFSPath) path;

                if (linkfspath.fileSystem == this.fileSystem) {
                    return linkfspath;
                }
            }

            throw new ProviderMismatchException();
        }
    }

    public boolean exists() {
        return this.hasRealContents();
    }

    public @Nullable Path getTargetPath() {
        PathContents pathcontents = this.pathContents;
        Path path;

        if (pathcontents instanceof PathContents.FileContents pathcontents_filecontents) {
            path = pathcontents_filecontents.contents();
        } else {
            path = null;
        }

        return path;
    }

    public PathContents.@Nullable DirectoryContents getDirectoryContents() {
        PathContents pathcontents = this.pathContents;
        PathContents.DirectoryContents pathcontents_directorycontents;

        if (pathcontents instanceof PathContents.DirectoryContents pathcontents_directorycontents1) {
            pathcontents_directorycontents = pathcontents_directorycontents1;
        } else {
            pathcontents_directorycontents = null;
        }

        return pathcontents_directorycontents;
    }

    public BasicFileAttributeView getBasicAttributeView() {
        return new BasicFileAttributeView() {
            public String name() {
                return "basic";
            }

            public BasicFileAttributes readAttributes() throws IOException {
                return LinkFSPath.this.getBasicAttributes();
            }

            public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
                throw new ReadOnlyFileSystemException();
            }
        };
    }

    public BasicFileAttributes getBasicAttributes() throws IOException {
        if (this.pathContents instanceof PathContents.DirectoryContents) {
            return LinkFSPath.DIRECTORY_ATTRIBUTES;
        } else if (this.pathContents instanceof PathContents.FileContents) {
            return LinkFSPath.FILE_ATTRIBUTES;
        } else {
            throw new NoSuchFileException(this.pathToString());
        }
    }
}
