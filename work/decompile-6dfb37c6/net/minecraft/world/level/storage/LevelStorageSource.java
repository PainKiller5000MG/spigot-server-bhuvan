package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.nbt.visitors.SkipFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.FileUtil;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import net.minecraft.world.level.validation.PathAllowList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LevelStorageSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String TAG_DATA = "Data";
    private static final PathMatcher NO_SYMLINKS_ALLOWED = (path) -> {
        return false;
    };
    public static final String ALLOWED_SYMLINKS_CONFIG_NAME = "allowed_symlinks.txt";
    private static final int DISK_SPACE_WARNING_THRESHOLD = 67108864;
    public final Path baseDir;
    private final Path backupDir;
    private final DataFixer fixerUpper;
    private final DirectoryValidator worldDirValidator;

    public LevelStorageSource(Path baseDir, Path backupDir, DirectoryValidator worldDirValidator, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;

        try {
            FileUtil.createDirectoriesSafe(baseDir);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }

        this.baseDir = baseDir;
        this.backupDir = backupDir;
        this.worldDirValidator = worldDirValidator;
    }

    public static DirectoryValidator parseValidator(Path configPath) {
        if (Files.exists(configPath, new LinkOption[0])) {
            try (BufferedReader bufferedreader = Files.newBufferedReader(configPath)) {
                return new DirectoryValidator(PathAllowList.readPlain(bufferedreader));
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to parse {}, disallowing all symbolic links", "allowed_symlinks.txt", exception);
            }
        }

        return new DirectoryValidator(LevelStorageSource.NO_SYMLINKS_ALLOWED);
    }

    public static LevelStorageSource createDefault(Path path) {
        DirectoryValidator directoryvalidator = parseValidator(path.resolve("allowed_symlinks.txt"));

        return new LevelStorageSource(path, path.resolve("../backups"), directoryvalidator, DataFixers.getDataFixer());
    }

    public static WorldDataConfiguration readDataConfig(Dynamic<?> levelData) {
        DataResult dataresult = WorldDataConfiguration.CODEC.parse(levelData);
        Logger logger = LevelStorageSource.LOGGER;

        Objects.requireNonNull(logger);
        return (WorldDataConfiguration) dataresult.resultOrPartial(logger::error).orElse(WorldDataConfiguration.DEFAULT);
    }

    public static WorldLoader.PackConfig getPackConfig(Dynamic<?> levelDataTag, PackRepository packRepository, boolean safeMode) {
        return new WorldLoader.PackConfig(packRepository, readDataConfig(levelDataTag), safeMode, false);
    }

    public static LevelDataAndDimensions getLevelDataAndDimensions(Dynamic<?> levelDataTag, WorldDataConfiguration dataConfiguration, Registry<LevelStem> datapackDimensions, HolderLookup.Provider registryAccess) {
        Dynamic<?> dynamic1 = RegistryOps.injectRegistryContext(levelDataTag, registryAccess);
        Dynamic<?> dynamic2 = dynamic1.get("WorldGenSettings").orElseEmptyMap();
        WorldGenSettings worldgensettings = (WorldGenSettings) WorldGenSettings.CODEC.parse(dynamic2).getOrThrow();
        LevelSettings levelsettings = LevelSettings.parse(dynamic1, dataConfiguration);
        WorldDimensions.Complete worlddimensions_complete = worldgensettings.dimensions().bake(datapackDimensions);
        Lifecycle lifecycle = worlddimensions_complete.lifecycle().add(registryAccess.allRegistriesLifecycle());
        PrimaryLevelData primaryleveldata = PrimaryLevelData.parse(dynamic1, levelsettings, worlddimensions_complete.specialWorldProperty(), worldgensettings.options(), lifecycle);

        return new LevelDataAndDimensions(primaryleveldata, worlddimensions_complete);
    }

    public String getName() {
        return "Anvil";
    }

    public LevelStorageSource.LevelCandidates findLevelCandidates() throws LevelStorageException {
        if (!Files.isDirectory(this.baseDir, new LinkOption[0])) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        } else {
            try (Stream<Path> stream = Files.list(this.baseDir)) {
                List<LevelStorageSource.LevelDirectory> list = stream.filter((path) -> {
                    return Files.isDirectory(path, new LinkOption[0]);
                }).map(LevelStorageSource.LevelDirectory::new).filter((levelstoragesource_leveldirectory) -> {
                    return Files.isRegularFile(levelstoragesource_leveldirectory.dataFile(), new LinkOption[0]) || Files.isRegularFile(levelstoragesource_leveldirectory.oldDataFile(), new LinkOption[0]);
                }).toList();

                return new LevelStorageSource.LevelCandidates(list);
            } catch (IOException ioexception) {
                throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
            }
        }
    }

    public CompletableFuture<List<LevelSummary>> loadLevelSummaries(LevelStorageSource.LevelCandidates candidates) {
        List<CompletableFuture<LevelSummary>> list = new ArrayList(candidates.levels.size());

        for (LevelStorageSource.LevelDirectory levelstoragesource_leveldirectory : candidates.levels) {
            list.add(CompletableFuture.supplyAsync(() -> {
                boolean flag;

                try {
                    flag = DirectoryLock.isLocked(levelstoragesource_leveldirectory.path());
                } catch (Exception exception) {
                    LevelStorageSource.LOGGER.warn("Failed to read {} lock", levelstoragesource_leveldirectory.path(), exception);
                    return null;
                }

                try {
                    return this.readLevelSummary(levelstoragesource_leveldirectory, flag);
                } catch (OutOfMemoryError outofmemoryerror) {
                    MemoryReserve.release();
                    String s = "Ran out of memory trying to read summary of world folder \"" + levelstoragesource_leveldirectory.directoryName() + "\"";

                    LevelStorageSource.LOGGER.error(LogUtils.FATAL_MARKER, s);
                    OutOfMemoryError outofmemoryerror1 = new OutOfMemoryError("Ran out of memory reading level data");

                    outofmemoryerror1.initCause(outofmemoryerror);
                    CrashReport crashreport = CrashReport.forThrowable(outofmemoryerror1, s);
                    CrashReportCategory crashreportcategory = crashreport.addCategory("World details");

                    crashreportcategory.setDetail("Folder Name", levelstoragesource_leveldirectory.directoryName());

                    try {
                        long i = Files.size(levelstoragesource_leveldirectory.dataFile());

                        crashreportcategory.setDetail("level.dat size", i);
                    } catch (IOException ioexception) {
                        crashreportcategory.setDetailError("level.dat size", ioexception);
                    }

                    throw new ReportedException(crashreport);
                }
            }, Util.backgroundExecutor().forName("loadLevelSummaries")));
        }

        return Util.sequenceFailFastAndCancel(list).thenApply((list1) -> {
            return list1.stream().filter(Objects::nonNull).sorted().toList();
        });
    }

    private int getStorageVersion() {
        return 19133;
    }

    private static CompoundTag readLevelDataTagRaw(Path dataFile) throws IOException {
        return NbtIo.readCompressed(dataFile, NbtAccounter.uncompressedQuota());
    }

    private static Dynamic<?> readLevelDataTagFixed(Path dataFile, DataFixer dataFixer) throws IOException {
        CompoundTag compoundtag = readLevelDataTagRaw(dataFile);
        CompoundTag compoundtag1 = compoundtag.getCompoundOrEmpty("Data");
        int i = NbtUtils.getDataVersion(compoundtag1);
        Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(dataFixer, new Dynamic(NbtOps.INSTANCE, compoundtag1), i);

        dynamic = dynamic.update("Player", (dynamic1) -> {
            return DataFixTypes.PLAYER.updateToCurrentVersion(dataFixer, dynamic1, i);
        });
        dynamic = dynamic.update("WorldGenSettings", (dynamic1) -> {
            return DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(dataFixer, dynamic1, i);
        });
        return dynamic;
    }

    private LevelSummary readLevelSummary(LevelStorageSource.LevelDirectory level, boolean locked) {
        Path path = level.dataFile();

        if (Files.exists(path, new LinkOption[0])) {
            try {
                if (Files.isSymbolicLink(path)) {
                    List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateSymlink(path);

                    if (!list.isEmpty()) {
                        LevelStorageSource.LOGGER.warn("{}", ContentValidationException.getMessage(path, list));
                        return new LevelSummary.SymlinkLevelSummary(level.directoryName(), level.iconFile());
                    }
                }

                Tag tag = readLightweightData(path);

                if (tag instanceof CompoundTag) {
                    CompoundTag compoundtag = (CompoundTag) tag;
                    CompoundTag compoundtag1 = compoundtag.getCompoundOrEmpty("Data");
                    int i = NbtUtils.getDataVersion(compoundtag1);
                    Dynamic<?> dynamic = DataFixTypes.LEVEL_SUMMARY.updateToCurrentVersion(this.fixerUpper, new Dynamic(NbtOps.INSTANCE, compoundtag1), i);

                    return this.makeLevelSummary(dynamic, level, locked);
                }

                LevelStorageSource.LOGGER.warn("Invalid root tag in {}", path);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Exception reading {}", path, exception);
            }
        }

        return new LevelSummary.CorruptedLevelSummary(level.directoryName(), level.iconFile(), getFileModificationTime(level));
    }

    private static long getFileModificationTime(LevelStorageSource.LevelDirectory level) {
        Instant instant = getFileModificationTime(level.dataFile());

        if (instant == null) {
            instant = getFileModificationTime(level.oldDataFile());
        }

        return instant == null ? -1L : instant.toEpochMilli();
    }

    private static @Nullable Instant getFileModificationTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ioexception) {
            return null;
        }
    }

    private LevelSummary makeLevelSummary(Dynamic<?> dataTag, LevelStorageSource.LevelDirectory levelDirectory, boolean locked) {
        LevelVersion levelversion = LevelVersion.parse(dataTag);
        int i = levelversion.levelDataVersion();

        if (i != 19132 && i != 19133) {
            throw new NbtFormatException("Unknown data version: " + Integer.toHexString(i));
        } else {
            boolean flag1 = i != this.getStorageVersion();
            Path path = levelDirectory.iconFile();
            WorldDataConfiguration worlddataconfiguration = readDataConfig(dataTag);
            LevelSettings levelsettings = LevelSettings.parse(dataTag, worlddataconfiguration);
            FeatureFlagSet featureflagset = parseFeatureFlagsFromSummary(dataTag);
            boolean flag2 = FeatureFlags.isExperimental(featureflagset);

            return new LevelSummary(levelsettings, levelversion, levelDirectory.directoryName(), flag1, locked, flag2, path);
        }
    }

    private static FeatureFlagSet parseFeatureFlagsFromSummary(Dynamic<?> tag) {
        Set<Identifier> set = (Set) tag.get("enabled_features").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().map(Identifier::tryParse).stream();
        }).collect(Collectors.toSet());

        return FeatureFlags.REGISTRY.fromNames(set, (identifier) -> {
        });
    }

    private static @Nullable Tag readLightweightData(Path dataFile) throws IOException {
        SkipFields skipfields = new SkipFields(new FieldSelector[]{new FieldSelector("Data", CompoundTag.TYPE, "Player"), new FieldSelector("Data", CompoundTag.TYPE, "WorldGenSettings")});

        NbtIo.parseCompressed(dataFile, skipfields, NbtAccounter.uncompressedQuota());
        return skipfields.getResult();
    }

    public boolean isNewLevelIdAcceptable(String levelId) {
        try {
            Path path = this.getLevelPath(levelId);

            Files.createDirectory(path);
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ioexception) {
            return false;
        }
    }

    public boolean levelExists(String levelId) {
        try {
            return Files.isDirectory(this.getLevelPath(levelId), new LinkOption[0]);
        } catch (InvalidPathException invalidpathexception) {
            return false;
        }
    }

    public Path getLevelPath(String levelId) {
        return this.baseDir.resolve(levelId);
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public Path getBackupPath() {
        return this.backupDir;
    }

    public LevelStorageSource.LevelStorageAccess validateAndCreateAccess(String levelId) throws IOException, ContentValidationException {
        Path path = this.getLevelPath(levelId);
        List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateDirectory(path, true);

        if (!list.isEmpty()) {
            throw new ContentValidationException(path, list);
        } else {
            return new LevelStorageSource.LevelStorageAccess(levelId, path);
        }
    }

    public LevelStorageSource.LevelStorageAccess createAccess(String levelId) throws IOException {
        Path path = this.getLevelPath(levelId);

        return new LevelStorageSource.LevelStorageAccess(levelId, path);
    }

    public DirectoryValidator getWorldDirValidator() {
        return this.worldDirValidator;
    }

    public class LevelStorageAccess implements AutoCloseable {

        private final DirectoryLock lock;
        public final LevelStorageSource.LevelDirectory levelDirectory;
        private final String levelId;
        private final Map<LevelResource, Path> resources = Maps.newHashMap();

        private LevelStorageAccess(String levelId, Path path) throws IOException {
            this.levelId = levelId;
            this.levelDirectory = new LevelStorageSource.LevelDirectory(path);
            this.lock = DirectoryLock.create(path);
        }

        public long estimateDiskSpace() {
            try {
                return Files.getFileStore(this.levelDirectory.path).getUsableSpace();
            } catch (Exception exception) {
                return Long.MAX_VALUE;
            }
        }

        public boolean checkForLowDiskSpace() {
            return this.estimateDiskSpace() < 67108864L;
        }

        public void safeClose() {
            try {
                this.close();
            } catch (IOException ioexception) {
                LevelStorageSource.LOGGER.warn("Failed to unlock access to level {}", this.getLevelId(), ioexception);
            }

        }

        public LevelStorageSource parent() {
            return LevelStorageSource.this;
        }

        public LevelStorageSource.LevelDirectory getLevelDirectory() {
            return this.levelDirectory;
        }

        public String getLevelId() {
            return this.levelId;
        }

        public Path getLevelPath(LevelResource resource) {
            Map map = this.resources;
            LevelStorageSource.LevelDirectory levelstoragesource_leveldirectory = this.levelDirectory;

            Objects.requireNonNull(this.levelDirectory);
            return (Path) map.computeIfAbsent(resource, levelstoragesource_leveldirectory::resourcePath);
        }

        public Path getDimensionPath(ResourceKey<Level> name) {
            return DimensionType.getStorageFolder(name, this.levelDirectory.path());
        }

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        public LevelSummary getSummary(Dynamic<?> dataTag) {
            this.checkLock();
            return LevelStorageSource.this.makeLevelSummary(dataTag, this.levelDirectory, false);
        }

        public Dynamic<?> getDataTag() throws IOException {
            return this.getDataTag(false);
        }

        public Dynamic<?> getDataTagFallback() throws IOException {
            return this.getDataTag(true);
        }

        private Dynamic<?> getDataTag(boolean useFallback) throws IOException {
            this.checkLock();
            return LevelStorageSource.readLevelDataTagFixed(useFallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile(), LevelStorageSource.this.fixerUpper);
        }

        public void saveDataTag(RegistryAccess registryAccess, WorldData levelData) {
            this.saveDataTag(registryAccess, levelData, (CompoundTag) null);
        }

        public void saveDataTag(RegistryAccess registryAccess, WorldData levelData, @Nullable CompoundTag playerData) {
            CompoundTag compoundtag1 = levelData.createTag(registryAccess, playerData);
            CompoundTag compoundtag2 = new CompoundTag();

            compoundtag2.put("Data", compoundtag1);
            this.saveLevelData(compoundtag2);
        }

        private void saveLevelData(CompoundTag root) {
            Path path = this.levelDirectory.path();

            try {
                Path path1 = Files.createTempFile(path, "level", ".dat");

                NbtIo.writeCompressed(root, path1);
                Path path2 = this.levelDirectory.oldDataFile();
                Path path3 = this.levelDirectory.dataFile();

                Util.safeReplaceFile(path3, path1, path2);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", path, exception);
            }

        }

        public Optional<Path> getIconFile() {
            return !this.lock.isValid() ? Optional.empty() : Optional.of(this.levelDirectory.iconFile());
        }

        public void deleteLevel() throws IOException {
            this.checkLock();
            final Path path = this.levelDirectory.lockFile();

            LevelStorageSource.LOGGER.info("Deleting level {}", this.levelId);

            for (int i = 1; i <= 5; ++i) {
                LevelStorageSource.LOGGER.info("Attempt {}...", i);

                try {
                    Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (!file.equals(path)) {
                                LevelStorageSource.LOGGER.debug("Deleting {}", file);
                                Files.delete(file);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
                            if (exc != null) {
                                throw exc;
                            } else {
                                if (dir.equals(LevelStorageAccess.this.levelDirectory.path())) {
                                    LevelStorageAccess.this.lock.close();
                                    Files.deleteIfExists(path);
                                }

                                Files.delete(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    });
                    break;
                } catch (IOException ioexception) {
                    if (i >= 5) {
                        throw ioexception;
                    }

                    LevelStorageSource.LOGGER.warn("Failed to delete {}", this.levelDirectory.path(), ioexception);

                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException interruptedexception) {
                        ;
                    }
                }
            }

        }

        public void renameLevel(String newName) throws IOException {
            this.modifyLevelDataWithoutDatafix((compoundtag) -> {
                compoundtag.putString("LevelName", newName.trim());
            });
        }

        public void renameAndDropPlayer(String newName) throws IOException {
            this.modifyLevelDataWithoutDatafix((compoundtag) -> {
                compoundtag.putString("LevelName", newName.trim());
                compoundtag.remove("Player");
            });
        }

        private void modifyLevelDataWithoutDatafix(Consumer<CompoundTag> updater) throws IOException {
            this.checkLock();
            CompoundTag compoundtag = LevelStorageSource.readLevelDataTagRaw(this.levelDirectory.dataFile());

            updater.accept(compoundtag.getCompoundOrEmpty("Data"));
            this.saveLevelData(compoundtag);
        }

        public long makeWorldBackup() throws IOException {
            this.checkLock();
            String s = FileNameDateFormatter.FORMATTER.format(ZonedDateTime.now());
            String s1 = s + "_" + this.levelId;
            Path path = LevelStorageSource.this.getBackupPath();

            try {
                FileUtil.createDirectoriesSafe(path);
            } catch (IOException ioexception) {
                throw new RuntimeException(ioexception);
            }

            Path path1 = path.resolve(FileUtil.findAvailableName(path, s1, ".zip"));

            try (final ZipOutputStream zipoutputstream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path1)))) {
                final Path path2 = Paths.get(this.levelId);

                Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        if (path.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            String s2 = path2.resolve(LevelStorageAccess.this.levelDirectory.path().relativize(path)).toString().replace('\\', '/');
                            ZipEntry zipentry = new ZipEntry(s2);

                            zipoutputstream.putNextEntry(zipentry);
                            com.google.common.io.Files.asByteSource(path.toFile()).copyTo(zipoutputstream);
                            zipoutputstream.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                });
            }

            return Files.size(path1);
        }

        public boolean hasWorldData() {
            return Files.exists(this.levelDirectory.dataFile(), new LinkOption[0]) || Files.exists(this.levelDirectory.oldDataFile(), new LinkOption[0]);
        }

        public void close() throws IOException {
            this.lock.close();
        }

        public boolean restoreLevelDataFromOld() {
            return Util.safeReplaceOrMoveFile(this.levelDirectory.dataFile(), this.levelDirectory.oldDataFile(), this.levelDirectory.corruptedDataFile(ZonedDateTime.now()), true);
        }

        public @Nullable Instant getFileModificationTime(boolean fallback) {
            return LevelStorageSource.getFileModificationTime(fallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile());
        }
    }

    public static record LevelCandidates(List<LevelStorageSource.LevelDirectory> levels) implements Iterable<LevelStorageSource.LevelDirectory> {

        public boolean isEmpty() {
            return this.levels.isEmpty();
        }

        public Iterator<LevelStorageSource.LevelDirectory> iterator() {
            return this.levels.iterator();
        }
    }

    public static record LevelDirectory(Path path) {

        public String directoryName() {
            return this.path.getFileName().toString();
        }

        public Path dataFile() {
            return this.resourcePath(LevelResource.LEVEL_DATA_FILE);
        }

        public Path oldDataFile() {
            return this.resourcePath(LevelResource.OLD_LEVEL_DATA_FILE);
        }

        public Path corruptedDataFile(ZonedDateTime time) {
            Path path = this.path;
            String s = LevelResource.LEVEL_DATA_FILE.getId();

            return path.resolve(s + "_corrupted_" + time.format(FileNameDateFormatter.FORMATTER));
        }

        public Path rawDataFile(ZonedDateTime time) {
            Path path = this.path;
            String s = LevelResource.LEVEL_DATA_FILE.getId();

            return path.resolve(s + "_raw_" + time.format(FileNameDateFormatter.FORMATTER));
        }

        public Path iconFile() {
            return this.resourcePath(LevelResource.ICON_FILE);
        }

        public Path lockFile() {
            return this.resourcePath(LevelResource.LOCK_FILE);
        }

        public Path resourcePath(LevelResource resource) {
            return this.path.resolve(resource.getId());
        }
    }
}
