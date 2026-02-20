package net.minecraft.server.packs.metadata.pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.InclusiveRange;

public record PackMetadataSection(Component description, InclusiveRange<PackFormat> supportedFormats) {

    private static final Codec<PackMetadataSection> FALLBACK_CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ComponentSerialization.CODEC.fieldOf("description").forGetter(PackMetadataSection::description)).apply(instance, (component) -> {
            return new PackMetadataSection(component, new InclusiveRange(PackFormat.of(Integer.MAX_VALUE)));
        });
    });
    public static final MetadataSectionType<PackMetadataSection> CLIENT_TYPE = new MetadataSectionType<PackMetadataSection>("pack", codecForPackType(PackType.CLIENT_RESOURCES));
    public static final MetadataSectionType<PackMetadataSection> SERVER_TYPE = new MetadataSectionType<PackMetadataSection>("pack", codecForPackType(PackType.SERVER_DATA));
    public static final MetadataSectionType<PackMetadataSection> FALLBACK_TYPE = new MetadataSectionType<PackMetadataSection>("pack", PackMetadataSection.FALLBACK_CODEC);

    private static Codec<PackMetadataSection> codecForPackType(PackType packType) {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(ComponentSerialization.CODEC.fieldOf("description").forGetter(PackMetadataSection::description), PackFormat.packCodec(packType).forGetter(PackMetadataSection::supportedFormats)).apply(instance, PackMetadataSection::new);
        });
    }

    public static MetadataSectionType<PackMetadataSection> forPackType(PackType packType) {
        MetadataSectionType metadatasectiontype;

        switch (packType) {
            case CLIENT_RESOURCES:
                metadatasectiontype = PackMetadataSection.CLIENT_TYPE;
                break;
            case SERVER_DATA:
                metadatasectiontype = PackMetadataSection.SERVER_TYPE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return metadatasectiontype;
    }
}
