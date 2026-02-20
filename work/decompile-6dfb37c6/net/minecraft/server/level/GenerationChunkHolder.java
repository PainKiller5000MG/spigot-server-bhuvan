package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jspecify.annotations.Nullable;

public abstract class GenerationChunkHolder {

    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
    public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(GenerationChunkHolder.UNLOADED_CHUNK);
    protected final ChunkPos pos;
    private volatile @Nullable ChunkStatus highestAllowedStatus;
    private final AtomicReference<@Nullable ChunkStatus> startedWork = new AtomicReference();
    private final AtomicReferenceArray<@Nullable CompletableFuture<ChunkResult<ChunkAccess>>> futures;
    private final AtomicReference<@Nullable ChunkGenerationTask> task;
    private final AtomicInteger generationRefCount;
    private volatile CompletableFuture<Void> generationSaveSyncFuture;

    public GenerationChunkHolder(ChunkPos pos) {
        this.futures = new AtomicReferenceArray(GenerationChunkHolder.CHUNK_STATUSES.size());
        this.task = new AtomicReference();
        this.generationRefCount = new AtomicInteger();
        this.generationSaveSyncFuture = CompletableFuture.completedFuture((Object) null);
        this.pos = pos;
        if (!pos.isValid()) {
            throw new IllegalStateException("Trying to create chunk out of reasonable bounds: " + String.valueOf(pos));
        }
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus status, ChunkMap scheduler) {
        if (this.isStatusDisallowed(status)) {
            return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getOrCreateFuture(status);

            if (completablefuture.isDone()) {
                return completablefuture;
            } else {
                ChunkGenerationTask chunkgenerationtask = (ChunkGenerationTask) this.task.get();

                if (chunkgenerationtask == null || status.isAfter(chunkgenerationtask.targetStatus)) {
                    this.rescheduleChunkTask(scheduler, status);
                }

                return completablefuture;
            }
        }
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap chunkMap, StaticCache2D<GenerationChunkHolder> cache) {
        return this.isStatusDisallowed(step.targetStatus()) ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE : (this.acquireStatusBump(step.targetStatus()) ? chunkMap.applyStep(this, step, cache).handle((chunkaccess, throwable) -> {
            if (throwable != null) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception chunk generation/loading");

                MinecraftServer.setFatalException(new ReportedException(crashreport));
            } else {
                this.completeFuture(step.targetStatus(), chunkaccess);
            }

            return ChunkResult.of(chunkaccess);
        }) : this.getOrCreateFuture(step.targetStatus()));
    }

    protected void updateHighestAllowedStatus(ChunkMap scheduler) {
        ChunkStatus chunkstatus = this.highestAllowedStatus;
        ChunkStatus chunkstatus1 = ChunkLevel.generationStatus(this.getTicketLevel());

        this.highestAllowedStatus = chunkstatus1;
        boolean flag = chunkstatus != null && (chunkstatus1 == null || chunkstatus1.isBefore(chunkstatus));

        if (flag) {
            this.failAndClearPendingFuturesBetween(chunkstatus1, chunkstatus);
            if (this.task.get() != null) {
                this.rescheduleChunkTask(scheduler, this.findHighestStatusWithPendingFuture(chunkstatus1));
            }
        }

    }

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));

        for (int i = 0; i < this.futures.length() - 1; ++i) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = (CompletableFuture) this.futures.get(i);

            Objects.requireNonNull(completablefuture1);
            ChunkAccess chunkaccess = (ChunkAccess) ((ChunkResult) completablefuture1.getNow(GenerationChunkHolder.NOT_DONE_YET)).orElse((Object) null);

            if (!(chunkaccess instanceof ProtoChunk)) {
                throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + String.valueOf(chunkaccess));
            }

            if (!this.futures.compareAndSet(i, completablefuture1, completablefuture)) {
                throw new IllegalStateException("Future changed by other thread while trying to replace it");
            }
        }

    }

    void removeTask(ChunkGenerationTask task) {
        this.task.compareAndSet(task, (Object) null);
    }

    private void rescheduleChunkTask(ChunkMap scheduler, @Nullable ChunkStatus status) {
        ChunkGenerationTask chunkgenerationtask;

        if (status != null) {
            chunkgenerationtask = scheduler.scheduleGenerationTask(status, this.getPos());
        } else {
            chunkgenerationtask = null;
        }

        ChunkGenerationTask chunkgenerationtask1 = (ChunkGenerationTask) this.task.getAndSet(chunkgenerationtask);

        if (chunkgenerationtask1 != null) {
            chunkgenerationtask1.markForCancellation();
        }

    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus status) {
        if (this.isStatusDisallowed(status)) {
            return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
        } else {
            int i = status.getIndex();
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.futures.get(i);

            while (completablefuture == null) {
                CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = new CompletableFuture();

                completablefuture = (CompletableFuture) this.futures.compareAndExchange(i, (Object) null, completablefuture1);
                if (completablefuture == null) {
                    if (this.isStatusDisallowed(status)) {
                        this.failAndClearPendingFuture(i, completablefuture1);
                        return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
                    }

                    return completablefuture1;
                }
            }

            return completablefuture;
        }
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus fromExclusive, ChunkStatus toInclusive) {
        int i = fromExclusive == null ? 0 : fromExclusive.getIndex() + 1;
        int j = toInclusive.getIndex();

        for (int k = i; k <= j; ++k) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.futures.get(k);

            if (completablefuture != null) {
                this.failAndClearPendingFuture(k, completablefuture);
            }
        }

    }

    private void failAndClearPendingFuture(int index, CompletableFuture<ChunkResult<ChunkAccess>> previous) {
        if (previous.complete(GenerationChunkHolder.UNLOADED_CHUNK) && !this.futures.compareAndSet(index, previous, (Object) null)) {
            throw new IllegalStateException("Nothing else should replace the future here");
        }
    }

    private void completeFuture(ChunkStatus status, ChunkAccess chunk) {
        ChunkResult<ChunkAccess> chunkresult = ChunkResult.<ChunkAccess>of(chunk);
        int i = status.getIndex();

        while (true) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.futures.get(i);

            if (completablefuture == null) {
                if (this.futures.compareAndSet(i, (Object) null, CompletableFuture.completedFuture(chunkresult))) {
                    return;
                }
            } else {
                if (completablefuture.complete(chunkresult)) {
                    return;
                }

                if (((ChunkResult) completablefuture.getNow(GenerationChunkHolder.NOT_DONE_YET)).isSuccess()) {
                    throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                }

                Thread.yield();
            }
        }
    }

    private @Nullable ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus newStatus) {
        if (newStatus == null) {
            return null;
        } else {
            ChunkStatus chunkstatus1 = newStatus;

            for (ChunkStatus chunkstatus2 = (ChunkStatus) this.startedWork.get(); chunkstatus2 == null || chunkstatus1.isAfter(chunkstatus2); chunkstatus1 = chunkstatus1.getParent()) {
                if (this.futures.get(chunkstatus1.getIndex()) != null) {
                    return chunkstatus1;
                }

                if (chunkstatus1 == ChunkStatus.EMPTY) {
                    break;
                }
            }

            return null;
        }
    }

    private boolean acquireStatusBump(ChunkStatus status) {
        ChunkStatus chunkstatus1 = status == ChunkStatus.EMPTY ? null : status.getParent();
        ChunkStatus chunkstatus2 = (ChunkStatus) this.startedWork.compareAndExchange(chunkstatus1, status);

        if (chunkstatus2 == chunkstatus1) {
            return true;
        } else if (chunkstatus2 != null && !status.isAfter(chunkstatus2)) {
            return false;
        } else {
            String s = String.valueOf(chunkstatus2);

            throw new IllegalStateException("Unexpected last startedWork status: " + s + " while trying to start: " + String.valueOf(status));
        }
    }

    private boolean isStatusDisallowed(ChunkStatus status) {
        ChunkStatus chunkstatus1 = this.highestAllowedStatus;

        return chunkstatus1 == null || status.isAfter(chunkstatus1);
    }

    protected abstract void addSaveDependency(CompletableFuture<?> sync);

    public void increaseGenerationRefCount() {
        if (this.generationRefCount.getAndIncrement() == 0) {
            this.generationSaveSyncFuture = new CompletableFuture();
            this.addSaveDependency(this.generationSaveSyncFuture);
        }

    }

    public void decreaseGenerationRefCount() {
        CompletableFuture<Void> completablefuture = this.generationSaveSyncFuture;
        int i = this.generationRefCount.decrementAndGet();

        if (i == 0) {
            completablefuture.complete((Object) null);
        }

        if (i < 0) {
            throw new IllegalStateException("More releases than claims. Count: " + i);
        }
    }

    public @Nullable ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.futures.get(status.getIndex());

        return completablefuture == null ? null : (ChunkAccess) ((ChunkResult) completablefuture.getNow(GenerationChunkHolder.NOT_DONE_YET)).orElse((Object) null);
    }

    public @Nullable ChunkAccess getChunkIfPresent(ChunkStatus status) {
        return this.isStatusDisallowed(status) ? null : this.getChunkIfPresentUnchecked(status);
    }

    public @Nullable ChunkAccess getLatestChunk() {
        ChunkStatus chunkstatus = (ChunkStatus) this.startedWork.get();

        if (chunkstatus == null) {
            return null;
        } else {
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);

            return chunkaccess != null ? chunkaccess : this.getChunkIfPresentUnchecked(chunkstatus.getParent());
        }
    }

    public @Nullable ChunkStatus getPersistedStatus() {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = (CompletableFuture) this.futures.get(ChunkStatus.EMPTY.getIndex());
        ChunkAccess chunkaccess = completablefuture == null ? null : (ChunkAccess) ((ChunkResult) completablefuture.getNow(GenerationChunkHolder.NOT_DONE_YET)).orElse((Object) null);

        return chunkaccess == null ? null : chunkaccess.getPersistedStatus();
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ChunkLevel.fullStatus(this.getTicketLevel());
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, @Nullable CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList();

        for (int i = 0; i < GenerationChunkHolder.CHUNK_STATUSES.size(); ++i) {
            list.add(Pair.of((ChunkStatus) GenerationChunkHolder.CHUNK_STATUSES.get(i), (CompletableFuture) this.futures.get(i)));
        }

        return list;
    }

    @VisibleForDebug
    public @Nullable ChunkStatus getLatestStatus() {
        ChunkStatus chunkstatus = (ChunkStatus) this.startedWork.get();

        if (chunkstatus == null) {
            return null;
        } else {
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);

            return chunkaccess != null ? chunkstatus : chunkstatus.getParent();
        }
    }
}
