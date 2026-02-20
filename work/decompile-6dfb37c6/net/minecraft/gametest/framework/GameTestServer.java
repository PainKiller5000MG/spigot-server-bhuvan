package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.SystemReport;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceSelectorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoCollector;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GameTestServer extends MinecraftServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_REPORT_INTERVAL = 20;
    private static final int TEST_POSITION_RANGE = 14999992;
    private static final Services NO_SERVICES = new Services((MinecraftSessionService) null, ServicesKeySet.EMPTY, (GameProfileRepository) null, new GameTestServer.MockUserNameToIdResolver(), new GameTestServer.MockProfileResolver());
    private static final FeatureFlagSet ENABLED_FEATURES = FeatureFlags.REGISTRY.allFlags().subtract(FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS, FeatureFlags.MINECART_IMPROVEMENTS));
    private final LocalSampleLogger sampleLogger = new LocalSampleLogger(4);
    private final Optional<String> testSelection;
    private final boolean verify;
    private List<GameTestBatch> testBatches = new ArrayList();
    private final Stopwatch stopwatch = Stopwatch.createUnstarted();
    private static final WorldOptions WORLD_OPTIONS = new WorldOptions(0L, false, false);
    private @Nullable MultipleTestTracker testTracker;

    public static GameTestServer create(Thread serverThread, LevelStorageSource.LevelStorageAccess levelStorageSource, PackRepository packRepository, Optional<String> testSelection, boolean verify) {
        packRepository.reload();
        ArrayList<String> arraylist = new ArrayList(packRepository.getAvailableIds());

        arraylist.remove("vanilla");
        arraylist.addFirst("vanilla");
        WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(new DataPackConfig(arraylist, List.of()), GameTestServer.ENABLED_FEATURES);
        LevelSettings levelsettings = new LevelSettings("Test Level", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(GameTestServer.ENABLED_FEATURES), worlddataconfiguration);
        WorldLoader.PackConfig worldloader_packconfig = new WorldLoader.PackConfig(packRepository, worlddataconfiguration, false, true);
        WorldLoader.InitConfig worldloader_initconfig = new WorldLoader.InitConfig(worldloader_packconfig, Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.OWNER);

        try {
            GameTestServer.LOGGER.debug("Starting resource loading");
            Stopwatch stopwatch = Stopwatch.createStarted();
            WorldStem worldstem = (WorldStem) Util.blockUntilDone((executor) -> {
                return WorldLoader.load(worldloader_initconfig, (worldloader_dataloadcontext) -> {
                    Registry<LevelStem> registry = (new MappedRegistry(Registries.LEVEL_STEM, Lifecycle.stable())).freeze();
                    WorldDimensions.Complete worlddimensions_complete = ((WorldPreset) worldloader_dataloadcontext.datapackWorldgen().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()).createWorldDimensions().bake(registry);

                    return new WorldLoader.DataLoadOutput(new PrimaryLevelData(levelsettings, GameTestServer.WORLD_OPTIONS, worlddimensions_complete.specialWorldProperty(), worlddimensions_complete.lifecycle()), worlddimensions_complete.dimensionsRegistryAccess());
                }, WorldStem::new, Util.backgroundExecutor(), executor);
            }).get();

            stopwatch.stop();
            GameTestServer.LOGGER.debug("Finished resource loading after {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            return new GameTestServer(serverThread, levelStorageSource, packRepository, worldstem, testSelection, verify);
        } catch (Exception exception) {
            GameTestServer.LOGGER.warn("Failed to load vanilla datapack, bit oops", exception);
            System.exit(-1);
            throw new IllegalStateException();
        }
    }

    private GameTestServer(Thread serverThread, LevelStorageSource.LevelStorageAccess levelStorageSource, PackRepository packRepository, WorldStem worldStem, Optional<String> testSelection, boolean verify) {
        super(serverThread, levelStorageSource, packRepository, worldStem, Proxy.NO_PROXY, DataFixers.getDataFixer(), GameTestServer.NO_SERVICES, LoggingLevelLoadListener.forDedicatedServer());
        this.testSelection = testSelection;
        this.verify = verify;
    }

    @Override
    protected boolean initServer() {
        this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, new EmptyNotificationService()) {
        });
        Gizmos.withCollector(GizmoCollector.NOOP);
        this.loadLevel();
        ServerLevel serverlevel = this.overworld();

        this.testBatches = this.evaluateTestsToRun(serverlevel);
        GameTestServer.LOGGER.info("Started game test server");
        return true;
    }

    private List<GameTestBatch> evaluateTestsToRun(ServerLevel level) {
        Registry<GameTestInstance> registry = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
        Collection<Holder.Reference<GameTestInstance>> collection;
        GameTestBatchFactory.TestDecorator gametestbatchfactory_testdecorator;

        if (this.testSelection.isPresent()) {
            collection = getTestsForSelection(level.registryAccess(), (String) this.testSelection.get()).filter((holder_reference) -> {
                return !((GameTestInstance) holder_reference.value()).manualOnly();
            }).toList();
            if (this.verify) {
                gametestbatchfactory_testdecorator = GameTestServer::rotateAndMultiply;
                GameTestServer.LOGGER.info("Verify requested. Will run each test that matches {} {} times", this.testSelection.get(), 100 * Rotation.values().length);
            } else {
                gametestbatchfactory_testdecorator = GameTestBatchFactory.DIRECT;
                GameTestServer.LOGGER.info("Will run tests matching {} ({} tests)", this.testSelection.get(), collection.size());
            }
        } else {
            collection = registry.listElements().filter((holder_reference) -> {
                return !((GameTestInstance) holder_reference.value()).manualOnly();
            }).toList();
            gametestbatchfactory_testdecorator = GameTestBatchFactory.DIRECT;
        }

        return GameTestBatchFactory.divideIntoBatches(collection, gametestbatchfactory_testdecorator, level);
    }

    private static Stream<GameTestInfo> rotateAndMultiply(Holder.Reference<GameTestInstance> test, ServerLevel level) {
        Stream.Builder<GameTestInfo> stream_builder = Stream.builder();

        for (Rotation rotation : Rotation.values()) {
            for (int i = 0; i < 100; ++i) {
                stream_builder.add(new GameTestInfo(test, rotation, level, RetryOptions.noRetries()));
            }
        }

        return stream_builder.build();
    }

    public static Stream<Holder.Reference<GameTestInstance>> getTestsForSelection(RegistryAccess registries, String selection) {
        return ResourceSelectorArgument.parse(new StringReader(selection), registries.lookupOrThrow(Registries.TEST_INSTANCE)).stream();
    }

    @Override
    protected void tickServer(BooleanSupplier haveTime) {
        super.tickServer(haveTime);
        ServerLevel serverlevel = this.overworld();

        if (!this.haveTestsStarted()) {
            this.startTests(serverlevel);
        }

        if (serverlevel.getGameTime() % 20L == 0L) {
            GameTestServer.LOGGER.info(this.testTracker.getProgressBar());
        }

        if (this.testTracker.isDone()) {
            this.halt(false);
            GameTestServer.LOGGER.info(this.testTracker.getProgressBar());
            GlobalTestReporter.finish();
            GameTestServer.LOGGER.info("========= {} GAME TESTS COMPLETE IN {} ======================", this.testTracker.getTotalCount(), this.stopwatch.stop());
            if (this.testTracker.hasFailedRequired()) {
                GameTestServer.LOGGER.info("{} required tests failed :(", this.testTracker.getFailedRequiredCount());
                this.testTracker.getFailedRequired().forEach(GameTestServer::logFailedTest);
            } else {
                GameTestServer.LOGGER.info("All {} required tests passed :)", this.testTracker.getTotalCount());
            }

            if (this.testTracker.hasFailedOptional()) {
                GameTestServer.LOGGER.info("{} optional tests failed", this.testTracker.getFailedOptionalCount());
                this.testTracker.getFailedOptional().forEach(GameTestServer::logFailedTest);
            }

            GameTestServer.LOGGER.info("====================================================");
        }

    }

    private static void logFailedTest(GameTestInfo testInfo) {
        if (testInfo.getRotation() != Rotation.NONE) {
            GameTestServer.LOGGER.info("   - {} with rotation {}: {}", new Object[]{testInfo.id(), testInfo.getRotation().getSerializedName(), testInfo.getError().getDescription().getString()});
        } else {
            GameTestServer.LOGGER.info("   - {}: {}", testInfo.id(), testInfo.getError().getDescription().getString());
        }

    }

    @Override
    protected SampleLogger getTickTimeLogger() {
        return this.sampleLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return false;
    }

    @Override
    protected void waitUntilNextTick() {
        this.runAllTasks();
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Type", "Game test server");
        return systemReport;
    }

    @Override
    protected void onServerExit() {
        super.onServerExit();
        GameTestServer.LOGGER.info("Game test server shutting down");
        System.exit(this.testTracker != null ? this.testTracker.getFailedRequiredCount() : -1);
    }

    @Override
    protected void onServerCrash(CrashReport report) {
        super.onServerCrash(report);
        GameTestServer.LOGGER.error("Game test server crashed\n{}", report.getFriendlyReport(ReportType.CRASH));
        System.exit(1);
    }

    private void startTests(ServerLevel level) {
        BlockPos blockpos = new BlockPos(level.random.nextIntBetweenInclusive(-14999992, 14999992), -59, level.random.nextIntBetweenInclusive(-14999992, 14999992));

        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), blockpos, 0.0F, 0.0F));
        GameTestRunner gametestrunner = GameTestRunner.Builder.fromBatches(this.testBatches, level).newStructureSpawner(new StructureGridSpawner(blockpos, 8, false)).build();
        Collection<GameTestInfo> collection = gametestrunner.getTestInfos();

        this.testTracker = new MultipleTestTracker(collection);
        GameTestServer.LOGGER.info("{} tests are now running at position {}!", this.testTracker.getTotalCount(), blockpos.toShortString());
        this.stopwatch.reset();
        this.stopwatch.start();
        gametestrunner.start();
    }

    private boolean haveTestsStarted() {
        return this.testTracker != null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public LevelBasedPermissionSet operatorUserPermissions() {
        return LevelBasedPermissionSet.ALL;
    }

    @Override
    public PermissionSet getFunctionCompilationPermissions() {
        return LevelBasedPermissionSet.OWNER;
    }

    @Override
    public boolean shouldRconBroadcast() {
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return 0;
    }

    @Override
    public boolean useNativeTransport() {
        return false;
    }

    @Override
    public boolean isPublished() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    @Override
    public boolean isSingleplayerOwner(NameAndId nameAndId) {
        return false;
    }

    @Override
    public int getMaxPlayers() {
        return 1;
    }

    private static class MockUserNameToIdResolver implements UserNameToIdResolver {

        private final Set<NameAndId> savedIds = new HashSet();

        private MockUserNameToIdResolver() {}

        @Override
        public void add(NameAndId nameAndId) {
            this.savedIds.add(nameAndId);
        }

        @Override
        public Optional<NameAndId> get(String name) {
            return this.savedIds.stream().filter((nameandid) -> {
                return nameandid.name().equals(name);
            }).findFirst().or(() -> {
                return Optional.of(NameAndId.createOffline(name));
            });
        }

        @Override
        public Optional<NameAndId> get(UUID id) {
            return this.savedIds.stream().filter((nameandid) -> {
                return nameandid.id().equals(id);
            }).findFirst();
        }

        @Override
        public void resolveOfflineUsers(boolean value) {}

        @Override
        public void save() {}
    }

    private static class MockProfileResolver implements ProfileResolver {

        private MockProfileResolver() {}

        @Override
        public Optional<GameProfile> fetchByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<GameProfile> fetchById(UUID id) {
            return Optional.empty();
        }
    }
}
