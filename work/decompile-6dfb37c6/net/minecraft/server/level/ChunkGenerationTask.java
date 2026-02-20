package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public class ChunkGenerationTask {

    private final GeneratingChunkMap chunkMap;
    private final ChunkPos pos;
    private @Nullable ChunkStatus scheduledStatus = null;
    public final ChunkStatus targetStatus;
    private volatile boolean markedForCancellation;
    private final List<CompletableFuture<ChunkResult<ChunkAccess>>> scheduledLayer = new ArrayList();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private boolean needsGeneration;

    private ChunkGenerationTask(GeneratingChunkMap chunkMap, ChunkStatus targetStatus, ChunkPos pos, StaticCache2D<GenerationChunkHolder> cache) {
        this.chunkMap = chunkMap;
        this.targetStatus = targetStatus;
        this.pos = pos;
        this.cache = cache;
    }

    public static ChunkGenerationTask create(GeneratingChunkMap chunkMap, ChunkStatus targetStatus, ChunkPos pos) {
        int i = ChunkPyramid.GENERATION_PYRAMID.getStepTo(targetStatus).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        StaticCache2D<GenerationChunkHolder> staticcache2d = StaticCache2D.<GenerationChunkHolder>create(pos.x, pos.z, i, (j, k) -> {
            return chunkMap.acquireGeneration(ChunkPos.asLong(j, k));
        });

        return new ChunkGenerationTask(chunkMap, targetStatus, pos, staticcache2d);
    }

    public @Nullable CompletableFuture<?> runUntilWait() {
        while (true) {
            CompletableFuture<?> completablefuture = this.waitForScheduledLayer();

            if (completablefuture != null) {
                return completablefuture;
            }

            if (this.markedForCancellation || this.scheduledStatus == this.targetStatus) {
                this.releaseClaim();
                return null;
            }

            this.scheduleNextLayer();
        }
    }

    private void scheduleNextLayer() {
        ChunkStatus chunkstatus;

        if (this.scheduledStatus == null) {
            chunkstatus = ChunkStatus.EMPTY;
        } else if (!this.needsGeneration && this.scheduledStatus == ChunkStatus.EMPTY && !this.canLoadWithoutGeneration()) {
            this.needsGeneration = true;
            chunkstatus = ChunkStatus.EMPTY;
        } else {
            chunkstatus = (ChunkStatus) ChunkStatus.getStatusList().get(this.scheduledStatus.getIndex() + 1);
        }

        this.scheduleLayer(chunkstatus, this.needsGeneration);
        this.scheduledStatus = chunkstatus;
    }

    public void markForCancellation() {
        this.markedForCancellation = true;
    }

    private void releaseClaim() {
        GenerationChunkHolder generationchunkholder = this.cache.get(this.pos.x, this.pos.z);

        generationchunkholder.removeTask(this);
        StaticCache2D staticcache2d = this.cache;
        GeneratingChunkMap generatingchunkmap = this.chunkMap;

        Objects.requireNonNull(this.chunkMap);
        staticcache2d.forEach(generatingchunkmap::releaseGeneration);
    }

    private boolean canLoadWithoutGeneration() {
        if (this.targetStatus == ChunkStatus.EMPTY) {
            return true;
        } else {
            ChunkStatus chunkstatus = ((GenerationChunkHolder) this.cache.get(this.pos.x, this.pos.z)).getPersistedStatus();

            if (chunkstatus != null && !chunkstatus.isBefore(this.targetStatus)) {
                ChunkDependencies chunkdependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(this.targetStatus).accumulatedDependencies();
                int i = chunkdependencies.getRadius();

                for (int j = this.pos.x - i; j <= this.pos.x + i; ++j) {
                    for (int k = this.pos.z - i; k <= this.pos.z + i; ++k) {
                        int l = this.pos.getChessboardDistance(j, k);
                        ChunkStatus chunkstatus1 = chunkdependencies.get(l);
                        ChunkStatus chunkstatus2 = ((GenerationChunkHolder) this.cache.get(j, k)).getPersistedStatus();

                        if (chunkstatus2 == null || chunkstatus2.isBefore(chunkstatus1)) {
                            return false;
                        }
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public GenerationChunkHolder getCenter() {
        return this.cache.get(this.pos.x, this.pos.z);
    }

    private void scheduleLayer(ChunkStatus status, boolean needsGeneration) {
        try (Zone zone = Profiler.get().zone("scheduleLayer")) {
            Objects.requireNonNull(status);
            zone.addText(status::getName);
            int i = this.getRadiusForLayer(status, needsGeneration);

            for (int j = this.pos.x - i; j <= this.pos.x + i; ++j) {
                for (int k = this.pos.z - i; k <= this.pos.z + i; ++k) {
                    GenerationChunkHolder generationchunkholder = this.cache.get(j, k);

                    if (this.markedForCancellation || !this.scheduleChunkInLayer(status, needsGeneration, generationchunkholder)) {
                        return;
                    }
                }
            }
        }

    }

    private int getRadiusForLayer(ChunkStatus status, boolean needsGeneration) {
        ChunkPyramid chunkpyramid = needsGeneration ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;

        return chunkpyramid.getStepTo(this.targetStatus).getAccumulatedRadiusOf(status);
    }

    private boolean scheduleChunkInLayer(ChunkStatus status, boolean needsGeneration, GenerationChunkHolder chunkHolder) {
        ChunkStatus chunkstatus1 = chunkHolder.getPersistedStatus();
        boolean flag1 = chunkstatus1 != null && status.isAfter(chunkstatus1);
        ChunkPyramid chunkpyramid = flag1 ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;

        if (flag1 && !needsGeneration) {
            throw new IllegalStateException("Can't load chunk, but didn't expect to need to generate");
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = chunkHolder.applyStep(chunkpyramid.getStepTo(status), this.chunkMap, this.cache);
            ChunkResult<ChunkAccess> chunkresult = (ChunkResult) completablefuture.getNow((Object) null);

            if (chunkresult == null) {
                this.scheduledLayer.add(completablefuture);
                return true;
            } else if (chunkresult.isSuccess()) {
                return true;
            } else {
                this.markForCancellation();
                return false;
            }
        }
    }

    private @Nullable CompletableFuture<?> waitForScheduledLayer() {
        while (!this.scheduledLayer.isEmpty()) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.scheduledLayer.getLast();
            ChunkResult<ChunkAccess> chunkresult = (ChunkResult) completablefuture.getNow((Object) null);

            if (chunkresult == null) {
                return completablefuture;
            }

            this.scheduledLayer.removeLast();
            if (!chunkresult.isSuccess()) {
                this.markForCancellation();
            }
        }

        return null;
    }
}
