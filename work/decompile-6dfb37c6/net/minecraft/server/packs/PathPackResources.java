package net.minecraft.server.packs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PathPackResources extends AbstractPackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Joiner PATH_JOINER = Joiner.on("/");
    private final Path root;

    public PathPackResources(PackLocationInfo location, Path root) {
        super(location);
        this.root = root;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        FileUtil.validatePath(path);
        Path path1 = FileUtil.resolvePath(this.root, List.of(path));

        return Files.exists(path1, new LinkOption[0]) ? IoSupplier.create(path1) : null;
    }

    public static boolean validatePath(Path path) {
        if (!SharedConstants.DEBUG_VALIDATE_RESOURCE_PATH_CASE) {
            return true;
        } else if (path.getFileSystem() != FileSystems.getDefault()) {
            return true;
        } else {
            try {
                return path.toRealPath().endsWith(path);
            } catch (IOException ioexception) {
                PathPackResources.LOGGER.warn("Failed to resolve real path for {}", path, ioexception);
                return false;
            }
        }
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        Path path = this.root.resolve(type.getDirectory()).resolve(location.getNamespace());

        return getResource(location, path);
    }

    public static @Nullable IoSupplier<InputStream> getResource(Identifier location, Path path) {
        return (IoSupplier) FileUtil.decomposePath(location.getPath()).mapOrElse((list) -> {
            Path path1 = FileUtil.resolvePath(path, list);

            return returnFileIfExists(path1);
        }, (error) -> {
            PathPackResources.LOGGER.error("Invalid path {}: {}", location, error.message());
            return null;
        });
    }

    private static @Nullable IoSupplier<InputStream> returnFileIfExists(Path resolvedPath) {
        return Files.exists(resolvedPath, new LinkOption[0]) && validatePath(resolvedPath) ? IoSupplier.create(resolvedPath) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
        FileUtil.decomposePath(directory).ifSuccess((list) -> {
            Path path = this.root.resolve(type.getDirectory()).resolve(namespace);

            listPath(namespace, path, list, output);
        }).ifError((error) -> {
            PathPackResources.LOGGER.error("Invalid path {}: {}", directory, error.message());
        });
    }

    public static void listPath(String namespace, Path rootDir, List<String> decomposedPrefixPath, PackResources.ResourceOutput output) {
        Path path1 = FileUtil.resolvePath(rootDir, decomposedPrefixPath);

        try (Stream<Path> stream = Files.find(path1, Integer.MAX_VALUE, PathPackResources::isRegularFile, new FileVisitOption[0])) {
            stream.forEach((path2) -> {
                String s1 = PathPackResources.PATH_JOINER.join(rootDir.relativize(path2));
                Identifier identifier = Identifier.tryBuild(namespace, s1);

                if (identifier == null) {
                    Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", namespace, s1));
                } else {
                    output.accept(identifier, IoSupplier.create(path2));
                }

            });
        } catch (NotDirectoryException | NoSuchFileException nosuchfileexception) {
            ;
        } catch (IOException ioexception) {
            PathPackResources.LOGGER.error("Failed to list path {}", path1, ioexception);
        }

    }

    private static boolean isRegularFile(Path file, BasicFileAttributes attributes) {
        return !SharedConstants.IS_RUNNING_IN_IDE ? attributes.isRegularFile() : attributes.isRegularFile() && !StringUtils.equalsIgnoreCase(file.getFileName().toString(), ".ds_store");
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = Sets.newHashSet();
        Path path = this.root.resolve(type.getDirectory());

        try (DirectoryStream<Path> directorystream = Files.newDirectoryStream(path)) {
            for (Path path1 : directorystream) {
                String s = path1.getFileName().toString();

                if (Identifier.isValidNamespace(s)) {
                    set.add(s);
                } else {
                    PathPackResources.LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", s, this.root);
                }
            }
        } catch (NotDirectoryException | NoSuchFileException nosuchfileexception) {
            ;
        } catch (IOException ioexception) {
            PathPackResources.LOGGER.error("Failed to list path {}", path, ioexception);
        }

        return set;
    }

    @Override
    public void close() {}

    public static class PathResourcesSupplier implements Pack.ResourcesSupplier {

        private final Path content;

        public PathResourcesSupplier(Path content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new PathPackResources(location, this.content);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            PackResources packresources = this.openPrimary(location);
            List<String> list = metadata.overlays();

            if (list.isEmpty()) {
                return packresources;
            } else {
                List<PackResources> list1 = new ArrayList(list.size());

                for (String s : list) {
                    Path path = this.content.resolve(s);

                    list1.add(new PathPackResources(location, path));
                }

                return new CompositePackResources(packresources, list1);
            }
        }
    }
}
