package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerChunkCache extends ChunkSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final DistanceManager distanceManager;
    private final ServerLevel level;
    private final Thread mainThread;
    private final ThreadedLevelLightEngine lightEngine;
    private final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    public final ChunkMap chunkMap;
    private final DimensionDataStorage dataStorage;
    public final TicketStorage ticketStorage;
    private long lastInhabitedUpdate;
    public boolean spawnEnemies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final @Nullable ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final @Nullable ChunkAccess[] lastChunk = new ChunkAccess[4];
    private final List<LevelChunk> spawningChunks = new ObjectArrayList();
    private final Set<ChunkHolder> chunkHoldersToBroadcast = new ReferenceOpenHashSet();
    @VisibleForDebug
    private NaturalSpawner.@Nullable SpawnState lastSpawnState;

    public ServerChunkCache(ServerLevel level, LevelStorageSource.LevelStorageAccess levelStorage, DataFixer fixerUpper, StructureTemplateManager structureTemplateManager, Executor executor, ChunkGenerator generator, int viewDistance, int simulationDistance, boolean syncWrites, ChunkStatusUpdateListener chunkStatusListener, Supplier<DimensionDataStorage> overworldDataStorage) {
        this.level = level;
        this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(level);
        this.mainThread = Thread.currentThread();
        Path path = levelStorage.getDimensionPath(level.dimension()).resolve("data");

        try {
            FileUtil.createDirectoriesSafe(path);
        } catch (IOException ioexception) {
            ServerChunkCache.LOGGER.error("Failed to create dimension data storage directory", ioexception);
        }

        this.dataStorage = new DimensionDataStorage(path, fixerUpper, level.registryAccess());
        this.ticketStorage = (TicketStorage) this.dataStorage.computeIfAbsent(TicketStorage.TYPE);
        this.chunkMap = new ChunkMap(level, levelStorage, fixerUpper, structureTemplateManager, executor, this.mainThreadProcessor, this, generator, chunkStatusListener, overworldDataStorage, this.ticketStorage, viewDistance, syncWrites);
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(simulationDistance);
        this.clearCache();
    }

    @Override
    public ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private @Nullable ChunkHolder getVisibleChunkIfPresent(long key) {
        return this.chunkMap.getVisibleChunkIfPresent(key);
    }

    private void storeInCache(long pos, @Nullable ChunkAccess chunk, ChunkStatus status) {
        for (int j = 3; j > 0; --j) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunkStatus[j] = this.lastChunkStatus[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = pos;
        this.lastChunkStatus[0] = status;
        this.lastChunk[0] = chunk;
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate) {
        if (Thread.currentThread() != this.mainThread) {
            return (ChunkAccess) CompletableFuture.supplyAsync(() -> {
                return this.getChunk(x, z, targetStatus, loadOrGenerate);
            }, this.mainThreadProcessor).join();
        } else {
            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.incrementCounter("getChunk");
            long k = ChunkPos.asLong(x, z);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && targetStatus == this.lastChunkStatus[l]) {
                    ChunkAccess chunkaccess = this.lastChunk[l];

                    if (chunkaccess != null || !loadOrGenerate) {
                        return chunkaccess;
                    }
                }
            }

            profilerfiller.incrementCounter("getChunkCacheMiss");
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getChunkFutureMainThread(x, z, targetStatus, loadOrGenerate);
            ServerChunkCache.MainThreadExecutor serverchunkcache_mainthreadexecutor = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            serverchunkcache_mainthreadexecutor.managedBlock(completablefuture::isDone);
            ChunkResult<ChunkAccess> chunkresult = (ChunkResult) completablefuture.join();
            ChunkAccess chunkaccess1 = chunkresult.orElse((Object) null);

            if (chunkaccess1 == null && loadOrGenerate) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkresult.getError()));
            } else {
                this.storeInCache(k, chunkaccess1, targetStatus);
                return chunkaccess1;
            }
        }
    }

    @Override
    public @Nullable LevelChunk getChunkNow(int x, int z) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            Profiler.get().incrementCounter("getChunkNow");
            long k = ChunkPos.asLong(x, z);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && this.lastChunkStatus[l] == ChunkStatus.FULL) {
                    ChunkAccess chunkaccess = this.lastChunk[l];

                    return chunkaccess instanceof LevelChunk ? (LevelChunk) chunkaccess : null;
                }
            }

            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(k);

            if (chunkholder == null) {
                return null;
            } else {
                ChunkAccess chunkaccess1 = chunkholder.getChunkIfPresent(ChunkStatus.FULL);

                if (chunkaccess1 != null) {
                    this.storeInCache(k, chunkaccess1, ChunkStatus.FULL);
                    if (chunkaccess1 instanceof LevelChunk) {
                        return (LevelChunk) chunkaccess1;
                    }
                }

                return null;
            }
        }
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, (Object) null);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate) {
        boolean flag1 = Thread.currentThread() == this.mainThread;
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture;

        if (flag1) {
            completablefuture = this.getChunkFutureMainThread(x, z, targetStatus, loadOrGenerate);
            ServerChunkCache.MainThreadExecutor serverchunkcache_mainthreadexecutor = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            serverchunkcache_mainthreadexecutor.managedBlock(completablefuture::isDone);
        } else {
            completablefuture = CompletableFuture.supplyAsync(() -> {
                return this.getChunkFutureMainThread(x, z, targetStatus, loadOrGenerate);
            }, this.mainThreadProcessor).thenCompose((completablefuture1) -> {
                return completablefuture1;
            });
        }

        return completablefuture;
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate) {
        ChunkPos chunkpos = new ChunkPos(x, z);
        long k = chunkpos.toLong();
        int l = ChunkLevel.byStatus(targetStatus);
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(k);

        if (loadOrGenerate) {
            this.addTicket(new Ticket(TicketType.UNKNOWN, l), chunkpos);
            if (this.chunkAbsent(chunkholder, l)) {
                ProfilerFiller profilerfiller = Profiler.get();

                profilerfiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                chunkholder = this.getVisibleChunkIfPresent(k);
                profilerfiller.pop();
                if (this.chunkAbsent(chunkholder, l)) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(chunkholder, l) ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE : chunkholder.scheduleChunkGenerationTask(targetStatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable ChunkHolder chunkHolder, int targetTicketLevel) {
        return chunkHolder == null || chunkHolder.getTicketLevel() > targetTicketLevel;
    }

    @Override
    public boolean hasChunk(int x, int z) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent((new ChunkPos(x, z)).toLong());
        int k = ChunkLevel.byStatus(ChunkStatus.FULL);

        return !this.chunkAbsent(chunkholder, k);
    }

    @Override
    public @Nullable LightChunk getChunkForLighting(int x, int z) {
        long k = ChunkPos.asLong(x, z);
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(k);

        return chunkholder == null ? null : chunkholder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
    }

    @Override
    public Level getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    boolean runDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = this.chunkMap.promoteChunkMap();

        this.chunkMap.runGenerationTasks();
        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    public boolean isPositionTicking(long chunkKey) {
        if (!this.level.shouldTickBlocksAt(chunkKey)) {
            return false;
        } else {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(chunkKey);

            return chunkholder == null ? false : ((ChunkResult) chunkholder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).isSuccess();
        }
    }

    public void save(boolean flushStorage) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flushStorage);
    }

    @Override
    public void close() throws IOException {
        this.save(true);
        this.dataStorage.close();
        this.lightEngine.close();
        this.chunkMap.close();
    }

    @Override
    public void tick(BooleanSupplier haveTime, boolean tickChunks) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("purge");
        if (this.level.tickRateManager().runsNormally() || !tickChunks) {
            this.ticketStorage.purgeStaleTickets(this.chunkMap);
        }

        this.runDistanceManagerUpdates();
        profilerfiller.popPush("chunks");
        if (tickChunks) {
            this.tickChunks();
            this.chunkMap.tick();
        }

        profilerfiller.popPush("unload");
        this.chunkMap.tick(haveTime);
        profilerfiller.pop();
        this.clearCache();
    }

    private void tickChunks() {
        long i = this.level.getGameTime();
        long j = i - this.lastInhabitedUpdate;

        this.lastInhabitedUpdate = i;
        if (!this.level.isDebug()) {
            ProfilerFiller profilerfiller = Profiler.get();

            profilerfiller.push("pollingChunks");
            if (this.level.tickRateManager().runsNormally()) {
                profilerfiller.push("tickingChunks");
                this.tickChunks(profilerfiller, j);
                profilerfiller.pop();
            }

            this.broadcastChangedChunks(profilerfiller);
            profilerfiller.pop();
        }
    }

    private void broadcastChangedChunks(ProfilerFiller profiler) {
        profiler.push("broadcast");

        for (ChunkHolder chunkholder : this.chunkHoldersToBroadcast) {
            LevelChunk levelchunk = chunkholder.getTickingChunk();

            if (levelchunk != null) {
                chunkholder.broadcastChanges(levelchunk);
            }
        }

        this.chunkHoldersToBroadcast.clear();
        profiler.pop();
    }

    private void tickChunks(ProfilerFiller profiler, long timeDiff) {
        profiler.push("naturalSpawnCount");
        int j = this.distanceManager.getNaturalSpawnChunkCount();
        NaturalSpawner.SpawnState naturalspawner_spawnstate = NaturalSpawner.createState(j, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));

        this.lastSpawnState = naturalspawner_spawnstate;
        boolean flag = (Boolean) this.level.getGameRules().get(GameRules.SPAWN_MOBS);
        int k = (Integer) this.level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        List<MobCategory> list;

        if (flag) {
            boolean flag1 = this.level.getGameTime() % 400L == 0L;

            list = NaturalSpawner.getFilteredSpawningCategories(naturalspawner_spawnstate, true, this.spawnEnemies, flag1);
        } else {
            list = List.of();
        }

        List<LevelChunk> list1 = this.spawningChunks;

        try {
            profiler.popPush("filteringSpawningChunks");
            this.chunkMap.collectSpawningChunks(list1);
            profiler.popPush("shuffleSpawningChunks");
            Util.shuffle(list1, this.level.random);
            profiler.popPush("tickSpawningChunks");

            for (LevelChunk levelchunk : list1) {
                this.tickSpawningChunk(levelchunk, timeDiff, list, naturalspawner_spawnstate);
            }
        } finally {
            list1.clear();
        }

        profiler.popPush("tickTickingChunks");
        this.chunkMap.forEachBlockTickingChunk((levelchunk1) -> {
            this.level.tickChunk(levelchunk1, k);
        });
        if (flag) {
            profiler.popPush("customSpawners");
            this.level.tickCustomSpawners(this.spawnEnemies);
        }

        profiler.pop();
    }

    private void tickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie) {
        ChunkPos chunkpos = chunk.getPos();

        chunk.incrementInhabitedTime(timeDiff);
        if (this.distanceManager.inEntityTickingRange(chunkpos.toLong())) {
            this.level.tickThunder(chunk);
        }

        if (!spawningCategories.isEmpty()) {
            if (this.level.canSpawnEntitiesInChunk(chunkpos)) {
                NaturalSpawner.spawnForChunk(this.level, chunk, spawnCookie, spawningCategories);
            }

        }
    }

    private void getFullChunk(long chunkKey, Consumer<LevelChunk> output) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(chunkKey);

        if (chunkholder != null) {
            ((ChunkResult) chunkholder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).ifSuccess(output);
        }

    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));

        if (chunkholder != null && chunkholder.blockChanged(pos)) {
            this.chunkHoldersToBroadcast.add(chunkholder);
        }

    }

    @Override
    public void onLightUpdate(LightLayer layer, SectionPos pos) {
        this.mainThreadProcessor.execute(() -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pos.chunk().toLong());

            if (chunkholder != null && chunkholder.sectionLightChanged(layer, pos.y())) {
                this.chunkHoldersToBroadcast.add(chunkholder);
            }

        });
    }

    public boolean hasActiveTickets() {
        return this.ticketStorage.shouldKeepDimensionActive();
    }

    public void addTicket(Ticket ticket, ChunkPos pos) {
        this.ticketStorage.addTicket(ticket, pos);
    }

    public CompletableFuture<?> addTicketAndLoadWithRadius(TicketType type, ChunkPos pos, int radius) {
        if (!type.doesLoad()) {
            throw new IllegalStateException("Ticket type " + String.valueOf(type) + " does not trigger chunk loading");
        } else if (type.canExpireIfUnloaded()) {
            throw new IllegalStateException("Ticket type " + String.valueOf(type) + " can expire before it loads, cannot fetch asynchronously");
        } else {
            this.addTicketWithRadius(type, pos, radius);
            this.runDistanceManagerUpdates();
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pos.toLong());

            Objects.requireNonNull(chunkholder, "No chunk was scheduled for loading");
            return this.chunkMap.getChunkRangeFuture(chunkholder, radius, (j) -> {
                return ChunkStatus.FULL;
            });
        }
    }

    public void addTicketWithRadius(TicketType type, ChunkPos pos, int radius) {
        this.ticketStorage.addTicketWithRadius(type, pos, radius);
    }

    public void removeTicketWithRadius(TicketType type, ChunkPos pos, int radius) {
        this.ticketStorage.removeTicketWithRadius(type, pos, radius);
    }

    @Override
    public boolean updateChunkForced(ChunkPos pos, boolean forced) {
        return this.ticketStorage.updateChunkForced(pos, forced);
    }

    @Override
    public LongSet getForceLoadedChunks() {
        return this.ticketStorage.getForceLoadedChunks();
    }

    public void move(ServerPlayer player) {
        if (!player.isRemoved()) {
            this.chunkMap.move(player);
            if (player.isReceivingWaypoints()) {
                this.level.getWaypointManager().updatePlayer(player);
            }
        }

    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        this.chunkMap.sendToTrackingPlayersAndSelf(entity, packet);
    }

    public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        this.chunkMap.sendToTrackingPlayers(entity, packet);
    }

    public void setViewDistance(int newDistance) {
        this.chunkMap.setServerViewDistance(newDistance);
    }

    public void setSimulationDistance(int simulationDistance) {
        this.distanceManager.updateSimulationDistance(simulationDistance);
    }

    @Override
    public void setSpawnSettings(boolean spawnEnemies) {
        this.spawnEnemies = spawnEnemies;
    }

    public String getChunkDebugData(ChunkPos pos) {
        return this.chunkMap.getChunkDebugData(pos);
    }

    public DimensionDataStorage getDataStorage() {
        return this.dataStorage;
    }

    public PoiManager getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @VisibleForDebug
    public NaturalSpawner.@Nullable SpawnState getLastSpawnState() {
        return this.lastSpawnState;
    }

    public void deactivateTicketsOnClosing() {
        this.ticketStorage.deactivateTicketsOnClosing();
    }

    public void onChunkReadyToSend(ChunkHolder chunk) {
        if (chunk.hasChangesToBroadcast()) {
            this.chunkHoldersToBroadcast.add(chunk);
        }

    }

    private final class MainThreadExecutor extends BlockableEventLoop<Runnable> {

        private MainThreadExecutor(Level level) {
            super("Chunk source main thread executor for " + String.valueOf(level.dimension().identifier()));
        }

        @Override
        public void managedBlock(BooleanSupplier condition) {
            super.managedBlock(() -> {
                return MinecraftServer.throwIfFatalException() && condition.getAsBoolean();
            });
        }

        @Override
        public Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable task) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ServerChunkCache.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable task) {
            Profiler.get().incrementCounter("runTask");
            super.doRunTask(task);
        }

        @Override
        protected boolean pollTask() {
            if (ServerChunkCache.this.runDistanceManagerUpdates()) {
                return true;
            } else {
                ServerChunkCache.this.lightEngine.tryScheduleUpdate();
                return super.pollTask();
            }
        }
    }
}
