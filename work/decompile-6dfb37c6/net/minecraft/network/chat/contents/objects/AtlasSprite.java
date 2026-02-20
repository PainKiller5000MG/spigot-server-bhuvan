package net.minecraft.network.chat.contents.objects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public record AtlasSprite(Identifier atlas, Identifier sprite) implements ObjectInfo {

    public static final Identifier DEFAULT_ATLAS = AtlasIds.BLOCKS;
    public static final MapCodec<AtlasSprite> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Identifier.CODEC.optionalFieldOf("atlas", AtlasSprite.DEFAULT_ATLAS).forGetter(AtlasSprite::atlas), Identifier.CODEC.fieldOf("sprite").forGetter(AtlasSprite::sprite)).apply(instance, AtlasSprite::new);
    });

    @Override
    public MapCodec<AtlasSprite> codec() {
        return AtlasSprite.MAP_CODEC;
    }

    @Override
    public FontDescription fontDescription() {
        return new FontDescription.AtlasSprite(this.atlas, this.sprite);
    }

    private static String toShortName(Identifier id) {
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    @Override
    public String description() {
        String s = toShortName(this.sprite);

        return this.atlas.equals(AtlasSprite.DEFAULT_ATLAS) ? "[" + s + "]" : "[" + s + "@" + toShortName(this.atlas) + "]";
    }
}
