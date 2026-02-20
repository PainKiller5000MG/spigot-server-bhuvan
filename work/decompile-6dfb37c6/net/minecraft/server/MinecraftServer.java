package net.minecraft.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.notifications.ServerActivityMonitor;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.FileUtil;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.PngInfo;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.ServerDebugSubscribers;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements CommandSource, ServerInfo, ChunkIOErrorReporter {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 20L * TimeUtil.NANOSECONDS_PER_SECOND / 20L;
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    public static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int SERVER_ACTIVITY_MONITOR_SECONDS_BETWEEN_NOTIFICATIONS = 30;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings("Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
    public static final NameAndId ANONYMOUS_PLAYER_PROFILE = new NameAndId(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder;
    private Consumer<ProfileResults> onMetricsRecordingStopped;
    private Consumer<Path> onMetricsRecordingFinished;
    private boolean willStartRecordingMetrics;
    private MinecraftServer.@Nullable TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    private ServerConnectionListener connection;
    public final LevelLoadListener levelLoadListener;
    private @Nullable ServerStatus status;
    private ServerStatus.@Nullable Favicon statusIcon;
    private final RandomSource random;
    public final DataFixer fixerUpper;
    private String localIp;
    private int port;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    private Map<ResourceKey<Level>, ServerLevel> levels;
    private PlayerList playerList;
    private volatile boolean running;
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private @Nullable String motd;
    private int playerIdleTimeout;
    private final long[] tickTimesNanos;
    private long aggregatedTickTimesNanos;
    private @Nullable KeyPair keyPair;
    private @Nullable GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private final NotificationManager notificationManager;
    private final ServerActivityMonitor serverActivityMonitor;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos;
    private long taskExecutionStartNanos;
    private long idleTimeNanos;
    private long nextTickTimeNanos;
    private boolean waitingForNextTick;
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard;
    private @Nullable Stopwatches stopwatches;
    private @Nullable CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents;
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private boolean usingWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    private @Nullable String serverId;
    public MinecraftServer.ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    private final ServerDebugSubscribers debugSubscribers;
    protected WorldData worldData;
    private LevelData.RespawnData effectiveRespawnData;
    private final PotionBrewing potionBrewing;
    private FuelValues fuelValues;
    private int emptyTicks;
    private volatile boolean isSaving;
    private static final AtomicReference<@Nullable RuntimeException> fatalException = new AtomicReference();
    private final SuppressedExceptionCollector suppressedExceptions;
    private final DiscontinuousFrame tickFrame;
    private final PacketProcessor packetProcessor;

    public static <S extends MinecraftServer> S spin(Function<Thread, S> factory) {
        AtomicReference<S> atomicreference = new AtomicReference();
        Thread thread = new Thread(() -> {
            ((MinecraftServer) atomicreference.get()).runServer();
        }, "Server thread");

        thread.setUncaughtExceptionHandler((thread1, throwable) -> {
            MinecraftServer.LOGGER.error("Uncaught exception in server thread", throwable);
        });
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S s0 = (S) (factory.apply(thread));

        atomicreference.set(s0);
        thread.start();
        return s0;
    }

    public MinecraftServer(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener) {
        super("Server");
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
        this.onMetricsRecordingStopped = (profileresults) -> {
            this.stopRecordingMetrics();
        };
        this.onMetricsRecordingFinished = (path) -> {
        };
        this.random = RandomSource.create();
        this.port = -1;
        this.levels = Maps.newLinkedHashMap();
        this.running = true;
        this.ticksUntilAutosave = 6000;
        this.tickTimesNanos = new long[100];
        this.aggregatedTickTimesNanos = 0L;
        this.lastTickNanos = Util.getNanos();
        this.taskExecutionStartNanos = Util.getNanos();
        this.nextTickTimeNanos = Util.getNanos();
        this.waitingForNextTick = false;
        this.scoreboard = new ServerScoreboard(this);
        this.customBossEvents = new CustomBossEvents();
        this.debugSubscribers = new ServerDebugSubscribers(this);
        this.effectiveRespawnData = LevelData.RespawnData.DEFAULT;
        this.suppressedExceptions = new SuppressedExceptionCollector();
        this.registries = worldStem.registries();
        this.worldData = worldStem.worldData();
        if (!this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) {
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.proxy = proxy;
            this.packRepository = packRepository;
            this.resources = new MinecraftServer.ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
            this.services = services;
            this.connection = new ServerConnectionListener(this);
            this.tickRateManager = new ServerTickRateManager(this);
            this.levelLoadListener = levelLoadListener;
            this.storageSource = storageSource;
            this.playerDataStorage = storageSource.createPlayerStorage();
            this.fixerUpper = fixerUpper;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holdergetter = this.registries.compositeAccess().lookupOrThrow(Registries.BLOCK).filterFeatures(this.worldData.enabledFeatures());

            this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), storageSource, fixerUpper, holdergetter);
            this.serverThread = serverThread;
            this.executor = Util.backgroundExecutor();
            this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
            this.tickFrame = TracyClient.createDiscontinuousFrame("Server Tick");
            this.notificationManager = new NotificationManager();
            this.serverActivityMonitor = new ServerActivityMonitor(this.notificationManager, 30);
            this.packetProcessor = new PacketProcessor(serverThread);
        }
    }

    protected abstract boolean initServer() throws IOException;

    public ChunkLoadStatusView createChunkLoadStatusView(final int radius) {
        return new ChunkLoadStatusView() {
            private @Nullable ChunkMap chunkMap;
            private int centerChunkX;
            private int centerChunkZ;

            @Override
            public void moveTo(ResourceKey<Level> dimension, ChunkPos centerChunk) {
                ServerLevel serverlevel = MinecraftServer.this.getLevel(dimension);

                this.chunkMap = serverlevel != null ? serverlevel.getChunkSource().chunkMap : null;
                this.centerChunkX = centerChunk.x;
                this.centerChunkZ = centerChunk.z;
            }

            @Override
            public @Nullable ChunkStatus get(int x, int z) {
                return this.chunkMap == null ? null : this.chunkMap.getLatestStatus(ChunkPos.asLong(x + this.centerChunkX - radius, z + this.centerChunkZ - radius));
            }

            @Override
            public int radius() {
                return radius;
            }
        };
    }

    protected void loadLevel() {
        boolean flag = !JvmProfiler.INSTANCE.isRunning() && SharedConstants.DEBUG_JFR_PROFILING_ENABLE_LEVEL_LOADING && JvmProfiler.INSTANCE.start(Environment.from(this));
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onWorldLoadedStarted();

        this.worldData.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
        this.createLevels();
        this.forceDifficulty();
        this.prepareLevels();
        if (profiledduration != null) {
            profiledduration.finish(true);
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }

    }

    protected void forceDifficulty() {}

    protected void createLevels() {
        ServerLevelData serverleveldata = this.worldData.overworldData();
        boolean flag = this.worldData.isDebugWorld();
        Registry<LevelStem> registry = this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
        WorldOptions worldoptions = this.worldData.worldGenOptions();
        long i = worldoptions.seed();
        long j = BiomeManager.obfuscateSeed(i);
        List<CustomSpawner> list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverleveldata));
        LevelStem levelstem = (LevelStem) registry.getValue(LevelStem.OVERWORLD);
        ServerLevel serverlevel = new ServerLevel(this, this.executor, this.storageSource, serverleveldata, Level.OVERWORLD, levelstem, flag, j, list, true, (RandomSequences) null);

        this.levels.put(Level.OVERWORLD, serverlevel);
        DimensionDataStorage dimensiondatastorage = serverlevel.getDataStorage();

        this.scoreboard.load(((ScoreboardSaveData) dimensiondatastorage.computeIfAbsent(ScoreboardSaveData.TYPE)).getData());
        this.commandStorage = new CommandStorage(dimensiondatastorage);
        this.stopwatches = (Stopwatches) dimensiondatastorage.computeIfAbsent(Stopwatches.TYPE);
        if (!serverleveldata.isInitialized()) {
            try {
                setInitialSpawn(serverlevel, serverleveldata, worldoptions.generateBonusChest(), flag, this.levelLoadListener);
                serverleveldata.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception initializing level");

                try {
                    serverlevel.fillReportDetails(crashreport);
                } catch (Throwable throwable1) {
                    ;
                }

                throw new ReportedException(crashreport);
            }

            serverleveldata.setInitialized(true);
        }

        GlobalPos globalpos = this.selectLevelLoadFocusPos();

        this.levelLoadListener.updateFocus(globalpos.dimension(), new ChunkPos(globalpos.pos()));
        if (this.worldData.getCustomBossEvents() != null) {
            this.getCustomBossEvents().load(this.worldData.getCustomBossEvents(), this.registryAccess());
        }

        RandomSequences randomsequences = serverlevel.getRandomSequences();
        boolean flag1 = false;

        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> map_entry : registry.entrySet()) {
            ResourceKey<LevelStem> resourcekey = (ResourceKey) map_entry.getKey();
            ServerLevel serverlevel1;

            if (resourcekey != LevelStem.OVERWORLD) {
                ResourceKey<Level> resourcekey1 = ResourceKey.create(Registries.DIMENSION, resourcekey.identifier());
                DerivedLevelData derivedleveldata = new DerivedLevelData(this.worldData, serverleveldata);

                serverlevel1 = new ServerLevel(this, this.executor, this.storageSource, derivedleveldata, resourcekey1, (LevelStem) map_entry.getValue(), flag, j, ImmutableList.of(), false, randomsequences);
                this.levels.put(resourcekey1, serverlevel1);
            } else {
                serverlevel1 = serverlevel;
            }

            Optional<WorldBorder.Settings> optional = serverleveldata.getLegacyWorldBorderSettings();

            if (optional.isPresent()) {
                WorldBorder.Settings worldborder_settings = (WorldBorder.Settings) optional.get();
                DimensionDataStorage dimensiondatastorage1 = serverlevel1.getDataStorage();

                if (dimensiondatastorage1.get(WorldBorder.TYPE) == null) {
                    double d0 = serverlevel1.dimensionType().coordinateScale();
                    WorldBorder.Settings worldborder_settings1 = new WorldBorder.Settings(worldborder_settings.centerX() / d0, worldborder_settings.centerZ() / d0, worldborder_settings.damagePerBlock(), worldborder_settings.safeZone(), worldborder_settings.warningBlocks(), worldborder_settings.warningTime(), worldborder_settings.size(), worldborder_settings.lerpTime(), worldborder_settings.lerpTarget());
                    WorldBorder worldborder = new WorldBorder(worldborder_settings1);

                    worldborder.applyInitialSettings(serverlevel1.getGameTime());
                    dimensiondatastorage1.set(WorldBorder.TYPE, worldborder);
                }

                flag1 = true;
            }

            serverlevel1.getWorldBorder().setAbsoluteMaxSize(this.getAbsoluteMaxWorldSize());
            this.getPlayerList().addWorldborderListener(serverlevel1);
        }

        if (flag1) {
            serverleveldata.setLegacyWorldBorderSettings(Optional.empty());
        }

    }

    private static void setInitialSpawn(ServerLevel level, ServerLevelData levelData, boolean spawnBonusChest, boolean isDebug, LevelLoadListener levelLoadListener) {
        if (SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && SharedConstants.DEBUG_WORLD_RECREATE) {
            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), new BlockPos(0, 64, -100), 0.0F, 0.0F));
        } else if (isDebug) {
            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), BlockPos.ZERO.above(80), 0.0F, 0.0F));
        } else {
            ServerChunkCache serverchunkcache = level.getChunkSource();
            ChunkPos chunkpos = new ChunkPos(serverchunkcache.randomState().sampler().findSpawnPosition());

            levelLoadListener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, 0);
            levelLoadListener.updateFocus(level.dimension(), chunkpos);
            int i = serverchunkcache.getGenerator().getSpawnHeight(level);

            if (i < level.getMinY()) {
                BlockPos blockpos = chunkpos.getWorldPosition();

                i = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockpos.getX() + 8, blockpos.getZ() + 8);
            }

            levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), chunkpos.getWorldPosition().offset(8, i, 8), 0.0F, 0.0F));
            int j = 0;
            int k = 0;
            int l = 0;
            int i1 = -1;

            for (int j1 = 0; j1 < Mth.square(11); ++j1) {
                if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                    BlockPos blockpos1 = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(chunkpos.x + j, chunkpos.z + k));

                    if (blockpos1 != null) {
                        levelData.setSpawn(LevelData.RespawnData.of(level.dimension(), blockpos1, 0.0F, 0.0F));
                        break;
                    }
                }

                if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
                    int k1 = l;

                    l = -i1;
                    i1 = k1;
                }

                j += l;
                k += i1;
            }

            if (spawnBonusChest) {
                level.registryAccess().lookup(Registries.CONFIGURED_FEATURE).flatMap((registry) -> {
                    return registry.get(MiscOverworldFeatures.BONUS_CHEST);
                }).ifPresent((holder_reference) -> {
                    ((ConfiguredFeature) holder_reference.value()).place(level, serverchunkcache.getGenerator(), level.random, levelData.getRespawnData().pos());
                });
            }

            levelLoadListener.finish(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN);
        }
    }

    private void setupDebugLevel(WorldData worldData) {
        worldData.setDifficulty(Difficulty.PEACEFUL);
        worldData.setDifficultyLocked(true);
        ServerLevelData serverleveldata = worldData.overworldData();

        serverleveldata.setRaining(false);
        serverleveldata.setThundering(false);
        serverleveldata.setClearWeatherTime(1000000000);
        serverleveldata.setDayTime(6000L);
        serverleveldata.setGameType(GameType.SPECTATOR);
    }

    public void prepareLevels() {
        ChunkLoadCounter chunkloadcounter = new ChunkLoadCounter();

        for (ServerLevel serverlevel : this.levels.values()) {
            chunkloadcounter.track(serverlevel, () -> {
                TicketStorage ticketstorage = (TicketStorage) serverlevel.getDataStorage().get(TicketStorage.TYPE);

                if (ticketstorage != null) {
                    ticketstorage.activateAllDeactivatedTickets();
                }

            });
        }

        this.levelLoadListener.start(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkloadcounter.totalChunks());

        do {
            this.levelLoadListener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkloadcounter.readyChunks(), chunkloadcounter.totalChunks());
            this.nextTickTimeNanos = Util.getNanos() + MinecraftServer.PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.waitUntilNextTick();
        } while (chunkloadcounter.pendingChunks() > 0);

        this.levelLoadListener.finish(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS);
        this.updateMobSpawningFlags();
        this.updateEffectiveRespawnData();
    }

    protected GlobalPos selectLevelLoadFocusPos() {
        return this.worldData.overworldData().getRespawnData().globalPos();
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract LevelBasedPermissionSet operatorUserPermissions();

    public abstract PermissionSet getFunctionCompilationPermissions();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean silent, boolean flush, boolean force) {
        this.scoreboard.storeToSaveDataIfDirty((ScoreboardSaveData) this.overworld().getDataStorage().computeIfAbsent(ScoreboardSaveData.TYPE));
        boolean flag3 = false;

        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (!silent) {
                MinecraftServer.LOGGER.info("Saving chunks for level '{}'/{}", serverlevel, serverlevel.dimension().identifier());
            }

            serverlevel.save((ProgressListener) null, flush, SharedConstants.DEBUG_DONT_SAVE_WORLD || serverlevel.noSave && !force);
            flag3 = true;
        }

        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        if (flush) {
            for (ServerLevel serverlevel1 : this.getAllLevels()) {
                MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", serverlevel1.getChunkSource().chunkMap.getStorageName());
            }

            MinecraftServer.LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }

        return flag3;
    }

    public boolean saveEverything(boolean silent, boolean flush, boolean force) {
        boolean flag3;

        try {
            this.isSaving = true;
            this.getPlayerList().saveAll();
            flag3 = this.saveAllChunks(silent, flush, force);
        } finally {
            this.isSaving = false;
        }

        return flag3;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    protected void stopServer() {
        this.packetProcessor.close();
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        MinecraftServer.LOGGER.info("Stopping server");
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            MinecraftServer.LOGGER.info("Saving players");
            this.playerList.saveAll();
            this.playerList.removeAll();
        }

        MinecraftServer.LOGGER.info("Saving worlds");

        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (serverlevel != null) {
                serverlevel.noSave = false;
            }
        }

        while (this.levels.values().stream().anyMatch((serverlevel1) -> {
            return serverlevel1.getChunkSource().chunkMap.hasWork();
        })) {
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

            for (ServerLevel serverlevel1 : this.getAllLevels()) {
                serverlevel1.getChunkSource().deactivateTicketsOnClosing();
                serverlevel1.getChunkSource().tick(() -> {
                    return true;
                }, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false);

        for (ServerLevel serverlevel2 : this.getAllLevels()) {
            if (serverlevel2 != null) {
                try {
                    serverlevel2.close();
                } catch (IOException ioexception) {
                    MinecraftServer.LOGGER.error("Exception closing the level", ioexception);
                }
            }
        }

        this.isSaving = false;
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception1) {
            MinecraftServer.LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), ioexception1);
        }

    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String ip) {
        this.localIp = ip;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean wait) {
        this.running = false;
        if (wait) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                MinecraftServer.LOGGER.error("Error while shutting down", interruptedexception);
            }
        }

    }

    protected void runServer() {
        try {
            if (!this.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = (ServerStatus.Favicon) this.loadStatusIcon().orElse((Object) null);
            this.status = this.buildServerStatus();

            while (this.running) {
                long i;

                if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
                    i = 0L;
                    this.nextTickTimeNanos = Util.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    i = this.tickRateManager.nanosecondsPerTick();
                    long j = Util.getNanos() - this.nextTickTimeNanos;

                    if (j > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * i && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * i) {
                        long k = j / i;

                        MinecraftServer.LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", j / TimeUtil.NANOSECONDS_PER_MILLISECOND, k);
                        this.nextTickTimeNanos += k * i;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }

                boolean flag = i == 0L;

                if (this.debugCommandProfilerDelayStart) {
                    this.debugCommandProfilerDelayStart = false;
                    this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount);
                }

                this.nextTickTimeNanos += i;

                try {
                    Profiler.Scope profiler_scope = Profiler.use(this.createProfiler());

                    try {
                        this.processPacketsAndTick(flag);
                        ProfilerFiller profilerfiller = Profiler.get();

                        profilerfiller.push("nextTickWait");
                        this.mayHaveDelayedTasks = true;
                        this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + i, this.nextTickTimeNanos);
                        this.startMeasuringTaskExecutionTime();
                        this.waitUntilNextTick();
                        this.finishMeasuringTaskExecutionTime();
                        if (flag) {
                            this.tickRateManager.endTickWork();
                        }

                        profilerfiller.pop();
                        this.logFullTickTime();
                    } catch (Throwable throwable) {
                        if (profiler_scope != null) {
                            try {
                                profiler_scope.close();
                            } catch (Throwable throwable1) {
                                throwable.addSuppressed(throwable1);
                            }
                        }

                        throw throwable;
                    }

                    if (profiler_scope != null) {
                        profiler_scope.close();
                    }
                } finally {
                    this.endMetricsRecordingTick();
                }

                this.isReady = true;
                JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
            }
        } catch (Throwable throwable2) {
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable2);
            CrashReport crashreport = constructOrExtractCrashReport(throwable2);

            this.fillSystemReport(crashreport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable3) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable3);
            } finally {
                this.onServerExit();
            }

        }

    }

    private void logFullTickTime() {
        long i = Util.getNanos();

        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(i - this.lastTickNanos);
        }

        this.lastTickNanos = i;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }

    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger samplelogger = this.getTickTimeLogger();

            samplelogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            samplelogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }

    }

    private static CrashReport constructOrExtractCrashReport(Throwable t) {
        ReportedException reportedexception = null;

        for (Throwable throwable1 = t; throwable1 != null; throwable1 = throwable1.getCause()) {
            if (throwable1 instanceof ReportedException reportedexception1) {
                reportedexception = reportedexception1;
            }
        }

        CrashReport crashreport;

        if (reportedexception != null) {
            crashreport = reportedexception.getReport();
            if (reportedexception != t) {
                crashreport.addCategory("Wrapped in").setDetailError("Wrapping exception", t);
            }
        } else {
            crashreport = new CrashReport("Exception in server tick loop", t);
        }

        return crashreport;
    }

    private boolean haveTime() {
        return this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    public static boolean throwIfFatalException() {
        RuntimeException runtimeexception = (RuntimeException) MinecraftServer.fatalException.get();

        if (runtimeexception != null) {
            throw runtimeexception;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException exception) {
        MinecraftServer.fatalException.compareAndSet((Object) null, exception);
    }

    @Override
    public void managedBlock(BooleanSupplier condition) {
        super.managedBlock(() -> {
            return throwIfFatalException() && condition.getAsBoolean();
        });
    }

    public NotificationManager notificationManager() {
        return this.notificationManager;
    }

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.waitingForNextTick = true;

        try {
            this.managedBlock(() -> {
                return !this.haveTime();
            });
        } finally {
            this.waitingForNextTick = false;
        }

    }

    @Override
    protected void waitForTasks() {
        boolean flag = this.isTickTimeLoggingEnabled();
        long i = flag ? Util.getNanos() : 0L;
        long j = this.waitingForNextTick ? this.nextTickTimeNanos - Util.getNanos() : 100000L;

        LockSupport.parkNanos("waiting for tasks", j);
        if (flag) {
            this.idleTimeNanos += Util.getNanos() - i;
        }

    }

    @Override
    public TickTask wrapRunnable(Runnable runnable) {
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(TickTask task) {
        return task.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    protected boolean pollTask() {
        boolean flag = this.pollTaskInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        } else {
            if (this.tickRateManager.isSprinting() || this.shouldRunAllTasks() || this.haveTime()) {
                for (ServerLevel serverlevel : this.getAllLevels()) {
                    if (serverlevel.getChunkSource().pollTask()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected void doRunTask(TickTask task) {
        Profiler.get().incrementCounter("runTask");
        super.doRunTask(task);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png")).filter((path) -> {
            return Files.isRegularFile(path, new LinkOption[0]);
        }).or(() -> {
            return this.storageSource.getIconFile().filter((path) -> {
                return Files.isRegularFile(path, new LinkOption[0]);
            });
        });

        return optional.flatMap((path) -> {
            try {
                byte[] abyte = Files.readAllBytes(path);
                PngInfo pnginfo = PngInfo.fromBytes(abyte);

                if (pnginfo.width() == 64 && pnginfo.height() == 64) {
                    return Optional.of(new ServerStatus.Favicon(abyte));
                } else {
                    int i = pnginfo.width();

                    throw new IllegalArgumentException("Invalid world icon size [" + i + ", " + pnginfo.height() + "], but expected [64, 64]");
                }
            } catch (Exception exception) {
                MinecraftServer.LOGGER.error("Couldn't load server icon", exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public ServerActivityMonitor getServerActivityMonitor() {
        return this.serverActivityMonitor;
    }

    protected void onServerCrash(CrashReport report) {}

    protected void onServerExit() {}

    public boolean isPaused() {
        return false;
    }

    protected void tickServer(BooleanSupplier haveTime) {
        long i = Util.getNanos();
        int j = this.pauseWhenEmptySeconds() * 20;

        if (j > 0) {
            if (this.playerList.getPlayerCount() == 0 && !this.tickRateManager.isSprinting()) {
                ++this.emptyTicks;
            } else {
                this.emptyTicks = 0;
            }

            if (this.emptyTicks >= j) {
                if (this.emptyTicks == j) {
                    MinecraftServer.LOGGER.info("Server empty for {} seconds, pausing", this.pauseWhenEmptySeconds());
                    this.autoSave();
                }

                this.tickConnection();
                return;
            }
        }

        ++this.tickCount;
        this.tickRateManager.tick();
        this.tickChildren(haveTime);
        if (i - this.lastServerStatus >= MinecraftServer.STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
        }

        --this.ticksUntilAutosave;
        if (this.ticksUntilAutosave <= 0) {
            this.autoSave();
        }

        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("tallying");
        long k = Util.getNanos() - i;
        int l = this.tickCount % 100;

        this.aggregatedTickTimesNanos -= this.tickTimesNanos[l];
        this.aggregatedTickTimesNanos += k;
        this.tickTimesNanos[l] = k;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float) k / (float) TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        this.logTickMethodTime(i);
        profilerfiller.pop();
    }

    protected void processPacketsAndTick(boolean sprinting) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("tick");
        this.tickFrame.start();
        profilerfiller.push("scheduledPacketProcessing");
        this.packetProcessor.processQueuedPackets();
        profilerfiller.pop();
        this.tickServer(sprinting ? () -> {
            return false;
        } : this::haveTime);
        this.tickFrame.end();
        profilerfiller.pop();
    }

    private void autoSave() {
        this.ticksUntilAutosave = this.computeNextAutosaveInterval();
        MinecraftServer.LOGGER.debug("Autosave started");
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("save");
        this.saveEverything(true, false, false);
        profilerfiller.pop();
        MinecraftServer.LOGGER.debug("Autosave finished");
    }

    private void logTickMethodTime(long startTime) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - startTime, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }

    }

    private int computeNextAutosaveInterval() {
        float f;

        if (this.tickRateManager.isSprinting()) {
            long i = this.getAverageTickTimeNanos() + 1L;

            f = (float) TimeUtil.NANOSECONDS_PER_SECOND / (float) i;
        } else {
            f = this.tickRateManager.tickrate();
        }

        int j = 300;

        return Math.max(100, (int) (f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();

        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }

    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    private ServerStatus buildServerStatus() {
        ServerStatus.Players serverstatus_players = this.buildPlayerStatus();

        return new ServerStatus(Component.nullToEmpty(this.getMotd()), Optional.of(serverstatus_players), Optional.of(ServerStatus.Version.current()), Optional.ofNullable(this.statusIcon), this.enforceSecureProfile());
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> list = this.playerList.getPlayers();
        int i = this.getMaxPlayers();

        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(i, list.size(), List.of());
        } else {
            int j = Math.min(list.size(), 12);
            ObjectArrayList<NameAndId> objectarraylist = new ObjectArrayList(j);
            int k = Mth.nextInt(this.random, 0, list.size() - j);

            for (int l = 0; l < j; ++l) {
                ServerPlayer serverplayer = (ServerPlayer) list.get(k + l);

                objectarraylist.add(serverplayer.allowsListing() ? serverplayer.nameAndId() : MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(objectarraylist, this.random);
            return new ServerStatus.Players(i, list.size(), objectarraylist);
        }
    }

    protected void tickChildren(BooleanSupplier haveTime) {
        ProfilerFiller profilerfiller = Profiler.get();

        this.getPlayerList().getPlayers().forEach((serverplayer) -> {
            serverplayer.connection.suspendFlushing();
        });
        profilerfiller.push("commandFunctions");
        this.getFunctions().tick();
        profilerfiller.popPush("levels");
        this.updateEffectiveRespawnData();

        for (ServerLevel serverlevel : this.getAllLevels()) {
            profilerfiller.push(() -> {
                String s = String.valueOf(serverlevel);

                return s + " " + String.valueOf(serverlevel.dimension().identifier());
            });
            if (this.tickCount % 20 == 0) {
                profilerfiller.push("timeSync");
                this.synchronizeTime(serverlevel);
                profilerfiller.pop();
            }

            profilerfiller.push("tick");

            try {
                serverlevel.tick(haveTime);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");

                serverlevel.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }

            profilerfiller.pop();
            profilerfiller.pop();
        }

        profilerfiller.popPush("connection");
        this.tickConnection();
        profilerfiller.popPush("players");
        this.playerList.tick();
        profilerfiller.popPush("debugSubscribers");
        this.debugSubscribers.tick();
        if (this.tickRateManager.runsNormally()) {
            profilerfiller.popPush("gameTests");
            GameTestTicker.SINGLETON.tick();
        }

        profilerfiller.popPush("server gui refresh");

        for (Runnable runnable : this.tickables) {
            runnable.run();
        }

        profilerfiller.popPush("send chunks");

        for (ServerPlayer serverplayer : this.playerList.getPlayers()) {
            serverplayer.connection.chunkSender.sendNextChunks(serverplayer);
            serverplayer.connection.resumeFlushing();
        }

        profilerfiller.pop();
        this.serverActivityMonitor.tick();
    }

    private void updateEffectiveRespawnData() {
        LevelData.RespawnData leveldata_respawndata = this.worldData.overworldData().getRespawnData();
        ServerLevel serverlevel = this.findRespawnDimension();

        this.effectiveRespawnData = serverlevel.getWorldBorderAdjustedRespawnData(leveldata_respawndata);
    }

    protected void tickConnection() {
        this.getConnection().tick();
    }

    private void synchronizeTime(ServerLevel level) {
        this.playerList.broadcastAll(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), (Boolean) level.getGameRules().get(GameRules.ADVANCE_TIME)), level.dimension());
    }

    public void forceTimeSynchronization() {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("timeSync");

        for (ServerLevel serverlevel : this.getAllLevels()) {
            this.synchronizeTime(serverlevel);
        }

        profilerfiller.pop();
    }

    public void addTickable(Runnable tickable) {
        this.tickables.add(tickable);
    }

    protected void setId(String serverId) {
        this.serverId = serverId;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String name) {
        return this.getServerDirectory().resolve(name);
    }

    public final ServerLevel overworld() {
        return (ServerLevel) this.levels.get(Level.OVERWORLD);
    }

    public @Nullable ServerLevel getLevel(ResourceKey<Level> dimension) {
        return (ServerLevel) this.levels.get(dimension);
    }

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    public String getServerModName() {
        return "vanilla";
    }

    public SystemReport fillSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Server Running", () -> {
            return Boolean.toString(this.running);
        });
        if (this.playerList != null) {
            systemReport.setDetail("Player Count", () -> {
                int i = this.playerList.getPlayerCount();

                return i + " / " + this.playerList.getMaxPlayers() + "; " + String.valueOf(this.playerList.getPlayers());
            });
        }

        systemReport.setDetail("Active Data Packs", () -> {
            return PackRepository.displayPackList(this.packRepository.getSelectedPacks());
        });
        systemReport.setDetail("Available Data Packs", () -> {
            return PackRepository.displayPackList(this.packRepository.getAvailablePacks());
        });
        systemReport.setDetail("Enabled Feature Flags", () -> {
            return (String) FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(Identifier::toString).collect(Collectors.joining(", "));
        });
        systemReport.setDetail("World Generation", () -> {
            return this.worldData.worldGenSettingsLifecycle().toString();
        });
        systemReport.setDetail("World Seed", () -> {
            return String.valueOf(this.worldData.worldGenOptions().seed());
        });
        SuppressedExceptionCollector suppressedexceptioncollector = this.suppressedExceptions;

        Objects.requireNonNull(this.suppressedExceptions);
        systemReport.setDetail("Suppressed Exceptions", suppressedexceptioncollector::dump);
        if (this.serverId != null) {
            systemReport.setDetail("Server Id", () -> {
                return this.serverId;
            });
        }

        return this.fillServerSystemReport(systemReport);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport systemReport);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component message) {
        MinecraftServer.LOGGER.info(message.getString());
    }

    public KeyPair getKeyPair() {
        return (KeyPair) Objects.requireNonNull(this.keyPair);
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public @Nullable GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile singleplayerProfile) {
        this.singleplayerProfile = singleplayerProfile;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        MinecraftServer.LOGGER.info("Generating keypair");

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException cryptexception) {
            throw new IllegalStateException("Failed to generate key pair", cryptexception);
        }
    }

    public void setDifficulty(Difficulty difficulty, boolean ignoreLock) {
        if (ignoreLock || !this.worldData.isDifficultyLocked()) {
            this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : difficulty);
            this.updateMobSpawningFlags();
            this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
        }
    }

    public int getScaledTrackingDistance(int baseRange) {
        return baseRange;
    }

    public void updateMobSpawningFlags() {
        for (ServerLevel serverlevel : this.getAllLevels()) {
            serverlevel.setSpawnSettings(serverlevel.isSpawningMonsters());
        }

    }

    public void setDifficultyLocked(boolean locked) {
        this.worldData.setDifficultyLocked(locked);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer player) {
        LevelData leveldata = player.level().getLevelData();

        player.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean demo) {
        this.isDemo = demo;
    }

    public Map<String, String> getCodeOfConducts() {
        return Map.of();
    }

    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean preventProxyConnections) {
        this.preventProxyConnections = preventProxyConnections;
    }

    public abstract boolean useNativeTransport();

    public boolean allowFlight() {
        return true;
    }

    @Override
    public String getMotd() {
        return this.motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList players) {
        this.playerList = players;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType gameType) {
        this.worldData.setGameType(gameType);
    }

    public int enforceGameTypeForPlayers(@Nullable GameType gameType) {
        if (gameType == null) {
            return 0;
        } else {
            int i = 0;

            for (ServerPlayer serverplayer : this.getPlayerList().getPlayers()) {
                if (serverplayer.setGameMode(gameType)) {
                    ++i;
                }
            }

            return i;
        }
    }

    public ServerConnectionListener getConnection() {
        return this.connection;
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean publishServer(@Nullable GameType gameMode, boolean allowCommands, int port) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int playerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        this.playerIdleTimeout = playerIdleTimeout;
    }

    public Services services() {
        return this.services;
    }

    public @Nullable ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable command) {
        if (this.isStopped()) {
            throw new RejectedExecutionException("Server already shutting down");
        } else {
            super.executeIfPossible(command);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    public CompletableFuture<Void> reloadResources(Collection<String> packsToEnable) {
        CompletableFuture<Void> completablefuture = CompletableFuture.supplyAsync(() -> {
            Stream stream = packsToEnable.stream();
            PackRepository packrepository = this.packRepository;

            Objects.requireNonNull(this.packRepository);
            return (ImmutableList) stream.map(packrepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList());
        }, this).thenCompose((immutablelist) -> {
            CloseableResourceManager closeableresourcemanager = new MultiPackResourceManager(PackType.SERVER_DATA, immutablelist);
            List<Registry.PendingTags<?>> list = TagLoader.loadTagsForExistingRegistries(closeableresourcemanager, this.registries.compositeAccess());

            return ReloadableServerResources.loadResources(closeableresourcemanager, this.registries, list, this.worldData.enabledFeatures(), this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED, this.getFunctionCompilationPermissions(), this.executor, this).whenComplete((reloadableserverresources, throwable) -> {
                if (throwable != null) {
                    closeableresourcemanager.close();
                }

            }).thenApply((reloadableserverresources) -> {
                return new MinecraftServer.ReloadableResources(closeableresourcemanager, reloadableserverresources);
            });
        }).thenAcceptAsync((minecraftserver_reloadableresources) -> {
            this.resources.close();
            this.resources = minecraftserver_reloadableresources;
            this.packRepository.setSelected(packsToEnable);
            WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures());

            this.worldData.setDataConfiguration(worlddataconfiguration);
            this.resources.managers.updateStaticRegistryTags();
            this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
            this.getPlayerList().saveAll();
            this.getPlayerList().reloadResources();
            this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
            this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
            this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
        }, this);

        if (this.isSameThread()) {
            Objects.requireNonNull(completablefuture);
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static WorldDataConfiguration configurePackRepository(PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean initMode, boolean safeMode) {
        DataPackConfig datapackconfig = initialDataConfig.dataPacks();
        FeatureFlagSet featureflagset = initMode ? FeatureFlagSet.of() : initialDataConfig.enabledFeatures();
        FeatureFlagSet featureflagset1 = initMode ? FeatureFlags.REGISTRY.allFlags() : initialDataConfig.enabledFeatures();

        packRepository.reload();
        if (safeMode) {
            return configureRepositoryWithSelection(packRepository, List.of("vanilla"), featureflagset, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();

            for (String s : datapackconfig.getEnabled()) {
                if (packRepository.isAvailable(s)) {
                    set.add(s);
                } else {
                    MinecraftServer.LOGGER.warn("Missing data pack {}", s);
                }
            }

            for (Pack pack : packRepository.getAvailablePacks()) {
                String s1 = pack.getId();

                if (!datapackconfig.getDisabled().contains(s1)) {
                    FeatureFlagSet featureflagset2 = pack.getRequestedFeatures();
                    boolean flag2 = set.contains(s1);

                    if (!flag2 && pack.getPackSource().shouldAddAutomatically()) {
                        if (featureflagset2.isSubsetOf(featureflagset1)) {
                            MinecraftServer.LOGGER.info("Found new data pack {}, loading it automatically", s1);
                            set.add(s1);
                        } else {
                            MinecraftServer.LOGGER.info("Found new data pack {}, but can't load it due to missing features {}", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        }
                    }

                    if (flag2 && !featureflagset2.isSubsetOf(featureflagset1)) {
                        MinecraftServer.LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", s1, FeatureFlags.printMissingFlags(featureflagset1, featureflagset2));
                        set.remove(s1);
                    }
                }
            }

            if (set.isEmpty()) {
                MinecraftServer.LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            return configureRepositoryWithSelection(packRepository, set, featureflagset, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(PackRepository packRepository, Collection<String> selected, FeatureFlagSet forcedFeatures, boolean disableInactive) {
        packRepository.setSelected(selected);
        enableForcedFeaturePacks(packRepository, forcedFeatures);
        DataPackConfig datapackconfig = getSelectedPacks(packRepository, disableInactive);
        FeatureFlagSet featureflagset1 = packRepository.getRequestedFeatureFlags().join(forcedFeatures);

        return new WorldDataConfiguration(datapackconfig, featureflagset1);
    }

    private static void enableForcedFeaturePacks(PackRepository packRepository, FeatureFlagSet forcedFeatures) {
        FeatureFlagSet featureflagset1 = packRepository.getRequestedFeatureFlags();
        FeatureFlagSet featureflagset2 = forcedFeatures.subtract(featureflagset1);

        if (!featureflagset2.isEmpty()) {
            Set<String> set = new ObjectArraySet(packRepository.getSelectedIds());

            for (Pack pack : packRepository.getAvailablePacks()) {
                if (featureflagset2.isEmpty()) {
                    break;
                }

                if (pack.getPackSource() == PackSource.FEATURE) {
                    String s = pack.getId();
                    FeatureFlagSet featureflagset3 = pack.getRequestedFeatures();

                    if (!featureflagset3.isEmpty() && featureflagset3.intersects(featureflagset2) && featureflagset3.isSubsetOf(forcedFeatures)) {
                        if (!set.add(s)) {
                            throw new IllegalStateException("Tried to force '" + s + "', but it was already enabled");
                        }

                        MinecraftServer.LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", s);
                        featureflagset2 = featureflagset2.subtract(featureflagset3);
                    }
                }
            }

            packRepository.setSelected(set);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository packRepository, boolean disableInactive) {
        Collection<String> collection = packRepository.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = disableInactive ? packRepository.getAvailableIds().stream().filter((s) -> {
            return !collection.contains(s);
        }).toList() : List.of();

        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers() {
        if (this.isEnforceWhitelist() && this.isUsingWhitelist()) {
            PlayerList playerlist = this.getPlayerList();
            UserWhiteList userwhitelist = playerlist.getWhiteList();

            for (ServerPlayer serverplayer : Lists.newArrayList(playerlist.getPlayers())) {
                if (!userwhitelist.isWhiteListed(serverplayer.nameAndId())) {
                    serverplayer.connection.disconnect((Component) Component.translatable("multiplayer.disconnect.not_whitelisted"));
                }
            }

        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverlevel = this.findRespawnDimension();

        return new CommandSourceStack(this, Vec3.atLowerCornerOf(this.getRespawnData().pos()), Vec2.ZERO, serverlevel, LevelBasedPermissionSet.OWNER, "Server", Component.literal("Server"), this, (Entity) null);
    }

    public ServerLevel findRespawnDimension() {
        LevelData.RespawnData leveldata_respawndata = this.getWorldData().overworldData().getRespawnData();
        ResourceKey<Level> resourcekey = leveldata_respawndata.dimension();
        ServerLevel serverlevel = this.getLevel(resourcekey);

        return serverlevel != null ? serverlevel : this.overworld();
    }

    public void setRespawnData(LevelData.RespawnData respawnData) {
        ServerLevelData serverleveldata = this.worldData.overworldData();
        LevelData.RespawnData leveldata_respawndata1 = serverleveldata.getRespawnData();

        if (!leveldata_respawndata1.equals(respawnData)) {
            serverleveldata.setSpawn(respawnData);
            this.getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(respawnData));
            this.updateEffectiveRespawnData();
        }

    }

    public LevelData.RespawnData getRespawnData() {
        return this.effectiveRespawnData;
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public Stopwatches getStopwatches() {
        if (this.stopwatches == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.stopwatches;
        }
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.enforceWhitelist = enforceWhitelist;
    }

    public boolean isUsingWhitelist() {
        return this.usingWhitelist;
    }

    public void setUsingWhitelist(boolean usingWhitelist) {
        this.usingWhitelist = usingWhitelist;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / (long) Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public LevelBasedPermissionSet getProfilePermissions(NameAndId nameAndId) {
        if (this.getPlayerList().isOp(nameAndId)) {
            ServerOpListEntry serveroplistentry = (ServerOpListEntry) this.getPlayerList().getOps().get(nameAndId);

            return serveroplistentry != null ? serveroplistentry.permissions() : (this.isSingleplayerOwner(nameAndId) ? LevelBasedPermissionSet.OWNER : (this.isSingleplayer() ? (this.getPlayerList().isAllowCommandsForAllPlayers() ? LevelBasedPermissionSet.OWNER : LevelBasedPermissionSet.ALL) : this.operatorUserPermissions()));
        } else {
            return LevelBasedPermissionSet.ALL;
        }
    }

    public abstract boolean isSingleplayerOwner(NameAndId nameAndId);

    public void dumpServerProperties(Path path) throws IOException {}

    private void saveDebugReport(Path output) {
        Path path1 = output.resolve("levels");

        try {
            for (Map.Entry<ResourceKey<Level>, ServerLevel> map_entry : this.levels.entrySet()) {
                Identifier identifier = ((ResourceKey) map_entry.getKey()).identifier();
                Path path2 = path1.resolve(identifier.getNamespace()).resolve(identifier.getPath());

                Files.createDirectories(path2);
                ((ServerLevel) map_entry.getValue()).saveDebugReport(path2);
            }

            this.dumpGameRules(output.resolve("gamerules.txt"));
            this.dumpClasspath(output.resolve("classpath.txt"));
            this.dumpMiscStats(output.resolve("stats.txt"));
            this.dumpThreads(output.resolve("threads.txt"));
            this.dumpServerProperties(output.resolve("server.properties.txt"));
            this.dumpNativeModules(output.resolve("modules.txt"));
        } catch (IOException ioexception) {
            MinecraftServer.LOGGER.warn("Failed to save debug report", ioexception);
        }

    }

    private void dumpMiscStats(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            writer.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            writer.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            writer.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        }

    }

    private void dumpGameRules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.worldData.getGameRules();

            gamerules.visitGameRuleTypes(new GameRuleTypeVisitor() {
                @Override
                public <T> void visit(GameRule<T> gameRule) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", gameRule.getIdentifier(), gamerules.getAsString(gameRule)));
                }
            });

            for (String s : list) {
                writer.write(s);
            }
        }

    }

    private void dumpClasspath(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            String s = System.getProperty("java.class.path");
            String s1 = File.pathSeparator;

            for (String s2 : Splitter.on(s1).split(s)) {
                writer.write(s2);
                writer.write("\n");
            }
        }

    }

    private void dumpThreads(Path path) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);

        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer writer = Files.newBufferedWriter(path)) {
            for (ThreadInfo threadinfo : athreadinfo) {
                writer.write(threadinfo.toString());
                writer.write(10);
            }
        }

    }

    private void dumpNativeModules(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            List<NativeModuleLister.NativeModuleInfo> list;

            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to list native modules", throwable);
                return;
            }

            list.sort(Comparator.comparing((nativemodulelister_nativemoduleinfo) -> {
                return nativemodulelister_nativemoduleinfo.name;
            }));

            for (NativeModuleLister.NativeModuleInfo nativemodulelister_nativemoduleinfo : list) {
                writer.write(nativemodulelister_nativemoduleinfo.toString());
                writer.write(10);
            }

        }
    }

    private ProfilerFiller createProfiler() {
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()), Util.timeSource, Util.ioPool(), new MetricsPersister("server"), this.onMetricsRecordingStopped, (path) -> {
                this.executeBlocking(() -> {
                    this.saveDebugReport(path.resolve("server"));
                });
                this.onMetricsRecordingFinished.accept(path);
            });
            this.willStartRecordingMetrics = false;
        }

        this.metricsRecorder.startTick();
        return SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
    }

    protected void endMetricsRecordingTick() {
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> onStopped, Consumer<Path> onFinished) {
        this.onMetricsRecordingStopped = (profileresults) -> {
            this.stopRecordingMetrics();
            onStopped.accept(profileresults);
        };
        this.onMetricsRecordingFinished = onFinished;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
    }

    public Path getWorldPath(LevelResource resource) {
        return this.storageSource.getLevelPath(resource);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer player) {
        return (ServerPlayerGameMode) (this.isDemo() ? new DemoMode(player) : new ServerPlayerGameMode(player));
    }

    public @Nullable GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return EmptyProfileResults.EMPTY;
        } else {
            ProfileResults profileresults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);

            this.debugCommandProfiler = null;
            return profileresults;
        }
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component message, ChatType.Bound chatType, @Nullable String tag) {
        String s1 = chatType.decorate(message).getString();

        if (tag != null) {
            MinecraftServer.LOGGER.info("[{}] {}", tag, s1);
        } else {
            MinecraftServer.LOGGER.info("{}", s1);
        }

    }

    public ChatDecorator getChatDecorator() {
        return ChatDecorator.PLAIN;
    }

    public boolean logIPs() {
        return true;
    }

    public void handleCustomClickAction(Identifier id, Optional<Tag> payload) {
        MinecraftServer.LOGGER.debug("Received custom click action {} with payload {}", id, payload.orElse((Object) null));
    }

    public LevelLoadListener getLevelLoadListener() {
        return this.levelLoadListener;
    }

    public boolean setAutoSave(boolean enable) {
        boolean flag1 = false;

        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (serverlevel != null && serverlevel.noSave == enable) {
                serverlevel.noSave = !enable;
                flag1 = true;
            }
        }

        return flag1;
    }

    public boolean isAutoSave() {
        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (serverlevel != null && !serverlevel.noSave) {
                return true;
            }
        }

        return false;
    }

    public <T> void onGameRuleChanged(GameRule<T> rule, T value) {
        this.notificationManager().onGameRuleChanged(rule, value);
        if (rule == GameRules.REDUCED_DEBUG_INFO) {
            byte b0 = (byte) ((Boolean) value ? 22 : 23);

            for (ServerPlayer serverplayer : this.getPlayerList().getPlayers()) {
                serverplayer.connection.send(new ClientboundEntityEventPacket(serverplayer, b0));
            }
        } else if (rule != GameRules.LIMITED_CRAFTING && rule != GameRules.IMMEDIATE_RESPAWN) {
            if (rule == GameRules.LOCATOR_BAR) {
                this.getAllLevels().forEach((serverlevel) -> {
                    ServerWaypointManager serverwaypointmanager = serverlevel.getWaypointManager();

                    if ((Boolean) value) {
                        List list = serverlevel.players();

                        Objects.requireNonNull(serverwaypointmanager);
                        list.forEach(serverwaypointmanager::updatePlayer);
                    } else {
                        serverwaypointmanager.breakAllConnections();
                    }

                });
            } else if (rule == GameRules.SPAWN_MONSTERS) {
                this.updateMobSpawningFlags();
            }
        } else {
            ClientboundGameEventPacket.Type clientboundgameeventpacket_type = rule == GameRules.LIMITED_CRAFTING ? ClientboundGameEventPacket.LIMITED_CRAFTING : ClientboundGameEventPacket.IMMEDIATE_RESPAWN;
            ClientboundGameEventPacket clientboundgameeventpacket = new ClientboundGameEventPacket(clientboundgameeventpacket_type, (Boolean) value ? 1.0F : 0.0F);

            this.getPlayerList().getPlayers().forEach((serverplayer1) -> {
                serverplayer1.connection.send(clientboundgameeventpacket);
            });
        }

    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport report, ChunkPos pos, RegionStorageInfo storageInfo) {
        Util.ioPool().execute(() -> {
            try {
                Path path = this.getFile("debug");

                FileUtil.createDirectoriesSafe(path);
                String s = FileUtil.sanitizeName(storageInfo.level());
                Path path1 = path.resolve("chunk-" + s + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore filestore = Files.getFileStore(path);
                long i = filestore.getUsableSpace();

                if (i < 8192L) {
                    MinecraftServer.LOGGER.warn("Not storing chunk IO report due to low space on drive {}", filestore.name());
                    return;
                }

                CrashReportCategory crashreportcategory = report.addCategory("Chunk Info");

                Objects.requireNonNull(storageInfo);
                crashreportcategory.setDetail("Level", storageInfo::level);
                crashreportcategory.setDetail("Dimension", () -> {
                    return storageInfo.dimension().identifier().toString();
                });
                Objects.requireNonNull(storageInfo);
                crashreportcategory.setDetail("Storage", storageInfo::type);
                Objects.requireNonNull(pos);
                crashreportcategory.setDetail("Position", pos::toString);
                report.saveToFile(path1, ReportType.CHUNK_IO_ERROR);
                MinecraftServer.LOGGER.info("Saved details to {}", report.getSaveFile());
            } catch (Exception exception) {
                MinecraftServer.LOGGER.warn("Failed to store chunk IO exception", exception);
            }

        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos) {
        MinecraftServer.LOGGER.error("Failed to load chunk {},{}", new Object[]{pos.x, pos.z, throwable});
        this.suppressedExceptions.addEntry("chunk/load", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), pos, storageInfo);
    }

    @Override
    public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo storageInfo, ChunkPos pos) {
        MinecraftServer.LOGGER.error("Failed to save chunk {},{}", new Object[]{pos.x, pos.z, throwable});
        this.suppressedExceptions.addEntry("chunk/save", throwable);
        this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), pos, storageInfo);
    }

    public void reportPacketHandlingException(Throwable throwable, PacketType<?> packetType) {
        this.suppressedExceptions.addEntry("packet/" + String.valueOf(packetType), throwable);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public FuelValues fuelValues() {
        return this.fuelValues;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    protected int pauseWhenEmptySeconds() {
        return 0;
    }

    public PacketProcessor packetProcessor() {
        return this.packetProcessor;
    }

    public ServerDebugSubscribers debugSubscribers() {
        return this.debugSubscribers;
    }

    public static record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {

    }

    public static record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {

        public void close() {
            this.resourceManager.close();
        }
    }

    private static class TimeProfiler {

        private final long startNanos;
        private final int startTick;

        private TimeProfiler(long startNanos, int startTick) {
            this.startNanos = startNanos;
            this.startTick = startTick;
        }

        private ProfileResults stop(final long stopNanos, final int stopTick) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String path) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path file) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return stopNanos;
                }

                @Override
                public int getEndTimeTicks() {
                    return stopTick;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }
}
