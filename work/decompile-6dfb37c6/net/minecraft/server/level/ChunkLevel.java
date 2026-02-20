package net.minecraft.server.level;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class ChunkLevel {

    private static final int FULL_CHUNK_LEVEL = 33;
    private static final int BLOCK_TICKING_LEVEL = 32;
    private static final int ENTITY_TICKING_LEVEL = 31;
    private static final ChunkStep FULL_CHUNK_STEP = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL);
    public static final int RADIUS_AROUND_FULL_CHUNK = ChunkLevel.FULL_CHUNK_STEP.accumulatedDependencies().getRadius();
    public static final int MAX_LEVEL = 33 + ChunkLevel.RADIUS_AROUND_FULL_CHUNK;

    public ChunkLevel() {}

    public static @Nullable ChunkStatus generationStatus(int level) {
        return getStatusAroundFullChunk(level - 33, (ChunkStatus) null);
    }

    @Contract("_,!null->!null;_,_->_")
    public static @Nullable ChunkStatus getStatusAroundFullChunk(int distanceToFullChunk, @Nullable ChunkStatus defaultValue) {
        return distanceToFullChunk > ChunkLevel.RADIUS_AROUND_FULL_CHUNK ? defaultValue : (distanceToFullChunk <= 0 ? ChunkStatus.FULL : ChunkLevel.FULL_CHUNK_STEP.accumulatedDependencies().get(distanceToFullChunk));
    }

    public static ChunkStatus getStatusAroundFullChunk(int distanceToFullChunk) {
        return getStatusAroundFullChunk(distanceToFullChunk, ChunkStatus.EMPTY);
    }

    public static int byStatus(ChunkStatus status) {
        return 33 + ChunkLevel.FULL_CHUNK_STEP.getAccumulatedRadiusOf(status);
    }

    public static FullChunkStatus fullStatus(int level) {
        return level <= 31 ? FullChunkStatus.ENTITY_TICKING : (level <= 32 ? FullChunkStatus.BLOCK_TICKING : (level <= 33 ? FullChunkStatus.FULL : FullChunkStatus.INACCESSIBLE));
    }

    public static int byStatus(FullChunkStatus status) {
        int i;

        switch (status) {
            case INACCESSIBLE:
                i = ChunkLevel.MAX_LEVEL;
                break;
            case FULL:
                i = 33;
                break;
            case BLOCK_TICKING:
                i = 32;
                break;
            case ENTITY_TICKING:
                i = 31;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return i;
    }

    public static boolean isEntityTicking(int level) {
        return level <= 31;
    }

    public static boolean isBlockTicking(int level) {
        return level <= 32;
    }

    public static boolean isLoaded(int level) {
        return level <= ChunkLevel.MAX_LEVEL;
    }
}
