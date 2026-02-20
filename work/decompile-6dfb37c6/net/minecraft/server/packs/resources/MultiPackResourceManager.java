package net.minecraft.server.packs.resources;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class MultiPackResourceManager implements CloseableResourceManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, FallbackResourceManager> namespacedManagers;
    private final List<PackResources> packs;

    public MultiPackResourceManager(PackType type, List<PackResources> packs) {
        this.packs = List.copyOf(packs);
        Map<String, FallbackResourceManager> map = new HashMap();
        List<String> list1 = packs.stream().flatMap((packresources) -> {
            return packresources.getNamespaces(type).stream();
        }).distinct().toList();

        for (PackResources packresources : packs) {
            ResourceFilterSection resourcefiltersection = this.getPackFilterSection(packresources);
            Set<String> set = packresources.getNamespaces(type);
            Predicate<Identifier> predicate = resourcefiltersection != null ? (identifier) -> {
                return resourcefiltersection.isPathFiltered(identifier.getPath());
            } : null;

            for (String s : list1) {
                boolean flag = set.contains(s);
                boolean flag1 = resourcefiltersection != null && resourcefiltersection.isNamespaceFiltered(s);

                if (flag || flag1) {
                    FallbackResourceManager fallbackresourcemanager = (FallbackResourceManager) map.get(s);

                    if (fallbackresourcemanager == null) {
                        fallbackresourcemanager = new FallbackResourceManager(type, s);
                        map.put(s, fallbackresourcemanager);
                    }

                    if (flag && flag1) {
                        fallbackresourcemanager.push(packresources, predicate);
                    } else if (flag) {
                        fallbackresourcemanager.push(packresources);
                    } else {
                        fallbackresourcemanager.pushFilterOnly(packresources.packId(), predicate);
                    }
                }
            }
        }

        this.namespacedManagers = map;
    }

    private @Nullable ResourceFilterSection getPackFilterSection(PackResources pack) {
        try {
            return (ResourceFilterSection) pack.getMetadataSection(ResourceFilterSection.TYPE);
        } catch (IOException ioexception) {
            MultiPackResourceManager.LOGGER.error("Failed to get filter section from pack {}", pack.packId());
            return null;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespacedManagers.keySet();
    }

    @Override
    public Optional<Resource> getResource(Identifier location) {
        ResourceManager resourcemanager = (ResourceManager) this.namespacedManagers.get(location.getNamespace());

        return resourcemanager != null ? resourcemanager.getResource(location) : Optional.empty();
    }

    @Override
    public List<Resource> getResourceStack(Identifier location) {
        ResourceManager resourcemanager = (ResourceManager) this.namespacedManagers.get(location.getNamespace());

        return resourcemanager != null ? resourcemanager.getResourceStack(location) : List.of();
    }

    @Override
    public Map<Identifier, Resource> listResources(String directory, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(directory);
        Map<Identifier, Resource> map = new TreeMap();

        for (FallbackResourceManager fallbackresourcemanager : this.namespacedManagers.values()) {
            map.putAll(fallbackresourcemanager.listResources(directory, filter));
        }

        return map;
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String directory, Predicate<Identifier> filter) {
        checkTrailingDirectoryPath(directory);
        Map<Identifier, List<Resource>> map = new TreeMap();

        for (FallbackResourceManager fallbackresourcemanager : this.namespacedManagers.values()) {
            map.putAll(fallbackresourcemanager.listResourceStacks(directory, filter));
        }

        return map;
    }

    private static void checkTrailingDirectoryPath(String directory) {
        if (directory.endsWith("/")) {
            throw new IllegalArgumentException("Trailing slash in path " + directory);
        }
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.packs.stream();
    }

    @Override
    public void close() {
        this.packs.forEach(PackResources::close);
    }
}
