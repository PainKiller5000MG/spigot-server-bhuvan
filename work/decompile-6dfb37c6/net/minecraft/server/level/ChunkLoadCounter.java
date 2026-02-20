package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkLoadCounter {

    private final List<ChunkHolder> pendingChunks = new ArrayList();
    private int totalChunks;

    public ChunkLoadCounter() {}

    public void track(ServerLevel level, Runnable scheduler) {
        ServerChunkCache serverchunkcache = level.getChunkSource();
        LongSet longset = new LongOpenHashSet();

        serverchunkcache.runDistanceManagerUpdates();
        serverchunkcache.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach((chunkholder) -> {
            longset.add(chunkholder.getPos().toLong());
        });
        scheduler.run();
        serverchunkcache.runDistanceManagerUpdates();
        serverchunkcache.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach((chunkholder) -> {
            if (!longset.contains(chunkholder.getPos().toLong())) {
                this.pendingChunks.add(chunkholder);
                ++this.totalChunks;
            }

        });
    }

    public int readyChunks() {
        return this.totalChunks - this.pendingChunks();
    }

    public int pendingChunks() {
        this.pendingChunks.removeIf((chunkholder) -> {
            return chunkholder.getLatestStatus() == ChunkStatus.FULL;
        });
        return this.pendingChunks.size();
    }

    public int totalChunks() {
        return this.totalChunks;
    }
}
