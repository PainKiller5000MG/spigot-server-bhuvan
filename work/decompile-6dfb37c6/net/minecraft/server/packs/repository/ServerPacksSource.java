package net.minecraft.server.packs.repository;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.jspecify.annotations.Nullable;

public class ServerPacksSource extends BuiltInPackSource {

    private static final PackMetadataSection VERSION_METADATA_SECTION = new PackMetadataSection(Component.translatable("dataPack.vanilla.description"), SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange());
    private static final FeatureFlagsMetadataSection FEATURE_FLAGS_METADATA_SECTION = new FeatureFlagsMetadataSection(FeatureFlags.DEFAULT_FLAGS);
    private static final BuiltInMetadata BUILT_IN_METADATA = BuiltInMetadata.of(PackMetadataSection.SERVER_TYPE, ServerPacksSource.VERSION_METADATA_SECTION, FeatureFlagsMetadataSection.TYPE, ServerPacksSource.FEATURE_FLAGS_METADATA_SECTION);
    private static final PackLocationInfo VANILLA_PACK_INFO = new PackLocationInfo("vanilla", Component.translatable("dataPack.vanilla.name"), PackSource.BUILT_IN, Optional.of(ServerPacksSource.CORE_PACK_INFO));
    private static final PackSelectionConfig VANILLA_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.BOTTOM, false);
    private static final PackSelectionConfig FEATURE_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
    private static final Identifier PACKS_DIR = Identifier.withDefaultNamespace("datapacks");

    public ServerPacksSource(DirectoryValidator validator) {
        super(PackType.SERVER_DATA, createVanillaPackSource(), ServerPacksSource.PACKS_DIR, validator);
    }

    private static PackLocationInfo createBuiltInPackLocation(String id, Component title) {
        return new PackLocationInfo(id, title, PackSource.FEATURE, Optional.of(KnownPack.vanilla(id)));
    }

    @VisibleForTesting
    public static VanillaPackResources createVanillaPackSource() {
        return (new VanillaPackResourcesBuilder()).setMetadata(ServerPacksSource.BUILT_IN_METADATA).exposeNamespace("minecraft").applyDevelopmentConfig().pushJarResources().build(ServerPacksSource.VANILLA_PACK_INFO);
    }

    @Override
    protected Component getPackTitle(String id) {
        return Component.literal(id);
    }

    @Override
    protected @Nullable Pack createVanillaPack(PackResources resources) {
        return Pack.readMetaAndCreate(ServerPacksSource.VANILLA_PACK_INFO, fixedResources(resources), PackType.SERVER_DATA, ServerPacksSource.VANILLA_SELECTION_CONFIG);
    }

    @Override
    protected @Nullable Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component name) {
        return Pack.readMetaAndCreate(createBuiltInPackLocation(id, name), resources, PackType.SERVER_DATA, ServerPacksSource.FEATURE_SELECTION_CONFIG);
    }

    public static PackRepository createPackRepository(Path datapackDir, DirectoryValidator validator) {
        return new PackRepository(new RepositorySource[]{new ServerPacksSource(validator), new FolderRepositorySource(datapackDir, PackType.SERVER_DATA, PackSource.WORLD, validator)});
    }

    public static PackRepository createVanillaTrustedRepository() {
        return new PackRepository(new RepositorySource[]{new ServerPacksSource(new DirectoryValidator((path) -> {
                    return true;
                }))});
    }

    public static PackRepository createPackRepository(LevelStorageSource.LevelStorageAccess levelSourceAccess) {
        return createPackRepository(levelSourceAccess.getLevelPath(LevelResource.DATAPACK_DIR), levelSourceAccess.parent().getWorldDirValidator());
    }
}
