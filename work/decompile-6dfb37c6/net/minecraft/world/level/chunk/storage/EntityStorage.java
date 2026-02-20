package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

public class EntityStorage implements EntityPersistentStorage<Entity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTITIES_TAG = "Entities";
    private static final String POSITION_TAG = "Position";
    public final ServerLevel level;
    private final SimpleRegionStorage simpleRegionStorage;
    private final LongSet emptyChunks = new LongOpenHashSet();
    public final ConsecutiveExecutor entityDeserializerQueue;

    public EntityStorage(SimpleRegionStorage simpleRegionStorage, ServerLevel level, Executor mainThreadExecutor) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.level = level;
        this.entityDeserializerQueue = new ConsecutiveExecutor(mainThreadExecutor, "entity-deserializer");
    }

    @Override
    public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) {
        if (this.emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyChunk(pos));
        } else {
            CompletableFuture<Optional<CompoundTag>> completablefuture = this.simpleRegionStorage.read(pos);

            this.reportLoadFailureIfPresent(completablefuture, pos);
            Function function = (optional) -> {
                if (optional.isEmpty()) {
                    this.emptyChunks.add(pos.toLong());
                    return emptyChunk(pos);
                } else {
                    try {
                        ChunkPos chunkpos1 = (ChunkPos) ((CompoundTag) optional.get()).read("Position", ChunkPos.CODEC).orElseThrow();

                        if (!Objects.equals(pos, chunkpos1)) {
                            EntityStorage.LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", new Object[]{pos, pos, chunkpos1});
                            this.level.getServer().reportMisplacedChunk(chunkpos1, pos, this.simpleRegionStorage.storageInfo());
                        }
                    } catch (Exception exception) {
                        EntityStorage.LOGGER.warn("Failed to parse chunk {} position info", pos, exception);
                        this.level.getServer().reportChunkLoadFailure(exception, this.simpleRegionStorage.storageInfo(), pos);
                    }

                    CompoundTag compoundtag = this.simpleRegionStorage.upgradeChunkTag((CompoundTag) optional.get(), -1);

                    try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(pos), EntityStorage.LOGGER)) {
                        ValueInput valueinput = TagValueInput.create(problemreporter_scopedcollector, this.level.registryAccess(), compoundtag);
                        ValueInput.ValueInputList valueinput_valueinputlist = valueinput.childrenListOrEmpty("Entities");
                        List<Entity> list = EntityType.loadEntitiesRecursive(valueinput_valueinputlist, this.level, EntitySpawnReason.LOAD).toList();

                        return new ChunkEntities(pos, list);
                    }
                }
            };
            ConsecutiveExecutor consecutiveexecutor = this.entityDeserializerQueue;

            Objects.requireNonNull(this.entityDeserializerQueue);
            return completablefuture.thenApplyAsync(function, consecutiveexecutor::schedule);
        }
    }

    private static ChunkEntities<Entity> emptyChunk(ChunkPos pos) {
        return new ChunkEntities<Entity>(pos, List.of());
    }

    @Override
    public void storeEntities(ChunkEntities<Entity> chunk) {
        ChunkPos chunkpos = chunk.getPos();

        if (chunk.isEmpty()) {
            if (this.emptyChunks.add(chunkpos.toLong())) {
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkpos, IOWorker.STORE_EMPTY), chunkpos);
            }

        } else {
            try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(ChunkAccess.problemPath(chunkpos), EntityStorage.LOGGER)) {
                ListTag listtag = new ListTag();

                chunk.getEntities().forEach((entity) -> {
                    TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter_scopedcollector.forChild(entity.problemPath()), entity.registryAccess());

                    if (entity.save(tagvalueoutput)) {
                        CompoundTag compoundtag = tagvalueoutput.buildResult();

                        listtag.add(compoundtag);
                    }

                });
                CompoundTag compoundtag = NbtUtils.addCurrentDataVersion(new CompoundTag());

                compoundtag.put("Entities", listtag);
                compoundtag.store("Position", ChunkPos.CODEC, chunkpos);
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkpos, compoundtag), chunkpos);
                this.emptyChunks.remove(chunkpos.toLong());
            }

        }
    }

    private void reportSaveFailureIfPresent(CompletableFuture<?> operation, ChunkPos pos) {
        operation.exceptionally((throwable) -> {
            EntityStorage.LOGGER.error("Failed to store entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    private void reportLoadFailureIfPresent(CompletableFuture<?> operation, ChunkPos pos) {
        operation.exceptionally((throwable) -> {
            EntityStorage.LOGGER.error("Failed to load entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkLoadFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    @Override
    public void flush(boolean flushStorage) {
        this.simpleRegionStorage.synchronize(flushStorage).join();
        this.entityDeserializerQueue.runAll();
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }
}
