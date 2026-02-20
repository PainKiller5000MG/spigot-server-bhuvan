package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.timeline.Timeline;
import org.slf4j.Logger;

public class RegistryDataLoader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<ResourceKey<?>> ERROR_KEY_COMPARATOR = Comparator.comparing(ResourceKey::registry).thenComparing(ResourceKey::identifier);
    private static final RegistrationInfo NETWORK_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());
    private static final Function<Optional<KnownPack>, RegistrationInfo> REGISTRATION_INFO_CACHE = Util.memoize((optional) -> {
        Lifecycle lifecycle = (Lifecycle) optional.map(KnownPack::isVanilla).map((obool) -> {
            return Lifecycle.stable();
        }).orElse(Lifecycle.experimental());

        return new RegistrationInfo(optional, lifecycle);
    });
    public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(new RegistryDataLoader.RegistryData(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.BIOME, Biome.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.STRUCTURE, Structure.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TRIAL_SPAWNER_CONFIG, TrialSpawnerConfig.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.WOLF_SOUND_VARIANT, WolfSoundVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.PIG_VARIANT, PigVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.FROG_VARIANT, FrogVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.CAT_VARIANT, CatVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.COW_VARIANT, CowVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.CHICKEN_VARIANT, ChickenVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.INSTRUMENT, Instrument.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TEST_ENVIRONMENT, TestEnvironmentDefinition.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TEST_INSTANCE, GameTestInstance.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.DIALOG, Dialog.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TIMELINE, Timeline.DIRECT_CODEC));
    public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(new RegistryDataLoader.RegistryData(Registries.LEVEL_STEM, LevelStem.CODEC));
    public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES = List.of(new RegistryDataLoader.RegistryData(Registries.BIOME, Biome.NETWORK_CODEC), new RegistryDataLoader.RegistryData(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.WOLF_VARIANT, WolfVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.WOLF_SOUND_VARIANT, WolfSoundVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.PIG_VARIANT, PigVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.FROG_VARIANT, FrogVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.CAT_VARIANT, CatVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.COW_VARIANT, CowVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.CHICKEN_VARIANT, ChickenVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.ZOMBIE_NAUTILUS_VARIANT, ZombieNautilusVariant.NETWORK_CODEC, true), new RegistryDataLoader.RegistryData(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true), new RegistryDataLoader.RegistryData(Registries.DIMENSION_TYPE, DimensionType.NETWORK_CODEC), new RegistryDataLoader.RegistryData(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.INSTRUMENT, Instrument.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TEST_ENVIRONMENT, TestEnvironmentDefinition.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TEST_INSTANCE, GameTestInstance.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.DIALOG, Dialog.DIRECT_CODEC), new RegistryDataLoader.RegistryData(Registries.TIMELINE, Timeline.NETWORK_CODEC));

    public RegistryDataLoader() {}

    public static RegistryAccess.Frozen load(ResourceManager resourceManager, List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.RegistryData<?>> registriesToLoad) {
        return load((registrydataloader_loader, registryops_registryinfolookup) -> {
            registrydataloader_loader.loadFromResources(resourceManager, registryops_registryinfolookup);
        }, contextRegistries, registriesToLoad);
    }

    public static RegistryAccess.Frozen load(Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries, ResourceProvider knownDataSource, List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.RegistryData<?>> registriesToLoad) {
        return load((registrydataloader_loader, registryops_registryinfolookup) -> {
            registrydataloader_loader.loadFromNetwork(entries, knownDataSource, registryops_registryinfolookup);
        }, contextRegistries, registriesToLoad);
    }

    private static RegistryAccess.Frozen load(RegistryDataLoader.LoadingFunction loadingFunction, List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.RegistryData<?>> registriesToLoad) {
        Map<ResourceKey<?>, Exception> map = new HashMap();
        List<RegistryDataLoader.Loader<?>> list2 = (List) registriesToLoad.stream().map((registrydataloader_registrydata) -> {
            return registrydataloader_registrydata.create(Lifecycle.stable(), map);
        }).collect(Collectors.toUnmodifiableList());
        RegistryOps.RegistryInfoLookup registryops_registryinfolookup = createContext(contextRegistries, list2);

        list2.forEach((registrydataloader_loader) -> {
            loadingFunction.apply(registrydataloader_loader, registryops_registryinfolookup);
        });
        list2.forEach((registrydataloader_loader) -> {
            Registry<?> registry = registrydataloader_loader.registry();

            try {
                registry.freeze();
            } catch (Exception exception) {
                map.put(registry.key(), exception);
            }

            if (registrydataloader_loader.data.requiredNonEmpty && registry.size() == 0) {
                map.put(registry.key(), new IllegalStateException("Registry must be non-empty: " + String.valueOf(registry.key().identifier())));
            }

        });
        if (!map.isEmpty()) {
            throw logErrors(map);
        } else {
            return (new RegistryAccess.ImmutableRegistryAccess(list2.stream().map(RegistryDataLoader.Loader::registry).toList())).freeze();
        }
    }

    private static RegistryOps.RegistryInfoLookup createContext(List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.Loader<?>> newRegistriesAndLoaders) {
        final Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> map = new HashMap();

        contextRegistries.forEach((holderlookup_registrylookup) -> {
            map.put(holderlookup_registrylookup.key(), createInfoForContextRegistry(holderlookup_registrylookup));
        });
        newRegistriesAndLoaders.forEach((registrydataloader_loader) -> {
            map.put(registrydataloader_loader.registry.key(), createInfoForNewRegistry(registrydataloader_loader.registry));
        });
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.ofNullable((RegistryOps.RegistryInfo) map.get(key));
            }
        };
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForNewRegistry(WritableRegistry<T> e) {
        return new RegistryOps.RegistryInfo<T>(e, e.createRegistrationLookup(), e.registryLifecycle());
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForContextRegistry(HolderLookup.RegistryLookup<T> lookup) {
        return new RegistryOps.RegistryInfo<T>(lookup, lookup, lookup.registryLifecycle());
    }

    private static ReportedException logErrors(Map<ResourceKey<?>, Exception> loadingErrors) {
        printFullDetailsToLog(loadingErrors);
        return createReportWithBriefInfo(loadingErrors);
    }

    private static void printFullDetailsToLog(Map<ResourceKey<?>, Exception> loadingErrors) {
        StringWriter stringwriter = new StringWriter();
        PrintWriter printwriter = new PrintWriter(stringwriter);
        Map<Identifier, Map<Identifier, Exception>> map1 = (Map) loadingErrors.entrySet().stream().collect(Collectors.groupingBy((entry) -> {
            return ((ResourceKey) entry.getKey()).registry();
        }, Collectors.toMap((entry) -> {
            return ((ResourceKey) entry.getKey()).identifier();
        }, Entry::getValue)));

        map1.entrySet().stream().sorted(Entry.comparingByKey()).forEach((entry) -> {
            printwriter.printf(Locale.ROOT, "> Errors in registry %s:%n", entry.getKey());
            ((Map) entry.getValue()).entrySet().stream().sorted(Entry.comparingByKey()).forEach((entry1) -> {
                printwriter.printf(Locale.ROOT, ">> Errors in element %s:%n", entry1.getKey());
                ((Exception) entry1.getValue()).printStackTrace(printwriter);
            });
        });
        printwriter.flush();
        RegistryDataLoader.LOGGER.error("Registry loading errors:\n{}", stringwriter);
    }

    private static ReportedException createReportWithBriefInfo(Map<ResourceKey<?>, Exception> loadingErrors) {
        CrashReport crashreport = CrashReport.forThrowable(new IllegalStateException("Failed to load registries due to errors"), "Registry Loading");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Loading info");

        crashreportcategory.setDetail("Errors", () -> {
            StringBuilder stringbuilder = new StringBuilder();

            loadingErrors.entrySet().stream().sorted(Entry.comparingByKey(RegistryDataLoader.ERROR_KEY_COMPARATOR)).forEach((entry) -> {
                stringbuilder.append("\n\t\t").append(((ResourceKey) entry.getKey()).registry()).append("/").append(((ResourceKey) entry.getKey()).identifier()).append(": ").append(((Exception) entry.getValue()).getMessage());
            });
            return stringbuilder.toString();
        });
        return new ReportedException(crashreport);
    }

    private static <E> void loadElementFromResource(WritableRegistry<E> output, Decoder<E> elementDecoder, RegistryOps<JsonElement> ops, ResourceKey<E> elementKey, Resource thunk, RegistrationInfo registrationInfo) throws IOException {
        try (Reader reader = thunk.openAsReader()) {
            JsonElement jsonelement = StrictJsonParser.parse(reader);
            DataResult<E> dataresult = elementDecoder.parse(ops, jsonelement);
            E e0 = (E) dataresult.getOrThrow();

            output.register(elementKey, e0, registrationInfo);
        }

    }

    private static <E> void loadContentsFromManager(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup lookup, WritableRegistry<E> registry, Decoder<E> elementDecoder, Map<ResourceKey<?>, Exception> loadingErrors) {
        FileToIdConverter filetoidconverter = FileToIdConverter.registry(registry.key());
        RegistryOps<JsonElement> registryops = RegistryOps.create(JsonOps.INSTANCE, lookup);

        for (Map.Entry<Identifier, Resource> map_entry : filetoidconverter.listMatchingResources(resourceManager).entrySet()) {
            Identifier identifier = (Identifier) map_entry.getKey();
            ResourceKey<E> resourcekey = ResourceKey.create(registry.key(), filetoidconverter.fileToId(identifier));
            Resource resource = (Resource) map_entry.getValue();
            RegistrationInfo registrationinfo = (RegistrationInfo) RegistryDataLoader.REGISTRATION_INFO_CACHE.apply(resource.knownPackInfo());

            try {
                loadElementFromResource(registry, elementDecoder, registryops, resourcekey, resource, registrationinfo);
            } catch (Exception exception) {
                loadingErrors.put(resourcekey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", identifier, resource.sourcePackId()), exception));
            }
        }

        TagLoader.loadTagsForRegistry(resourceManager, registry);
    }

    private static <E> void loadContentsFromNetwork(Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries, ResourceProvider knownDataSource, RegistryOps.RegistryInfoLookup lookup, WritableRegistry<E> registry, Decoder<E> elementDecoder, Map<ResourceKey<?>, Exception> loadingErrors) {
        RegistryDataLoader.NetworkedRegistryData registrydataloader_networkedregistrydata = (RegistryDataLoader.NetworkedRegistryData) entries.get(registry.key());

        if (registrydataloader_networkedregistrydata != null) {
            RegistryOps<Tag> registryops = RegistryOps.create(NbtOps.INSTANCE, lookup);
            RegistryOps<JsonElement> registryops1 = RegistryOps.create(JsonOps.INSTANCE, lookup);
            FileToIdConverter filetoidconverter = FileToIdConverter.registry(registry.key());

            for (RegistrySynchronization.PackedRegistryEntry registrysynchronization_packedregistryentry : registrydataloader_networkedregistrydata.elements) {
                ResourceKey<E> resourcekey = ResourceKey.create(registry.key(), registrysynchronization_packedregistryentry.id());
                Optional<Tag> optional = registrysynchronization_packedregistryentry.data();

                if (optional.isPresent()) {
                    try {
                        DataResult<E> dataresult = elementDecoder.parse(registryops, (Tag) optional.get());
                        E e0 = (E) dataresult.getOrThrow();

                        registry.register(resourcekey, e0, RegistryDataLoader.NETWORK_REGISTRATION_INFO);
                    } catch (Exception exception) {
                        loadingErrors.put(resourcekey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse value %s from server", optional.get()), exception));
                    }
                } else {
                    Identifier identifier = filetoidconverter.idToFile(registrysynchronization_packedregistryentry.id());

                    try {
                        Resource resource = knownDataSource.getResourceOrThrow(identifier);

                        loadElementFromResource(registry, elementDecoder, registryops1, resourcekey, resource, RegistryDataLoader.NETWORK_REGISTRATION_INFO);
                    } catch (Exception exception1) {
                        loadingErrors.put(resourcekey, new IllegalStateException("Failed to parse local data", exception1));
                    }
                }
            }

            TagLoader.loadTagsFromNetwork(registrydataloader_networkedregistrydata.tags, registry);
        }
    }

    public static record RegistryData<T>(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, boolean requiredNonEmpty) {

        private RegistryData(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec) {
            this(key, elementCodec, false);
        }

        private RegistryDataLoader.Loader<T> create(Lifecycle lifecycle, Map<ResourceKey<?>, Exception> loadingErrors) {
            WritableRegistry<T> writableregistry = new MappedRegistry<T>(this.key, lifecycle);

            return new RegistryDataLoader.Loader<T>(this, writableregistry, loadingErrors);
        }

        public void runWithArguments(BiConsumer<ResourceKey<? extends Registry<T>>, Codec<T>> output) {
            output.accept(this.key, this.elementCodec);
        }
    }

    private static record Loader<T>(RegistryDataLoader.RegistryData<T> data, WritableRegistry<T> registry, Map<ResourceKey<?>, Exception> loadingErrors) {

        public void loadFromResources(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup context) {
            RegistryDataLoader.loadContentsFromManager(resourceManager, context, this.registry, this.data.elementCodec, this.loadingErrors);
        }

        public void loadFromNetwork(Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries, ResourceProvider knownDataSource, RegistryOps.RegistryInfoLookup context) {
            RegistryDataLoader.loadContentsFromNetwork(entries, knownDataSource, context, this.registry, this.data.elementCodec, this.loadingErrors);
        }
    }

    public static record NetworkedRegistryData(List<RegistrySynchronization.PackedRegistryEntry> elements, TagNetworkSerialization.NetworkPayload tags) {

    }

    @FunctionalInterface
    private interface LoadingFunction {

        void apply(RegistryDataLoader.Loader<?> loader, RegistryOps.RegistryInfoLookup context);
    }
}
