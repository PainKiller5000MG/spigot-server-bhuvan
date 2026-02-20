package net.minecraft.world.level.levelgen.structure.structures;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidPiece extends ScatteredFeaturePiece {

    public static final int WIDTH = 21;
    public static final int DEPTH = 21;
    private final boolean[] hasPlacedChest = new boolean[4];
    private final List<BlockPos> potentialSuspiciousSandWorldPositions = new ArrayList();
    private BlockPos randomCollapsedRoofPos;

    public DesertPyramidPiece(RandomSource random, int west, int north) {
        super(StructurePieceType.DESERT_PYRAMID_PIECE, west, 64, north, 21, 15, 21, getRandomHorizontalDirection(random));
        this.randomCollapsedRoofPos = BlockPos.ZERO;
    }

    public DesertPyramidPiece(CompoundTag tag) {
        super(StructurePieceType.DESERT_PYRAMID_PIECE, tag);
        this.randomCollapsedRoofPos = BlockPos.ZERO;
        this.hasPlacedChest[0] = tag.getBooleanOr("hasPlacedChest0", false);
        this.hasPlacedChest[1] = tag.getBooleanOr("hasPlacedChest1", false);
        this.hasPlacedChest[2] = tag.getBooleanOr("hasPlacedChest2", false);
        this.hasPlacedChest[3] = tag.getBooleanOr("hasPlacedChest3", false);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("hasPlacedChest0", this.hasPlacedChest[0]);
        tag.putBoolean("hasPlacedChest1", this.hasPlacedChest[1]);
        tag.putBoolean("hasPlacedChest2", this.hasPlacedChest[2]);
        tag.putBoolean("hasPlacedChest3", this.hasPlacedChest[3]);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        if (this.updateHeightPositionToLowestGroundHeight(level, -random.nextInt(3))) {
            this.generateBox(level, chunkBB, 0, -4, 0, this.width - 1, 0, this.depth - 1, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);

            for (int i = 1; i <= 9; ++i) {
                this.generateBox(level, chunkBB, i, i, i, this.width - 1 - i, i, this.depth - 1 - i, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
                this.generateBox(level, chunkBB, i + 1, i, i + 1, this.width - 2 - i, i, this.depth - 2 - i, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            }

            for (int j = 0; j < this.width; ++j) {
                for (int k = 0; k < this.depth; ++k) {
                    int l = -5;

                    this.fillColumnDown(level, Blocks.SANDSTONE.defaultBlockState(), j, -5, k, chunkBB);
                }
            }

            BlockState blockstate = (BlockState) Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            BlockState blockstate1 = (BlockState) Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
            BlockState blockstate2 = (BlockState) Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            BlockState blockstate3 = (BlockState) Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);

            this.generateBox(level, chunkBB, 0, 0, 0, 4, 9, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 10, 1, 3, 10, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, blockstate, 2, 10, 0, chunkBB);
            this.placeBlock(level, blockstate1, 2, 10, 4, chunkBB);
            this.placeBlock(level, blockstate2, 0, 10, 2, chunkBB);
            this.placeBlock(level, blockstate3, 4, 10, 2, chunkBB);
            this.generateBox(level, chunkBB, this.width - 5, 0, 0, this.width - 1, 9, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 4, 10, 1, this.width - 2, 10, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, blockstate, this.width - 3, 10, 0, chunkBB);
            this.placeBlock(level, blockstate1, this.width - 3, 10, 4, chunkBB);
            this.placeBlock(level, blockstate2, this.width - 5, 10, 2, chunkBB);
            this.placeBlock(level, blockstate3, this.width - 1, 10, 2, chunkBB);
            this.generateBox(level, chunkBB, 8, 0, 0, 12, 4, 4, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 1, 0, 11, 3, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 1, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 2, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 9, 3, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, 3, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 3, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 2, 1, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 11, 1, 1, chunkBB);
            this.generateBox(level, chunkBB, 4, 1, 1, 8, 3, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 4, 1, 2, 8, 2, 2, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 12, 1, 1, 16, 3, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 12, 1, 2, 16, 2, 2, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 4, 5, this.width - 6, 4, this.depth - 6, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, 4, 9, 11, 4, 11, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 1, 8, 8, 3, 8, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 12, 1, 8, 12, 3, 8, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, 1, 12, 8, 3, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 12, 1, 12, 12, 3, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 1, 1, 5, 4, 4, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 5, 1, 5, this.width - 2, 4, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 6, 7, 9, 6, 7, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 7, 7, 9, this.width - 7, 7, 11, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 5, 5, 9, 5, 7, 11, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 6, 5, 9, this.width - 6, 7, 11, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 5, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 5, 6, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 6, 6, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 6, 5, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 6, 6, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), this.width - 7, 6, 10, chunkBB);
            this.generateBox(level, chunkBB, 2, 4, 4, 2, 6, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 3, 4, 4, this.width - 3, 6, 4, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, blockstate, 2, 4, 5, chunkBB);
            this.placeBlock(level, blockstate, 2, 3, 4, chunkBB);
            this.placeBlock(level, blockstate, this.width - 3, 4, 5, chunkBB);
            this.placeBlock(level, blockstate, this.width - 3, 3, 4, chunkBB);
            this.generateBox(level, chunkBB, 1, 1, 3, 2, 2, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 3, 1, 3, this.width - 2, 2, 3, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, Blocks.SANDSTONE.defaultBlockState(), 1, 1, 2, chunkBB);
            this.placeBlock(level, Blocks.SANDSTONE.defaultBlockState(), this.width - 2, 1, 2, chunkBB);
            this.placeBlock(level, Blocks.SANDSTONE_SLAB.defaultBlockState(), 1, 2, 2, chunkBB);
            this.placeBlock(level, Blocks.SANDSTONE_SLAB.defaultBlockState(), this.width - 2, 2, 2, chunkBB);
            this.placeBlock(level, blockstate3, 2, 1, 2, chunkBB);
            this.placeBlock(level, blockstate2, this.width - 3, 1, 2, chunkBB);
            this.generateBox(level, chunkBB, 4, 3, 5, 4, 3, 17, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 5, 3, 5, this.width - 5, 3, 17, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 3, 1, 5, 4, 2, 16, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.generateBox(level, chunkBB, this.width - 6, 1, 5, this.width - 5, 2, 16, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);

            for (int i1 = 5; i1 <= 17; i1 += 2) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 4, 1, i1, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 4, 2, i1, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), this.width - 5, 1, i1, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), this.width - 5, 2, i1, chunkBB);
            }

            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 7, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 8, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 0, 9, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 0, 9, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 8, 0, 10, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 12, 0, 10, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 7, 0, 10, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 13, 0, 10, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 0, 11, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 0, 11, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 12, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 10, 0, 13, chunkBB);
            this.placeBlock(level, Blocks.BLUE_TERRACOTTA.defaultBlockState(), 10, 0, 10, chunkBB);

            for (int j1 = 0; j1 <= this.width - 1; j1 += this.width - 1) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 2, 1, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 2, 2, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 2, 3, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 3, 1, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 3, 2, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 3, 3, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 4, 1, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), j1, 4, 2, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 4, 3, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 5, 1, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 5, 2, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 5, 3, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 6, 1, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), j1, 6, 2, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 6, 3, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 7, 1, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 7, 2, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), j1, 7, 3, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 8, 1, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 8, 2, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), j1, 8, 3, chunkBB);
            }

            for (int k1 = 2; k1 <= this.width - 3; k1 += this.width - 3 - 2) {
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 - 1, 2, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1, 2, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 + 1, 2, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 - 1, 3, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1, 3, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 + 1, 3, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 - 1, 4, 0, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), k1, 4, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 + 1, 4, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 - 1, 5, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1, 5, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 + 1, 5, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 - 1, 6, 0, chunkBB);
                this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), k1, 6, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 + 1, 6, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 - 1, 7, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1, 7, 0, chunkBB);
                this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), k1 + 1, 7, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 - 1, 8, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1, 8, 0, chunkBB);
                this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), k1 + 1, 8, 0, chunkBB);
            }

            this.generateBox(level, chunkBB, 8, 4, 0, 12, 6, 0, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, 6, 0, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, 6, 0, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 9, 5, 0, chunkBB);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, 5, 0, chunkBB);
            this.placeBlock(level, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), 11, 5, 0, chunkBB);
            this.generateBox(level, chunkBB, 8, -14, 8, 12, -11, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, -10, 8, 12, -10, 12, Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, -9, 8, 12, -9, 12, Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 8, -8, 8, 12, -1, 12, Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), false);
            this.generateBox(level, chunkBB, 9, -11, 9, 11, -1, 11, Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.STONE_PRESSURE_PLATE.defaultBlockState(), 10, -11, 10, chunkBB);
            this.generateBox(level, chunkBB, 9, -13, 9, 11, -13, 11, Blocks.TNT.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, -11, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 8, -10, 10, chunkBB);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 7, -10, 10, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 7, -11, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, -11, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 12, -10, 10, chunkBB);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 13, -10, 10, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 13, -11, 10, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -11, 8, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -10, 8, chunkBB);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, -10, 7, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, -11, 7, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -11, 12, chunkBB);
            this.placeBlock(level, Blocks.AIR.defaultBlockState(), 10, -10, 12, chunkBB);
            this.placeBlock(level, Blocks.CHISELED_SANDSTONE.defaultBlockState(), 10, -10, 13, chunkBB);
            this.placeBlock(level, Blocks.CUT_SANDSTONE.defaultBlockState(), 10, -11, 13, chunkBB);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!this.hasPlacedChest[direction.get2DDataValue()]) {
                    int l1 = direction.getStepX() * 2;
                    int i2 = direction.getStepZ() * 2;

                    this.hasPlacedChest[direction.get2DDataValue()] = this.createChest(level, chunkBB, random, 10 + l1, -11, 10 + i2, BuiltInLootTables.DESERT_PYRAMID);
                }
            }

            this.addCellar(level, chunkBB);
        }
    }

    private void addCellar(WorldGenLevel level, BoundingBox chunkBB) {
        BlockPos blockpos = new BlockPos(16, -4, 13);

        this.addCellarStairs(blockpos, level, chunkBB);
        this.addCellarRoom(blockpos, level, chunkBB);
    }

    private void addCellarStairs(BlockPos roomCenter, WorldGenLevel level, BoundingBox chunkBB) {
        int i = roomCenter.getX();
        int j = roomCenter.getY();
        int k = roomCenter.getZ();
        BlockState blockstate = Blocks.SANDSTONE_STAIRS.defaultBlockState();

        this.placeBlock(level, blockstate.rotate(Rotation.COUNTERCLOCKWISE_90), 13, -1, 17, chunkBB);
        this.placeBlock(level, blockstate.rotate(Rotation.COUNTERCLOCKWISE_90), 14, -2, 17, chunkBB);
        this.placeBlock(level, blockstate.rotate(Rotation.COUNTERCLOCKWISE_90), 15, -3, 17, chunkBB);
        BlockState blockstate1 = Blocks.SAND.defaultBlockState();
        BlockState blockstate2 = Blocks.SANDSTONE.defaultBlockState();
        boolean flag = level.getRandom().nextBoolean();

        this.placeBlock(level, blockstate1, i - 4, j + 4, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i - 3, j + 4, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i - 2, j + 4, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i - 1, j + 4, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i, j + 4, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i - 2, j + 3, k + 4, chunkBB);
        this.placeBlock(level, flag ? blockstate1 : blockstate2, i - 1, j + 3, k + 4, chunkBB);
        this.placeBlock(level, !flag ? blockstate1 : blockstate2, i, j + 3, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i - 1, j + 2, k + 4, chunkBB);
        this.placeBlock(level, blockstate2, i, j + 2, k + 4, chunkBB);
        this.placeBlock(level, blockstate1, i, j + 1, k + 4, chunkBB);
    }

    private void addCellarRoom(BlockPos roomCenter, WorldGenLevel level, BoundingBox chunkBB) {
        int i = roomCenter.getX();
        int j = roomCenter.getY();
        int k = roomCenter.getZ();
        BlockState blockstate = Blocks.CUT_SANDSTONE.defaultBlockState();
        BlockState blockstate1 = Blocks.CHISELED_SANDSTONE.defaultBlockState();

        this.generateBox(level, chunkBB, i - 3, j + 1, k - 3, i - 3, j + 1, k + 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i + 3, j + 1, k - 3, i + 3, j + 1, k + 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i - 3, j + 1, k - 3, i + 3, j + 1, k - 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i - 3, j + 1, k + 3, i + 3, j + 1, k + 3, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i - 3, j + 2, k - 3, i - 3, j + 2, k + 2, blockstate1, blockstate1, true);
        this.generateBox(level, chunkBB, i + 3, j + 2, k - 3, i + 3, j + 2, k + 2, blockstate1, blockstate1, true);
        this.generateBox(level, chunkBB, i - 3, j + 2, k - 3, i + 3, j + 2, k - 2, blockstate1, blockstate1, true);
        this.generateBox(level, chunkBB, i - 3, j + 2, k + 3, i + 3, j + 2, k + 3, blockstate1, blockstate1, true);
        this.generateBox(level, chunkBB, i - 3, -1, k - 3, i - 3, -1, k + 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i + 3, -1, k - 3, i + 3, -1, k + 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i - 3, -1, k - 3, i + 3, -1, k - 2, blockstate, blockstate, true);
        this.generateBox(level, chunkBB, i - 3, -1, k + 3, i + 3, -1, k + 3, blockstate, blockstate, true);
        this.placeSandBox(i - 2, j + 1, k - 2, i + 2, j + 3, k + 2);
        this.placeCollapsedRoof(level, chunkBB, i - 2, j + 4, k - 2, i + 2, k + 2);
        BlockState blockstate2 = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        BlockState blockstate3 = Blocks.BLUE_TERRACOTTA.defaultBlockState();

        this.placeBlock(level, blockstate3, i, j, k, chunkBB);
        this.placeBlock(level, blockstate2, i + 1, j, k - 1, chunkBB);
        this.placeBlock(level, blockstate2, i + 1, j, k + 1, chunkBB);
        this.placeBlock(level, blockstate2, i - 1, j, k - 1, chunkBB);
        this.placeBlock(level, blockstate2, i - 1, j, k + 1, chunkBB);
        this.placeBlock(level, blockstate2, i + 2, j, k, chunkBB);
        this.placeBlock(level, blockstate2, i - 2, j, k, chunkBB);
        this.placeBlock(level, blockstate2, i, j, k + 2, chunkBB);
        this.placeBlock(level, blockstate2, i, j, k - 2, chunkBB);
        this.placeBlock(level, blockstate2, i + 3, j, k, chunkBB);
        this.placeSand(i + 3, j + 1, k);
        this.placeSand(i + 3, j + 2, k);
        this.placeBlock(level, blockstate, i + 4, j + 1, k, chunkBB);
        this.placeBlock(level, blockstate1, i + 4, j + 2, k, chunkBB);
        this.placeBlock(level, blockstate2, i - 3, j, k, chunkBB);
        this.placeSand(i - 3, j + 1, k);
        this.placeSand(i - 3, j + 2, k);
        this.placeBlock(level, blockstate, i - 4, j + 1, k, chunkBB);
        this.placeBlock(level, blockstate1, i - 4, j + 2, k, chunkBB);
        this.placeBlock(level, blockstate2, i, j, k + 3, chunkBB);
        this.placeSand(i, j + 1, k + 3);
        this.placeSand(i, j + 2, k + 3);
        this.placeBlock(level, blockstate2, i, j, k - 3, chunkBB);
        this.placeSand(i, j + 1, k - 3);
        this.placeSand(i, j + 2, k - 3);
        this.placeBlock(level, blockstate, i, j + 1, k - 4, chunkBB);
        this.placeBlock(level, blockstate1, i, -2, k - 4, chunkBB);
    }

    private void placeSand(int x, int y, int z) {
        BlockPos blockpos = this.getWorldPos(x, y, z);

        this.potentialSuspiciousSandWorldPositions.add(blockpos);
    }

    private void placeSandBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int k1 = y0; k1 <= y1; ++k1) {
            for (int l1 = x0; l1 <= x1; ++l1) {
                for (int i2 = z0; i2 <= z1; ++i2) {
                    this.placeSand(l1, k1, i2);
                }
            }
        }

    }

    private void placeCollapsedRoofPiece(WorldGenLevel level, int x, int y, int z, BoundingBox chunkBB) {
        if (level.getRandom().nextFloat() < 0.33F) {
            BlockState blockstate = Blocks.SANDSTONE.defaultBlockState();

            this.placeBlock(level, blockstate, x, y, z, chunkBB);
        } else {
            BlockState blockstate1 = Blocks.SAND.defaultBlockState();

            this.placeBlock(level, blockstate1, x, y, z, chunkBB);
        }

    }

    private void placeCollapsedRoof(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int z1) {
        for (int j1 = x0; j1 <= x1; ++j1) {
            for (int k1 = z0; k1 <= z1; ++k1) {
                this.placeCollapsedRoofPiece(level, j1, y0, k1, chunkBB);
            }
        }

        RandomSource randomsource = RandomSource.create(level.getSeed()).forkPositional().at(this.getWorldPos(x0, y0, z0));
        int l1 = randomsource.nextIntBetweenInclusive(x0, x1);
        int i2 = randomsource.nextIntBetweenInclusive(z0, z1);

        this.randomCollapsedRoofPos = new BlockPos(this.getWorldX(l1, i2), this.getWorldY(y0), this.getWorldZ(l1, i2));
    }

    public List<BlockPos> getPotentialSuspiciousSandWorldPositions() {
        return this.potentialSuspiciousSandWorldPositions;
    }

    public BlockPos getRandomCollapsedRoofPos() {
        return this.randomCollapsedRoofPos;
    }
}
