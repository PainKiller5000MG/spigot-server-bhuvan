package net.minecraft.server.level;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public class ChunkHolder extends GenerationChunkHolder {

    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(ChunkHolder.UNLOADED_LEVEL_CHUNK);
    private final LevelHeightAccessor levelHeightAccessor;
    private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture;
    private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture;
    private volatile CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture;
    public int oldTicketLevel;
    private int ticketLevel;
    private int queueLevel;
    private boolean hasChangedSections;
    private final @Nullable ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter;
    private final BitSet skyChangedLightSectionFilter;
    private final LevelLightEngine lightEngine;
    private final ChunkHolder.LevelChangeListener onLevelChange;
    public final ChunkHolder.PlayerProvider playerProvider;
    private boolean wasAccessibleSinceLastSave;
    private CompletableFuture<?> pendingFullStateConfirmation;
    private CompletableFuture<?> sendSync;
    private CompletableFuture<?> saveSync;

    public ChunkHolder(ChunkPos pos, int ticketLevel, LevelHeightAccessor levelHeightAccessor, LevelLightEngine lightEngine, ChunkHolder.LevelChangeListener onLevelChange, ChunkHolder.PlayerProvider playerProvider) {
        super(pos);
        this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        this.blockChangedLightSectionFilter = new BitSet();
        this.skyChangedLightSectionFilter = new BitSet();
        this.pendingFullStateConfirmation = CompletableFuture.completedFuture((Object) null);
        this.sendSync = CompletableFuture.completedFuture((Object) null);
        this.saveSync = CompletableFuture.completedFuture((Object) null);
        this.levelHeightAccessor = levelHeightAccessor;
        this.lightEngine = lightEngine;
        this.onLevelChange = onLevelChange;
        this.playerProvider = playerProvider;
        this.oldTicketLevel = ChunkLevel.MAX_LEVEL + 1;
        this.ticketLevel = this.oldTicketLevel;
        this.queueLevel = this.oldTicketLevel;
        this.setTicketLevel(ticketLevel);
        this.changedBlocksPerSection = new ShortSet[levelHeightAccessor.getSectionsCount()];
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        return this.tickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
        return this.entityTickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
        return this.fullChunkFuture;
    }

    public @Nullable LevelChunk getTickingChunk() {
        return (LevelChunk) ((ChunkResult) this.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)).orElse((Object) null);
    }

    public @Nullable LevelChunk getChunkToSend() {
        return !this.sendSync.isDone() ? null : this.getTickingChunk();
    }

    public CompletableFuture<?> getSendSyncFuture() {
        return this.sendSync;
    }

    public void addSendDependency(CompletableFuture<?> sync) {
        if (this.sendSync.isDone()) {
            this.sendSync = sync;
        } else {
            this.sendSync = this.sendSync.thenCombine(sync, (object, object1) -> {
                return null;
            });
        }

    }

    public CompletableFuture<?> getSaveSyncFuture() {
        return this.saveSync;
    }

    public boolean isReadyForSaving() {
        return this.saveSync.isDone();
    }

    @Override
    protected void addSaveDependency(CompletableFuture<?> sync) {
        if (this.saveSync.isDone()) {
            this.saveSync = sync;
        } else {
            this.saveSync = this.saveSync.thenCombine(sync, (object, object1) -> {
                return null;
            });
        }

    }

    public boolean blockChanged(BlockPos pos) {
        LevelChunk levelchunk = this.getTickingChunk();

        if (levelchunk == null) {
            return false;
        } else {
            boolean flag = this.hasChangedSections;
            int i = this.levelHeightAccessor.getSectionIndex(pos.getY());
            ShortSet shortset = this.changedBlocksPerSection[i];

            if (shortset == null) {
                this.hasChangedSections = true;
                shortset = new ShortOpenHashSet();
                this.changedBlocksPerSection[i] = shortset;
            }

            shortset.add(SectionPos.sectionRelativePos(pos));
            return !flag;
        }
    }

    public boolean sectionLightChanged(LightLayer layer, int chunkY) {
        ChunkAccess chunkaccess = this.getChunkIfPresent(ChunkStatus.INITIALIZE_LIGHT);

        if (chunkaccess == null) {
            return false;
        } else {
            chunkaccess.markUnsaved();
            LevelChunk levelchunk = this.getTickingChunk();

            if (levelchunk == null) {
                return false;
            } else {
                int j = this.lightEngine.getMinLightSection();
                int k = this.lightEngine.getMaxLightSection();

                if (chunkY >= j && chunkY <= k) {
                    BitSet bitset = layer == LightLayer.SKY ? this.skyChangedLightSectionFilter : this.blockChangedLightSectionFilter;
                    int l = chunkY - j;

                    if (!bitset.get(l)) {
                        bitset.set(l);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
    }

    public boolean hasChangesToBroadcast() {
        return this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty();
    }

    public void broadcastChanges(LevelChunk chunk) {
        if (this.hasChangesToBroadcast()) {
            Level level = chunk.getLevel();

            if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                List<ServerPlayer> list = this.playerProvider.getPlayers(this.pos, true);

                if (!list.isEmpty()) {
                    ClientboundLightUpdatePacket clientboundlightupdatepacket = new ClientboundLightUpdatePacket(chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter);

                    this.broadcast(list, clientboundlightupdatepacket);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                List<ServerPlayer> list1 = this.playerProvider.getPlayers(this.pos, false);

                for (int i = 0; i < this.changedBlocksPerSection.length; ++i) {
                    ShortSet shortset = this.changedBlocksPerSection[i];

                    if (shortset != null) {
                        this.changedBlocksPerSection[i] = null;
                        if (!list1.isEmpty()) {
                            int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                            SectionPos sectionpos = SectionPos.of(chunk.getPos(), j);

                            if (shortset.size() == 1) {
                                BlockPos blockpos = sectionpos.relativeToBlockPos(shortset.iterator().nextShort());
                                BlockState blockstate = level.getBlockState(blockpos);

                                this.broadcast(list1, new ClientboundBlockUpdatePacket(blockpos, blockstate));
                                this.broadcastBlockEntityIfNeeded(list1, level, blockpos, blockstate);
                            } else {
                                LevelChunkSection levelchunksection = chunk.getSection(i);
                                ClientboundSectionBlocksUpdatePacket clientboundsectionblocksupdatepacket = new ClientboundSectionBlocksUpdatePacket(sectionpos, shortset, levelchunksection);

                                this.broadcast(list1, clientboundsectionblocksupdatepacket);
                                clientboundsectionblocksupdatepacket.runUpdates((blockpos1, blockstate1) -> {
                                    this.broadcastBlockEntityIfNeeded(list1, level, blockpos1, blockstate1);
                                });
                            }
                        }
                    }
                }

                this.hasChangedSections = false;
            }
        }
    }

    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            this.broadcastBlockEntity(players, level, pos);
        }

    }

    private void broadcastBlockEntity(List<ServerPlayer> players, Level level, BlockPos blockPos) {
        BlockEntity blockentity = level.getBlockEntity(blockPos);

        if (blockentity != null) {
            Packet<?> packet = blockentity.getUpdatePacket();

            if (packet != null) {
                this.broadcast(players, packet);
            }
        }

    }

    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
        players.forEach((serverplayer) -> {
            serverplayer.connection.send(packet);
        });
    }

    @Override
    public int getTicketLevel() {
        return this.ticketLevel;
    }

    @Override
    public int getQueueLevel() {
        return this.queueLevel;
    }

    private void setQueueLevel(int queueLevel) {
        this.queueLevel = queueLevel;
    }

    public void setTicketLevel(int ticketLevel) {
        this.ticketLevel = ticketLevel;
    }

    private void scheduleFullChunkPromotion(ChunkMap scheduler, CompletableFuture<ChunkResult<LevelChunk>> task, Executor mainThreadExecutor, FullChunkStatus status) {
        this.pendingFullStateConfirmation.cancel(false);
        CompletableFuture<Void> completablefuture1 = new CompletableFuture();

        completablefuture1.thenRunAsync(() -> {
            scheduler.onFullChunkStatusChange(this.pos, status);
        }, mainThreadExecutor);
        this.pendingFullStateConfirmation = completablefuture1;
        task.thenAccept((chunkresult) -> {
            chunkresult.ifSuccess((levelchunk) -> {
                completablefuture1.complete((Object) null);
            });
        });
    }

    private void demoteFullChunk(ChunkMap scheduler, FullChunkStatus status) {
        this.pendingFullStateConfirmation.cancel(false);
        scheduler.onFullChunkStatusChange(this.pos, status);
    }

    protected void updateFutures(ChunkMap scheduler, Executor mainThreadExecutor) {
        FullChunkStatus fullchunkstatus = ChunkLevel.fullStatus(this.oldTicketLevel);
        FullChunkStatus fullchunkstatus1 = ChunkLevel.fullStatus(this.ticketLevel);
        boolean flag = fullchunkstatus.isOrAfter(FullChunkStatus.FULL);
        boolean flag1 = fullchunkstatus1.isOrAfter(FullChunkStatus.FULL);

        this.wasAccessibleSinceLastSave |= flag1;
        if (!flag && flag1) {
            this.fullChunkFuture = scheduler.prepareAccessibleChunk(this);
            this.scheduleFullChunkPromotion(scheduler, this.fullChunkFuture, mainThreadExecutor, FullChunkStatus.FULL);
            this.addSaveDependency(this.fullChunkFuture);
        }

        if (flag && !flag1) {
            this.fullChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.fullChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean flag2 = fullchunkstatus.isOrAfter(FullChunkStatus.BLOCK_TICKING);
        boolean flag3 = fullchunkstatus1.isOrAfter(FullChunkStatus.BLOCK_TICKING);

        if (!flag2 && flag3) {
            this.tickingChunkFuture = scheduler.prepareTickingChunk(this);
            this.scheduleFullChunkPromotion(scheduler, this.tickingChunkFuture, mainThreadExecutor, FullChunkStatus.BLOCK_TICKING);
            this.addSaveDependency(this.tickingChunkFuture);
        }

        if (flag2 && !flag3) {
            this.tickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.tickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean flag4 = fullchunkstatus.isOrAfter(FullChunkStatus.ENTITY_TICKING);
        boolean flag5 = fullchunkstatus1.isOrAfter(FullChunkStatus.ENTITY_TICKING);

        if (!flag4 && flag5) {
            if (this.entityTickingChunkFuture != ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException());
            }

            this.entityTickingChunkFuture = scheduler.prepareEntityTickingChunk(this);
            this.scheduleFullChunkPromotion(scheduler, this.entityTickingChunkFuture, mainThreadExecutor, FullChunkStatus.ENTITY_TICKING);
            this.addSaveDependency(this.entityTickingChunkFuture);
        }

        if (flag4 && !flag5) {
            this.entityTickingChunkFuture.complete(ChunkHolder.UNLOADED_LEVEL_CHUNK);
            this.entityTickingChunkFuture = ChunkHolder.UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        if (!fullchunkstatus1.isOrAfter(fullchunkstatus)) {
            this.demoteFullChunk(scheduler, fullchunkstatus1);
        }

        this.onLevelChange.onLevelChange(this.pos, this::getQueueLevel, this.ticketLevel, this::setQueueLevel);
        this.oldTicketLevel = this.ticketLevel;
    }

    public boolean wasAccessibleSinceLastSave() {
        return this.wasAccessibleSinceLastSave;
    }

    public void refreshAccessibility() {
        this.wasAccessibleSinceLastSave = ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL);
    }

    @FunctionalInterface
    public interface LevelChangeListener {

        void onLevelChange(ChunkPos pos, IntSupplier oldLevel, int newLevel, IntConsumer setQueueLevel);
    }

    public interface PlayerProvider {

        List<ServerPlayer> getPlayers(ChunkPos pos, boolean borderOnly);
    }
}
