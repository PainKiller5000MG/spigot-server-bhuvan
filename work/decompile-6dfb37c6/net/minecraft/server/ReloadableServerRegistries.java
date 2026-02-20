package net.minecraft.server;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class ReloadableServerRegistries {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RegistrationInfo DEFAULT_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    public ReloadableServerRegistries() {}

    public static CompletableFuture<ReloadableServerRegistries.LoadResult> reload(LayeredRegistryAccess<RegistryLayer> context, List<Registry.PendingTags<?>> updatedContextTags, ResourceManager manager, Executor executor) {
        List<HolderLookup.RegistryLookup<?>> list1 = TagLoader.buildUpdatedLookups(context.getAccessForLoading(RegistryLayer.RELOADABLE), updatedContextTags);
        HolderLookup.Provider holderlookup_provider = HolderLookup.Provider.create(list1.stream());
        RegistryOps<JsonElement> registryops = holderlookup_provider.<JsonElement>createSerializationContext(JsonOps.INSTANCE);
        List<CompletableFuture<WritableRegistry<?>>> list2 = LootDataType.values().map((lootdatatype) -> {
            return scheduleRegistryLoad(lootdatatype, registryops, manager, executor);
        }).toList();
        CompletableFuture<List<WritableRegistry<?>>> completablefuture = Util.sequence(list2);

        return completablefuture.thenApplyAsync((list3) -> {
            return createAndValidateFullContext(context, holderlookup_provider, list3);
        }, executor);
    }

    private static <T> CompletableFuture<WritableRegistry<?>> scheduleRegistryLoad(LootDataType<T> type, RegistryOps<JsonElement> ops, ResourceManager manager, Executor taskExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            WritableRegistry<T> writableregistry = new MappedRegistry<T>(type.registryKey(), Lifecycle.experimental());
            Map<Identifier, T> map = new HashMap();

            SimpleJsonResourceReloadListener.scanDirectory(manager, type.registryKey(), ops, type.codec(), map);
            map.forEach((identifier, object) -> {
                writableregistry.register(ResourceKey.create(type.registryKey(), identifier), object, ReloadableServerRegistries.DEFAULT_REGISTRATION_INFO);
            });
            TagLoader.loadTagsForRegistry(manager, writableregistry);
            return writableregistry;
        }, taskExecutor);
    }

    private static ReloadableServerRegistries.LoadResult createAndValidateFullContext(LayeredRegistryAccess<RegistryLayer> contextLayers, HolderLookup.Provider contextLookupWithUpdatedTags, List<WritableRegistry<?>> newRegistries) {
        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess1 = createUpdatedRegistries(contextLayers, newRegistries);
        HolderLookup.Provider holderlookup_provider1 = concatenateLookups(contextLookupWithUpdatedTags, layeredregistryaccess1.getLayer(RegistryLayer.RELOADABLE));

        validateLootRegistries(holderlookup_provider1);
        return new ReloadableServerRegistries.LoadResult(layeredregistryaccess1, holderlookup_provider1);
    }

    private static HolderLookup.Provider concatenateLookups(HolderLookup.Provider first, HolderLookup.Provider second) {
        return HolderLookup.Provider.create(Stream.concat(first.listRegistries(), second.listRegistries()));
    }

    private static void validateLootRegistries(HolderLookup.Provider fullContextWithNewTags) {
        ProblemReporter.Collector problemreporter_collector = new ProblemReporter.Collector();
        ValidationContext validationcontext = new ValidationContext(problemreporter_collector, LootContextParamSets.ALL_PARAMS, fullContextWithNewTags);

        LootDataType.values().forEach((lootdatatype) -> {
            validateRegistry(validationcontext, lootdatatype, fullContextWithNewTags);
        });
        problemreporter_collector.forEach((s, problemreporter_problem) -> {
            ReloadableServerRegistries.LOGGER.warn("Found loot table element validation problem in {}: {}", s, problemreporter_problem.description());
        });
    }

    private static LayeredRegistryAccess<RegistryLayer> createUpdatedRegistries(LayeredRegistryAccess<RegistryLayer> context, List<WritableRegistry<?>> registries) {
        return context.replaceFrom(RegistryLayer.RELOADABLE, (new RegistryAccess.ImmutableRegistryAccess(registries)).freeze());
    }

    private static <T> void validateRegistry(ValidationContext validationContext, LootDataType<T> type, HolderLookup.Provider registries) {
        HolderLookup<T> holderlookup = registries.lookupOrThrow(type.registryKey());

        holderlookup.listElements().forEach((net_minecraft_core_holder_reference) -> {
            type.runValidation(validationContext, net_minecraft_core_holder_reference.key(), net_minecraft_core_holder_reference.value());
        });
    }

    public static record LoadResult(LayeredRegistryAccess<RegistryLayer> layers, HolderLookup.Provider lookupWithUpdatedTags) {

    }

    public static class Holder {

        private final HolderLookup.Provider registries;

        public Holder(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public HolderLookup.Provider lookup() {
            return this.registries;
        }

        public LootTable getLootTable(ResourceKey<LootTable> id) {
            return (LootTable) this.registries.lookup(Registries.LOOT_TABLE).flatMap((holderlookup_registrylookup) -> {
                return holderlookup_registrylookup.get(id);
            }).map(net.minecraft.core.Holder::value).orElse(LootTable.EMPTY);
        }
    }
}
