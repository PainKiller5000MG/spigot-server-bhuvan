package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

public record NameAndId(UUID id, String name) {

    public static final Codec<NameAndId> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(NameAndId::id), Codec.STRING.fieldOf("name").forGetter(NameAndId::name)).apply(instance, NameAndId::new);
    });

    public NameAndId(GameProfile profile) {
        this(profile.id(), profile.name());
    }

    public NameAndId(com.mojang.authlib.yggdrasil.response.NameAndId profile) {
        this(profile.id(), profile.name());
    }

    public static @Nullable NameAndId fromJson(JsonObject object) {
        if (object.has("uuid") && object.has("name")) {
            String s = object.get("uuid").getAsString();

            UUID uuid;

            try {
                uuid = UUID.fromString(s);
            } catch (Throwable throwable) {
                return null;
            }

            return new NameAndId(uuid, object.get("name").getAsString());
        } else {
            return null;
        }
    }

    public void appendTo(JsonObject output) {
        output.addProperty("uuid", this.id().toString());
        output.addProperty("name", this.name());
    }

    public static NameAndId createOffline(String name) {
        UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);

        return new NameAndId(uuid, name);
    }
}
