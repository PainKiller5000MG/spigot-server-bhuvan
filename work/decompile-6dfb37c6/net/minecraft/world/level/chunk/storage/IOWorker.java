package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.thread.PriorityConsecutiveExecutor;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class IOWorker implements AutoCloseable, ChunkScanAccess {

    public static final Supplier<CompoundTag> STORE_EMPTY = () -> {
        return null;
    };
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final PriorityConsecutiveExecutor consecutiveExecutor;
    private final RegionFileStorage storage;
    private final SequencedMap<ChunkPos, IOWorker.PendingStore> pendingWrites = new LinkedHashMap();
    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<BitSet>> regionCacheForBlender = new Long2ObjectLinkedOpenHashMap();
    private static final int REGION_CACHE_SIZE = 1024;

    protected IOWorker(RegionStorageInfo info, Path dir, boolean sync) {
        this.storage = new RegionFileStorage(info, dir, sync);
        this.consecutiveExecutor = new PriorityConsecutiveExecutor(IOWorker.Priority.values().length, Util.ioPool(), "IOWorker-" + info.type());
    }

    public boolean isOldChunkAround(ChunkPos pos, int range) {
        ChunkPos chunkpos1 = new ChunkPos(pos.x - range, pos.z - range);
        ChunkPos chunkpos2 = new ChunkPos(pos.x + range, pos.z + range);

        for (int j = chunkpos1.getRegionX(); j <= chunkpos2.getRegionX(); ++j) {
            for (int k = chunkpos1.getRegionZ(); k <= chunkpos2.getRegionZ(); ++k) {
                BitSet bitset = (BitSet) this.getOrCreateOldDataForRegion(j, k).join();

                if (!bitset.isEmpty()) {
                    ChunkPos chunkpos3 = ChunkPos.minFromRegion(j, k);
                    int l = Math.max(chunkpos1.x - chunkpos3.x, 0);
                    int i1 = Math.max(chunkpos1.z - chunkpos3.z, 0);
                    int j1 = Math.min(chunkpos2.x - chunkpos3.x, 31);
                    int k1 = Math.min(chunkpos2.z - chunkpos3.z, 31);

                    for (int l1 = l; l1 <= j1; ++l1) {
                        for (int i2 = i1; i2 <= k1; ++i2) {
                            int j2 = i2 * 32 + l1;

                            if (bitset.get(j2)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private CompletableFuture<BitSet> getOrCreateOldDataForRegion(int regionX, int regionZ) {
        long k = ChunkPos.asLong(regionX, regionZ);

        synchronized (this.regionCacheForBlender) {
            CompletableFuture<BitSet> completablefuture = (CompletableFuture) this.regionCacheForBlender.getAndMoveToFirst(k);

            if (completablefuture == null) {
                completablefuture = this.createOldDataForRegion(regionX, regionZ);
                this.regionCacheForBlender.putAndMoveToFirst(k, completablefuture);
                if (this.regionCacheForBlender.size() > 1024) {
                    this.regionCacheForBlender.removeLast();
                }
            }

            return completablefuture;
        }
    }

    private CompletableFuture<BitSet> createOldDataForRegion(int regionX, int regionZ) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkpos = ChunkPos.minFromRegion(regionX, regionZ);
            ChunkPos chunkpos1 = ChunkPos.maxFromRegion(regionX, regionZ);
            BitSet bitset = new BitSet();

            ChunkPos.rangeClosed(chunkpos, chunkpos1).forEach((chunkpos2) -> {
                CollectFields collectfields = new CollectFields(new FieldSelector[]{new FieldSelector(IntTag.TYPE, "DataVersion"), new FieldSelector(CompoundTag.TYPE, "blending_data")});

                try {
                    this.scanChunk(chunkpos2, collectfields).join();
                } catch (Exception exception) {
                    IOWorker.LOGGER.warn("Failed to scan chunk {}", chunkpos2, exception);
                    return;
                }

                Tag tag = collectfields.getResult();

                if (tag instanceof CompoundTag compoundtag) {
                    if (this.isOldChunk(compoundtag)) {
                        int k = chunkpos2.getRegionLocalZ() * 32 + chunkpos2.getRegionLocalX();

                        bitset.set(k);
                    }
                }

            });
            return bitset;
        }, Util.backgroundExecutor());
    }

    private boolean isOldChunk(CompoundTag tag) {
        return tag.getIntOr("DataVersion", 0) < 4295 ? true : tag.getCompound("blending_data").isPresent();
    }

    public CompletableFuture<Void> store(ChunkPos pos, CompoundTag value) {
        return this.store(pos, () -> {
            return value;
        });
    }

    public CompletableFuture<Void> store(ChunkPos pos, Supplier<CompoundTag> supplier) {
        return this.submitTask(() -> {
            CompoundTag compoundtag = (CompoundTag) supplier.get();
            IOWorker.PendingStore ioworker_pendingstore = (IOWorker.PendingStore) this.pendingWrites.computeIfAbsent(pos, (chunkpos1) -> {
                return new IOWorker.PendingStore(compoundtag);
            });

            ioworker_pendingstore.data = compoundtag;
            return ioworker_pendingstore.result;
        }).thenCompose(Function.identity());
    }

    public CompletableFuture<Optional<CompoundTag>> loadAsync(ChunkPos pos) {
        return this.<Optional<CompoundTag>>submitThrowingTask(() -> {
            IOWorker.PendingStore ioworker_pendingstore = (IOWorker.PendingStore) this.pendingWrites.get(pos);

            if (ioworker_pendingstore != null) {
                return Optional.ofNullable(ioworker_pendingstore.copyData());
            } else {
                try {
                    CompoundTag compoundtag = this.storage.read(pos);

                    return Optional.ofNullable(compoundtag);
                } catch (Exception exception) {
                    IOWorker.LOGGER.warn("Failed to read chunk {}", pos, exception);
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<Void> synchronize(boolean flush) {
        CompletableFuture<Void> completablefuture = this.submitTask(() -> {
            return CompletableFuture.allOf((CompletableFuture[]) this.pendingWrites.values().stream().map((ioworker_pendingstore) -> {
                return ioworker_pendingstore.result;
            }).toArray((i) -> {
                return new CompletableFuture[i];
            }));
        }).thenCompose(Function.identity());

        return flush ? completablefuture.thenCompose((ovoid) -> {
            return this.submitThrowingTask(() -> {
                try {
                    this.storage.flush();
                    return null;
                } catch (Exception exception) {
                    IOWorker.LOGGER.warn("Failed to synchronize chunks", exception);
                    throw exception;
                }
            });
        }) : completablefuture.thenCompose((ovoid) -> {
            return this.submitTask(() -> {
                return null;
            });
        });
    }

    @Override
    public CompletableFuture<Void> scanChunk(ChunkPos pos, StreamTagVisitor visitor) {
        return this.<Void>submitThrowingTask(() -> {
            try {
                IOWorker.PendingStore ioworker_pendingstore = (IOWorker.PendingStore) this.pendingWrites.get(pos);

                if (ioworker_pendingstore != null) {
                    if (ioworker_pendingstore.data != null) {
                        ioworker_pendingstore.data.acceptAsRoot(visitor);
                    }
                } else {
                    this.storage.scanChunk(pos, visitor);
                }

                return null;
            } catch (Exception exception) {
                IOWorker.LOGGER.warn("Failed to bulk scan chunk {}", pos, exception);
                throw exception;
            }
        });
    }

    private <T> CompletableFuture<T> submitThrowingTask(IOWorker.ThrowingSupplier<T> task) {
        return this.consecutiveExecutor.<T>scheduleWithResult(IOWorker.Priority.FOREGROUND.ordinal(), (completablefuture) -> {
            if (!this.shutdownRequested.get()) {
                try {
                    completablefuture.complete(task.get());
                } catch (Exception exception) {
                    completablefuture.completeExceptionally(exception);
                }
            }

            this.tellStorePending();
        });
    }

    private <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return this.consecutiveExecutor.<T>scheduleWithResult(IOWorker.Priority.FOREGROUND.ordinal(), (completablefuture) -> {
            if (!this.shutdownRequested.get()) {
                completablefuture.complete(task.get());
            }

            this.tellStorePending();
        });
    }

    private void storePendingChunk() {
        Map.Entry<ChunkPos, IOWorker.PendingStore> map_entry = this.pendingWrites.pollFirstEntry();

        if (map_entry != null) {
            this.runStore((ChunkPos) map_entry.getKey(), (IOWorker.PendingStore) map_entry.getValue());
            this.tellStorePending();
        }
    }

    private void tellStorePending() {
        this.consecutiveExecutor.schedule(new StrictQueue.RunnableWithPriority(IOWorker.Priority.BACKGROUND.ordinal(), this::storePendingChunk));
    }

    private void runStore(ChunkPos pos, IOWorker.PendingStore write) {
        try {
            this.storage.write(pos, write.data);
            write.result.complete((Object) null);
        } catch (Exception exception) {
            IOWorker.LOGGER.error("Failed to store chunk {}", pos, exception);
            write.result.completeExceptionally(exception);
        }

    }

    public void close() throws IOException {
        if (this.shutdownRequested.compareAndSet(false, true)) {
            this.waitForShutdown();
            this.consecutiveExecutor.close();

            try {
                this.storage.close();
            } catch (Exception exception) {
                IOWorker.LOGGER.error("Failed to close storage", exception);
            }

        }
    }

    private void waitForShutdown() {
        this.consecutiveExecutor.scheduleWithResult(IOWorker.Priority.SHUTDOWN.ordinal(), (completablefuture) -> {
            completablefuture.complete(Unit.INSTANCE);
        }).join();
    }

    public RegionStorageInfo storageInfo() {
        return this.storage.info();
    }

    private static enum Priority {

        FOREGROUND, BACKGROUND, SHUTDOWN;

        private Priority() {}
    }

    private static class PendingStore {

        private @Nullable CompoundTag data;
        private final CompletableFuture<Void> result = new CompletableFuture();

        public PendingStore(@Nullable CompoundTag data) {
            this.data = data;
        }

        private @Nullable CompoundTag copyData() {
            CompoundTag compoundtag = this.data;

            return compoundtag == null ? null : compoundtag.copy();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {

        @Nullable
        T get() throws Exception;
    }
}
