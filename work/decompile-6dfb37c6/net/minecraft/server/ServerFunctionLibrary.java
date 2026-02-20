package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import org.slf4j.Logger;

public class ServerFunctionLibrary implements PreparableReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Registry<CommandFunction<CommandSourceStack>>> TYPE_KEY = ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("function"));
    private static final FileToIdConverter LISTER = new FileToIdConverter(Registries.elementsDirPath(ServerFunctionLibrary.TYPE_KEY), ".mcfunction");
    private volatile Map<Identifier, CommandFunction<CommandSourceStack>> functions = ImmutableMap.of();
    private final TagLoader<CommandFunction<CommandSourceStack>> tagsLoader;
    private volatile Map<Identifier, List<CommandFunction<CommandSourceStack>>> tags;
    private final PermissionSet functionCompilationPermissions;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public Optional<CommandFunction<CommandSourceStack>> getFunction(Identifier id) {
        return Optional.ofNullable((CommandFunction) this.functions.get(id));
    }

    public Map<Identifier, CommandFunction<CommandSourceStack>> getFunctions() {
        return this.functions;
    }

    public List<CommandFunction<CommandSourceStack>> getTag(Identifier tag) {
        return (List) this.tags.getOrDefault(tag, List.of());
    }

    public Iterable<Identifier> getAvailableTags() {
        return this.tags.keySet();
    }

    public ServerFunctionLibrary(PermissionSet functionCompilationPermissions, CommandDispatcher<CommandSourceStack> dispatcher) {
        this.tagsLoader = new TagLoader<CommandFunction<CommandSourceStack>>((identifier, flag) -> {
            return this.getFunction(identifier);
        }, Registries.tagsDirPath(ServerFunctionLibrary.TYPE_KEY));
        this.tags = Map.of();
        this.functionCompilationPermissions = functionCompilationPermissions;
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload, Executor taskExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor reloadExecutor) {
        ResourceManager resourcemanager = currentReload.resourceManager();
        CompletableFuture<Map<Identifier, List<TagLoader.EntryWithSource>>> completablefuture = CompletableFuture.supplyAsync(() -> {
            return this.tagsLoader.load(resourcemanager);
        }, taskExecutor);
        CompletableFuture<Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>>> completablefuture1 = CompletableFuture.supplyAsync(() -> {
            return ServerFunctionLibrary.LISTER.listMatchingResources(resourcemanager);
        }, taskExecutor).thenCompose((map) -> {
            Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>> map1 = Maps.newHashMap();
            CommandSourceStack commandsourcestack = Commands.createCompilationContext(this.functionCompilationPermissions);

            for (Map.Entry<Identifier, Resource> map_entry : map.entrySet()) {
                Identifier identifier = (Identifier) map_entry.getKey();
                Identifier identifier1 = ServerFunctionLibrary.LISTER.fileToId(identifier);

                map1.put(identifier1, CompletableFuture.supplyAsync(() -> {
                    List<String> list = readLines((Resource) map_entry.getValue());

                    return CommandFunction.fromLines(identifier1, this.dispatcher, commandsourcestack, list);
                }, taskExecutor));
            }

            CompletableFuture<?>[] acompletablefuture = (CompletableFuture[]) map1.values().toArray(new CompletableFuture[0]);

            return CompletableFuture.allOf(acompletablefuture).handle((ovoid, throwable) -> {
                return map1;
            });
        });
        CompletableFuture completablefuture2 = completablefuture.thenCombine(completablefuture1, Pair::of);

        Objects.requireNonNull(preparationBarrier);
        return completablefuture2.thenCompose(preparationBarrier::wait).thenAcceptAsync((pair) -> {
            Map<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>> map = (Map) pair.getSecond();
            ImmutableMap.Builder<Identifier, CommandFunction<CommandSourceStack>> immutablemap_builder = ImmutableMap.builder();

            map.forEach((identifier, completablefuture3) -> {
                completablefuture3.handle((commandfunction, throwable) -> {
                    if (throwable != null) {
                        ServerFunctionLibrary.LOGGER.error("Failed to load function {}", identifier, throwable);
                    } else {
                        immutablemap_builder.put(identifier, commandfunction);
                    }

                    return null;
                }).join();
            });
            this.functions = immutablemap_builder.build();
            this.tags = this.tagsLoader.build((Map) pair.getFirst());
        }, reloadExecutor);
    }

    private static List<String> readLines(Resource resource) {
        try (BufferedReader bufferedreader = resource.openAsReader()) {
            return bufferedreader.lines().toList();
        } catch (IOException ioexception) {
            throw new CompletionException(ioexception);
        }
    }
}
