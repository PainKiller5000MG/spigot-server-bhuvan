package net.minecraft.server.packs.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {

    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionType<T> serializer) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> {
        return ResourceMetadata.EMPTY;
    };

    static ResourceMetadata fromJsonStream(InputStream inputStream) throws IOException {
        try (BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            final JsonObject jsonobject = GsonHelper.parse((Reader) bufferedreader);

            return new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionType<T> serializer) {
                    String s = serializer.name();

                    if (jsonobject.has(s)) {
                        T t0 = (T) serializer.codec().parse(JsonOps.INSTANCE, jsonobject.get(s)).getOrThrow(JsonParseException::new);

                        return Optional.of(t0);
                    } else {
                        return Optional.empty();
                    }
                }
            };
        }
    }

    <T> Optional<T> getSection(MetadataSectionType<T> serializer);

    default <T> Optional<MetadataSectionType.WithValue<T>> getTypedSection(MetadataSectionType<T> type) {
        Optional optional = this.getSection(type);

        Objects.requireNonNull(type);
        return optional.map(type::withValue);
    }

    default List<MetadataSectionType.WithValue<?>> getTypedSections(Collection<MetadataSectionType<?>> types) {
        return (List) types.stream().map(this::getTypedSection).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
    }
}
