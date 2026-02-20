package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface ResourceProvider {

    ResourceProvider EMPTY = (identifier) -> {
        return Optional.empty();
    };

    Optional<Resource> getResource(Identifier location);

    default Resource getResourceOrThrow(Identifier location) throws FileNotFoundException {
        return (Resource) this.getResource(location).orElseThrow(() -> {
            return new FileNotFoundException(location.toString());
        });
    }

    default InputStream open(Identifier location) throws IOException {
        return this.getResourceOrThrow(location).open();
    }

    default BufferedReader openAsReader(Identifier location) throws IOException {
        return this.getResourceOrThrow(location).openAsReader();
    }

    static ResourceProvider fromMap(Map<Identifier, Resource> map) {
        return (identifier) -> {
            return Optional.ofNullable((Resource) map.get(identifier));
        };
    }
}
