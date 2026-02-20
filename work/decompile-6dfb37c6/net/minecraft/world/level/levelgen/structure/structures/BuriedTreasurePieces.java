package net.minecraft.world.level.levelgen.structure.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class BuriedTreasurePieces {

    public BuriedTreasurePieces() {}

    public static class BuriedTreasurePiece extends StructurePiece {

        public BuriedTreasurePiece(BlockPos offset) {
            super(StructurePieceType.BURIED_TREASURE_PIECE, 0, new BoundingBox(offset));
        }

        public BuriedTreasurePiece(CompoundTag tag) {
            super(StructurePieceType.BURIED_TREASURE_PIECE, tag);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            int i = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, this.boundingBox.minX(), this.boundingBox.minZ());
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(this.boundingBox.minX(), i, this.boundingBox.minZ());

            while (blockpos_mutableblockpos.getY() > level.getMinY()) {
                BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);
                BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos.below());

                if (blockstate1 == Blocks.SANDSTONE.defaultBlockState() || blockstate1 == Blocks.STONE.defaultBlockState() || blockstate1 == Blocks.ANDESITE.defaultBlockState() || blockstate1 == Blocks.GRANITE.defaultBlockState() || blockstate1 == Blocks.DIORITE.defaultBlockState()) {
                    BlockState blockstate2 = !blockstate.isAir() && !this.isLiquid(blockstate) ? blockstate : Blocks.SAND.defaultBlockState();

                    for (Direction direction : Direction.values()) {
                        BlockPos blockpos1 = blockpos_mutableblockpos.relative(direction);
                        BlockState blockstate3 = level.getBlockState(blockpos1);

                        if (blockstate3.isAir() || this.isLiquid(blockstate3)) {
                            BlockPos blockpos2 = blockpos1.below();
                            BlockState blockstate4 = level.getBlockState(blockpos2);

                            if ((blockstate4.isAir() || this.isLiquid(blockstate4)) && direction != Direction.UP) {
                                level.setBlock(blockpos1, blockstate1, 3);
                            } else {
                                level.setBlock(blockpos1, blockstate2, 3);
                            }
                        }
                    }

                    this.boundingBox = new BoundingBox(blockpos_mutableblockpos);
                    this.createChest(level, chunkBB, random, blockpos_mutableblockpos, BuiltInLootTables.BURIED_TREASURE, (BlockState) null);
                    return;
                }

                blockpos_mutableblockpos.move(0, -1, 0);
            }

        }

        private boolean isLiquid(BlockState blockState) {
            return blockState == Blocks.WATER.defaultBlockState() || blockState == Blocks.LAVA.defaultBlockState();
        }
    }
}
