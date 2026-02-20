package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public abstract class ScatteredFeaturePiece extends StructurePiece {

    protected final int width;
    protected final int height;
    protected final int depth;
    protected int heightPosition = -1;

    protected ScatteredFeaturePiece(StructurePieceType type, int west, int floor, int north, int width, int height, int depth, Direction direction) {
        super(type, 0, StructurePiece.makeBoundingBox(west, floor, north, direction, width, height, depth));
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.setOrientation(direction);
    }

    protected ScatteredFeaturePiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.width = tag.getIntOr("Width", 0);
        this.height = tag.getIntOr("Height", 0);
        this.depth = tag.getIntOr("Depth", 0);
        this.heightPosition = tag.getIntOr("HPos", 0);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Depth", this.depth);
        tag.putInt("HPos", this.heightPosition);
    }

    protected boolean updateAverageGroundHeight(LevelAccessor level, BoundingBox chunkBB, int offset) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int j = 0;
            int k = 0;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (int l = this.boundingBox.minZ(); l <= this.boundingBox.maxZ(); ++l) {
                for (int i1 = this.boundingBox.minX(); i1 <= this.boundingBox.maxX(); ++i1) {
                    blockpos_mutableblockpos.set(i1, 64, l);
                    if (chunkBB.isInside(blockpos_mutableblockpos)) {
                        j += level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos_mutableblockpos).getY();
                        ++k;
                    }
                }
            }

            if (k == 0) {
                return false;
            } else {
                this.heightPosition = j / k;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + offset, 0);
                return true;
            }
        }
    }

    protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor level, int offset) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int j = level.getMaxY() + 1;
            boolean flag = false;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); ++k) {
                for (int l = this.boundingBox.minX(); l <= this.boundingBox.maxX(); ++l) {
                    blockpos_mutableblockpos.set(l, 0, k);
                    j = Math.min(j, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos_mutableblockpos).getY());
                    flag = true;
                }
            }

            if (!flag) {
                return false;
            } else {
                this.heightPosition = j;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + offset, 0);
                return true;
            }
        }
    }
}
