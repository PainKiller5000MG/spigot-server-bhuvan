package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class DesertPyramidStructure extends SinglePieceStructure {

    public static final MapCodec<DesertPyramidStructure> CODEC = simpleCodec(DesertPyramidStructure::new);

    public DesertPyramidStructure(Structure.StructureSettings settings) {
        super(DesertPyramidPiece::new, 21, 21, settings);
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, PiecesContainer pieces) {
        Set<BlockPos> set = SortedArraySet.<BlockPos>create(Vec3i::compareTo);

        for (StructurePiece structurepiece : pieces.pieces()) {
            if (structurepiece instanceof DesertPyramidPiece desertpyramidpiece) {
                set.addAll(desertpyramidpiece.getPotentialSuspiciousSandWorldPositions());
                placeSuspiciousSand(chunkBB, level, desertpyramidpiece.getRandomCollapsedRoofPos());
            }
        }

        ObjectArrayList<BlockPos> objectarraylist = new ObjectArrayList(set.stream().toList());
        RandomSource randomsource1 = RandomSource.create(level.getSeed()).forkPositional().at(pieces.calculateBoundingBox().getCenter());

        Util.shuffle(objectarraylist, randomsource1);
        int i = Math.min(set.size(), randomsource1.nextInt(5, 8));
        ObjectListIterator objectlistiterator = objectarraylist.iterator();

        while (objectlistiterator.hasNext()) {
            BlockPos blockpos = (BlockPos) objectlistiterator.next();

            if (i > 0) {
                --i;
                placeSuspiciousSand(chunkBB, level, blockpos);
            } else if (chunkBB.isInside(blockpos)) {
                level.setBlock(blockpos, Blocks.SAND.defaultBlockState(), 2);
            }
        }

    }

    private static void placeSuspiciousSand(BoundingBox chunkBB, WorldGenLevel level, BlockPos blockPos) {
        if (chunkBB.isInside(blockPos)) {
            level.setBlock(blockPos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 2);
            level.getBlockEntity(blockPos, BlockEntityType.BRUSHABLE_BLOCK).ifPresent((brushableblockentity) -> {
                brushableblockentity.setLootTable(BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, blockPos.asLong());
            });
        }

    }

    @Override
    public StructureType<?> type() {
        return StructureType.DESERT_PYRAMID;
    }
}
