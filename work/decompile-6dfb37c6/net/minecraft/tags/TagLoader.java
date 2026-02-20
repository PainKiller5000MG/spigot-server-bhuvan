package net.minecraft.tags;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.DependencySorter;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TagLoader<T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final TagLoader.ElementLookup<T> elementLookup;
    private final String directory;

    public TagLoader(TagLoader.ElementLookup<T> elementLookup, String directory) {
        this.elementLookup = elementLookup;
        this.directory = directory;
    }

    public Map<Identifier, List<TagLoader.EntryWithSource>> load(ResourceManager resourceManager) {
        Map<Identifier, List<TagLoader.EntryWithSource>> map = new HashMap();
        FileToIdConverter filetoidconverter = FileToIdConverter.json(this.directory);

        for (Map.Entry<Identifier, List<Resource>> map_entry : filetoidconverter.listMatchingResourceStacks(resourceManager).entrySet()) {
            Identifier identifier = (Identifier) map_entry.getKey();
            Identifier identifier1 = filetoidconverter.fileToId(identifier);

            for (Resource resource : (List) map_entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement jsonelement = StrictJsonParser.parse(reader);
                    List<TagLoader.EntryWithSource> list = (List) map.computeIfAbsent(identifier1, (identifier2) -> {
                        return new ArrayList();
                    });
                    TagFile tagfile = (TagFile) TagFile.CODEC.parse(new Dynamic(JsonOps.INSTANCE, jsonelement)).getOrThrow();

                    if (tagfile.replace()) {
                        list.clear();
                    }

                    String s = resource.sourcePackId();

                    tagfile.entries().forEach((tagentry) -> {
                        list.add(new TagLoader.EntryWithSource(tagentry, s));
                    });
                } catch (Exception exception) {
                    TagLoader.LOGGER.error("Couldn't read tag list {} from {} in data pack {}", new Object[]{identifier1, identifier, resource.sourcePackId(), exception});
                }
            }
        }

        return map;
    }

    private Either<List<TagLoader.EntryWithSource>, List<T>> tryBuildTag(TagEntry.Lookup<T> lookup, List<TagLoader.EntryWithSource> entries) {
        SequencedSet<T> sequencedset = new LinkedHashSet();
        List<TagLoader.EntryWithSource> list1 = new ArrayList();

        for (TagLoader.EntryWithSource tagloader_entrywithsource : entries) {
            TagEntry tagentry = tagloader_entrywithsource.entry();

            Objects.requireNonNull(sequencedset);
            if (!tagentry.build(lookup, sequencedset::add)) {
                list1.add(tagloader_entrywithsource);
            }
        }

        return list1.isEmpty() ? Either.right(List.copyOf(sequencedset)) : Either.left(list1);
    }

    public Map<Identifier, List<T>> build(Map<Identifier, List<TagLoader.EntryWithSource>> builders) {
        final Map<Identifier, List<T>> map1 = new HashMap();
        TagEntry.Lookup<T> tagentry_lookup = new TagEntry.Lookup<T>() {
            @Override
            public @Nullable T element(Identifier key, boolean required) {
                return (T) TagLoader.this.elementLookup.get(key, required).orElse((Object) null);
            }

            @Override
            public @Nullable Collection<T> tag(Identifier key) {
                return (Collection) map1.get(key);
            }
        };
        DependencySorter<Identifier, TagLoader.SortingEntry> dependencysorter = new DependencySorter<Identifier, TagLoader.SortingEntry>();

        builders.forEach((identifier, list) -> {
            dependencysorter.addEntry(identifier, new TagLoader.SortingEntry(list));
        });
        dependencysorter.orderByDependencies((identifier, tagloader_sortingentry) -> {
            this.tryBuildTag(tagentry_lookup, tagloader_sortingentry.entries).ifLeft((list) -> {
                TagLoader.LOGGER.error("Couldn't load tag {} as it is missing following references: {}", identifier, list.stream().map(Objects::toString).collect(Collectors.joining(", ")));
            }).ifRight((list) -> {
                map1.put(identifier, list);
            });
        });
        return map1;
    }

    public static <T> void loadTagsFromNetwork(TagNetworkSerialization.NetworkPayload tags, WritableRegistry<T> registry) {
        Map map = tags.resolve(registry).tags;

        Objects.requireNonNull(registry);
        map.forEach(registry::bindTag);
    }

    public static List<Registry.PendingTags<?>> loadTagsForExistingRegistries(ResourceManager manager, RegistryAccess layer) {
        return (List) layer.registries().map((registryaccess_registryentry) -> {
            return loadPendingTags(manager, registryaccess_registryentry.value());
        }).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
    }

    public static <T> void loadTagsForRegistry(ResourceManager manager, WritableRegistry<T> registry) {
        ResourceKey<? extends Registry<T>> resourcekey = registry.key();
        TagLoader<Holder<T>> tagloader = new TagLoader<Holder<T>>(TagLoader.ElementLookup.fromWritableRegistry(registry), Registries.tagsDirPath(resourcekey));

        tagloader.build(tagloader.load(manager)).forEach((identifier, list) -> {
            registry.bindTag(TagKey.create(resourcekey, identifier), list);
        });
    }

    private static <T> Map<TagKey<T>, List<Holder<T>>> wrapTags(ResourceKey<? extends Registry<T>> registryKey, Map<Identifier, List<Holder<T>>> tags) {
        return (Map) tags.entrySet().stream().collect(Collectors.toUnmodifiableMap((entry) -> {
            return TagKey.create(registryKey, (Identifier) entry.getKey());
        }, Entry::getValue));
    }

    private static <T> Optional<Registry.PendingTags<T>> loadPendingTags(ResourceManager manager, Registry<T> registry) {
        ResourceKey<? extends Registry<T>> resourcekey = registry.key();
        TagLoader<Holder<T>> tagloader = new TagLoader<Holder<T>>(TagLoader.ElementLookup.fromFrozenRegistry(registry), Registries.tagsDirPath(resourcekey));
        TagLoader.LoadResult<T> tagloader_loadresult = new TagLoader.LoadResult<T>(resourcekey, wrapTags(registry.key(), tagloader.build(tagloader.load(manager))));

        return tagloader_loadresult.tags().isEmpty() ? Optional.empty() : Optional.of(registry.prepareTagReload(tagloader_loadresult));
    }

    public static List<HolderLookup.RegistryLookup<?>> buildUpdatedLookups(RegistryAccess.Frozen registries, List<Registry.PendingTags<?>> tags) {
        List<HolderLookup.RegistryLookup<?>> list1 = new ArrayList();

        registries.registries().forEach((registryaccess_registryentry) -> {
            Registry.PendingTags<?> registry_pendingtags = findTagsForRegistry(tags, registryaccess_registryentry.key());

            list1.add(registry_pendingtags != null ? registry_pendingtags.lookup() : registryaccess_registryentry.value());
        });
        return list1;
    }

    private static Registry.@Nullable PendingTags<?> findTagsForRegistry(List<Registry.PendingTags<?>> tags, ResourceKey<? extends Registry<?>> registryKey) {
        for (Registry.PendingTags<?> registry_pendingtags : tags) {
            if (registry_pendingtags.key() == registryKey) {
                return registry_pendingtags;
            }
        }

        return null;
    }

    public static record EntryWithSource(TagEntry entry, String source) {

        public String toString() {
            String s = String.valueOf(this.entry);

            return s + " (from " + this.source + ")";
        }
    }

    private static record SortingEntry(List<TagLoader.EntryWithSource> entries) implements DependencySorter.Entry<Identifier> {

        @Override
        public void visitRequiredDependencies(Consumer<Identifier> output) {
            this.entries.forEach((tagloader_entrywithsource) -> {
                tagloader_entrywithsource.entry.visitRequiredDependencies(output);
            });
        }

        @Override
        public void visitOptionalDependencies(Consumer<Identifier> output) {
            this.entries.forEach((tagloader_entrywithsource) -> {
                tagloader_entrywithsource.entry.visitOptionalDependencies(output);
            });
        }
    }

    public static record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<TagKey<T>, List<Holder<T>>> tags) {

    }

    public interface ElementLookup<T> {

        Optional<? extends T> get(Identifier id, boolean required);

        static <T> TagLoader.ElementLookup<? extends Holder<T>> fromFrozenRegistry(Registry<T> registry) {
            return (identifier, flag) -> {
                return registry.get(identifier);
            };
        }

        static <T> TagLoader.ElementLookup<Holder<T>> fromWritableRegistry(WritableRegistry<T> registry) {
            HolderGetter<T> holdergetter = registry.createRegistrationLookup();

            return (identifier, flag) -> {
                return ((HolderGetter) (flag ? holdergetter : registry)).get(ResourceKey.create(registry.key(), identifier));
            };
        }
    }
}
