package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.linkfs.LinkFileSystem;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FolderRepositorySource implements RepositorySource {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PackSelectionConfig DISCOVERED_PACK_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
    private final Path folder;
    private final PackType packType;
    private final PackSource packSource;
    private final DirectoryValidator validator;

    public FolderRepositorySource(Path folder, PackType packType, PackSource packSource, DirectoryValidator validator) {
        this.folder = folder;
        this.packType = packType;
        this.packSource = packSource;
        this.validator = validator;
    }

    private static String nameFromPath(Path content) {
        return content.getFileName().toString();
    }

    @Override
    public void loadPacks(Consumer<Pack> result) {
        try {
            FileUtil.createDirectoriesSafe(this.folder);
            discoverPacks(this.folder, this.validator, (path, pack_resourcessupplier) -> {
                PackLocationInfo packlocationinfo = this.createDiscoveredFilePackInfo(path);
                Pack pack = Pack.readMetaAndCreate(packlocationinfo, pack_resourcessupplier, this.packType, FolderRepositorySource.DISCOVERED_PACK_SELECTION_CONFIG);

                if (pack != null) {
                    result.accept(pack);
                }

            });
        } catch (IOException ioexception) {
            FolderRepositorySource.LOGGER.warn("Failed to list packs in {}", this.folder, ioexception);
        }

    }

    private PackLocationInfo createDiscoveredFilePackInfo(Path content) {
        String s = nameFromPath(content);

        return new PackLocationInfo("file/" + s, Component.literal(s), this.packSource, Optional.empty());
    }

    public static void discoverPacks(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> result) throws IOException {
        FolderRepositorySource.FolderPackDetector folderrepositorysource_folderpackdetector = new FolderRepositorySource.FolderPackDetector(validator);

        try (DirectoryStream<Path> directorystream = Files.newDirectoryStream(folder)) {
            for (Path path1 : directorystream) {
                try {
                    List<ForbiddenSymlinkInfo> list = new ArrayList();
                    Pack.ResourcesSupplier pack_resourcessupplier = (Pack.ResourcesSupplier) folderrepositorysource_folderpackdetector.detectPackResources(path1, list);

                    if (!list.isEmpty()) {
                        FolderRepositorySource.LOGGER.warn("Ignoring potential pack entry: {}", ContentValidationException.getMessage(path1, list));
                    } else if (pack_resourcessupplier != null) {
                        result.accept(path1, pack_resourcessupplier);
                    } else {
                        FolderRepositorySource.LOGGER.info("Found non-pack entry '{}', ignoring", path1);
                    }
                } catch (IOException ioexception) {
                    FolderRepositorySource.LOGGER.warn("Failed to read properties of '{}', ignoring", path1, ioexception);
                }
            }
        }

    }

    private static class FolderPackDetector extends PackDetector<Pack.ResourcesSupplier> {

        protected FolderPackDetector(DirectoryValidator validator) {
            super(validator);
        }

        @Override
        protected Pack.@Nullable ResourcesSupplier createZipPack(Path content) {
            FileSystem filesystem = content.getFileSystem();

            if (filesystem != FileSystems.getDefault() && !(filesystem instanceof LinkFileSystem)) {
                FolderRepositorySource.LOGGER.info("Can't open pack archive at {}", content);
                return null;
            } else {
                return new FilePackResources.FileResourcesSupplier(content);
            }
        }

        @Override
        protected Pack.ResourcesSupplier createDirectoryPack(Path content) {
            return new PathPackResources.PathResourcesSupplier(content);
        }
    }
}
