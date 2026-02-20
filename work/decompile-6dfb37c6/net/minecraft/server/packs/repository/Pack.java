package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Pack {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    public final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;

    public static @Nullable Pack readMetaAndCreate(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType packType, PackSelectionConfig selectionConfig) {
        PackFormat packformat = SharedConstants.getCurrentVersion().packVersion(packType);
        Pack.Metadata pack_metadata = readPackMetadata(location, resources, packformat, packType);

        return pack_metadata != null ? new Pack(location, resources, pack_metadata, selectionConfig) : null;
    }

    public Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig) {
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    public static Pack.@Nullable Metadata readPackMetadata(PackLocationInfo location, Pack.ResourcesSupplier resources, PackFormat currentPackVersion, PackType type) {
        try (PackResources packresources = resources.openPrimary(location)) {
            PackMetadataSection packmetadatasection = (PackMetadataSection) packresources.getMetadataSection(PackMetadataSection.forPackType(type));

            if (packmetadatasection == null) {
                packmetadatasection = (PackMetadataSection) packresources.getMetadataSection(PackMetadataSection.FALLBACK_TYPE);
            }

            if (packmetadatasection == null) {
                Pack.LOGGER.warn("Missing metadata in pack {}", location.id());
                return null;
            } else {
                FeatureFlagsMetadataSection featureflagsmetadatasection = (FeatureFlagsMetadataSection) packresources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureflagset = featureflagsmetadatasection != null ? featureflagsmetadatasection.flags() : FeatureFlagSet.of();
                PackCompatibility packcompatibility = PackCompatibility.forVersion(packmetadatasection.supportedFormats(), currentPackVersion);
                OverlayMetadataSection overlaymetadatasection = (OverlayMetadataSection) packresources.getMetadataSection(OverlayMetadataSection.forPackType(type));
                List<String> list = overlaymetadatasection != null ? overlaymetadatasection.overlaysForVersion(currentPackVersion) : List.of();

                return new Pack.Metadata(packmetadatasection.description(), packcompatibility, featureflagset, list);
            }
        } catch (Exception exception) {
            Pack.LOGGER.warn("Failed to read pack {} metadata", location.id(), exception);
            return null;
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    public Component getChatLink(boolean enabled) {
        return this.location.createChatLink(enabled, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Pack)) {
            return false;
        } else {
            Pack pack = (Pack) o;

            return this.location.equals(pack.location);
        }
    }

    public int hashCode() {
        return this.location.hashCode();
    }

    public static record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {

    }

    public static enum Position {

        TOP, BOTTOM;

        private Position() {}

        public <T> int insert(List<T> list, T value, Function<T, PackSelectionConfig> converter, boolean reverse) {
            Pack.Position pack_position = reverse ? this.opposite() : this;

            if (pack_position == Pack.Position.BOTTOM) {
                int i;

                for (i = 0; i < list.size(); ++i) {
                    PackSelectionConfig packselectionconfig = (PackSelectionConfig) converter.apply(list.get(i));

                    if (!packselectionconfig.fixedPosition() || packselectionconfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i, value);
                return i;
            } else {
                int j;

                for (j = list.size() - 1; j >= 0; --j) {
                    PackSelectionConfig packselectionconfig1 = (PackSelectionConfig) converter.apply(list.get(j));

                    if (!packselectionconfig1.fixedPosition() || packselectionconfig1.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(j + 1, value);
                return j + 1;
            }
        }

        public Pack.Position opposite() {
            return this == Pack.Position.TOP ? Pack.Position.BOTTOM : Pack.Position.TOP;
        }
    }

    public interface ResourcesSupplier {

        PackResources openPrimary(PackLocationInfo location);

        PackResources openFull(PackLocationInfo location, Pack.Metadata metadata);
    }
}
