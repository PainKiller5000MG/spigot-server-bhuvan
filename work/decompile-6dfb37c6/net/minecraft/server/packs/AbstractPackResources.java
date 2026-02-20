package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class AbstractPackResources implements PackResources {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;

    protected AbstractPackResources(PackLocationInfo location) {
        this.location = location;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSerializer) throws IOException {
        IoSupplier<InputStream> iosupplier = this.getRootResource(new String[]{"pack.mcmeta"});

        if (iosupplier == null) {
            return null;
        } else {
            try (InputStream inputstream = iosupplier.get()) {
                return (T) getMetadataFromStream(metadataSerializer, inputstream, this.location);
            }
        }
    }

    public static <T> @Nullable T getMetadataFromStream(MetadataSectionType<T> serializer, InputStream stream, PackLocationInfo location) {
        JsonObject jsonobject;

        try (BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            jsonobject = GsonHelper.parse((Reader) bufferedreader);
        } catch (Exception exception) {
            AbstractPackResources.LOGGER.error("Couldn't load {} {} metadata: {}", new Object[]{location.id(), serializer.name(), exception.getMessage()});
            return null;
        }

        return (T) (!jsonobject.has(serializer.name()) ? null : serializer.codec().parse(JsonOps.INSTANCE, jsonobject.get(serializer.name())).ifError((error) -> {
            AbstractPackResources.LOGGER.error("Couldn't load {} {} metadata: {}", new Object[]{location.id(), serializer.name(), error.message()});
        }).result().orElse((Object) null));
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }
}
