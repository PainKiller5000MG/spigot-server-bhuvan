package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.InclusiveRange;

public record OverlayMetadataSection(List<OverlayMetadataSection.OverlayEntry> overlays) {

    private static final Pattern DIR_VALIDATOR = Pattern.compile("[-_a-zA-Z0-9.]+");
    public static final MetadataSectionType<OverlayMetadataSection> CLIENT_TYPE = new MetadataSectionType<OverlayMetadataSection>("overlays", codecForPackType(PackType.CLIENT_RESOURCES));
    public static final MetadataSectionType<OverlayMetadataSection> SERVER_TYPE = new MetadataSectionType<OverlayMetadataSection>("overlays", codecForPackType(PackType.SERVER_DATA));

    private static DataResult<String> validateOverlayDir(String path) {
        return !OverlayMetadataSection.DIR_VALIDATOR.matcher(path).matches() ? DataResult.error(() -> {
            return path + " is not accepted directory name";
        }) : DataResult.success(path);
    }

    @VisibleForTesting
    public static Codec<OverlayMetadataSection> codecForPackType(PackType packType) {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(OverlayMetadataSection.OverlayEntry.listCodecForPackType(packType).fieldOf("entries").forGetter(OverlayMetadataSection::overlays)).apply(instance, OverlayMetadataSection::new);
        });
    }

    public static MetadataSectionType<OverlayMetadataSection> forPackType(PackType packType) {
        MetadataSectionType metadatasectiontype;

        switch (packType) {
            case CLIENT_RESOURCES:
                metadatasectiontype = OverlayMetadataSection.CLIENT_TYPE;
                break;
            case SERVER_DATA:
                metadatasectiontype = OverlayMetadataSection.SERVER_TYPE;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return metadatasectiontype;
    }

    public List<String> overlaysForVersion(PackFormat version) {
        return this.overlays.stream().filter((overlaymetadatasection_overlayentry) -> {
            return overlaymetadatasection_overlayentry.isApplicable(version);
        }).map(OverlayMetadataSection.OverlayEntry::overlay).toList();
    }

    public static record OverlayEntry(InclusiveRange<PackFormat> format, String overlay) {

        private static Codec<List<OverlayMetadataSection.OverlayEntry>> listCodecForPackType(PackType packType) {
            int i = PackFormat.lastPreMinorVersion(packType);

            return OverlayMetadataSection.OverlayEntry.IntermediateEntry.CODEC.listOf().flatXmap((list) -> {
                return PackFormat.validateHolderList(list, i, (overlaymetadatasection_overlayentry_intermediateentry, inclusiverange) -> {
                    return new OverlayMetadataSection.OverlayEntry(inclusiverange, overlaymetadatasection_overlayentry_intermediateentry.overlay());
                });
            }, (list) -> {
                return DataResult.success(list.stream().map((overlaymetadatasection_overlayentry) -> {
                    return new OverlayMetadataSection.OverlayEntry.IntermediateEntry(PackFormat.IntermediaryFormat.fromRange(overlaymetadatasection_overlayentry.format(), i), overlaymetadatasection_overlayentry.overlay());
                }).toList());
            });
        }

        public boolean isApplicable(PackFormat formatToTest) {
            return this.format.isValueInRange(formatToTest);
        }

        private static record IntermediateEntry(PackFormat.IntermediaryFormat format, String overlay) implements PackFormat.IntermediaryFormatHolder {

            private static final Codec<OverlayMetadataSection.OverlayEntry.IntermediateEntry> CODEC = RecordCodecBuilder.create((instance) -> {
                return instance.group(PackFormat.IntermediaryFormat.OVERLAY_CODEC.forGetter(OverlayMetadataSection.OverlayEntry.IntermediateEntry::format), Codec.STRING.validate(OverlayMetadataSection::validateOverlayDir).fieldOf("directory").forGetter(OverlayMetadataSection.OverlayEntry.IntermediateEntry::overlay)).apply(instance, OverlayMetadataSection.OverlayEntry.IntermediateEntry::new);
            });

            public String toString() {
                return this.overlay;
            }
        }
    }
}
