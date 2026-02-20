package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FallbackResourceManager implements ResourceManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    protected final List<FallbackResourceManager.PackEntry> fallbacks = Lists.newArrayList();
    private final PackType type;
    private final String namespace;

    public FallbackResourceManager(PackType type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    public void push(PackResources pack) {
        this.pushInternal(pack.packId(), pack, (Predicate) null);
    }

    public void push(PackResources pack, Predicate<Identifier> filter) {
        this.pushInternal(pack.packId(), pack, filter);
    }

    public void pushFilterOnly(String name, Predicate<Identifier> filter) {
        this.pushInternal(name, (PackResources) null, filter);
    }

    private void pushInternal(String name, @Nullable PackResources pack, @Nullable Predicate<Identifier> contentFilter) {
        this.fallbacks.add(new FallbackResourceManager.PackEntry(name, pack, contentFilter));
    }

    @Override
    public Set<String> getNamespaces() {
        return ImmutableSet.of(this.namespace);
    }

    @Override
    public Optional<Resource> getResource(Identifier location) {
        for (int i = this.fallbacks.size() - 1; i >= 0; --i) {
            FallbackResourceManager.PackEntry fallbackresourcemanager_packentry = (FallbackResourceManager.PackEntry) this.fallbacks.get(i);
            PackResources packresources = fallbackresourcemanager_packentry.resources;

            if (packresources != null) {
                IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, location);

                if (iosupplier != null) {
                    IoSupplier<ResourceMetadata> iosupplier1 = this.createStackMetadataFinder(location, i);

                    return Optional.of(createResource(packresources, location, iosupplier, iosupplier1));
                }
            }

            if (fallbackresourcemanager_packentry.isFiltered(location)) {
                FallbackResourceManager.LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, fallbackresourcemanager_packentry.name);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Resource createResource(PackResources source, Identifier location, IoSupplier<InputStream> resource, IoSupplier<ResourceMetadata> metadata) {
        return new Resource(source, wrapForDebug(location, source, resource), metadata);
    }

    private static IoSupplier<InputStream> wrapForDebug(Identifier location, PackResources source, IoSupplier<InputStream> resource) {
        return FallbackResourceManager.LOGGER.isDebugEnabled() ? () -> {
            return new FallbackResourceManager.LeakedResourceWarningInputStream(resource.get(), location, source.packId());
        } : resource;
    }

    @Override
    public List<Resource> getResourceStack(Identifier location) {
        Identifier identifier1 = getMetadataLocation(location);
        List<Resource> list = new ArrayList();
        boolean flag = false;
        String s = null;

        for (int i = this.fallbacks.size() - 1; i >= 0; --i) {
            FallbackResourceManager.PackEntry fallbackresourcemanager_packentry = (FallbackResourceManager.PackEntry) this.fallbacks.get(i);
            PackResources packresources = fallbackresourcemanager_packentry.resources;

            if (packresources != null) {
                IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, location);

                if (iosupplier != null) {
                    IoSupplier<ResourceMetadata> iosupplier1;

                    if (flag) {
                        iosupplier1 = ResourceMetadata.EMPTY_SUPPLIER;
                    } else {
                        iosupplier1 = () -> {
                            IoSupplier<InputStream> iosupplier2 = packresources.getResource(this.type, identifier1);

                            return iosupplier2 != null ? parseMetadata(iosupplier2) : ResourceMetadata.EMPTY;
                        };
                    }

                    list.add(new Resource(packresources, iosupplier, iosupplier1));
                }
            }

            if (fallbackresourcemanager_packentry.isFiltered(location)) {
                s = fallbackresourcemanager_packentry.name;
                break;
            }

            if (fallbackresourcemanager_packentry.isFiltered(identifier1)) {
                flag = true;
            }
        }

        if (list.isEmpty() && s != null) {
            FallbackResourceManager.LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, s);
        }

        return Lists.reverse(list);
    }

    private static boolean isMetadata(Identifier location) {
        return location.getPath().endsWith(".mcmeta");
    }

    private static Identifier getIdentifierFromMetadata(Identifier identifier) {
        String s = identifier.getPath().substring(0, identifier.getPath().length() - ".mcmeta".length());

        return identifier.withPath(s);
    }

    private static Identifier getMetadataLocation(Identifier identifier) {
        return identifier.withPath(identifier.getPath() + ".mcmeta");
    }

    @Override
    public Map<Identifier, Resource> listResources(String directory, Predicate<Identifier> filter) {
        Map<Identifier, 1ResourceWithSourceAndIndex> map = new HashMap();
        Map<Identifier, 1ResourceWithSourceAndIndex> map1 = new HashMap();
        int i = this.fallbacks.size();

        for(int j = 0; j < i; ++j) {
            FallbackResourceManager.PackEntry fallbackresourcemanager_packentry = (FallbackResourceManager.PackEntry)this.fallbacks.get(j);

            fallbackresourcemanager_packentry.filterAll(map.keySet());
            fallbackresourcemanager_packentry.filterAll(map1.keySet());
            PackResources packresources = fallbackresourcemanager_packentry.resources;

            if (packresources != null) {
                packresources.listResources(this.type, this.namespace, directory, (identifier, iosupplier) -> {
                    record 1ResourceWithSourceAndIndex(PackResources packResources, IoSupplier<InputStream> resource, int packIndex) {

                    }


                    if (isMetadata(identifier)) {
                        if (filter.test(getIdentifierFromMetadata(identifier))) {
                            map1.put(identifier, new 1ResourceWithSourceAndIndex(packresources, iosupplier, j));
                        }
                    } else if (filter.test(identifier)) {
                        map.put(identifier, new 1ResourceWithSourceAndIndex(packresources, iosupplier, j));
                    }

                });
            }
        }

        Map<Identifier, Resource> map2 = Maps.newTreeMap();

        map.forEach((identifier, 1resourcewithsourceandindex) -> {
            Identifier identifier1 = getMetadataLocation(identifier);
            1ResourceWithSourceAndIndex 1resourcewithsourceandindex1 = (1ResourceWithSourceAndIndex)map1.get(identifier1);
            IoSupplier<ResourceMetadata> iosupplier;

            if (1resourcewithsourceandindex1 != null && 1resourcewithsourceandindex1.packIndex >= 1resourcewithsourceandindex.packIndex) {
                iosupplier = convertToMetadata(1resourcewithsourceandindex1.resource);
            } else {
                iosupplier = ResourceMetadata.EMPTY_SUPPLIER;
            }

            map2.put(identifier, createResource(1resourcewithsourceandindex.packResources, identifier, 1resourcewithsourceandindex.resource, iosupplier));
        });
        return map2;
    }

    private IoSupplier<ResourceMetadata> createStackMetadataFinder(Identifier location, int finalPackIndex) {
        return () -> {
            Identifier identifier1 = getMetadataLocation(location);

            for (int j = this.fallbacks.size() - 1; j >= finalPackIndex; --j) {
                FallbackResourceManager.PackEntry fallbackresourcemanager_packentry = (FallbackResourceManager.PackEntry) this.fallbacks.get(j);
                PackResources packresources = fallbackresourcemanager_packentry.resources;

                if (packresources != null) {
                    IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, identifier1);

                    if (iosupplier != null) {
                        return parseMetadata(iosupplier);
                    }
                }

                if (fallbackresourcemanager_packentry.isFiltered(identifier1)) {
                    break;
                }
            }

            return ResourceMetadata.EMPTY;
        };
    }

    private static IoSupplier<ResourceMetadata> convertToMetadata(IoSupplier<InputStream> input) {
        return () -> {
            return parseMetadata(input);
        };
    }

    private static ResourceMetadata parseMetadata(IoSupplier<InputStream> input) throws IOException {
        try (InputStream inputstream = input.get()) {
            return ResourceMetadata.fromJsonStream(inputstream);
        }
    }

    private static void applyPackFiltersToExistingResources(FallbackResourceManager.PackEntry entry, Map<Identifier, FallbackResourceManager.EntryStack> foundResources) {
        for (FallbackResourceManager.EntryStack fallbackresourcemanager_entrystack : foundResources.values()) {
            if (entry.isFiltered(fallbackresourcemanager_entrystack.fileLocation)) {
                fallbackresourcemanager_entrystack.fileSources.clear();
            } else if (entry.isFiltered(fallbackresourcemanager_entrystack.metadataLocation())) {
                fallbackresourcemanager_entrystack.metaSources.clear();
            }
        }

    }

    private void listPackResources(FallbackResourceManager.PackEntry entry, String directory, Predicate<Identifier> filter, Map<Identifier, FallbackResourceManager.EntryStack> foundResources) {
        PackResources packresources = entry.resources;

        if (packresources != null) {
            packresources.listResources(this.type, this.namespace, directory, (identifier, iosupplier) -> {
                if (isMetadata(identifier)) {
                    Identifier identifier1 = getIdentifierFromMetadata(identifier);

                    if (!filter.test(identifier1)) {
                        return;
                    }

                    ((FallbackResourceManager.EntryStack) foundResources.computeIfAbsent(identifier1, FallbackResourceManager.EntryStack::new)).metaSources.put(packresources, iosupplier);
                } else {
                    if (!filter.test(identifier)) {
                        return;
                    }

                    ((FallbackResourceManager.EntryStack) foundResources.computeIfAbsent(identifier, FallbackResourceManager.EntryStack::new)).fileSources.add(new FallbackResourceManager.ResourceWithSource(packresources, iosupplier));
                }

            });
        }
    }

    @Override
    public Map<Identifier, List<Resource>> listResourceStacks(String directory, Predicate<Identifier> filter) {
        Map<Identifier, FallbackResourceManager.EntryStack> map = Maps.newHashMap();

        for (FallbackResourceManager.PackEntry fallbackresourcemanager_packentry : this.fallbacks) {
            applyPackFiltersToExistingResources(fallbackresourcemanager_packentry, map);
            this.listPackResources(fallbackresourcemanager_packentry, directory, filter, map);
        }

        TreeMap<Identifier, List<Resource>> treemap = Maps.newTreeMap();

        for (FallbackResourceManager.EntryStack fallbackresourcemanager_entrystack : map.values()) {
            if (!fallbackresourcemanager_entrystack.fileSources.isEmpty()) {
                List<Resource> list = new ArrayList();

                for (FallbackResourceManager.ResourceWithSource fallbackresourcemanager_resourcewithsource : fallbackresourcemanager_entrystack.fileSources) {
                    PackResources packresources = fallbackresourcemanager_resourcewithsource.source;
                    IoSupplier<InputStream> iosupplier = (IoSupplier) fallbackresourcemanager_entrystack.metaSources.get(packresources);
                    IoSupplier<ResourceMetadata> iosupplier1 = iosupplier != null ? convertToMetadata(iosupplier) : ResourceMetadata.EMPTY_SUPPLIER;

                    list.add(createResource(packresources, fallbackresourcemanager_entrystack.fileLocation, fallbackresourcemanager_resourcewithsource.resource, iosupplier1));
                }

                treemap.put(fallbackresourcemanager_entrystack.fileLocation, list);
            }
        }

        return treemap;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.fallbacks.stream().map((fallbackresourcemanager_packentry) -> {
            return fallbackresourcemanager_packentry.resources;
        }).filter(Objects::nonNull);
    }

    private static class LeakedResourceWarningInputStream extends FilterInputStream {

        private final Supplier<String> message;
        private boolean closed;

        public LeakedResourceWarningInputStream(InputStream wrapped, Identifier location, String name) {
            super(wrapped);
            Exception exception = new Exception("Stacktrace");

            this.message = () -> {
                StringWriter stringwriter = new StringWriter();

                exception.printStackTrace(new PrintWriter(stringwriter));
                return "Leaked resource: '" + String.valueOf(location) + "' loaded from pack: '" + name + "'\n" + String.valueOf(stringwriter);
            };
        }

        public void close() throws IOException {
            super.close();
            this.closed = true;
        }

        protected void finalize() throws Throwable {
            if (!this.closed) {
                FallbackResourceManager.LOGGER.warn("{}", this.message.get());
            }

            super.finalize();
        }
    }

    private static record EntryStack(Identifier fileLocation, Identifier metadataLocation, List<FallbackResourceManager.ResourceWithSource> fileSources, Map<PackResources, IoSupplier<InputStream>> metaSources) {

        EntryStack(Identifier fileLocation) {
            this(fileLocation, FallbackResourceManager.getMetadataLocation(fileLocation), new ArrayList(), new Object2ObjectArrayMap());
        }
    }

    private static record PackEntry(String name, @Nullable PackResources resources, @Nullable Predicate<Identifier> filter) {

        public void filterAll(Collection<Identifier> collection) {
            if (this.filter != null) {
                collection.removeIf(this.filter);
            }

        }

        public boolean isFiltered(Identifier location) {
            return this.filter != null && this.filter.test(location);
        }
    }

    private static record ResourceWithSource(PackResources source, IoSupplier<InputStream> resource) {

    }
}
