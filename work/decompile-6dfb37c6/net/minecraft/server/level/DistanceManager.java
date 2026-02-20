package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.util.TriState;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class DistanceManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap();
    private final LoadingChunkTracker loadingChunkTracker;
    private final SimulationChunkTracker simulationChunkTracker;
    private final TicketStorage ticketStorage;
    private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
    private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(32);
    protected final Set<ChunkHolder> chunksToUpdateFutures = new ReferenceOpenHashSet();
    private final ThrottlingChunkTaskDispatcher ticketDispatcher;
    private final LongSet ticketsToRelease = new LongOpenHashSet();
    private final Executor mainThreadExecutor;
    public int simulationDistance = 10;

    protected DistanceManager(TicketStorage ticketStorage, Executor executor, Executor mainThreadExecutor) {
        this.ticketStorage = ticketStorage;
        this.loadingChunkTracker = new LoadingChunkTracker(this, ticketStorage);
        this.simulationChunkTracker = new SimulationChunkTracker(ticketStorage);
        TaskScheduler<Runnable> taskscheduler = TaskScheduler.wrapExecutor("player ticket throttler", mainThreadExecutor);

        this.ticketDispatcher = new ThrottlingChunkTaskDispatcher(taskscheduler, executor, 4);
        this.mainThreadExecutor = mainThreadExecutor;
    }

    protected abstract boolean isChunkToRemove(long node);

    protected abstract @Nullable ChunkHolder getChunk(long node);

    protected abstract @Nullable ChunkHolder updateChunkScheduling(long node, int level, @Nullable ChunkHolder chunk, int oldLevel);

    public boolean runAllUpdates(ChunkMap scheduler) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.simulationChunkTracker.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.loadingChunkTracker.runDistanceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;

        if (flag && SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
            DistanceManager.LOGGER.debug("DMU {}", i);
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            for (ChunkHolder chunkholder : this.chunksToUpdateFutures) {
                chunkholder.updateHighestAllowedStatus(scheduler);
            }

            for (ChunkHolder chunkholder1 : this.chunksToUpdateFutures) {
                chunkholder1.updateFutures(scheduler, this.mainThreadExecutor);
            }

            this.chunksToUpdateFutures.clear();
            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                LongIterator longiterator = this.ticketsToRelease.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();

                    if (this.ticketStorage.getTickets(j).stream().anyMatch((ticket) -> {
                        return ticket.getType() == TicketType.PLAYER_LOADING;
                    })) {
                        ChunkHolder chunkholder2 = scheduler.getUpdatingChunkIfPresent(j);

                        if (chunkholder2 == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<ChunkResult<LevelChunk>> completablefuture = chunkholder2.getEntityTickingChunkFuture();

                        completablefuture.thenAccept((chunkresult) -> {
                            this.mainThreadExecutor.execute(() -> {
                                this.ticketDispatcher.release(j, () -> {
                                }, false);
                            });
                        });
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    public void addPlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkpos = pos.chunk();
        long i = chunkpos.toLong();

        ((ObjectSet) this.playersPerChunk.computeIfAbsent(i, (j) -> {
            return new ObjectOpenHashSet();
        })).add(player);
        this.naturalSpawnChunkCounter.update(i, 0, true);
        this.playerTicketManager.update(i, 0, true);
        this.ticketStorage.addTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkpos);
    }

    public void removePlayer(SectionPos pos, ServerPlayer player) {
        ChunkPos chunkpos = pos.chunk();
        long i = chunkpos.toLong();
        ObjectSet<ServerPlayer> objectset = (ObjectSet) this.playersPerChunk.get(i);

        objectset.remove(player);
        if (objectset.isEmpty()) {
            this.playersPerChunk.remove(i);
            this.naturalSpawnChunkCounter.update(i, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(i, Integer.MAX_VALUE, false);
            this.ticketStorage.removeTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkpos);
        }

    }

    private int getPlayerTicketLevel() {
        return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
    }

    public boolean inEntityTickingRange(long key) {
        return ChunkLevel.isEntityTicking(this.simulationChunkTracker.getLevel(key));
    }

    public boolean inBlockTickingRange(long key) {
        return ChunkLevel.isBlockTicking(this.simulationChunkTracker.getLevel(key));
    }

    public int getChunkLevel(long key, boolean simulation) {
        return simulation ? this.simulationChunkTracker.getLevel(key) : this.loadingChunkTracker.getLevel(key);
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.playerTicketManager.updateViewDistance(viewDistance);
    }

    public void updateSimulationDistance(int newDistance) {
        if (newDistance != this.simulationDistance) {
            this.simulationDistance = newDistance;
            this.ticketStorage.replaceTicketLevelOfType(this.getPlayerTicketLevel(), TicketType.PLAYER_SIMULATION);
        }

    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public TriState hasPlayersNearby(long pos) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        int j = this.naturalSpawnChunkCounter.getLevel(pos);

        return j <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK ? TriState.TRUE : (j > 8 ? TriState.FALSE : TriState.DEFAULT);
    }

    public void forEachEntityTickingChunk(LongConsumer consumer) {
        ObjectIterator objectiterator = Long2ByteMaps.fastIterable(this.simulationChunkTracker.chunks).iterator();

        while (objectiterator.hasNext()) {
            Entry entry = (Entry) objectiterator.next();
            byte b0 = entry.getByteValue();
            long i = entry.getLongKey();

            if (ChunkLevel.isEntityTicking(b0)) {
                consumer.accept(i);
            }
        }

    }

    public LongIterator getSpawnCandidateChunks() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.keySet().iterator();
    }

    public String getDebugStatus() {
        return this.ticketDispatcher.getDebugStatus();
    }

    public boolean hasTickets() {
        return this.ticketStorage.hasTickets();
    }

    private class FixedPlayerDistanceChunkTracker extends ChunkTracker {

        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(int maxDistance) {
            super(maxDistance + 2, 16, 256);
            this.maxDistance = maxDistance;
            this.chunks.defaultReturnValue((byte) (maxDistance + 2));
        }

        @Override
        protected int getLevel(long node) {
            return this.chunks.get(node);
        }

        @Override
        protected void setLevel(long node, int level) {
            byte b0;

            if (level > this.maxDistance) {
                b0 = this.chunks.remove(node);
            } else {
                b0 = this.chunks.put(node, (byte) level);
            }

            this.onLevelChange(node, b0, level);
        }

        protected void onLevelChange(long node, int oldLevel, int level) {}

        @Override
        protected int getLevelFromSource(long to) {
            return this.havePlayer(to) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> objectset = (ObjectSet) DistanceManager.this.playersPerChunk.get(chunkPos);

            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }
    }

    private class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {

        private int viewDistance = 0;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(int maxDistance) {
            super(maxDistance);
            this.queueLevels.defaultReturnValue(maxDistance + 2);
        }

        @Override
        protected void onLevelChange(long node, int oldLevel, int level) {
            this.toUpdate.add(node);
        }

        public void updateViewDistance(int viewDistance) {
            ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Entry entry = (Entry) objectiterator.next();
                byte b0 = entry.getByteValue();
                long j = entry.getLongKey();

                this.onLevelChange(j, b0, this.haveTicketFor(b0), b0 <= viewDistance);
            }

            this.viewDistance = viewDistance;
        }

        private void onLevelChange(long key, int level, boolean saw, boolean sees) {
            if (saw != sees) {
                Ticket ticket = new Ticket(TicketType.PLAYER_LOADING, DistanceManager.PLAYER_TICKET_LEVEL);

                if (sees) {
                    DistanceManager.this.ticketDispatcher.submit(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            if (this.haveTicketFor(this.getLevel(key))) {
                                DistanceManager.this.ticketStorage.addTicket(key, ticket);
                                DistanceManager.this.ticketsToRelease.add(key);
                            } else {
                                DistanceManager.this.ticketDispatcher.release(key, () -> {
                                }, false);
                            }

                        });
                    }, key, () -> {
                        return level;
                    });
                } else {
                    DistanceManager.this.ticketDispatcher.release(key, () -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            DistanceManager.this.ticketStorage.removeTicket(key, ticket);
                        });
                    }, true);
                }
            }

        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);

                    if (j != k) {
                        DistanceManager.this.ticketDispatcher.onLevelChange(new ChunkPos(i), () -> {
                            return this.queueLevels.get(i);
                        }, k, (l) -> {
                            if (l >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, l);
                            }

                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }

        }

        private boolean haveTicketFor(int level) {
            return level <= this.viewDistance;
        }
    }
}
