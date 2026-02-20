package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class BuiltInPackSource implements RepositorySource {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_ID = "vanilla";
    public static final String TESTS_ID = "tests";
    public static final KnownPack CORE_PACK_INFO = KnownPack.vanilla("core");
    private final PackType packType;
    private final VanillaPackResources vanillaPack;
    private final Identifier packDir;
    private final DirectoryValidator validator;

    public BuiltInPackSource(PackType packType, VanillaPackResources vanillaPack, Identifier packDir, DirectoryValidator validator) {
        this.packType = packType;
        this.vanillaPack = vanillaPack;
        this.packDir = packDir;
        this.validator = validator;
    }

    @Override
    public void loadPacks(Consumer<Pack> result) {
        Pack pack = this.createVanillaPack(this.vanillaPack);

        if (pack != null) {
            result.accept(pack);
        }

        this.listBundledPacks(result);
    }

    protected abstract @Nullable Pack createVanillaPack(PackResources resources);

    protected abstract Component getPackTitle(String id);

    public VanillaPackResources getVanillaPack() {
        return this.vanillaPack;
    }

    private void listBundledPacks(Consumer<Pack> packConsumer) {
        Map<String, Function<String, Pack>> map = new HashMap();

        Objects.requireNonNull(map);
        this.populatePackList(map::put);
        map.forEach((s, function) -> {
            Pack pack = (Pack) function.apply(s);

            if (pack != null) {
                packConsumer.accept(pack);
            }

        });
    }

    protected void populatePackList(BiConsumer<String, Function<String, Pack>> discoveredPacks) {
        this.vanillaPack.listRawPaths(this.packType, this.packDir, (path) -> {
            this.discoverPacksInPath(path, discoveredPacks);
        });
    }

    protected void discoverPacksInPath(@Nullable Path targetDir, BiConsumer<String, Function<String, @Nullable Pack>> discoveredPacks) {
        if (targetDir != null && Files.isDirectory(targetDir, new LinkOption[0])) {
            try {
                FolderRepositorySource.discoverPacks(targetDir, this.validator, (path1, pack_resourcessupplier) -> {
                    discoveredPacks.accept(pathToId(path1), (Function) (s) -> {
                        return this.createBuiltinPack(s, pack_resourcessupplier, this.getPackTitle(s));
                    });
                });
            } catch (IOException ioexception) {
                BuiltInPackSource.LOGGER.warn("Failed to discover packs in {}", targetDir, ioexception);
            }
        }

    }

    private static String pathToId(Path path) {
        return StringUtils.removeEnd(path.getFileName().toString(), ".zip");
    }

    protected abstract @Nullable Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component name);

    protected static Pack.ResourcesSupplier fixedResources(final PackResources instance) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return instance;
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return instance;
            }
        };
    }
}
