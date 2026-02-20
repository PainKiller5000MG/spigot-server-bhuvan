package net.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.DataVersion;
import org.slf4j.Logger;

public class DetectedVersion {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final WorldVersion BUILT_IN = createBuiltIn(UUID.randomUUID().toString().replaceAll("-", ""), "Development Version");

    public DetectedVersion() {}

    public static WorldVersion createBuiltIn(String id, String name) {
        return createBuiltIn(id, name, true);
    }

    public static WorldVersion createBuiltIn(String id, String name, boolean stable) {
        return new WorldVersion.Simple(id, name, new DataVersion(4671, "main"), SharedConstants.getProtocolVersion(), PackFormat.of(75, 0), PackFormat.of(94, 1), new Date(), stable);
    }

    private static WorldVersion createFromJson(JsonObject root) {
        JsonObject jsonobject1 = GsonHelper.getAsJsonObject(root, "pack_version");

        return new WorldVersion.Simple(GsonHelper.getAsString(root, "id"), GsonHelper.getAsString(root, "name"), new DataVersion(GsonHelper.getAsInt(root, "world_version"), GsonHelper.getAsString(root, "series_id", "main")), GsonHelper.getAsInt(root, "protocol_version"), PackFormat.of(GsonHelper.getAsInt(jsonobject1, "resource_major"), GsonHelper.getAsInt(jsonobject1, "resource_minor")), PackFormat.of(GsonHelper.getAsInt(jsonobject1, "data_major"), GsonHelper.getAsInt(jsonobject1, "data_minor")), Date.from(ZonedDateTime.parse(GsonHelper.getAsString(root, "build_time")).toInstant()), GsonHelper.getAsBoolean(root, "stable"));
    }

    public static WorldVersion tryDetectVersion() {
        try {
            WorldVersion worldversion;

            try (InputStream inputstream = DetectedVersion.class.getResourceAsStream("/version.json")) {
                if (inputstream == null) {
                    DetectedVersion.LOGGER.warn("Missing version information!");
                    WorldVersion worldversion1 = DetectedVersion.BUILT_IN;

                    return worldversion1;
                }

                try (InputStreamReader inputstreamreader = new InputStreamReader(inputstream, StandardCharsets.UTF_8)) {
                    worldversion = createFromJson(GsonHelper.parse((Reader) inputstreamreader));
                }
            }

            return worldversion;
        } catch (JsonParseException | IOException ioexception) {
            throw new IllegalStateException("Game version information is corrupt", ioexception);
        }
    }
}
