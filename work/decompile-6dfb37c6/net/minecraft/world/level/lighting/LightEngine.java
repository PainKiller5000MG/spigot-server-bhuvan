package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class LightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> implements LayerLightEventListener {

    public static final int MAX_LEVEL = 15;
    protected static final int MIN_OPACITY = 1;
    protected static final long PULL_LIGHT_IN_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(1);
    private static final int MIN_QUEUE_SIZE = 512;
    protected static final Direction[] PROPAGATION_DIRECTIONS = Direction.values();
    protected final LightChunkGetter chunkSource;
    protected final S storage;
    private final LongOpenHashSet blockNodesToCheck = new LongOpenHashSet(512, 0.5F);
    private final LongArrayFIFOQueue decreaseQueue = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue increaseQueue = new LongArrayFIFOQueue();
    private static final int CACHE_SIZE = 2;
    private final long[] lastChunkPos = new long[2];
    private final LightChunk[] lastChunk = new LightChunk[2];

    protected LightEngine(LightChunkGetter chunkSource, S storage) {
        this.chunkSource = chunkSource;
        this.storage = storage;
        this.clearChunkCache();
    }

    public static boolean hasDifferentLightProperties(BlockState oldState, BlockState newState) {
        return newState == oldState ? false : newState.getLightBlock() != oldState.getLightBlock() || newState.getLightEmission() != oldState.getLightEmission() || newState.useShapeForLightOcclusion() || oldState.useShapeForLightOcclusion();
    }

    public static int getLightBlockInto(BlockState fromState, BlockState toState, Direction direction, int simpleOpacity) {
        boolean flag = isEmptyShape(fromState);
        boolean flag1 = isEmptyShape(toState);

        if (flag && flag1) {
            return simpleOpacity;
        } else {
            VoxelShape voxelshape = flag ? Shapes.empty() : fromState.getOcclusionShape();
            VoxelShape voxelshape1 = flag1 ? Shapes.empty() : toState.getOcclusionShape();

            return Shapes.mergedFaceOccludes(voxelshape, voxelshape1, direction) ? 16 : simpleOpacity;
        }
    }

    public static VoxelShape getOcclusionShape(BlockState state, Direction direction) {
        return isEmptyShape(state) ? Shapes.empty() : state.getFaceOcclusionShape(direction);
    }

    protected static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    protected BlockState getState(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        LightChunk lightchunk = this.getChunk(i, j);

        return lightchunk == null ? Blocks.BEDROCK.defaultBlockState() : lightchunk.getBlockState(pos);
    }

    protected int getOpacity(BlockState state) {
        return Math.max(1, state.getLightBlock());
    }

    protected boolean shapeOccludes(BlockState fromState, BlockState toState, Direction direction) {
        VoxelShape voxelshape = getOcclusionShape(fromState, direction);
        VoxelShape voxelshape1 = getOcclusionShape(toState, direction.getOpposite());

        return Shapes.faceShapeOccludes(voxelshape, voxelshape1);
    }

    protected @Nullable LightChunk getChunk(int chunkX, int chunkZ) {
        long k = ChunkPos.asLong(chunkX, chunkZ);

        for (int l = 0; l < 2; ++l) {
            if (k == this.lastChunkPos[l]) {
                return this.lastChunk[l];
            }
        }

        LightChunk lightchunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);

        for (int i1 = 1; i1 > 0; --i1) {
            this.lastChunkPos[i1] = this.lastChunkPos[i1 - 1];
            this.lastChunk[i1] = this.lastChunk[i1 - 1];
        }

        this.lastChunkPos[0] = k;
        this.lastChunk[0] = lightchunk;
        return lightchunk;
    }

    private void clearChunkCache() {
        Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.blockNodesToCheck.add(pos.asLong());
    }

    public void queueSectionData(long pos, @Nullable DataLayer data) {
        this.storage.queueSectionData(pos, data);
    }

    public void retainData(ChunkPos pos, boolean retain) {
        this.storage.retainData(SectionPos.getZeroNode(pos.x, pos.z), retain);
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean sectionEmpty) {
        this.storage.updateSectionStatus(pos.asLong(), sectionEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enable) {
        this.storage.setLightEnabled(SectionPos.getZeroNode(pos.x, pos.z), enable);
    }

    @Override
    public int runLightUpdates() {
        LongIterator longiterator = this.blockNodesToCheck.iterator();

        while (longiterator.hasNext()) {
            this.checkNode(longiterator.nextLong());
        }

        this.blockNodesToCheck.clear();
        this.blockNodesToCheck.trim(512);
        int i = 0;

        i += this.propagateDecreases();
        i += this.propagateIncreases();
        this.clearChunkCache();
        this.storage.markNewInconsistencies(this);
        this.storage.swapSectionMap();
        return i;
    }

    private int propagateIncreases() {
        int i;

        for (i = 0; !this.increaseQueue.isEmpty(); ++i) {
            long j = this.increaseQueue.dequeueLong();
            long k = this.increaseQueue.dequeueLong();
            int l = this.storage.getStoredLevel(j);
            int i1 = LightEngine.QueueEntry.getFromLevel(k);

            if (LightEngine.QueueEntry.isIncreaseFromEmission(k) && l < i1) {
                this.storage.setStoredLevel(j, i1);
                l = i1;
            }

            if (l == i1) {
                this.propagateIncrease(j, k, l);
            }
        }

        return i;
    }

    private int propagateDecreases() {
        int i;

        for (i = 0; !this.decreaseQueue.isEmpty(); ++i) {
            long j = this.decreaseQueue.dequeueLong();
            long k = this.decreaseQueue.dequeueLong();

            this.propagateDecrease(j, k);
        }

        return i;
    }

    protected void enqueueDecrease(long fromNode, long decreaseData) {
        this.decreaseQueue.enqueue(fromNode);
        this.decreaseQueue.enqueue(decreaseData);
    }

    protected void enqueueIncrease(long fromNode, long increaseData) {
        this.increaseQueue.enqueue(fromNode);
        this.increaseQueue.enqueue(increaseData);
    }

    @Override
    public boolean hasLightWork() {
        return this.storage.hasInconsistencies() || !this.blockNodesToCheck.isEmpty() || !this.decreaseQueue.isEmpty() || !this.increaseQueue.isEmpty();
    }

    @Override
    public @Nullable DataLayer getDataLayerData(SectionPos pos) {
        return this.storage.getDataLayerData(pos.asLong());
    }

    @Override
    public int getLightValue(BlockPos pos) {
        return this.storage.getLightValue(pos.asLong());
    }

    public String getDebugData(long sectionNode) {
        return this.getDebugSectionType(sectionNode).display();
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionNode) {
        return this.storage.getDebugSectionType(sectionNode);
    }

    protected abstract void checkNode(long blockNode);

    protected abstract void propagateIncrease(long fromNode, long increaseData, int fromLevel);

    protected abstract void propagateDecrease(long fromNode, long decreaseData);

    public static class QueueEntry {

        private static final int FROM_LEVEL_BITS = 4;
        private static final int DIRECTION_BITS = 6;
        private static final long LEVEL_MASK = 15L;
        private static final long DIRECTIONS_MASK = 1008L;
        private static final long FLAG_FROM_EMPTY_SHAPE = 1024L;
        private static final long FLAG_INCREASE_FROM_EMISSION = 2048L;

        public QueueEntry() {}

        public static long decreaseSkipOneDirection(int oldFromLevel, Direction skipDirection) {
            long j = withoutDirection(1008L, skipDirection);

            return withLevel(j, oldFromLevel);
        }

        public static long decreaseAllDirections(int oldFromLevel) {
            return withLevel(1008L, oldFromLevel);
        }

        public static long increaseLightFromEmission(int newFromLevel, boolean fromEmptyShape) {
            long j = 1008L;

            j |= 2048L;
            if (fromEmptyShape) {
                j |= 1024L;
            }

            return withLevel(j, newFromLevel);
        }

        public static long increaseSkipOneDirection(int newFromLevel, boolean fromEmptyShape, Direction skipDirection) {
            long j = withoutDirection(1008L, skipDirection);

            if (fromEmptyShape) {
                j |= 1024L;
            }

            return withLevel(j, newFromLevel);
        }

        public static long increaseOnlyOneDirection(int newFromLevel, boolean fromEmptyShape, Direction direction) {
            long j = 0L;

            if (fromEmptyShape) {
                j |= 1024L;
            }

            j = withDirection(j, direction);
            return withLevel(j, newFromLevel);
        }

        public static long increaseSkySourceInDirections(boolean down, boolean north, boolean south, boolean west, boolean east) {
            long i = withLevel(0L, 15);

            if (down) {
                i = withDirection(i, Direction.DOWN);
            }

            if (north) {
                i = withDirection(i, Direction.NORTH);
            }

            if (south) {
                i = withDirection(i, Direction.SOUTH);
            }

            if (west) {
                i = withDirection(i, Direction.WEST);
            }

            if (east) {
                i = withDirection(i, Direction.EAST);
            }

            return i;
        }

        public static int getFromLevel(long entry) {
            return (int) (entry & 15L);
        }

        public static boolean isFromEmptyShape(long entry) {
            return (entry & 1024L) != 0L;
        }

        public static boolean isIncreaseFromEmission(long entry) {
            return (entry & 2048L) != 0L;
        }

        public static boolean shouldPropagateInDirection(long entry, Direction direction) {
            return (entry & 1L << direction.ordinal() + 4) != 0L;
        }

        private static long withLevel(long entry, int level) {
            return entry & -16L | (long) level & 15L;
        }

        private static long withDirection(long entry, Direction direction) {
            return entry | 1L << direction.ordinal() + 4;
        }

        private static long withoutDirection(long entry, Direction direction) {
            return entry & ~(1L << direction.ordinal() + 4);
        }
    }
}
