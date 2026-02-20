package net.minecraft.world.level.chunk.status;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

public class ChunkStatusTasks {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ChunkStatusTasks() {}

    private static boolean isLighted(ChunkAccess chunk) {
        return chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && chunk.isLightCorrect();
    }

    static CompletableFuture<ChunkAccess> passThrough(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();

        if (serverlevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
            context.generator().createStructures(serverlevel.registryAccess(), serverlevel.getChunkSource().getGeneratorState(), serverlevel.structureManager(), chunk, context.structureManager(), serverlevel.dimension());
        }

        serverlevel.onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> loadStructureStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        context.level().onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureReferences(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        context.generator().createReferences(worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateBiomes(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        return context.generator().createBiomes(serverlevel.getChunkSource().randomState(), Blender.of(worldgenregion), serverlevel.structureManager().forWorldGenRegion(worldgenregion), chunk);
    }

    static CompletableFuture<ChunkAccess> generateNoise(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        return context.generator().fillFromNoise(Blender.of(worldgenregion), serverlevel.getChunkSource().randomState(), serverlevel.structureManager().forWorldGenRegion(worldgenregion), chunk).thenApply((chunkaccess1) -> {
            if (chunkaccess1 instanceof ProtoChunk protochunk) {
                BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();

                if (belowzeroretrogen != null) {
                    BelowZeroRetrogen.replaceOldBedrock(protochunk);
                    if (belowzeroretrogen.hasBedrockHoles()) {
                        belowzeroretrogen.applyBedrockMask(protochunk);
                    }
                }
            }

            return chunkaccess1;
        });
    }

    static CompletableFuture<ChunkAccess> generateSurface(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        context.generator().buildSurface(worldgenregion, serverlevel.structureManager().forWorldGenRegion(worldgenregion), serverlevel.getChunkSource().randomState(), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateCarvers(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        if (chunk instanceof ProtoChunk protochunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(worldgenregion, protochunk);
        }

        context.generator().applyCarvers(worldgenregion, serverlevel.getSeed(), serverlevel.getChunkSource().randomState(), serverlevel.getBiomeManager(), serverlevel.structureManager().forWorldGenRegion(worldgenregion), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateFeatures(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel serverlevel = context.level();

        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        WorldGenRegion worldgenregion = new WorldGenRegion(serverlevel, chunks, step, chunk);

        if (!SharedConstants.DEBUG_DISABLE_FEATURES) {
            context.generator().applyBiomeDecoration(worldgenregion, chunk, serverlevel.structureManager().forWorldGenRegion(worldgenregion));
        }

        Blender.generateBorderTicks(worldgenregion, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> initializeLight(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ThreadedLevelLightEngine threadedlevellightengine = context.lightEngine();

        chunk.initializeLightSources();
        ((ProtoChunk) chunk).setLightEngine(threadedlevellightengine);
        boolean flag = isLighted(chunk);

        return threadedlevellightengine.initializeLight(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> light(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        boolean flag = isLighted(chunk);

        return context.lightEngine().lightChunk(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> generateSpawn(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        if (!chunk.isUpgrading()) {
            context.generator().spawnOriginalMobs(new WorldGenRegion(context.level(), chunks, step, chunk));
        }

        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> full(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ChunkPos chunkpos = chunk.getPos();
        GenerationChunkHolder generationchunkholder = chunks.get(chunkpos.x, chunkpos.z);

        return CompletableFuture.supplyAsync(() -> {
            ProtoChunk protochunk = (ProtoChunk) chunk;
            ServerLevel serverlevel = context.level();
            LevelChunk levelchunk;

            if (protochunk instanceof ImposterProtoChunk imposterprotochunk) {
                levelchunk = imposterprotochunk.getWrapped();
            } else {
                levelchunk = new LevelChunk(serverlevel, protochunk, (levelchunk1) -> {
                    try (ProblemReporter.ScopedCollector problemreporter_scopedcollector = new ProblemReporter.ScopedCollector(chunk.problemPath(), ChunkStatusTasks.LOGGER)) {
                        postLoadProtoChunk(serverlevel, TagValueInput.create(problemreporter_scopedcollector, serverlevel.registryAccess(), protochunk.getEntities()));
                    }

                });
                generationchunkholder.replaceProtoChunk(new ImposterProtoChunk(levelchunk, false));
            }

            Objects.requireNonNull(generationchunkholder);
            levelchunk.setFullStatus(generationchunkholder::getFullStatus);
            levelchunk.runPostLoad();
            levelchunk.setLoaded(true);
            levelchunk.registerAllBlockEntitiesAfterLevelLoad();
            levelchunk.registerTickContainerInLevel(serverlevel);
            levelchunk.setUnsavedListener(context.unsavedListener());
            return levelchunk;
        }, context.mainThreadExecutor());
    }

    private static void postLoadProtoChunk(ServerLevel level, ValueInput.ValueInputList entities) {
        if (!entities.isEmpty()) {
            level.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(entities, level, EntitySpawnReason.LOAD));
        }

    }
}
