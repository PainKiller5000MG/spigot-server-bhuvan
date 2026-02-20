package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.FileUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final BuiltInMetadata metadata;
    private final Set<String> namespaces;
    private final List<Path> rootPaths;
    private final Map<PackType, List<Path>> pathsForType;

    VanillaPackResources(PackLocationInfo location, BuiltInMetadata metadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType) {
        this.location = location;
        this.metadata = metadata;
        this.namespaces = namespaces;
        this.rootPaths = rootPaths;
        this.pathsForType = pathsForType;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        FileUtil.validatePath(path);
        List<String> list = List.of(path);

        for (Path path1 : this.rootPaths) {
            Path path2 = FileUtil.resolvePath(path1, list);

            if (Files.exists(path2, new LinkOption[0]) && PathPackResources.validatePath(path2)) {
                return IoSupplier.create(path2);
            }
        }

        return null;
    }

    public void listRawPaths(PackType type, Identifier resource, Consumer<Path> output) {
        FileUtil.decomposePath(resource.getPath()).ifSuccess((list) -> {
            String s = resource.getNamespace();

            for (Path path : (List) this.pathsForType.get(type)) {
                Path path1 = path.resolve(s);

                output.accept(FileUtil.resolvePath(path1, list));
            }

        }).ifError((error) -> {
            VanillaPackResources.LOGGER.error("Invalid path {}: {}", resource, error.message());
        });
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
        FileUtil.decomposePath(directory).ifSuccess((list) -> {
            List<Path> list1 = (List) this.pathsForType.get(type);
            int i = list1.size();

            if (i == 1) {
                getResources(output, namespace, (Path) list1.get(0), list);
            } else if (i > 1) {
                Map<Identifier, IoSupplier<InputStream>> map = new HashMap();

                for (int j = 0; j < i - 1; ++j) {
                    Objects.requireNonNull(map);
                    getResources(map::putIfAbsent, namespace, (Path) list1.get(j), list);
                }

                Path path = (Path) list1.get(i - 1);

                if (map.isEmpty()) {
                    getResources(output, namespace, path, list);
                } else {
                    Objects.requireNonNull(map);
                    getResources(map::putIfAbsent, namespace, path, list);
                    map.forEach(output);
                }
            }

        }).ifError((error) -> {
            VanillaPackResources.LOGGER.error("Invalid path {}: {}", directory, error.message());
        });
    }

    private static void getResources(PackResources.ResourceOutput result, String namespace, Path root, List<String> directory) {
        Path path1 = root.resolve(namespace);

        PathPackResources.listPath(namespace, path1, directory, result);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        return (IoSupplier) FileUtil.decomposePath(location.getPath()).mapOrElse((list) -> {
            String s = location.getNamespace();

            for (Path path : (List) this.pathsForType.get(type)) {
                Path path1 = FileUtil.resolvePath(path.resolve(s), list);

                if (Files.exists(path1, new LinkOption[0]) && PathPackResources.validatePath(path1)) {
                    return IoSupplier.create(path1);
                }
            }

            return null;
        }, (error) -> {
            VanillaPackResources.LOGGER.error("Invalid path {}: {}", location, error.message());
            return null;
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) {
        IoSupplier<InputStream> iosupplier = this.getRootResource("pack.mcmeta");

        if (iosupplier != null) {
            try (InputStream inputstream = iosupplier.get()) {
                T t0 = (T) AbstractPackResources.getMetadataFromStream(metadataSerializer, inputstream, this.location);

                if (t0 != null) {
                    return t0;
                }
            } catch (IOException ioexception) {
                ;
            }
        }

        return (T) this.metadata.get(metadataSerializer);
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {}

    public ResourceProvider asProvider() {
        return (identifier) -> {
            return Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, identifier)).map((iosupplier) -> {
                return new Resource(this, iosupplier);
            });
        };
    }
}
