package net.minecraft.server.packs.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public abstract class SimpleJsonResourceReloadListener<T> extends SimplePreparableReloadListener<Map<Identifier, T>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final DynamicOps<JsonElement> ops;
    private final Codec<T> codec;
    private final FileToIdConverter lister;

    protected SimpleJsonResourceReloadListener(HolderLookup.Provider registries, Codec<T> codec, ResourceKey<? extends Registry<T>> registryKey) {
        this((DynamicOps) registries.createSerializationContext(JsonOps.INSTANCE), codec, FileToIdConverter.registry(registryKey));
    }

    protected SimpleJsonResourceReloadListener(Codec<T> codec, FileToIdConverter lister) {
        this((DynamicOps) JsonOps.INSTANCE, codec, lister);
    }

    private SimpleJsonResourceReloadListener(DynamicOps<JsonElement> ops, Codec<T> codec, FileToIdConverter lister) {
        this.ops = ops;
        this.codec = codec;
        this.lister = lister;
    }

    @Override
    protected Map<Identifier, T> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, T> map = new HashMap();

        scanDirectory(manager, this.lister, this.ops, this.codec, map);
        return map;
    }

    public static <T> void scanDirectory(ResourceManager manager, ResourceKey<? extends Registry<T>> registryKey, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result) {
        scanDirectory(manager, FileToIdConverter.registry(registryKey), ops, codec, result);
    }

    public static <T> void scanDirectory(ResourceManager manager, FileToIdConverter lister, DynamicOps<JsonElement> ops, Codec<T> codec, Map<Identifier, T> result) {
        for (Map.Entry<Identifier, Resource> map_entry : lister.listMatchingResources(manager).entrySet()) {
            Identifier identifier = (Identifier) map_entry.getKey();
            Identifier identifier1 = lister.fileToId(identifier);

            try (Reader reader = ((Resource) map_entry.getValue()).openAsReader()) {
                codec.parse(ops, StrictJsonParser.parse(reader)).ifSuccess((object) -> {
                    if (result.putIfAbsent(identifier1, object) != null) {
                        throw new IllegalStateException("Duplicate data file ignored with ID " + String.valueOf(identifier1));
                    }
                }).ifError((error) -> {
                    SimpleJsonResourceReloadListener.LOGGER.error("Couldn't parse data file '{}' from '{}': {}", new Object[]{identifier1, identifier, error});
                });
            } catch (IllegalArgumentException | IOException | JsonParseException jsonparseexception) {
                SimpleJsonResourceReloadListener.LOGGER.error("Couldn't parse data file '{}' from '{}'", new Object[]{identifier1, identifier, jsonparseexception});
            }
        }

    }
}
