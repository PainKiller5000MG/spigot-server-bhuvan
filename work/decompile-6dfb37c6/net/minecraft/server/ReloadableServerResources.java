package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

public class ReloadableServerResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final ReloadableServerRegistries.Holder fullRegistryHolder;
    public Commands commands;
    private final RecipeManager recipes;
    private final ServerAdvancementManager advancements;
    private final ServerFunctionLibrary functionLibrary;
    private final List<Registry.PendingTags<?>> postponedTags;

    private ReloadableServerResources(LayeredRegistryAccess<RegistryLayer> fullLayers, HolderLookup.Provider loadingContext, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List<Registry.PendingTags<?>> postponedTags, PermissionSet functionCompilationPermissions) {
        this.fullRegistryHolder = new ReloadableServerRegistries.Holder(fullLayers.compositeAccess());
        this.postponedTags = postponedTags;
        this.recipes = new RecipeManager(loadingContext);
        this.commands = new Commands(commandSelection, CommandBuildContext.simple(loadingContext, enabledFeatures));
        this.advancements = new ServerAdvancementManager(loadingContext);
        this.functionLibrary = new ServerFunctionLibrary(functionCompilationPermissions, this.commands.getDispatcher());
    }

    public ServerFunctionLibrary getFunctionLibrary() {
        return this.functionLibrary;
    }

    public ReloadableServerRegistries.Holder fullRegistries() {
        return this.fullRegistryHolder;
    }

    public RecipeManager getRecipeManager() {
        return this.recipes;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.advancements;
    }

    public List<PreparableReloadListener> listeners() {
        return List.of(this.recipes, this.functionLibrary, this.advancements);
    }

    public static CompletableFuture<ReloadableServerResources> loadResources(ResourceManager resourceManager, LayeredRegistryAccess<RegistryLayer> contextLayers, List<Registry.PendingTags<?>> updatedContextTags, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, PermissionSet functionCompilationPermissions, Executor backgroundExecutor, Executor mainThreadExecutor) {
        return ReloadableServerRegistries.reload(contextLayers, updatedContextTags, resourceManager, backgroundExecutor).thenCompose((reloadableserverregistries_loadresult) -> {
            ReloadableServerResources reloadableserverresources = new ReloadableServerResources(reloadableserverregistries_loadresult.layers(), reloadableserverregistries_loadresult.lookupWithUpdatedTags(), enabledFeatures, commandSelection, updatedContextTags, functionCompilationPermissions);

            return SimpleReloadInstance.create(resourceManager, reloadableserverresources.listeners(), backgroundExecutor, mainThreadExecutor, ReloadableServerResources.DATA_RELOAD_INITIAL_TASK, ReloadableServerResources.LOGGER.isDebugEnabled()).done().thenApply((object) -> {
                return reloadableserverresources;
            });
        });
    }

    public void updateStaticRegistryTags() {
        this.postponedTags.forEach(Registry.PendingTags::apply);
    }
}
