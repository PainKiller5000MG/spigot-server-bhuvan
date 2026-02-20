package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkMap extends SimpleRegionStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {

    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(ChunkMap.UNLOADED_CHUNK_LIST_RESULT);
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    private static final int MAX_ACTIVE_CHUNK_WRITES = 128;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    public final Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap = new Long2ObjectLinkedOpenHashMap();
    public volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    private final List<ChunkGenerationTask> pendingGenerationTasks;
    public final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final TicketStorage ticketStorage;
    private final PoiManager poiManager;
    public final LongSet toDrop;
    private boolean modified;
    private final ChunkTaskDispatcher worldgenTaskDispatcher;
    private final ChunkTaskDispatcher lightTaskDispatcher;
    private final ChunkStatusUpdateListener chunkStatusListener;
    public final ChunkMap.DistanceManager distanceManager;
    private final String storageName;
    private final PlayerMap playerMap;
    public final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    private final Long2ByteMap chunkTypeCache;
    private final Long2LongMap nextChunkSaveTime;
    private final LongSet chunksToEagerlySave;
    private final Queue<Runnable> unloadQueue;
    private final AtomicInteger activeChunkWrites;
    public int serverViewDistance;
    private final WorldGenContext worldGenContext;

    public ChunkMap(ServerLevel level, LevelStorageSource.LevelStorageAccess levelStorage, DataFixer dataFixer, StructureTemplateManager structureManager, Executor executor, BlockableEventLoop<Runnable> mainThreadExecutor, LightChunkGetter chunkGetter, ChunkGenerator generator, ChunkStatusUpdateListener chunkStatusListener, Supplier<DimensionDataStorage> overworldDataStorage, TicketStorage ticketStorage, int serverViewDistance, boolean syncWrites) {
        super(new RegionStorageInfo(levelStorage.getLevelId(), level.dimension(), "chunk"), levelStorage.getDimensionPath(level.dimension()).resolve("region"), dataFixer, syncWrites, DataFixTypes.CHUNK, LegacyStructureDataHandler.getLegacyTagFixer(level.dimension(), overworldDataStorage, dataFixer));
        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.pendingUnloads = new Long2ObjectLinkedOpenHashMap();
        this.pendingGenerationTasks = new ArrayList();
        this.toDrop = new LongOpenHashSet();
        this.playerMap = new PlayerMap();
        this.entityMap = new Int2ObjectOpenHashMap();
        this.chunkTypeCache = new Long2ByteOpenHashMap();
        this.nextChunkSaveTime = new Long2LongOpenHashMap();
        this.chunksToEagerlySave = new LongLinkedOpenHashSet();
        this.unloadQueue = Queues.newConcurrentLinkedQueue();
        this.activeChunkWrites = new AtomicInteger();
        Path path = levelStorage.getDimensionPath(level.dimension());

        this.storageName = path.getFileName().toString();
        this.level = level;
        RegistryAccess registryaccess = level.registryAccess();
        long j = level.getSeed();

        if (generator instanceof NoiseBasedChunkGenerator noisebasedchunkgenerator) {
            this.randomState = RandomState.create((NoiseGeneratorSettings) noisebasedchunkgenerator.generatorSettings().value(), registryaccess.lookupOrThrow(Registries.NOISE), j);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), registryaccess.lookupOrThrow(Registries.NOISE), j);
        }

        this.chunkGeneratorState = generator.createState(registryaccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, j);
        this.mainThreadExecutor = mainThreadExecutor;
        ConsecutiveExecutor consecutiveexecutor = new ConsecutiveExecutor(executor, "worldgen");

        this.chunkStatusListener = chunkStatusListener;
        ConsecutiveExecutor consecutiveexecutor1 = new ConsecutiveExecutor(executor, "light");

        this.worldgenTaskDispatcher = new ChunkTaskDispatcher(consecutiveexecutor, executor);
        this.lightTaskDispatcher = new ChunkTaskDispatcher(consecutiveexecutor1, executor);
        this.lightEngine = new ThreadedLevelLightEngine(chunkGetter, this, this.level.dimensionType().hasSkyLight(), consecutiveexecutor1, this.lightTaskDispatcher);
        this.distanceManager = new ChunkMap.DistanceManager(ticketStorage, executor, mainThreadExecutor);
        this.ticketStorage = ticketStorage;
        this.poiManager = new PoiManager(new RegionStorageInfo(levelStorage.getLevelId(), level.dimension(), "poi"), path.resolve("poi"), dataFixer, syncWrites, registryaccess, level.getServer(), level);
        this.setServerViewDistance(serverViewDistance);
        this.worldGenContext = new WorldGenContext(level, generator, structureManager, this.lightEngine, mainThreadExecutor, this::setChunkUnsaved);
    }

    private void setChunkUnsaved(ChunkPos chunkPos) {
        this.chunksToEagerlySave.add(chunkPos.toLong());
    }

    protected ChunkGenerator generator() {
        return this.worldGenContext.generator();
    }

    protected ChunkGeneratorStructureState generatorState() {
        return this.chunkGeneratorState;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    public boolean isChunkTracked(ServerPlayer player, int chunkX, int chunkZ) {
        return player.getChunkTrackingView().contains(chunkX, chunkZ) && !player.connection.chunkSender.isPending(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean isChunkOnTrackedBorder(ServerPlayer player, int chunkX, int chunkZ) {
        if (!this.isChunkTracked(player, chunkX, chunkZ)) {
            return false;
        } else {
            for (int k = -1; k <= 1; ++k) {
                for (int l = -1; l <= 1; ++l) {
                    if ((k != 0 || l != 0) && !this.isChunkTracked(player, chunkX + k, chunkZ + l)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    public @Nullable ChunkHolder getUpdatingChunkIfPresent(long key) {
        return (ChunkHolder) this.updatingChunkMap.get(key);
    }

    protected @Nullable ChunkHolder getVisibleChunkIfPresent(long key) {
        return (ChunkHolder) this.visibleChunkMap.get(key);
    }

    public @Nullable ChunkStatus getLatestStatus(long key) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(key);

        return chunkholder != null ? chunkholder.getLatestStatus() : null;
    }

    protected IntSupplier getChunkQueueLevel(long pos) {
        return () -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pos);

            return chunkholder == null ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1 : Math.min(chunkholder.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(ChunkPos pos) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pos.toLong());

        if (chunkholder == null) {
            return "null";
        } else {
            String s = chunkholder.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = chunkholder.getLatestStatus();
            ChunkAccess chunkaccess = chunkholder.getLatestChunk();

            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + String.valueOf(chunkstatus) + "\u00a7r\n";
            }

            if (chunkaccess != null) {
                s = s + "Ch: \u00a7" + chunkaccess.getPersistedStatus().getIndex() + String.valueOf(chunkaccess.getPersistedStatus()) + "\u00a7r\n";
            }

            FullChunkStatus fullchunkstatus = chunkholder.getFullStatus();

            s = s + String.valueOf('\u00a7') + fullchunkstatus.ordinal() + String.valueOf(fullchunkstatus);
            return s + "\u00a7r";
        }
    }

    CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(ChunkHolder centerChunk, int range, IntFunction<ChunkStatus> distanceToStatus) {
        if (range == 0) {
            ChunkStatus chunkstatus = (ChunkStatus) distanceToStatus.apply(0);

            return centerChunk.scheduleChunkGenerationTask(chunkstatus, this).thenApply((chunkresult) -> {
                return chunkresult.map(List::of);
            });
        } else {
            int j = Mth.square(range * 2 + 1);
            List<CompletableFuture<ChunkResult<ChunkAccess>>> list = new ArrayList(j);
            ChunkPos chunkpos = centerChunk.getPos();

            for (int k = -range; k <= range; ++k) {
                for (int l = -range; l <= range; ++l) {
                    int i1 = Math.max(Math.abs(l), Math.abs(k));
                    long j1 = ChunkPos.asLong(chunkpos.x + l, chunkpos.z + k);
                    ChunkHolder chunkholder1 = this.getUpdatingChunkIfPresent(j1);

                    if (chunkholder1 == null) {
                        return ChunkMap.UNLOADED_CHUNK_LIST_FUTURE;
                    }

                    ChunkStatus chunkstatus1 = (ChunkStatus) distanceToStatus.apply(i1);

                    list.add(chunkholder1.scheduleChunkGenerationTask(chunkstatus1, this));
                }
            }

            return Util.sequence(list).thenApply((list1) -> {
                List<ChunkAccess> list2 = new ArrayList(list1.size());

                for (ChunkResult<ChunkAccess> chunkresult : list1) {
                    if (chunkresult == null) {
                        throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                    }

                    ChunkAccess chunkaccess = chunkresult.orElse((Object) null);

                    if (chunkaccess == null) {
                        return ChunkMap.UNLOADED_CHUNK_LIST_RESULT;
                    }

                    list2.add(chunkaccess);
                }

                return ChunkResult.of(list2);
            });
        }
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException exception, String details) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = (chunkholder) -> {
            chunkholder.getAllFutures().forEach((pair) -> {
                ChunkStatus chunkstatus = (ChunkStatus) pair.getFirst();
                CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) pair.getSecond();

                if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                    stringbuilder.append(chunkholder.getPos()).append(" - status: ").append(chunkstatus).append(" future: ").append(completablefuture).append(System.lineSeparator());
                }

            });
        };

        stringbuilder.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(consumer);
        stringbuilder.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(consumer);
        CrashReport crashreport = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk loading");

        crashreportcategory.setDetail("Details", details);
        crashreportcategory.setDetail("Futures", stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 2, (i) -> {
            return ChunkStatus.FULL;
        }).thenApply((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        });
    }

    private @Nullable ChunkHolder updateChunkScheduling(long node, int level, @Nullable ChunkHolder chunk, int oldLevel) {
        if (!ChunkLevel.isLoaded(oldLevel) && !ChunkLevel.isLoaded(level)) {
            return chunk;
        } else {
            if (chunk != null) {
                chunk.setTicketLevel(level);
            }

            if (chunk != null) {
                if (!ChunkLevel.isLoaded(level)) {
                    this.toDrop.add(node);
                } else {
                    this.toDrop.remove(node);
                }
            }

            if (ChunkLevel.isLoaded(level) && chunk == null) {
                chunk = (ChunkHolder) this.pendingUnloads.remove(node);
                if (chunk != null) {
                    chunk.setTicketLevel(level);
                } else {
                    chunk = new ChunkHolder(new ChunkPos(node), level, this.level, this.lightEngine, this::onLevelChange, this);
                }

                this.updatingChunkMap.put(node, chunk);
                this.modified = true;
            }

            return chunk;
        }
    }

    private void onLevelChange(ChunkPos pos, IntSupplier oldLevel, int newLevel, IntConsumer setQueueLevel) {
        this.worldgenTaskDispatcher.onLevelChange(pos, oldLevel, newLevel, setQueueLevel);
        this.lightTaskDispatcher.onLevelChange(pos, oldLevel, newLevel, setQueueLevel);
    }

    @Override
    public void close() throws IOException {
        try {
            this.worldgenTaskDispatcher.close();
            this.lightTaskDispatcher.close();
            this.poiManager.close();
        } finally {
            super.close();
        }

    }

    protected void saveAllChunks(boolean flushStorage) {
        if (flushStorage) {
            List<ChunkHolder> list = this.visibleChunkMap.values().stream().filter(ChunkHolder::wasAccessibleSinceLastSave).peek(ChunkHolder::refreshAccessibility).toList();
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((chunkholder) -> {
                    BlockableEventLoop blockableeventloop = this.mainThreadExecutor;

                    Objects.requireNonNull(chunkholder);
                    blockableeventloop.managedBlock(chunkholder::isReadyForSaving);
                    return chunkholder.getLatestChunk();
                }).filter((chunkaccess) -> {
                    return chunkaccess instanceof ImposterProtoChunk || chunkaccess instanceof LevelChunk;
                }).filter(this::save).forEach((chunkaccess) -> {
                    mutableboolean.setTrue();
                });
            } while (mutableboolean.isTrue());

            this.poiManager.flushAll();
            this.processUnloads(() -> {
                return true;
            });
            this.synchronize(true).join();
        } else {
            this.nextChunkSaveTime.clear();
            long i = Util.getMillis();
            ObjectIterator objectiterator = this.visibleChunkMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkHolder chunkholder = (ChunkHolder) objectiterator.next();

                this.saveChunkIfNeeded(chunkholder, i);
            }
        }

    }

    protected void tick(BooleanSupplier haveTime) {
        ProfilerFiller profilerfiller = Profiler.get();

        profilerfiller.push("poi");
        this.poiManager.tick(haveTime);
        profilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(haveTime);
        }

        profilerfiller.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork() || !this.pendingUnloads.isEmpty() || !this.updatingChunkMap.isEmpty() || this.poiManager.hasWork() || !this.toDrop.isEmpty() || !this.unloadQueue.isEmpty() || this.worldgenTaskDispatcher.hasWork() || this.lightTaskDispatcher.hasWork() || this.distanceManager.hasTickets();
    }

    private void processUnloads(BooleanSupplier haveTime) {
        for (LongIterator longiterator = this.toDrop.iterator(); longiterator.hasNext(); longiterator.remove()) {
            long i = longiterator.nextLong();
            ChunkHolder chunkholder = (ChunkHolder) this.updatingChunkMap.get(i);

            if (chunkholder != null) {
                this.updatingChunkMap.remove(i);
                this.pendingUnloads.put(i, chunkholder);
                this.modified = true;
                this.scheduleUnload(i, chunkholder);
            }
        }

        int j = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;

        while ((j > 0 || haveTime.getAsBoolean()) && (runnable = (Runnable) this.unloadQueue.poll()) != null) {
            --j;
            runnable.run();
        }

        this.saveChunksEagerly(haveTime);
    }

    private void saveChunksEagerly(BooleanSupplier haveTime) {
        long i = Util.getMillis();
        int j = 0;
        LongIterator longiterator = this.chunksToEagerlySave.iterator();

        while (j < 20 && this.activeChunkWrites.get() < 128 && haveTime.getAsBoolean() && longiterator.hasNext()) {
            long k = longiterator.nextLong();
            ChunkHolder chunkholder = (ChunkHolder) this.visibleChunkMap.get(k);
            ChunkAccess chunkaccess = chunkholder != null ? chunkholder.getLatestChunk() : null;

            if (chunkaccess != null && chunkaccess.isUnsaved()) {
                if (this.saveChunkIfNeeded(chunkholder, i)) {
                    ++j;
                    longiterator.remove();
                }
            } else {
                longiterator.remove();
            }
        }

    }

    private void scheduleUnload(long pos, ChunkHolder chunkHolder) {
        CompletableFuture<?> completablefuture = chunkHolder.getSaveSyncFuture();
        Runnable runnable = () -> {
            CompletableFuture<?> completablefuture1 = chunkHolder.getSaveSyncFuture();

            if (completablefuture1 != completablefuture) {
                this.scheduleUnload(pos, chunkHolder);
            } else {
                ChunkAccess chunkaccess = chunkHolder.getLatestChunk();

                if (this.pendingUnloads.remove(pos, chunkHolder) && chunkaccess != null) {
                    if (chunkaccess instanceof LevelChunk) {
                        LevelChunk levelchunk = (LevelChunk) chunkaccess;

                        levelchunk.setLoaded(false);
                    }

                    this.save(chunkaccess);
                    if (chunkaccess instanceof LevelChunk) {
                        LevelChunk levelchunk1 = (LevelChunk) chunkaccess;

                        this.level.unload(levelchunk1);
                    }

                    this.lightEngine.updateChunkStatus(chunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.nextChunkSaveTime.remove(chunkaccess.getPos().toLong());
                }

            }
        };
        Queue queue = this.unloadQueue;

        Objects.requireNonNull(this.unloadQueue);
        completablefuture.thenRunAsync(runnable, queue::add).whenComplete((ovoid, throwable) -> {
            if (throwable != null) {
                ChunkMap.LOGGER.error("Failed to save chunk {}", chunkHolder.getPos(), throwable);
            }

        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos pos) {
        CompletableFuture<Optional<SerializableChunkData>> completablefuture = this.readChunk(pos).thenApplyAsync((optional) -> {
            return optional.map((compoundtag) -> {
                SerializableChunkData serializablechunkdata = SerializableChunkData.parse(this.level, this.level.palettedContainerFactory(), compoundtag);

                if (serializablechunkdata == null) {
                    ChunkMap.LOGGER.error("Chunk file at {} is missing level data, skipping", pos);
                }

                return serializablechunkdata;
            });
        }, Util.backgroundExecutor().forName("parseChunk"));
        CompletableFuture<?> completablefuture1 = this.poiManager.prefetch(pos);

        return completablefuture.thenCombine(completablefuture1, (optional, object) -> {
            return optional;
        }).thenApplyAsync((optional) -> {
            Profiler.get().incrementCounter("chunkLoad");
            if (optional.isPresent()) {
                ChunkAccess chunkaccess = ((SerializableChunkData) optional.get()).read(this.level, this.poiManager, this.storageInfo(), pos);

                this.markPosition(pos, chunkaccess.getPersistedStatus().getChunkType());
                return chunkaccess;
            } else {
                return this.createEmptyChunk(pos);
            }
        }, this.mainThreadExecutor).exceptionallyAsync((throwable) -> {
            return this.handleChunkLoadFailure(throwable, pos);
        }, this.mainThreadExecutor);
    }

    private ChunkAccess handleChunkLoadFailure(Throwable throwable, ChunkPos pos) {
        Throwable throwable1;

        if (throwable instanceof CompletionException completionexception) {
            throwable1 = completionexception.getCause();
        } else {
            throwable1 = throwable;
        }

        Throwable throwable2 = throwable1;

        if (throwable2 instanceof ReportedException reportedexception) {
            throwable1 = reportedexception.getCause();
        } else {
            throwable1 = throwable2;
        }

        Throwable throwable3 = throwable1;
        boolean flag = throwable3 instanceof Error;
        boolean flag1 = throwable3 instanceof IOException || throwable3 instanceof NbtException;

        if (!flag) {
            if (!flag1) {
                ;
            }

            this.level.getServer().reportChunkLoadFailure(throwable3, this.storageInfo(), pos);
            return this.createEmptyChunk(pos);
        } else {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception loading chunk");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk being loaded");

            crashreportcategory.setDetail("pos", pos);
            this.markPositionReplaceable(pos);
            throw new ReportedException(crashreport);
        }
    }

    private ChunkAccess createEmptyChunk(ChunkPos pos) {
        this.markPositionReplaceable(pos);
        return new ProtoChunk(pos, UpgradeData.EMPTY, this.level, this.level.palettedContainerFactory(), (BlendingData) null);
    }

    private void markPositionReplaceable(ChunkPos pos) {
        this.chunkTypeCache.put(pos.toLong(), (byte) -1);
    }

    private byte markPosition(ChunkPos pos, ChunkType type) {
        return this.chunkTypeCache.put(pos.toLong(), (byte) (type == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long chunkNode) {
        ChunkHolder chunkholder = (ChunkHolder) this.updatingChunkMap.get(chunkNode);

        chunkholder.increaseGenerationRefCount();
        return chunkholder;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder chunkHolder) {
        chunkHolder.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder chunkHolder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache) {
        ChunkPos chunkpos = chunkHolder.getPos();

        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkpos);
        } else {
            try {
                GenerationChunkHolder generationchunkholder1 = cache.get(chunkpos.x, chunkpos.z);
                ChunkAccess chunkaccess = generationchunkholder1.getChunkIfPresentUnchecked(step.targetStatus().getParent());

                if (chunkaccess == null) {
                    throw new IllegalStateException("Parent chunk missing");
                } else {
                    return step.apply(this.worldGenContext, cache, chunkaccess);
                }
            } catch (Exception exception) {
                exception.getStackTrace();
                CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk to be generated");

                crashreportcategory.setDetail("Status being generated", () -> {
                    return step.targetStatus().getName();
                });
                crashreportcategory.setDetail("Location", String.format(Locale.ROOT, "%d,%d", chunkpos.x, chunkpos.z));
                crashreportcategory.setDetail("Position hash", ChunkPos.asLong(chunkpos.x, chunkpos.z));
                crashreportcategory.setDetail("Generator", this.generator());
                this.mainThreadExecutor.execute(() -> {
                    throw new ReportedException(crashreport);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus targetStatus, ChunkPos pos) {
        ChunkGenerationTask chunkgenerationtask = ChunkGenerationTask.create(this, targetStatus, pos);

        this.pendingGenerationTasks.add(chunkgenerationtask);
        return chunkgenerationtask;
    }

    private void runGenerationTask(ChunkGenerationTask task) {
        GenerationChunkHolder generationchunkholder = task.getCenter();
        ChunkTaskDispatcher chunktaskdispatcher = this.worldgenTaskDispatcher;
        Runnable runnable = () -> {
            CompletableFuture<?> completablefuture = task.runUntilWait();

            if (completablefuture != null) {
                completablefuture.thenRun(() -> {
                    this.runGenerationTask(task);
                });
            }
        };
        long i = generationchunkholder.getPos().toLong();

        Objects.requireNonNull(generationchunkholder);
        chunktaskdispatcher.submit(runnable, i, generationchunkholder::getQueueLevel);
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(ChunkHolder chunk) {
        CompletableFuture<ChunkResult<List<ChunkAccess>>> completablefuture = this.getChunkRangeFuture(chunk, 1, (i) -> {
            return ChunkStatus.FULL;
        });

        return completablefuture.thenApplyAsync((chunkresult) -> {
            return chunkresult.map((list) -> {
                LevelChunk levelchunk = (LevelChunk) list.get(list.size() / 2);

                levelchunk.postProcessGeneration(this.level);
                this.level.startTickingChunk(levelchunk);
                CompletableFuture<?> completablefuture1 = chunk.getSendSyncFuture();

                if (completablefuture1.isDone()) {
                    this.onChunkReadyToSend(chunk, levelchunk);
                } else {
                    completablefuture1.thenAcceptAsync((object) -> {
                        this.onChunkReadyToSend(chunk, levelchunk);
                    }, this.mainThreadExecutor);
                }

                return levelchunk;
            });
        }, this.mainThreadExecutor);
    }

    private void onChunkReadyToSend(ChunkHolder chunkHolder, LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();

        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            if (serverplayer.getChunkTrackingView().contains(chunkpos)) {
                markChunkPendingToSend(serverplayer, chunk);
            }
        }

        this.level.getChunkSource().onChunkReadyToSend(chunkHolder);
        this.level.debugSynchronizers().registerChunk(chunk);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 1, ChunkLevel::getStatusAroundFullChunk).thenApply((chunkresult) -> {
            return chunkresult.map((list) -> {
                return (LevelChunk) list.get(list.size() / 2);
            });
        });
    }

    Stream<ChunkHolder> allChunksWithAtLeastStatus(ChunkStatus status) {
        int i = ChunkLevel.byStatus(status);

        return this.visibleChunkMap.values().stream().filter((chunkholder) -> {
            return chunkholder.getTicketLevel() <= i;
        });
    }

    private boolean saveChunkIfNeeded(ChunkHolder chunk, long now) {
        if (chunk.wasAccessibleSinceLastSave() && chunk.isReadyForSaving()) {
            ChunkAccess chunkaccess = chunk.getLatestChunk();

            if (!(chunkaccess instanceof ImposterProtoChunk) && !(chunkaccess instanceof LevelChunk)) {
                return false;
            } else if (!chunkaccess.isUnsaved()) {
                return false;
            } else {
                long j = chunkaccess.getPos().toLong();
                long k = this.nextChunkSaveTime.getOrDefault(j, -1L);

                if (now < k) {
                    return false;
                } else {
                    boolean flag = this.save(chunkaccess);

                    chunk.refreshAccessibility();
                    if (flag) {
                        this.nextChunkSaveTime.put(j, now + 10000L);
                    }

                    return flag;
                }
            }
        } else {
            return false;
        }
    }

    public boolean save(ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.tryMarkSaved()) {
            return false;
        } else {
            ChunkPos chunkpos = chunk.getPos();

            try {
                ChunkStatus chunkstatus = chunk.getPersistedStatus();

                if (chunkstatus.getChunkType() != ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkpos)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && chunk.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                Profiler.get().incrementCounter("chunkSave");
                this.activeChunkWrites.incrementAndGet();
                SerializableChunkData serializablechunkdata = SerializableChunkData.copyOf(this.level, chunk);

                Objects.requireNonNull(serializablechunkdata);
                CompletableFuture<CompoundTag> completablefuture = CompletableFuture.supplyAsync(serializablechunkdata::write, Util.backgroundExecutor());

                Objects.requireNonNull(completablefuture);
                this.write(chunkpos, completablefuture::join).handle((ovoid, throwable) -> {
                    if (throwable != null) {
                        this.level.getServer().reportChunkSaveFailure(throwable, this.storageInfo(), chunkpos);
                    }

                    this.activeChunkWrites.decrementAndGet();
                    return null;
                });
                this.markPosition(chunkpos, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                this.level.getServer().reportChunkSaveFailure(exception, this.storageInfo(), chunkpos);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkPos pos) {
        byte b0 = this.chunkTypeCache.get(pos.toLong());

        if (b0 != 0) {
            return b0 == 1;
        } else {
            CompoundTag compoundtag;

            try {
                compoundtag = (CompoundTag) ((Optional) this.readChunk(pos).join()).orElse((Object) null);
                if (compoundtag == null) {
                    this.markPositionReplaceable(pos);
                    return false;
                }
            } catch (Exception exception) {
                ChunkMap.LOGGER.error("Failed to read chunk {}", pos, exception);
                this.markPositionReplaceable(pos);
                return false;
            }

            ChunkType chunktype = SerializableChunkData.getChunkStatusFromTag(compoundtag).getChunkType();

            return this.markPosition(pos, chunktype) == 1;
        }
    }

    protected void setServerViewDistance(int newViewDistance) {
        int j = Mth.clamp(newViewDistance, 2, 32);

        if (j != this.serverViewDistance) {
            this.serverViewDistance = j;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);

            for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
                this.updateChunkTracking(serverplayer);
            }
        }

    }

    private int getPlayerViewDistance(ServerPlayer player) {
        return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(ServerPlayer player, ChunkPos pos) {
        LevelChunk levelchunk = this.getChunkToSend(pos.toLong());

        if (levelchunk != null) {
            markChunkPendingToSend(player, levelchunk);
        }

    }

    private static void markChunkPendingToSend(ServerPlayer player, LevelChunk chunk) {
        player.connection.chunkSender.markChunkPendingToSend(chunk);
    }

    private static void dropChunk(ServerPlayer player, ChunkPos pos) {
        player.connection.chunkSender.dropChunk(player, pos);
    }

    public @Nullable LevelChunk getChunkToSend(long key) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(key);

        return chunkholder == null ? null : chunkholder.getChunkToSend();
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    void dumpChunks(Writer output) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory").addColumn("status").addColumn("full_status").addColumn("accessible_ready").addColumn("ticking_ready").addColumn("entity_ticking_ready").addColumn("ticket").addColumn("spawning").addColumn("block_entity_count").addColumn("ticking_ticket").addColumn("ticking_level").addColumn("block_ticks").addColumn("fluid_ticks").build(output);
        ObjectBidirectionalIterator objectbidirectionaliterator = this.visibleChunkMap.long2ObjectEntrySet().iterator();

        while (objectbidirectionaliterator.hasNext()) {
            Long2ObjectMap.Entry<ChunkHolder> long2objectmap_entry = (Entry) objectbidirectionaliterator.next();
            long i = long2objectmap_entry.getLongKey();
            ChunkPos chunkpos = new ChunkPos(i);
            ChunkHolder chunkholder = (ChunkHolder) long2objectmap_entry.getValue();
            Optional<ChunkAccess> optional = Optional.ofNullable(chunkholder.getLatestChunk());
            Optional<LevelChunk> optional1 = optional.flatMap((chunkaccess) -> {
                return chunkaccess instanceof LevelChunk ? Optional.of((LevelChunk) chunkaccess) : Optional.empty();
            });

            csvoutput.writeRow(chunkpos.x, chunkpos.z, chunkholder.getTicketLevel(), optional.isPresent(), optional.map(ChunkAccess::getPersistedStatus).orElse((Object) null), optional1.map(LevelChunk::getFullStatus).orElse((Object) null), printFuture(chunkholder.getFullChunkFuture()), printFuture(chunkholder.getTickingChunkFuture()), printFuture(chunkholder.getEntityTickingChunkFuture()), this.ticketStorage.getTicketDebugString(i, false), this.anyPlayerCloseEnoughForSpawning(chunkpos), optional1.map((levelchunk) -> {
                return levelchunk.getBlockEntities().size();
            }).orElse(0), this.ticketStorage.getTicketDebugString(i, true), this.distanceManager.getChunkLevel(i, true), optional1.map((levelchunk) -> {
                return levelchunk.getBlockTicks().count();
            }).orElse(0), optional1.map((levelchunk) -> {
                return levelchunk.getFluidTicks().count();
            }).orElse(0));
        }

    }

    private static String printFuture(CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> chunkresult = (ChunkResult) future.getNow((Object) null);

            return chunkresult != null ? (chunkresult.isSuccess() ? "done" : "unloaded") : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pos) {
        return this.read(pos).thenApplyAsync((optional) -> {
            return optional.map(this::upgradeChunkTag);
        }, Util.backgroundExecutor().forName("upgradeChunk"));
    }

    private CompoundTag upgradeChunkTag(CompoundTag tag) {
        return this.upgradeChunkTag(tag, -1, getChunkDataFixContextTag(this.level.dimension(), this.generator().getTypeNameForDataFixer()));
    }

    public static CompoundTag getChunkDataFixContextTag(ResourceKey<Level> dimension, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> generator) {
        CompoundTag compoundtag = new CompoundTag();

        compoundtag.putString("dimension", dimension.identifier().toString());
        generator.ifPresent((resourcekey1) -> {
            compoundtag.putString("generator", resourcekey1.identifier().toString());
        });
        return compoundtag;
    }

    void collectSpawningChunks(List<LevelChunk> output) {
        LongIterator longiterator = this.distanceManager.getSpawnCandidateChunks();

        while (longiterator.hasNext()) {
            ChunkHolder chunkholder = (ChunkHolder) this.visibleChunkMap.get(longiterator.nextLong());

            if (chunkholder != null) {
                LevelChunk levelchunk = chunkholder.getTickingChunk();

                if (levelchunk != null && this.anyPlayerCloseEnoughForSpawningInternal(chunkholder.getPos())) {
                    output.add(levelchunk);
                }
            }
        }

    }

    void forEachBlockTickingChunk(Consumer<LevelChunk> tickingChunkConsumer) {
        this.distanceManager.forEachEntityTickingChunk((i) -> {
            ChunkHolder chunkholder = (ChunkHolder) this.visibleChunkMap.get(i);

            if (chunkholder != null) {
                LevelChunk levelchunk = chunkholder.getTickingChunk();

                if (levelchunk != null) {
                    tickingChunkConsumer.accept(levelchunk);
                }
            }
        });
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) {
        TriState tristate = this.distanceManager.hasPlayersNearby(pos.toLong());

        return tristate == TriState.DEFAULT ? this.anyPlayerCloseEnoughForSpawningInternal(pos) : tristate.toBoolean(true);
    }

    boolean anyPlayerCloseEnoughTo(BlockPos pos, int maxDistance) {
        Vec3 vec3 = new Vec3(pos);

        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughTo(serverplayer, vec3, maxDistance)) {
                return true;
            }
        }

        return false;
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(ChunkPos pos) {
        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(serverplayer, pos)) {
                return true;
            }
        }

        return false;
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pos) {
        long i = pos.toLong();

        if (!this.distanceManager.hasPlayersNearby(i).toBoolean(true)) {
            return List.of();
        } else {
            ImmutableList.Builder<ServerPlayer> immutablelist_builder = ImmutableList.builder();

            for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
                if (this.playerIsCloseEnoughForSpawning(serverplayer, pos)) {
                    immutablelist_builder.add(serverplayer);
                }
            }

            return immutablelist_builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer player, ChunkPos pos) {
        if (player.isSpectator()) {
            return false;
        } else {
            double d0 = euclideanDistanceSquared(pos, player.position());

            return d0 < 16384.0D;
        }
    }

    private boolean playerIsCloseEnoughTo(ServerPlayer player, Vec3 pos, int maxDistance) {
        if (player.isSpectator()) {
            return false;
        } else {
            double d0 = player.position().distanceTo(pos);

            return d0 < (double) maxDistance;
        }
    }

    private static double euclideanDistanceSquared(ChunkPos chunkPos, Vec3 pos) {
        double d0 = (double) SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double d1 = (double) SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double d2 = d0 - pos.x;
        double d3 = d1 - pos.z;

        return d2 * d2 + d3 * d3;
    }

    private boolean skipPlayer(ServerPlayer player) {
        return player.isSpectator() && !(Boolean) this.level.getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS);
    }

    void updatePlayerStatus(ServerPlayer player, boolean added) {
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = this.playerMap.ignoredOrUnknown(player);

        if (added) {
            this.playerMap.addPlayer(player, flag1);
            this.updatePlayerPos(player);
            if (!flag1) {
                this.distanceManager.addPlayer(SectionPos.of((EntityAccess) player), player);
            }

            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(player);
        } else {
            SectionPos sectionpos = player.getLastSectionPos();

            this.playerMap.removePlayer(player);
            if (!flag2) {
                this.distanceManager.removePlayer(sectionpos, player);
            }

            this.applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
        }

    }

    private void updatePlayerPos(ServerPlayer player) {
        SectionPos sectionpos = SectionPos.of((EntityAccess) player);

        player.setLastSectionPos(sectionpos);
    }

    public void move(ServerPlayer player) {
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) objectiterator.next();

            if (chunkmap_trackedentity.entity == player) {
                chunkmap_trackedentity.updatePlayers(this.level.players());
            } else {
                chunkmap_trackedentity.updatePlayer(player);
            }
        }

        SectionPos sectionpos = player.getLastSectionPos();
        SectionPos sectionpos1 = SectionPos.of((EntityAccess) player);
        boolean flag = this.playerMap.ignored(player);
        boolean flag1 = this.skipPlayer(player);
        boolean flag2 = sectionpos.asLong() != sectionpos1.asLong();

        if (flag2 || flag != flag1) {
            this.updatePlayerPos(player);
            if (!flag) {
                this.distanceManager.removePlayer(sectionpos, player);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionpos1, player);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(player);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(player);
            }

            this.updateChunkTracking(player);
        }

    }

    private void updateChunkTracking(ServerPlayer player) {
        ChunkPos chunkpos = player.chunkPosition();
        int i = this.getPlayerViewDistance(player);
        ChunkTrackingView chunktrackingview = player.getChunkTrackingView();

        if (chunktrackingview instanceof ChunkTrackingView.Positioned chunktrackingview_positioned) {
            if (chunktrackingview_positioned.center().equals(chunkpos) && chunktrackingview_positioned.viewDistance() == i) {
                return;
            }
        }

        this.applyChunkTrackingView(player, ChunkTrackingView.of(chunkpos, i));
    }

    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView next) {
        if (player.level() == this.level) {
            ChunkTrackingView chunktrackingview1 = player.getChunkTrackingView();

            if (next instanceof ChunkTrackingView.Positioned) {
                label15:
                {
                    ChunkTrackingView.Positioned chunktrackingview_positioned = (ChunkTrackingView.Positioned) next;

                    if (chunktrackingview1 instanceof ChunkTrackingView.Positioned) {
                        ChunkTrackingView.Positioned chunktrackingview_positioned1 = (ChunkTrackingView.Positioned) chunktrackingview1;

                        if (chunktrackingview_positioned1.center().equals(chunktrackingview_positioned.center())) {
                            break label15;
                        }
                    }

                    player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunktrackingview_positioned.center().x, chunktrackingview_positioned.center().z));
                }
            }

            ChunkTrackingView.difference(chunktrackingview1, next, (chunkpos) -> {
                this.markChunkPendingToSend(player, chunkpos);
            }, (chunkpos) -> {
                dropChunk(player, chunkpos);
            });
            player.setChunkTrackingView(next);
        }
    }

    @Override
    public List<ServerPlayer> getPlayers(ChunkPos pos, boolean borderOnly) {
        Set<ServerPlayer> set = this.playerMap.getAllPlayers();
        ImmutableList.Builder<ServerPlayer> immutablelist_builder = ImmutableList.builder();

        for (ServerPlayer serverplayer : set) {
            if (borderOnly && this.isChunkOnTrackedBorder(serverplayer, pos.x, pos.z) || !borderOnly && this.isChunkTracked(serverplayer, pos.x, pos.z)) {
                immutablelist_builder.add(serverplayer);
            }
        }

        return immutablelist_builder.build();
    }

    protected void addEntity(Entity entity) {
        if (!(entity instanceof EnderDragonPart)) {
            EntityType<?> entitytype = entity.getType();
            int i = entitytype.clientTrackingRange() * 16;

            if (i != 0) {
                int j = entitytype.updateInterval();

                if (this.entityMap.containsKey(entity.getId())) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity chunkmap_trackedentity = new ChunkMap.TrackedEntity(entity, i, j, entitytype.trackDeltas());

                    this.entityMap.put(entity.getId(), chunkmap_trackedentity);
                    chunkmap_trackedentity.updatePlayers(this.level.players());
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) entity;

                        this.updatePlayerStatus(serverplayer, true);
                        ObjectIterator objectiterator = this.entityMap.values().iterator();

                        while (objectiterator.hasNext()) {
                            ChunkMap.TrackedEntity chunkmap_trackedentity1 = (ChunkMap.TrackedEntity) objectiterator.next();

                            if (chunkmap_trackedentity1.entity != serverplayer) {
                                chunkmap_trackedentity1.updatePlayer(serverplayer);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void removeEntity(Entity entity) {
        if (entity instanceof ServerPlayer serverplayer) {
            this.updatePlayerStatus(serverplayer, false);
            ObjectIterator objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) objectiterator.next();

                chunkmap_trackedentity.removePlayer(serverplayer);
            }
        }

        ChunkMap.TrackedEntity chunkmap_trackedentity1 = (ChunkMap.TrackedEntity) this.entityMap.remove(entity.getId());

        if (chunkmap_trackedentity1 != null) {
            chunkmap_trackedentity1.broadcastRemoved();
        }

    }

    protected void tick() {
        for (ServerPlayer serverplayer : this.playerMap.getAllPlayers()) {
            this.updateChunkTracking(serverplayer);
        }

        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) objectiterator.next();
            SectionPos sectionpos = chunkmap_trackedentity.lastSectionPos;
            SectionPos sectionpos1 = SectionPos.of((EntityAccess) chunkmap_trackedentity.entity);
            boolean flag = !Objects.equals(sectionpos, sectionpos1);

            if (flag) {
                chunkmap_trackedentity.updatePlayers(list1);
                Entity entity = chunkmap_trackedentity.entity;

                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer) entity);
                }

                chunkmap_trackedentity.lastSectionPos = sectionpos1;
            }

            if (flag || chunkmap_trackedentity.entity.needsSync || this.distanceManager.inEntityTickingRange(sectionpos1.chunk().toLong())) {
                chunkmap_trackedentity.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            objectiterator = this.entityMap.values().iterator();

            while (objectiterator.hasNext()) {
                ChunkMap.TrackedEntity chunkmap_trackedentity1 = (ChunkMap.TrackedEntity) objectiterator.next();

                chunkmap_trackedentity1.updatePlayers(list);
            }
        }

    }

    public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (chunkmap_trackedentity != null) {
            chunkmap_trackedentity.sendToTrackingPlayers(packet);
        }

    }

    public void sendToTrackingPlayersFiltered(Entity entity, Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> targetPredicate) {
        ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (chunkmap_trackedentity != null) {
            chunkmap_trackedentity.sendToTrackingPlayersFiltered(packet, targetPredicate);
        }

    }

    protected void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        if (chunkmap_trackedentity != null) {
            chunkmap_trackedentity.sendToTrackingPlayersAndSelf(packet);
        }

    }

    public boolean isTrackedByAnyPlayer(Entity entity) {
        ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) this.entityMap.get(entity.getId());

        return chunkmap_trackedentity != null ? !chunkmap_trackedentity.seenBy.isEmpty() : false;
    }

    public void forEachEntityTrackedBy(ServerPlayer player, Consumer<Entity> consumer) {
        ObjectIterator objectiterator = this.entityMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkMap.TrackedEntity chunkmap_trackedentity = (ChunkMap.TrackedEntity) objectiterator.next();

            if (chunkmap_trackedentity.seenBy.contains(player.connection)) {
                consumer.accept(chunkmap_trackedentity.entity);
            }
        }

    }

    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap();

        for (ChunkAccess chunkaccess : chunks) {
            ChunkPos chunkpos = chunkaccess.getPos();
            LevelChunk levelchunk;

            if (chunkaccess instanceof LevelChunk levelchunk1) {
                levelchunk = levelchunk1;
            } else {
                levelchunk = this.level.getChunk(chunkpos.x, chunkpos.z);
            }

            for (ServerPlayer serverplayer : this.getPlayers(chunkpos, false)) {
                ((List) map.computeIfAbsent(serverplayer, (serverplayer1) -> {
                    return new ArrayList();
                })).add(levelchunk);
            }
        }

        map.forEach((serverplayer1, list1) -> {
            serverplayer1.connection.send(ClientboundChunksBiomesPacket.forChunks(list1));
        });
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos pos, FullChunkStatus status) {
        this.chunkStatusListener.onChunkStatusChange(pos, status);
    }

    public void waitForLightBeforeSending(ChunkPos centerChunk, int chunkRadius) {
        int j = chunkRadius + 1;

        ChunkPos.rangeClosed(centerChunk, j).forEach((chunkpos1) -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(chunkpos1.toLong());

            if (chunkholder != null) {
                chunkholder.addSendDependency(this.lightEngine.waitForPendingTasks(chunkpos1.x, chunkpos1.z));
            }

        });
    }

    public void forEachReadyToSendChunk(Consumer<LevelChunk> consumer) {
        ObjectIterator objectiterator = this.visibleChunkMap.values().iterator();

        while (objectiterator.hasNext()) {
            ChunkHolder chunkholder = (ChunkHolder) objectiterator.next();
            LevelChunk levelchunk = chunkholder.getChunkToSend();

            if (levelchunk != null) {
                consumer.accept(levelchunk);
            }
        }

    }

    private class DistanceManager extends net.minecraft.server.level.DistanceManager {

        protected DistanceManager(TicketStorage ticketStorage, Executor executor, Executor mainThreadExecutor) {
            super(ticketStorage, executor, mainThreadExecutor);
        }

        @Override
        protected boolean isChunkToRemove(long node) {
            return ChunkMap.this.toDrop.contains(node);
        }

        @Override
        protected @Nullable ChunkHolder getChunk(long node) {
            return ChunkMap.this.getUpdatingChunkIfPresent(node);
        }

        @Override
        protected @Nullable ChunkHolder updateChunkScheduling(long node, int level, @Nullable ChunkHolder chunk, int oldLevel) {
            return ChunkMap.this.updateChunkScheduling(node, level, chunk, oldLevel);
        }
    }

    public class TrackedEntity implements ServerEntity.Synchronizer {

        public final ServerEntity serverEntity;
        private final Entity entity;
        private final int range;
        private SectionPos lastSectionPos;
        public final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public TrackedEntity(Entity entity, int range, int updateInterval, boolean trackDelta) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, updateInterval, trackDelta, this);
            this.entity = entity;
            this.range = range;
            this.lastSectionPos = SectionPos.of((EntityAccess) entity);
        }

        public boolean equals(Object obj) {
            return obj instanceof ChunkMap.TrackedEntity ? ((ChunkMap.TrackedEntity) obj).entity.getId() == this.entity.getId() : false;
        }

        public int hashCode() {
            return this.entity.getId();
        }

        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                serverplayerconnection.send(packet);
            }

        }

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
            this.sendToTrackingPlayers(packet);
            Entity entity = this.entity;

            if (entity instanceof ServerPlayer serverplayer) {
                serverplayer.connection.send(packet);
            }

        }

        @Override
        public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> targetPredicate) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                if (targetPredicate.test(serverplayerconnection.getPlayer())) {
                    serverplayerconnection.send(packet);
                }
            }

        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }

        }

        public void removePlayer(ServerPlayer player) {
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
                if (this.seenBy.isEmpty()) {
                    ChunkMap.this.level.debugSynchronizers().dropEntity(this.entity);
                }
            }

        }

        public void updatePlayer(ServerPlayer player) {
            if (player != this.entity) {
                Vec3 vec3 = player.position().subtract(this.entity.position());
                int i = ChunkMap.this.getPlayerViewDistance(player);
                double d0 = (double) Math.min(this.getEffectiveRange(), i * 16);
                double d1 = vec3.x * vec3.x + vec3.z * vec3.z;
                double d2 = d0 * d0;
                boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(player) && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition().x, this.entity.chunkPosition().z);

                if (flag) {
                    if (this.seenBy.add(player.connection)) {
                        this.serverEntity.addPairing(player);
                        if (this.seenBy.size() == 1) {
                            ChunkMap.this.level.debugSynchronizers().registerEntity(this.entity);
                        }

                        ChunkMap.this.level.debugSynchronizers().startTrackingEntity(player, this.entity);
                    }
                } else {
                    this.removePlayer(player);
                }

            }
        }

        private int scaledRange(int range) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(range);
        }

        private int getEffectiveRange() {
            int i = this.range;

            for (Entity entity : this.entity.getIndirectPassengers()) {
                int j = entity.getType().clientTrackingRange() * 16;

                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> players) {
            for (ServerPlayer serverplayer : players) {
                this.updatePlayer(serverplayer);
            }

        }
    }
}
