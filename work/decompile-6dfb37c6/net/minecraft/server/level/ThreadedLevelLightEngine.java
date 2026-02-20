package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ThreadedLevelLightEngine extends LevelLightEngine implements AutoCloseable {

    public static final int DEFAULT_BATCH_SIZE = 1000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ConsecutiveExecutor consecutiveExecutor;
    private final ObjectList<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> lightTasks = new ObjectArrayList();
    private final ChunkMap chunkMap;
    private final ChunkTaskDispatcher taskDispatcher;
    private final int taskPerBatch = 1000;
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ThreadedLevelLightEngine(LightChunkGetter lightChunkGetter, ChunkMap chunkMap, boolean hasSkyLight, ConsecutiveExecutor consecutiveExecutor, ChunkTaskDispatcher taskDispatcher) {
        super(lightChunkGetter, true, hasSkyLight);
        this.chunkMap = chunkMap;
        this.taskDispatcher = taskDispatcher;
        this.consecutiveExecutor = consecutiveExecutor;
    }

    public void close() {}

    @Override
    public int runLightUpdates() {
        throw (UnsupportedOperationException) Util.pauseInIde(new UnsupportedOperationException("Ran automatically on a different thread!"));
    }

    @Override
    public void checkBlock(BlockPos pos) {
        BlockPos blockpos1 = pos.immutable();

        this.addTask(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.checkBlock(blockpos1);
        }, () -> {
            return "checkBlock " + String.valueOf(blockpos1);
        }));
    }

    protected void updateChunkStatus(ChunkPos pos) {
        this.addTask(pos.x, pos.z, () -> {
            return 0;
        }, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(pos, false);
            super.setLightEnabled(pos, false);

            for (int i = this.getMinLightSection(); i < this.getMaxLightSection(); ++i) {
                super.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, i), (DataLayer) null);
                super.queueSectionData(LightLayer.SKY, SectionPos.of(pos, i), (DataLayer) null);
            }

            for (int j = this.levelHeightAccessor.getMinSectionY(); j <= this.levelHeightAccessor.getMaxSectionY(); ++j) {
                super.updateSectionStatus(SectionPos.of(pos, j), true);
            }

        }, () -> {
            return "updateChunkStatus " + String.valueOf(pos) + " true";
        }));
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        this.addTask(pos.x(), pos.z(), () -> {
            return 0;
        }, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.updateSectionStatus(pos, sectionEmpty);
        }, () -> {
            String s = String.valueOf(pos);

            return "updateSectionStatus " + s + " " + sectionEmpty;
        }));
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        this.addTask(pos.x, pos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.propagateLightSources(pos);
        }, () -> {
            return "propagateLight " + String.valueOf(pos);
        }));
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        this.addTask(pos.x, pos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.setLightEnabled(pos, enable);
        }, () -> {
            String s = String.valueOf(pos);

            return "enableLight " + s + " " + enable;
        }));
    }

    @Override
    public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
        this.addTask(pos.x(), pos.z(), () -> {
            return 0;
        }, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.queueSectionData(layer, pos, data);
        }, () -> {
            return "queueData " + String.valueOf(pos);
        }));
    }

    private void addTask(int chunkX, int chunkZ, ThreadedLevelLightEngine.TaskType type, Runnable runnable) {
        this.addTask(chunkX, chunkZ, this.chunkMap.getChunkQueueLevel(ChunkPos.asLong(chunkX, chunkZ)), type, runnable);
    }

    private void addTask(int chunkX, int chunkZ, IntSupplier level, ThreadedLevelLightEngine.TaskType type, Runnable runnable) {
        this.taskDispatcher.submit(() -> {
            this.lightTasks.add(Pair.of(type, runnable));
            if (this.lightTasks.size() >= 1000) {
                this.runUpdate();
            }

        }, ChunkPos.asLong(chunkX, chunkZ), level);
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
        this.addTask(pos.x, pos.z, () -> {
            return 0;
        }, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            super.retainData(pos, retain);
        }, () -> {
            return "retainData " + String.valueOf(pos);
        }));
    }

    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lighted) {
        ChunkPos chunkpos = chunk.getPos();

        this.addTask(chunkpos.x, chunkpos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            LevelChunkSection[] alevelchunksection = chunk.getSections();

            for (int i = 0; i < chunk.getSectionsCount(); ++i) {
                LevelChunkSection levelchunksection = alevelchunksection[i];

                if (!levelchunksection.hasOnlyAir()) {
                    int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);

                    super.updateSectionStatus(SectionPos.of(chunkpos, j), false);
                }
            }

        }, () -> {
            return "initializeLight: " + String.valueOf(chunkpos);
        }));
        return CompletableFuture.supplyAsync(() -> {
            super.setLightEnabled(chunkpos, lighted);
            super.retainData(chunkpos, false);
            return chunk;
        }, (runnable) -> {
            this.addTask(chunkpos.x, chunkpos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable);
        });
    }

    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess centerChunk, boolean lighted) {
        ChunkPos chunkpos = centerChunk.getPos();

        centerChunk.setLightCorrect(false);
        this.addTask(chunkpos.x, chunkpos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, Util.name(() -> {
            if (!lighted) {
                super.propagateLightSources(chunkpos);
            }

            if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
                ThreadedLevelLightEngine.LOGGER.debug("LIT {}", chunkpos);
            }

        }, () -> {
            String s = String.valueOf(chunkpos);

            return "lightChunk " + s + " " + lighted;
        }));
        return CompletableFuture.supplyAsync(() -> {
            centerChunk.setLightCorrect(true);
            return centerChunk;
        }, (runnable) -> {
            this.addTask(chunkpos.x, chunkpos.z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable);
        });
    }

    public void tryScheduleUpdate() {
        if ((!this.lightTasks.isEmpty() || super.hasLightWork()) && this.scheduled.compareAndSet(false, true)) {
            this.consecutiveExecutor.schedule(() -> {
                this.runUpdate();
                this.scheduled.set(false);
            });
        }

    }

    private void runUpdate() {
        int i = Math.min(this.lightTasks.size(), 1000);
        ObjectListIterator<Pair<ThreadedLevelLightEngine.TaskType, Runnable>> objectlistiterator = this.lightTasks.iterator();

        int j;

        for (j = 0; objectlistiterator.hasNext() && j < i; ++j) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair = (Pair) objectlistiterator.next();

            if (pair.getFirst() == ThreadedLevelLightEngine.TaskType.PRE_UPDATE) {
                ((Runnable) pair.getSecond()).run();
            }
        }

        objectlistiterator.back(j);
        super.runLightUpdates();

        for (int k = 0; objectlistiterator.hasNext() && k < i; ++k) {
            Pair<ThreadedLevelLightEngine.TaskType, Runnable> pair1 = (Pair) objectlistiterator.next();

            if (pair1.getFirst() == ThreadedLevelLightEngine.TaskType.POST_UPDATE) {
                ((Runnable) pair1.getSecond()).run();
            }

            objectlistiterator.remove();
        }

    }

    public CompletableFuture<?> waitForPendingTasks(int chunkX, int chunkZ) {
        return CompletableFuture.runAsync(() -> {
        }, (runnable) -> {
            this.addTask(chunkX, chunkZ, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable);
        });
    }

    private static enum TaskType {

        PRE_UPDATE, POST_UPDATE;

        private TaskType() {}
    }
}
