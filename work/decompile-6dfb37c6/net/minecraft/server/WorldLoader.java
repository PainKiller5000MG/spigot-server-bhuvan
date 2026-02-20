package net.minecraft.server;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.WorldDataConfiguration;
import org.slf4j.Logger;

public class WorldLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    public WorldLoader() {}

    public static <D, R> CompletableFuture<R> load(WorldLoader.InitConfig config, WorldLoader.WorldDataSupplier<D> worldDataSupplier, WorldLoader.ResultFactory<D, R> resultFactory, Executor backgroundExecutor, Executor mainThreadExecutor) {
        try {
            Pair<WorldDataConfiguration, CloseableResourceManager> pair = config.packConfig.createResourceManager();
            CloseableResourceManager closeableresourcemanager = (CloseableResourceManager) pair.getSecond();
            LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = RegistryLayer.createRegistryAccess();
            List<Registry.PendingTags<?>> list = TagLoader.loadTagsForExistingRegistries(closeableresourcemanager, layeredregistryaccess.getLayer(RegistryLayer.STATIC));
            RegistryAccess.Frozen registryaccess_frozen = layeredregistryaccess.getAccessForLoading(RegistryLayer.WORLDGEN);
            List<HolderLookup.RegistryLookup<?>> list1 = TagLoader.buildUpdatedLookups(registryaccess_frozen, list);
            RegistryAccess.Frozen registryaccess_frozen1 = RegistryDataLoader.load((ResourceManager) closeableresourcemanager, list1, RegistryDataLoader.WORLDGEN_REGISTRIES);
            List<HolderLookup.RegistryLookup<?>> list2 = Stream.concat(list1.stream(), registryaccess_frozen1.listRegistries()).toList();
            RegistryAccess.Frozen registryaccess_frozen2 = RegistryDataLoader.load((ResourceManager) closeableresourcemanager, list2, RegistryDataLoader.DIMENSION_REGISTRIES);
            WorldDataConfiguration worlddataconfiguration = (WorldDataConfiguration) pair.getFirst();
            HolderLookup.Provider holderlookup_provider = HolderLookup.Provider.create(list2.stream());
            WorldLoader.DataLoadOutput<D> worldloader_dataloadoutput = worldDataSupplier.get(new WorldLoader.DataLoadContext(closeableresourcemanager, worlddataconfiguration, holderlookup_provider, registryaccess_frozen2));
            LayeredRegistryAccess<RegistryLayer> layeredregistryaccess1 = layeredregistryaccess.replaceFrom(RegistryLayer.WORLDGEN, registryaccess_frozen1, worldloader_dataloadoutput.finalDimensions);

            return ReloadableServerResources.loadResources(closeableresourcemanager, layeredregistryaccess1, list, worlddataconfiguration.enabledFeatures(), config.commandSelection(), config.functionCompilationPermissions(), backgroundExecutor, mainThreadExecutor).whenComplete((reloadableserverresources, throwable) -> {
                if (throwable != null) {
                    closeableresourcemanager.close();
                }

            }).thenApplyAsync((reloadableserverresources) -> {
                reloadableserverresources.updateStaticRegistryTags();
                return resultFactory.create(closeableresourcemanager, reloadableserverresources, layeredregistryaccess1, worldloader_dataloadoutput.cookie);
            }, mainThreadExecutor);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static record DataLoadContext(ResourceManager resources, WorldDataConfiguration dataConfiguration, HolderLookup.Provider datapackWorldgen, RegistryAccess.Frozen datapackDimensions) {

    }

    public static record DataLoadOutput<D>(D cookie, RegistryAccess.Frozen finalDimensions) {

    }

    public static record PackConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean safeMode, boolean initMode) {

        public Pair<WorldDataConfiguration, CloseableResourceManager> createResourceManager() {
            WorldDataConfiguration worlddataconfiguration = MinecraftServer.configurePackRepository(this.packRepository, this.initialDataConfig, this.initMode, this.safeMode);
            List<PackResources> list = this.packRepository.openAllSelected();
            CloseableResourceManager closeableresourcemanager = new MultiPackResourceManager(PackType.SERVER_DATA, list);

            return Pair.of(worlddataconfiguration, closeableresourcemanager);
        }
    }

    public static record InitConfig(WorldLoader.PackConfig packConfig, Commands.CommandSelection commandSelection, PermissionSet functionCompilationPermissions) {

    }

    @FunctionalInterface
    public interface ResultFactory<D, R> {

        R create(CloseableResourceManager resources, ReloadableServerResources managers, LayeredRegistryAccess<RegistryLayer> registries, D cookie);
    }

    @FunctionalInterface
    public interface WorldDataSupplier<D> {

        WorldLoader.DataLoadOutput<D> get(WorldLoader.DataLoadContext context);
    }
}
