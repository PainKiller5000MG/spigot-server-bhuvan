package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {

    private final BlockPos.MutableBlockPos mutablePos;

    public BlockLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new BlockLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    public BlockLightEngine(LightChunkGetter chunkSource, BlockLightSectionStorage storage) {
        super(chunkSource, storage);
        this.mutablePos = new BlockPos.MutableBlockPos();
    }

    @Override
    protected void checkNode(long blockNode) {
        long j = SectionPos.blockToSection(blockNode);

        if (((BlockLightSectionStorage) this.storage).storingLightForSection(j)) {
            BlockState blockstate = this.getState(this.mutablePos.set(blockNode));
            int k = this.getEmission(blockNode, blockstate);
            int l = ((BlockLightSectionStorage) this.storage).getStoredLevel(blockNode);

            if (k < l) {
                ((BlockLightSectionStorage) this.storage).setStoredLevel(blockNode, 0);
                this.enqueueDecrease(blockNode, LightEngine.QueueEntry.decreaseAllDirections(l));
            } else {
                this.enqueueDecrease(blockNode, BlockLightEngine.PULL_LIGHT_IN_ENTRY);
            }

            if (k > 0) {
                this.enqueueIncrease(blockNode, LightEngine.QueueEntry.increaseLightFromEmission(k, isEmptyShape(blockstate)));
            }

        }
    }

    @Override
    protected void propagateIncrease(long fromNode, long increaseData, int fromLevel) {
        BlockState blockstate = null;

        for (Direction direction : BlockLightEngine.PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(increaseData, direction)) {
                long l = BlockPos.offset(fromNode, direction);

                if (((BlockLightSectionStorage) this.storage).storingLightForSection(SectionPos.blockToSection(l))) {
                    int i1 = ((BlockLightSectionStorage) this.storage).getStoredLevel(l);
                    int j1 = fromLevel - 1;

                    if (j1 > i1) {
                        this.mutablePos.set(l);
                        BlockState blockstate1 = this.getState(this.mutablePos);
                        int k1 = fromLevel - this.getOpacity(blockstate1);

                        if (k1 > i1) {
                            if (blockstate == null) {
                                blockstate = LightEngine.QueueEntry.isFromEmptyShape(increaseData) ? Blocks.AIR.defaultBlockState() : this.getState(this.mutablePos.set(fromNode));
                            }

                            if (!this.shapeOccludes(blockstate, blockstate1, direction)) {
                                ((BlockLightSectionStorage) this.storage).setStoredLevel(l, k1);
                                if (k1 > 1) {
                                    this.enqueueIncrease(l, LightEngine.QueueEntry.increaseSkipOneDirection(k1, isEmptyShape(blockstate1), direction.getOpposite()));
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    protected void propagateDecrease(long fromNode, long decreaseData) {
        int k = LightEngine.QueueEntry.getFromLevel(decreaseData);

        for (Direction direction : BlockLightEngine.PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(decreaseData, direction)) {
                long l = BlockPos.offset(fromNode, direction);

                if (((BlockLightSectionStorage) this.storage).storingLightForSection(SectionPos.blockToSection(l))) {
                    int i1 = ((BlockLightSectionStorage) this.storage).getStoredLevel(l);

                    if (i1 != 0) {
                        if (i1 <= k - 1) {
                            BlockState blockstate = this.getState(this.mutablePos.set(l));
                            int j1 = this.getEmission(l, blockstate);

                            ((BlockLightSectionStorage) this.storage).setStoredLevel(l, 0);
                            if (j1 < i1) {
                                this.enqueueDecrease(l, LightEngine.QueueEntry.decreaseSkipOneDirection(i1, direction.getOpposite()));
                            }

                            if (j1 > 0) {
                                this.enqueueIncrease(l, LightEngine.QueueEntry.increaseLightFromEmission(j1, isEmptyShape(blockstate)));
                            }
                        } else {
                            this.enqueueIncrease(l, LightEngine.QueueEntry.increaseOnlyOneDirection(i1, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }

    }

    private int getEmission(long blockNode, BlockState state) {
        int j = state.getLightEmission();

        return j > 0 && ((BlockLightSectionStorage) this.storage).lightOnInSection(SectionPos.blockToSection(blockNode)) ? j : 0;
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        this.setLightEnabled(pos, true);
        LightChunk lightchunk = this.chunkSource.getChunkForLighting(pos.x, pos.z);

        if (lightchunk != null) {
            lightchunk.findBlockLightSources((blockpos, blockstate) -> {
                int i = blockstate.getLightEmission();

                this.enqueueIncrease(blockpos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockstate)));
            });
        }

    }
}
