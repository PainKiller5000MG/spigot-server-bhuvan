package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

public interface FontDescription {

    Codec<FontDescription> CODEC = Identifier.CODEC.flatComapMap(FontDescription.Resource::new, (fontdescription) -> {
        if (fontdescription instanceof FontDescription.Resource fontdescription_resource) {
            return DataResult.success(fontdescription_resource.id());
        } else {
            return DataResult.error(() -> {
                return "Unsupported font description type: " + String.valueOf(fontdescription);
            });
        }
    });
    FontDescription.Resource DEFAULT = new FontDescription.Resource(Identifier.withDefaultNamespace("default"));

    public static record Resource(Identifier id) implements FontDescription {

    }

    public static record AtlasSprite(Identifier atlasId, Identifier spriteId) implements FontDescription {

    }

    public static record PlayerSprite(ResolvableProfile profile, boolean hat) implements FontDescription {

    }
}
