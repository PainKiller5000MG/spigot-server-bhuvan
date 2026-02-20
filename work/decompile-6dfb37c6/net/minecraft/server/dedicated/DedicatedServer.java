package net.minecraft.server.dedicated;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import io.netty.handler.ssl.SslContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ConsoleInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.MinecraftServerGui;
import net.minecraft.server.jsonrpc.JsonRpcNotificationService;
import net.minecraft.server.jsonrpc.ManagementServer;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.security.AuthenticationHandler;
import net.minecraft.server.jsonrpc.security.JsonRpcSslContextProvider;
import net.minecraft.server.jsonrpc.security.SecurityConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.network.ServerTextFilter;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import net.minecraft.server.rcon.thread.RconThread;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerStatistics;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DedicatedServer extends MinecraftServer implements ServerInterface {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONVERSION_RETRY_DELAY_MS = 5000;
    private static final int CONVERSION_RETRIES = 2;
    private final List<ConsoleInput> consoleInput = Collections.synchronizedList(Lists.newArrayList());
    private @Nullable QueryThreadGs4 queryThreadGs4;
    private final RconConsoleSource rconConsoleSource;
    private @Nullable RconThread rconThread;
    public DedicatedServerSettings settings;
    private @Nullable MinecraftServerGui gui;
    private final @Nullable ServerTextFilter serverTextFilter;
    private @Nullable RemoteSampleLogger tickTimeLogger;
    private boolean isTickTimeLoggingEnabled;
    public ServerLinks serverLinks;
    private final Map<String, String> codeOfConductTexts;
    private @Nullable ManagementServer jsonRpcServer;
    private long lastHeartbeat;

    public DedicatedServer(Thread serverThread, LevelStorageSource.LevelStorageAccess levelStorageSource, PackRepository packRepository, WorldStem worldStem, DedicatedServerSettings settings, DataFixer fixerUpper, Services services) {
        super(serverThread, levelStorageSource, packRepository, worldStem, Proxy.NO_PROXY, fixerUpper, services, LoggingLevelLoadListener.forDedicatedServer());
        this.settings = settings;
        this.rconConsoleSource = new RconConsoleSource(this);
        this.serverTextFilter = ServerTextFilter.createFromConfig(settings.getProperties());
        this.serverLinks = createServerLinks(settings);
        if (settings.getProperties().codeOfConduct) {
            this.codeOfConductTexts = readCodeOfConducts();
        } else {
            this.codeOfConductTexts = Map.of();
        }

    }

    private static Map<String, String> readCodeOfConducts() {
        Path path = Path.of("codeofconduct");

        if (!Files.isDirectory(path, new LinkOption[]{LinkOption.NOFOLLOW_LINKS})) {
            throw new IllegalArgumentException("Code of Conduct folder does not exist: " + String.valueOf(path));
        } else {
            try {
                ImmutableMap.Builder<String, String> immutablemap_builder = ImmutableMap.builder();

                try (Stream<Path> stream = Files.list(path)) {
                    for (Path path1 : stream.toList()) {
                        String s = path1.getFileName().toString();

                        if (s.endsWith(".txt")) {
                            String s1 = s.substring(0, s.length() - 4).toLowerCase(Locale.ROOT);

                            if (!path1.toRealPath().getParent().equals(path.toAbsolutePath())) {
                                throw new IllegalArgumentException("Failed to read Code of Conduct file \"" + s + "\" because it links to a file outside the allowed directory");
                            }

                            try {
                                String s2 = String.join("\n", Files.readAllLines(path1, StandardCharsets.UTF_8));

                                immutablemap_builder.put(s1, StringUtil.stripColor(s2));
                            } catch (IOException ioexception) {
                                throw new IllegalArgumentException("Failed to read Code of Conduct file " + s, ioexception);
                            }
                        }
                    }
                }

                return immutablemap_builder.build();
            } catch (IOException ioexception1) {
                throw new IllegalArgumentException("Failed to read Code of Conduct folder", ioexception1);
            }
        }
    }

    private SslContext createSslContext() {
        try {
            return JsonRpcSslContextProvider.createFrom(this.getProperties().managementServerTlsKeystore, this.getProperties().managementServerTlsKeystorePassword);
        } catch (Exception exception) {
            JsonRpcSslContextProvider.printInstructions();
            throw new IllegalStateException("Failed to configure TLS for the server management protocol", exception);
        }
    }

    @Override
    protected boolean initServer() throws IOException {
        int i = this.getProperties().managementServerPort;

        if (this.getProperties().managementServerEnabled) {
            String s = this.settings.getProperties().managementServerSecret;

            if (!SecurityConfig.isValid(s)) {
                throw new IllegalStateException("Invalid management server secret, must be 40 alphanumeric characters");
            }

            String s1 = this.getProperties().managementServerHost;
            HostAndPort hostandport = HostAndPort.fromParts(s1, i);
            SecurityConfig securityconfig = new SecurityConfig(s);
            String s2 = this.getProperties().managementServerAllowedOrigins;
            AuthenticationHandler authenticationhandler = new AuthenticationHandler(securityconfig, s2);

            DedicatedServer.LOGGER.info("Starting json RPC server on {}", hostandport);
            this.jsonRpcServer = new ManagementServer(hostandport, authenticationhandler);
            MinecraftApi minecraftapi = MinecraftApi.of(this);

            minecraftapi.notificationManager().registerService(new JsonRpcNotificationService(minecraftapi, this.jsonRpcServer));
            if (this.getProperties().managementServerTlsEnabled) {
                SslContext sslcontext = this.createSslContext();

                this.jsonRpcServer.startWithTls(minecraftapi, sslcontext);
            } else {
                this.jsonRpcServer.startWithoutTls(minecraftapi);
            }
        }

        Thread thread = new Thread("Server console handler") {
            public void run() {
                BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                String s3;

                try {
                    while (!DedicatedServer.this.isStopped() && DedicatedServer.this.isRunning() && (s3 = bufferedreader.readLine()) != null) {
                        DedicatedServer.this.handleConsoleInput(s3, DedicatedServer.this.createCommandSourceStack());
                    }
                } catch (IOException ioexception) {
                    DedicatedServer.LOGGER.error("Exception handling console input", ioexception);
                }

            }
        };

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(DedicatedServer.LOGGER));
        thread.start();
        DedicatedServer.LOGGER.info("Starting minecraft server version {}", SharedConstants.getCurrentVersion().name());
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            DedicatedServer.LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        DedicatedServer.LOGGER.info("Loading properties");
        DedicatedServerProperties dedicatedserverproperties = this.settings.getProperties();

        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(dedicatedserverproperties.onlineMode);
            this.setPreventProxyConnections(dedicatedserverproperties.preventProxyConnections);
            this.setLocalIp(dedicatedserverproperties.serverIp);
        }

        this.worldData.setGameType(dedicatedserverproperties.gameMode.get());
        DedicatedServer.LOGGER.info("Default game type: {}", dedicatedserverproperties.gameMode.get());
        InetAddress inetaddress = null;

        if (!this.getLocalIp().isEmpty()) {
            inetaddress = InetAddress.getByName(this.getLocalIp());
        }

        if (this.getPort() < 0) {
            this.setPort(dedicatedserverproperties.serverPort);
        }

        this.initializeKeyPair();
        DedicatedServer.LOGGER.info("Starting Minecraft server on {}:{}", this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort());

        try {
            this.getConnection().startTcpServerListener(inetaddress, this.getPort());
        } catch (IOException ioexception) {
            DedicatedServer.LOGGER.warn("**** FAILED TO BIND TO PORT!");
            DedicatedServer.LOGGER.warn("The exception was: {}", ioexception.toString());
            DedicatedServer.LOGGER.warn("Perhaps a server is already running on that port?");
            return false;
        }

        if (!this.usesAuthentication()) {
            DedicatedServer.LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            DedicatedServer.LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            DedicatedServer.LOGGER.warn("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            DedicatedServer.LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        if (this.convertOldUsers()) {
            this.services.nameToIdCache().save();
        }

        if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
            this.tickTimeLogger = new RemoteSampleLogger(TpsDebugDimensions.values().length, this.debugSubscribers(), RemoteDebugSampleType.TICK_TIME);
            long j = Util.getNanos();

            this.services.nameToIdCache().resolveOfflineUsers(!this.usesAuthentication());
            DedicatedServer.LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel();
            long k = Util.getNanos() - j;
            String s3 = String.format(Locale.ROOT, "%.3fs", (double) k / 1.0E9D);

            DedicatedServer.LOGGER.info("Done ({})! For help, type \"help\"", s3);
            if (dedicatedserverproperties.announcePlayerAchievements != null) {
                this.worldData.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, dedicatedserverproperties.announcePlayerAchievements, this);
            }

            if (dedicatedserverproperties.enableQuery) {
                DedicatedServer.LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = QueryThreadGs4.create(this);
            }

            if (dedicatedserverproperties.enableRcon) {
                DedicatedServer.LOGGER.info("Starting remote control listener");
                this.rconThread = RconThread.create(this);
            }

            if (this.getMaxTickLength() > 0L) {
                Thread thread1 = new Thread(new ServerWatchdog(this));

                thread1.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(DedicatedServer.LOGGER));
                thread1.setName("Server Watchdog");
                thread1.setDaemon(true);
                thread1.start();
            }

            if (dedicatedserverproperties.enableJmxMonitoring) {
                MinecraftServerStatistics.registerJmxMonitoring(this);
                DedicatedServer.LOGGER.info("JMX monitoring enabled");
            }

            this.notificationManager().serverStarted();
            return true;
        }
    }

    @Override
    public boolean isEnforceWhitelist() {
        return (Boolean) this.settings.getProperties().enforceWhitelist.get();
    }

    @Override
    public void setEnforceWhitelist(boolean enforceWhitelist) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.enforceWhitelist.update(this.registryAccess(), enforceWhitelist);
        });
    }

    @Override
    public boolean isUsingWhitelist() {
        return (Boolean) this.settings.getProperties().whiteList.get();
    }

    @Override
    public void setUsingWhitelist(boolean usingWhitelist) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.whiteList.update(this.registryAccess(), usingWhitelist);
        });
    }

    @Override
    protected void tickServer(BooleanSupplier haveTime) {
        super.tickServer(haveTime);
        if (this.jsonRpcServer != null) {
            this.jsonRpcServer.tick();
        }

        long i = Util.getMillis();
        int j = this.statusHeartbeatInterval();

        if (j > 0) {
            long k = (long) j * TimeUtil.MILLISECONDS_PER_SECOND;

            if (i - this.lastHeartbeat >= k) {
                this.lastHeartbeat = i;
                this.notificationManager().statusHeartbeat();
            }
        }

    }

    @Override
    public boolean saveAllChunks(boolean silent, boolean flush, boolean force) {
        this.notificationManager().serverSaveStarted();
        boolean flag3 = super.saveAllChunks(silent, flush, force);

        this.notificationManager().serverSaveCompleted();
        return flag3;
    }

    @Override
    public boolean allowFlight() {
        return (Boolean) this.settings.getProperties().allowFlight.get();
    }

    public void setAllowFlight(boolean allowed) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.allowFlight.update(this.registryAccess(), allowed);
        });
    }

    @Override
    public DedicatedServerProperties getProperties() {
        return this.settings.getProperties();
    }

    public void setDifficulty(Difficulty difficulty) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.difficulty.update(this.registryAccess(), difficulty);
        });
        this.forceDifficulty();
    }

    @Override
    protected void forceDifficulty() {
        this.setDifficulty(this.getProperties().difficulty.get(), true);
    }

    public int viewDistance() {
        return (Integer) this.settings.getProperties().viewDistance.get();
    }

    public void setViewDistance(int viewDistance) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.viewDistance.update(this.registryAccess(), viewDistance);
        });
        this.getPlayerList().setViewDistance(viewDistance);
    }

    public int simulationDistance() {
        return (Integer) this.settings.getProperties().simulationDistance.get();
    }

    public void setSimulationDistance(int simulationDistance) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.simulationDistance.update(this.registryAccess(), simulationDistance);
        });
        this.getPlayerList().setSimulationDistance(simulationDistance);
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport systemReport) {
        systemReport.setDetail("Is Modded", () -> {
            return this.getModdedStatus().fullDescription();
        });
        systemReport.setDetail("Type", () -> {
            return "Dedicated Server";
        });
        return systemReport;
    }

    @Override
    public void dumpServerProperties(Path path) throws IOException {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();

        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", dedicatedserverproperties.syncChunkWrites));
            writer.write(String.format(Locale.ROOT, "gamemode=%s%n", dedicatedserverproperties.gameMode.get()));
            writer.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", dedicatedserverproperties.entityBroadcastRangePercentage.get()));
            writer.write(String.format(Locale.ROOT, "max-world-size=%d%n", dedicatedserverproperties.maxWorldSize));
            writer.write(String.format(Locale.ROOT, "view-distance=%d%n", dedicatedserverproperties.viewDistance.get()));
            writer.write(String.format(Locale.ROOT, "simulation-distance=%d%n", dedicatedserverproperties.simulationDistance.get()));
            writer.write(String.format(Locale.ROOT, "generate-structures=%s%n", dedicatedserverproperties.worldOptions.generateStructures()));
            writer.write(String.format(Locale.ROOT, "use-native=%s%n", dedicatedserverproperties.useNativeTransport));
            writer.write(String.format(Locale.ROOT, "rate-limit=%d%n", dedicatedserverproperties.rateLimitPacketsPerSecond));
        }

    }

    @Override
    protected void onServerExit() {
        if (this.serverTextFilter != null) {
            this.serverTextFilter.close();
        }

        if (this.gui != null) {
            this.gui.close();
        }

        if (this.rconThread != null) {
            this.rconThread.stop();
        }

        if (this.queryThreadGs4 != null) {
            this.queryThreadGs4.stop();
        }

        if (this.jsonRpcServer != null) {
            try {
                this.jsonRpcServer.stop(true);
            } catch (InterruptedException interruptedexception) {
                DedicatedServer.LOGGER.error("Interrupted while stopping the management server", interruptedexception);
            }
        }

    }

    @Override
    protected void tickConnection() {
        super.tickConnection();
        this.handleConsoleInputs();
    }

    public void handleConsoleInput(String msg, CommandSourceStack source) {
        this.consoleInput.add(new ConsoleInput(msg, source));
    }

    public void handleConsoleInputs() {
        while (!this.consoleInput.isEmpty()) {
            ConsoleInput consoleinput = (ConsoleInput) this.consoleInput.remove(0);

            this.getCommands().performPrefixedCommand(consoleinput.source, consoleinput.msg);
        }

    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return this.getProperties().rateLimitPacketsPerSecond;
    }

    @Override
    public boolean useNativeTransport() {
        return this.getProperties().useNativeTransport;
    }

    @Override
    public DedicatedPlayerList getPlayerList() {
        return (DedicatedPlayerList) super.getPlayerList();
    }

    @Override
    public int getMaxPlayers() {
        return (Integer) this.settings.getProperties().maxPlayers.get();
    }

    public void setMaxPlayers(int maxPlayers) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.maxPlayers.update(this.registryAccess(), maxPlayers);
        });
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public String getServerIp() {
        return this.getLocalIp();
    }

    @Override
    public int getServerPort() {
        return this.getPort();
    }

    @Override
    public String getServerName() {
        return this.getMotd();
    }

    public void showGui() {
        if (this.gui == null) {
            this.gui = MinecraftServerGui.showFrameFor(this);
        }

    }

    public int spawnProtectionRadius() {
        return (Integer) this.getProperties().spawnProtection.get();
    }

    public void setSpawnProtectionRadius(int spawnProtectionRadius) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.spawnProtection.update(this.registryAccess(), spawnProtectionRadius);
        });
    }

    @Override
    public boolean isUnderSpawnProtection(ServerLevel level, BlockPos pos, Player player) {
        LevelData.RespawnData leveldata_respawndata = level.getRespawnData();

        if (level.dimension() != leveldata_respawndata.dimension()) {
            return false;
        } else if (this.getPlayerList().getOps().isEmpty()) {
            return false;
        } else if (this.getPlayerList().isOp(player.nameAndId())) {
            return false;
        } else if (this.spawnProtectionRadius() <= 0) {
            return false;
        } else {
            BlockPos blockpos1 = leveldata_respawndata.pos();
            int i = Mth.abs(pos.getX() - blockpos1.getX());
            int j = Mth.abs(pos.getZ() - blockpos1.getZ());
            int k = Math.max(i, j);

            return k <= this.spawnProtectionRadius();
        }
    }

    @Override
    public boolean repliesToStatus() {
        return (Boolean) this.getProperties().enableStatus.get();
    }

    public void setRepliesToStatus(boolean enable) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.enableStatus.update(this.registryAccess(), enable);
        });
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return (Boolean) this.getProperties().hideOnlinePlayers.get();
    }

    public void setHidesOnlinePlayers(boolean hide) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.hideOnlinePlayers.update(this.registryAccess(), hide);
        });
    }

    @Override
    public LevelBasedPermissionSet operatorUserPermissions() {
        return this.getProperties().opPermissions.get();
    }

    public void setOperatorUserPermissions(LevelBasedPermissionSet permissions) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.opPermissions.update(this.registryAccess(), permissions);
        });
    }

    @Override
    public PermissionSet getFunctionCompilationPermissions() {
        return this.getProperties().functionPermissions;
    }

    @Override
    public int playerIdleTimeout() {
        return (Integer) this.settings.getProperties().playerIdleTimeout.get();
    }

    @Override
    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.playerIdleTimeout.update(this.registryAccess(), playerIdleTimeout);
        });
    }

    public int statusHeartbeatInterval() {
        return (Integer) this.settings.getProperties().statusHeartbeatInterval.get();
    }

    public void setStatusHeartbeatInterval(int statusHeartbeatInterval) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.statusHeartbeatInterval.update(this.registryAccess(), statusHeartbeatInterval);
        });
    }

    @Override
    public String getMotd() {
        return this.settings.getProperties().motd.get();
    }

    @Override
    public void setMotd(String motd) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.motd.update(this.registryAccess(), motd);
        });
    }

    @Override
    public boolean shouldRconBroadcast() {
        return this.getProperties().broadcastRconToOps;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getProperties().broadcastConsoleToOps;
    }

    @Override
    public int getAbsoluteMaxWorldSize() {
        return this.getProperties().maxWorldSize;
    }

    @Override
    public int getCompressionThreshold() {
        return this.getProperties().networkCompressionThreshold;
    }

    @Override
    public boolean enforceSecureProfile() {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();

        return dedicatedserverproperties.enforceSecureProfile && dedicatedserverproperties.onlineMode && this.services.canValidateProfileKeys();
    }

    @Override
    public boolean logIPs() {
        return this.getProperties().logIPs;
    }

    protected boolean convertOldUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; ++i) {
            if (i > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag = OldUsersConverter.convertUserBanlist(this);
        }

        boolean flag1 = false;

        for (int j = 0; !flag1 && j <= 2; ++j) {
            if (j > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag1 = OldUsersConverter.convertIpBanlist(this);
        }

        boolean flag2 = false;

        for (int k = 0; !flag2 && k <= 2; ++k) {
            if (k > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.waitForRetry();
            }

            flag2 = OldUsersConverter.convertOpsList(this);
        }

        boolean flag3 = false;

        for (int l = 0; !flag3 && l <= 2; ++l) {
            if (l > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag3 = OldUsersConverter.convertWhiteList(this);
        }

        boolean flag4 = false;

        for (int i1 = 0; !flag4 && i1 <= 2; ++i1) {
            if (i1 > 0) {
                DedicatedServer.LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.waitForRetry();
            }

            flag4 = OldUsersConverter.convertPlayers(this);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void waitForRetry() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException interruptedexception) {
            ;
        }
    }

    public long getMaxTickLength() {
        return this.getProperties().maxTickTime;
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return this.getProperties().maxChainedNeighborUpdates;
    }

    @Override
    public String getPluginNames() {
        return "";
    }

    @Override
    public String runCommand(String command) {
        this.rconConsoleSource.prepareForCommand();
        this.executeBlocking(() -> {
            this.getCommands().performPrefixedCommand(this.rconConsoleSource.createCommandSourceStack(), command);
        });
        return this.rconConsoleSource.getCommandResponse();
    }

    @Override
    protected void stopServer() {
        this.notificationManager().serverShuttingDown();
        super.stopServer();
        Util.shutdownExecutors();
    }

    @Override
    public boolean isSingleplayerOwner(NameAndId nameAndId) {
        return false;
    }

    @Override
    public int getScaledTrackingDistance(int range) {
        return this.entityBroadcastRangePercentage() * range / 100;
    }

    public int entityBroadcastRangePercentage() {
        return (Integer) this.getProperties().entityBroadcastRangePercentage.get();
    }

    public void setEntityBroadcastRangePercentage(int range) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.entityBroadcastRangePercentage.update(this.registryAccess(), range);
        });
    }

    @Override
    public String getLevelIdName() {
        return this.storageSource.getLevelId();
    }

    @Override
    public boolean forceSynchronousWrites() {
        return this.settings.getProperties().syncChunkWrites;
    }

    @Override
    public TextFilter createTextFilterForPlayer(ServerPlayer player) {
        return this.serverTextFilter != null ? this.serverTextFilter.createContext(player.getGameProfile()) : TextFilter.DUMMY;
    }

    @Override
    public @Nullable GameType getForcedGameType() {
        return this.forceGameMode() ? this.worldData.getGameType() : null;
    }

    public boolean forceGameMode() {
        return (Boolean) this.settings.getProperties().forceGameMode.get();
    }

    public void setForceGameMode(boolean forceGameMode) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.forceGameMode.update(this.registryAccess(), forceGameMode);
        });
        this.enforceGameTypeForPlayers(this.getForcedGameType());
    }

    public GameType gameMode() {
        return this.getProperties().gameMode.get();
    }

    public void setGameMode(GameType gameMode) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.gameMode.update(this.registryAccess(), gameMode);
        });
        this.worldData.setGameType(this.gameMode());
        this.enforceGameTypeForPlayers(this.getForcedGameType());
    }

    @Override
    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return this.settings.getProperties().serverResourcePackInfo;
    }

    @Override
    protected void endMetricsRecordingTick() {
        super.endMetricsRecordingTick();
        this.isTickTimeLoggingEnabled = this.debugSubscribers().hasAnySubscriberFor(DebugSubscriptions.DEDICATED_SERVER_TICK_TIME);
    }

    @Override
    protected SampleLogger getTickTimeLogger() {
        return this.tickTimeLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return this.isTickTimeLoggingEnabled;
    }

    @Override
    public boolean acceptsTransfers() {
        return (Boolean) this.settings.getProperties().acceptsTransfers.get();
    }

    public void setAcceptsTransfers(boolean acceptTransfers) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.acceptsTransfers.update(this.registryAccess(), acceptTransfers);
        });
    }

    @Override
    public ServerLinks serverLinks() {
        return this.serverLinks;
    }

    @Override
    public int pauseWhenEmptySeconds() {
        return (Integer) this.settings.getProperties().pauseWhenEmptySeconds.get();
    }

    public void setPauseWhenEmptySeconds(int seconds) {
        this.settings.update((dedicatedserverproperties) -> {
            return (DedicatedServerProperties) dedicatedserverproperties.pauseWhenEmptySeconds.update(this.registryAccess(), seconds);
        });
    }

    private static ServerLinks createServerLinks(DedicatedServerSettings settings) {
        Optional<URI> optional = parseBugReportLink(settings.getProperties());

        return (ServerLinks) optional.map((uri) -> {
            return new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(uri)));
        }).orElse(ServerLinks.EMPTY);
    }

    private static Optional<URI> parseBugReportLink(DedicatedServerProperties properties) {
        String s = properties.bugReportLink;

        if (s.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Util.parseAndValidateUntrustedUri(s));
            } catch (Exception exception) {
                DedicatedServer.LOGGER.warn("Failed to parse bug link {}", s, exception);
                return Optional.empty();
            }
        }
    }

    @Override
    public Map<String, String> getCodeOfConducts() {
        return this.codeOfConductTexts;
    }
}
