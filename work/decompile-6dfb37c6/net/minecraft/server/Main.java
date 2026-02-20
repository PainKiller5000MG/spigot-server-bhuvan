package net.minecraft.server;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import net.minecraft.CrashReport;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Main {

    private static final Logger LOGGER = LogUtils.getLogger();

    public Main() {}

    @SuppressForbidden(reason = "System.out needed before bootstrap")
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        OptionParser optionparser = new OptionParser();
        OptionSpec<Void> optionspec = optionparser.accepts("nogui");
        OptionSpec<Void> optionspec1 = optionparser.accepts("initSettings", "Initializes 'server.properties' and 'eula.txt', then quits");
        OptionSpec<Void> optionspec2 = optionparser.accepts("demo");
        OptionSpec<Void> optionspec3 = optionparser.accepts("bonusChest");
        OptionSpec<Void> optionspec4 = optionparser.accepts("forceUpgrade");
        OptionSpec<Void> optionspec5 = optionparser.accepts("eraseCache");
        OptionSpec<Void> optionspec6 = optionparser.accepts("recreateRegionFiles");
        OptionSpec<Void> optionspec7 = optionparser.accepts("safeMode", "Loads level with vanilla datapack only");
        OptionSpec<Void> optionspec8 = optionparser.accepts("help").forHelp();
        OptionSpec<String> optionspec9 = optionparser.accepts("universe").withRequiredArg().defaultsTo(".", new String[0]);
        OptionSpec<String> optionspec10 = optionparser.accepts("world").withRequiredArg();
        OptionSpec<Integer> optionspec11 = optionparser.accepts("port").withRequiredArg().ofType(Integer.class).defaultsTo(-1, new Integer[0]);
        OptionSpec<String> optionspec12 = optionparser.accepts("serverId").withRequiredArg();
        OptionSpec<Void> optionspec13 = optionparser.accepts("jfrProfile");
        OptionSpec<Path> optionspec14 = optionparser.accepts("pidFile").withRequiredArg().withValuesConvertedBy(new PathConverter(new PathProperties[0]));
        OptionSpec<String> optionspec15 = optionparser.nonOptions();

        try {
            OptionSet optionset = optionparser.parse(args);

            if (optionset.has(optionspec8)) {
                optionparser.printHelpOn(System.err);
                return;
            }

            Path path = (Path) optionset.valueOf(optionspec14);

            if (path != null) {
                writePidFile(path);
            }

            CrashReport.preload();
            if (optionset.has(optionspec13)) {
                JvmProfiler.INSTANCE.start(Environment.SERVER);
            }

            Bootstrap.bootStrap();
            Bootstrap.validate();
            Util.startTimerHackThread();
            Path path1 = Paths.get("server.properties");
            DedicatedServerSettings dedicatedserversettings = new DedicatedServerSettings(path1);

            dedicatedserversettings.forceSave();
            RegionFileVersion.configure(dedicatedserversettings.getProperties().regionFileComression);
            Path path2 = Paths.get("eula.txt");
            Eula eula = new Eula(path2);

            if (optionset.has(optionspec1)) {
                Main.LOGGER.info("Initialized '{}' and '{}'", path1.toAbsolutePath(), path2.toAbsolutePath());
                return;
            }

            if (!eula.hasAgreedToEULA()) {
                Main.LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                return;
            }

            File file = new File((String) optionset.valueOf(optionspec9));
            Services services = Services.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY), file);
            String s = (String) Optional.ofNullable((String) optionset.valueOf(optionspec10)).orElse(dedicatedserversettings.getProperties().levelName);
            LevelStorageSource levelstoragesource = LevelStorageSource.createDefault(file.toPath());
            LevelStorageSource.LevelStorageAccess levelstoragesource_levelstorageaccess = levelstoragesource.validateAndCreateAccess(s);
            Dynamic<?> dynamic;

            if (levelstoragesource_levelstorageaccess.hasWorldData()) {
                LevelSummary levelsummary;

                try {
                    dynamic = levelstoragesource_levelstorageaccess.getDataTag();
                    levelsummary = levelstoragesource_levelstorageaccess.getSummary(dynamic);
                } catch (NbtException | ReportedNbtException | IOException ioexception) {
                    LevelStorageSource.LevelDirectory levelstoragesource_leveldirectory = levelstoragesource_levelstorageaccess.getLevelDirectory();

                    Main.LOGGER.warn("Failed to load world data from {}", levelstoragesource_leveldirectory.dataFile(), ioexception);
                    Main.LOGGER.info("Attempting to use fallback");

                    try {
                        dynamic = levelstoragesource_levelstorageaccess.getDataTagFallback();
                        levelsummary = levelstoragesource_levelstorageaccess.getSummary(dynamic);
                    } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                        Main.LOGGER.error("Failed to load world data from {}", levelstoragesource_leveldirectory.oldDataFile(), ioexception1);
                        Main.LOGGER.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", levelstoragesource_leveldirectory.dataFile(), levelstoragesource_leveldirectory.oldDataFile());
                        return;
                    }

                    levelstoragesource_levelstorageaccess.restoreLevelDataFromOld();
                }

                if (levelsummary.requiresManualConversion()) {
                    Main.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                    return;
                }

                if (!levelsummary.isCompatible()) {
                    Main.LOGGER.info("This world was created by an incompatible version.");
                    return;
                }
            } else {
                dynamic = null;
            }

            Dynamic<?> dynamic1 = dynamic;
            boolean flag = optionset.has(optionspec7);

            if (flag) {
                Main.LOGGER.warn("Safe mode active, only vanilla datapack will be loaded");
            }

            PackRepository packrepository = ServerPacksSource.createPackRepository(levelstoragesource_levelstorageaccess);

            WorldStem worldstem;

            try {
                WorldLoader.InitConfig worldloader_initconfig = loadOrCreateConfig(dedicatedserversettings.getProperties(), dynamic1, flag, packrepository);

                worldstem = (WorldStem) Util.blockUntilDone((executor) -> {
                    return WorldLoader.load(worldloader_initconfig, (worldloader_dataloadcontext) -> {
                        Registry<LevelStem> registry = worldloader_dataloadcontext.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM);

                        if (dynamic1 != null) {
                            LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(dynamic1, worldloader_dataloadcontext.dataConfiguration(), registry, worldloader_dataloadcontext.datapackWorldgen());

                            return new WorldLoader.DataLoadOutput(leveldataanddimensions.worldData(), leveldataanddimensions.dimensions().dimensionsRegistryAccess());
                        } else {
                            Main.LOGGER.info("No existing world data, creating new world");
                            return createNewWorldData(dedicatedserversettings, worldloader_dataloadcontext, registry, optionset.has(optionspec2), optionset.has(optionspec3));
                        }
                    }, WorldStem::new, Util.backgroundExecutor(), executor);
                }).get();
            } catch (Exception exception) {
                Main.LOGGER.warn("Failed to load datapacks, can't proceed with server load. You can either fix your datapacks or reset to vanilla with --safeMode", exception);
                return;
            }

            RegistryAccess.Frozen registryaccess_frozen = worldstem.registries().compositeAccess();
            WorldData worlddata = worldstem.worldData();
            boolean flag1 = optionset.has(optionspec6);

            if (optionset.has(optionspec4) || flag1) {
                forceUpgrade(levelstoragesource_levelstorageaccess, worlddata, DataFixers.getDataFixer(), optionset.has(optionspec5), () -> {
                    return true;
                }, registryaccess_frozen, flag1);
            }

            levelstoragesource_levelstorageaccess.saveDataTag(registryaccess_frozen, worlddata);
            final DedicatedServer dedicatedserver = (DedicatedServer) MinecraftServer.spin((thread) -> {
                DedicatedServer dedicatedserver1 = new DedicatedServer(thread, levelstoragesource_levelstorageaccess, packrepository, worldstem, dedicatedserversettings, DataFixers.getDataFixer(), services);

                dedicatedserver1.setPort((Integer) optionset.valueOf(optionspec11));
                dedicatedserver1.setDemo(optionset.has(optionspec2));
                dedicatedserver1.setId((String) optionset.valueOf(optionspec12));
                boolean flag2 = !optionset.has(optionspec) && !optionset.valuesOf(optionspec15).contains("nogui");

                if (flag2 && !GraphicsEnvironment.isHeadless()) {
                    dedicatedserver1.showGui();
                }

                return dedicatedserver1;
            });
            Thread thread = new Thread("Server Shutdown Thread") {
                public void run() {
                    dedicatedserver.halt(true);
                }
            };

            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(Main.LOGGER));
            Runtime.getRuntime().addShutdownHook(thread);
        } catch (Throwable throwable) {
            Main.LOGGER.error(LogUtils.FATAL_MARKER, "Failed to start the minecraft server", throwable);
        }

    }

    private static WorldLoader.DataLoadOutput<WorldData> createNewWorldData(DedicatedServerSettings settings, WorldLoader.DataLoadContext context, Registry<LevelStem> datapackDimensions, boolean demoMode, boolean bonusChest) {
        LevelSettings levelsettings;
        WorldOptions worldoptions;
        WorldDimensions worlddimensions;

        if (demoMode) {
            levelsettings = MinecraftServer.DEMO_SETTINGS;
            worldoptions = WorldOptions.DEMO_OPTIONS;
            worlddimensions = WorldPresets.createNormalWorldDimensions(context.datapackWorldgen());
        } else {
            DedicatedServerProperties dedicatedserverproperties = settings.getProperties();

            levelsettings = new LevelSettings(dedicatedserverproperties.levelName, dedicatedserverproperties.gameMode.get(), dedicatedserverproperties.hardcore, dedicatedserverproperties.difficulty.get(), false, new GameRules(context.dataConfiguration().enabledFeatures()), context.dataConfiguration());
            worldoptions = bonusChest ? dedicatedserverproperties.worldOptions.withBonusChest(true) : dedicatedserverproperties.worldOptions;
            worlddimensions = dedicatedserverproperties.createDimensions(context.datapackWorldgen());
        }

        WorldDimensions.Complete worlddimensions_complete = worlddimensions.bake(datapackDimensions);
        Lifecycle lifecycle = worlddimensions_complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());

        return new WorldLoader.DataLoadOutput<WorldData>(new PrimaryLevelData(levelsettings, worldoptions, worlddimensions_complete.specialWorldProperty(), lifecycle), worlddimensions_complete.dimensionsRegistryAccess());
    }

    private static void writePidFile(Path path) {
        try {
            long i = ProcessHandle.current().pid();

            Files.writeString(path, Long.toString(i));
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }
    }

    private static WorldLoader.InitConfig loadOrCreateConfig(DedicatedServerProperties properties, @Nullable Dynamic<?> levelDataTag, boolean safeModeEnabled, PackRepository packRepository) {
        boolean flag1;
        WorldDataConfiguration worlddataconfiguration;

        if (levelDataTag != null) {
            WorldDataConfiguration worlddataconfiguration1 = LevelStorageSource.readDataConfig(levelDataTag);

            flag1 = false;
            worlddataconfiguration = worlddataconfiguration1;
        } else {
            flag1 = true;
            worlddataconfiguration = new WorldDataConfiguration(properties.initialDataPackConfiguration, FeatureFlags.DEFAULT_FLAGS);
        }

        WorldLoader.PackConfig worldloader_packconfig = new WorldLoader.PackConfig(packRepository, worlddataconfiguration, safeModeEnabled, flag1);

        return new WorldLoader.InitConfig(worldloader_packconfig, Commands.CommandSelection.DEDICATED, properties.functionPermissions);
    }

    public static void forceUpgrade(LevelStorageSource.LevelStorageAccess storageSource, WorldData worldData, DataFixer fixerUpper, boolean eraseCache, BooleanSupplier isRunning, RegistryAccess registryAccess, boolean recreateRegionFiles) {
        Main.LOGGER.info("Forcing world upgrade!");

        try (WorldUpgrader worldupgrader = new WorldUpgrader(storageSource, fixerUpper, worldData, registryAccess, eraseCache, recreateRegionFiles)) {
            Component component = null;

            while (!worldupgrader.isFinished()) {
                Component component1 = worldupgrader.getStatus();

                if (component != component1) {
                    component = component1;
                    Main.LOGGER.info(worldupgrader.getStatus().getString());
                }

                int i = worldupgrader.getTotalChunks();

                if (i > 0) {
                    int j = worldupgrader.getConverted() + worldupgrader.getSkipped();

                    Main.LOGGER.info("{}% completed ({} / {} chunks)...", new Object[]{Mth.floor((float) j / (float) i * 100.0F), j, i});
                }

                if (!isRunning.getAsBoolean()) {
                    worldupgrader.cancel();
                } else {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException interruptedexception) {
                        ;
                    }
                }
            }
        }

    }
}
