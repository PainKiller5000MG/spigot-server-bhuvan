package net.minecraft.server.level;

import net.minecraft.world.level.TicketStorage;

class LoadingChunkTracker extends ChunkTracker {

    private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;
    private final DistanceManager distanceManager;
    private final TicketStorage ticketStorage;

    public LoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {
        super(LoadingChunkTracker.MAX_LEVEL + 1, 16, 256);
        this.distanceManager = distanceManager;
        this.ticketStorage = ticketStorage;
        ticketStorage.setLoadingChunkUpdatedListener(this::update);
    }

    @Override
    protected int getLevelFromSource(long to) {
        return this.ticketStorage.getTicketLevelAt(to, false);
    }

    @Override
    protected int getLevel(long node) {
        if (!this.distanceManager.isChunkToRemove(node)) {
            ChunkHolder chunkholder = this.distanceManager.getChunk(node);

            if (chunkholder != null) {
                return chunkholder.getTicketLevel();
            }
        }

        return LoadingChunkTracker.MAX_LEVEL;
    }

    @Override
    protected void setLevel(long node, int level) {
        ChunkHolder chunkholder = this.distanceManager.getChunk(node);
        int k = chunkholder == null ? LoadingChunkTracker.MAX_LEVEL : chunkholder.getTicketLevel();

        if (k != level) {
            chunkholder = this.distanceManager.updateChunkScheduling(node, level, chunkholder, k);
            if (chunkholder != null) {
                this.distanceManager.chunksToUpdateFutures.add(chunkholder);
            }

        }
    }

    public int runDistanceUpdates(int count) {
        return this.runUpdates(count);
    }
}
