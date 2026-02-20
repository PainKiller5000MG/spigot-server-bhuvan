package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChunkSkyLightSources {

    private static final int SIZE = 16;
    public static final int NEGATIVE_INFINITY = Integer.MIN_VALUE;
    private final int minY;
    private final BitStorage heightmap;
    private final BlockPos.MutableBlockPos mutablePos1 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos mutablePos2 = new BlockPos.MutableBlockPos();

    public ChunkSkyLightSources(LevelHeightAccessor level) {
        this.minY = level.getMinY() - 1;
        int i = level.getMaxY() + 1;
        int j = Mth.ceillog2(i - this.minY + 1);

        this.heightmap = new SimpleBitStorage(j, 256);
    }

    public void fillFrom(ChunkAccess chunk) {
        int i = chunk.getHighestFilledSectionIndex();

        if (i == -1) {
            this.fill(this.minY);
        } else {
            for (int j = 0; j < 16; ++j) {
                for (int k = 0; k < 16; ++k) {
                    int l = Math.max(this.findLowestSourceY(chunk, i, k, j), this.minY);

                    this.set(index(k, j), l);
                }
            }

        }
    }

    private int findLowestSourceY(ChunkAccess chunk, int topSectionIndex, int x, int z) {
        int l = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(topSectionIndex) + 1);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = this.mutablePos1.set(x, l, z);
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = this.mutablePos2.setWithOffset(blockpos_mutableblockpos, Direction.DOWN);
        BlockState blockstate = Blocks.AIR.defaultBlockState();

        for (int i1 = topSectionIndex; i1 >= 0; --i1) {
            LevelChunkSection levelchunksection = chunk.getSection(i1);

            if (levelchunksection.hasOnlyAir()) {
                blockstate = Blocks.AIR.defaultBlockState();
                int j1 = chunk.getSectionYFromSectionIndex(i1);

                blockpos_mutableblockpos.setY(SectionPos.sectionToBlockCoord(j1));
                blockpos_mutableblockpos1.setY(blockpos_mutableblockpos.getY() - 1);
            } else {
                for (int k1 = 15; k1 >= 0; --k1) {
                    BlockState blockstate1 = levelchunksection.getBlockState(x, k1, z);

                    if (isEdgeOccluded(blockstate, blockstate1)) {
                        return blockpos_mutableblockpos.getY();
                    }

                    blockstate = blockstate1;
                    blockpos_mutableblockpos.set(blockpos_mutableblockpos1);
                    blockpos_mutableblockpos1.move(Direction.DOWN);
                }
            }
        }

        return this.minY;
    }

    public boolean update(BlockGetter level, int x, int y, int z) {
        int l = y + 1;
        int i1 = index(x, z);
        int j1 = this.get(i1);

        if (l < j1) {
            return false;
        } else {
            BlockPos blockpos = this.mutablePos1.set(x, y + 1, z);
            BlockState blockstate = level.getBlockState(blockpos);
            BlockPos blockpos1 = this.mutablePos2.set(x, y, z);
            BlockState blockstate1 = level.getBlockState(blockpos1);

            if (this.updateEdge(level, i1, j1, blockpos, blockstate, blockpos1, blockstate1)) {
                return true;
            } else {
                BlockPos blockpos2 = this.mutablePos1.set(x, y - 1, z);
                BlockState blockstate2 = level.getBlockState(blockpos2);

                return this.updateEdge(level, i1, j1, blockpos1, blockstate1, blockpos2, blockstate2);
            }
        }
    }

    private boolean updateEdge(BlockGetter level, int index, int oldTopEdgeY, BlockPos topPos, BlockState topState, BlockPos bottomPos, BlockState bottomState) {
        int k = topPos.getY();

        if (isEdgeOccluded(topState, bottomState)) {
            if (k > oldTopEdgeY) {
                this.set(index, k);
                return true;
            }
        } else if (k == oldTopEdgeY) {
            this.set(index, this.findLowestSourceBelow(level, bottomPos, bottomState));
            return true;
        }

        return false;
    }

    private int findLowestSourceBelow(BlockGetter level, BlockPos startPos, BlockState startState) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = this.mutablePos1.set(startPos);
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = this.mutablePos2.setWithOffset(startPos, Direction.DOWN);
        BlockState blockstate1 = startState;

        while (blockpos_mutableblockpos1.getY() >= this.minY) {
            BlockState blockstate2 = level.getBlockState(blockpos_mutableblockpos1);

            if (isEdgeOccluded(blockstate1, blockstate2)) {
                return blockpos_mutableblockpos.getY();
            }

            blockstate1 = blockstate2;
            blockpos_mutableblockpos.set(blockpos_mutableblockpos1);
            blockpos_mutableblockpos1.move(Direction.DOWN);
        }

        return this.minY;
    }

    private static boolean isEdgeOccluded(BlockState topState, BlockState bottomState) {
        if (bottomState.getLightBlock() != 0) {
            return true;
        } else {
            VoxelShape voxelshape = LightEngine.getOcclusionShape(topState, Direction.DOWN);
            VoxelShape voxelshape1 = LightEngine.getOcclusionShape(bottomState, Direction.UP);

            return Shapes.faceShapeOccludes(voxelshape, voxelshape1);
        }
    }

    public int getLowestSourceY(int x, int z) {
        int k = this.get(index(x, z));

        return this.extendSourcesBelowWorld(k);
    }

    public int getHighestLowestSourceY() {
        int i = Integer.MIN_VALUE;

        for (int j = 0; j < this.heightmap.getSize(); ++j) {
            int k = this.heightmap.get(j);

            if (k > i) {
                i = k;
            }
        }

        return this.extendSourcesBelowWorld(i + this.minY);
    }

    private void fill(int lowestSourceY) {
        int j = lowestSourceY - this.minY;

        for (int k = 0; k < this.heightmap.getSize(); ++k) {
            this.heightmap.set(k, j);
        }

    }

    private void set(int index, int value) {
        this.heightmap.set(index, value - this.minY);
    }

    private int get(int index) {
        return this.heightmap.get(index) + this.minY;
    }

    private int extendSourcesBelowWorld(int value) {
        return value == this.minY ? Integer.MIN_VALUE : value;
    }

    private static int index(int x, int z) {
        return x + z * 16;
    }
}
