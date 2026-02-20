package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class FileToIdConverter {

    private final String prefix;
    private final String extension;

    public FileToIdConverter(String prefix, String extension) {
        this.prefix = prefix;
        this.extension = extension;
    }

    public static FileToIdConverter json(String prefix) {
        return new FileToIdConverter(prefix, ".json");
    }

    public static FileToIdConverter registry(ResourceKey<? extends Registry<?>> registry) {
        return json(Registries.elementsDirPath(registry));
    }

    public Identifier idToFile(Identifier id) {
        return id.withPath(this.prefix + "/" + id.getPath() + this.extension);
    }

    public Identifier fileToId(Identifier file) {
        String s = file.getPath();

        return file.withPath(s.substring(this.prefix.length() + 1, s.length() - this.extension.length()));
    }

    public Map<Identifier, Resource> listMatchingResources(ResourceManager manager) {
        return manager.listResources(this.prefix, (identifier) -> {
            return identifier.getPath().endsWith(this.extension);
        });
    }

    public Map<Identifier, List<Resource>> listMatchingResourceStacks(ResourceManager manager) {
        return manager.listResourceStacks(this.prefix, (identifier) -> {
            return identifier.getPath().endsWith(this.extension);
        });
    }
}
