package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class WoodlandMansionStructure extends Structure {

    public static final MapCodec<WoodlandMansionStructure> CODEC = simpleCodec(WoodlandMansionStructure::new);

    public WoodlandMansionStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockpos = this.getLowestYIn5by5BoxOffset7Blocks(context, rotation);

        return blockpos.getY() < 60 ? Optional.empty() : Optional.of(new Structure.GenerationStub(blockpos, (structurepiecesbuilder) -> {
            this.generatePieces(structurepiecesbuilder, context, blockpos, rotation);
        }));
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context, BlockPos startPos, Rotation rotation) {
        List<WoodlandMansionPieces.WoodlandMansionPiece> list = Lists.newLinkedList();

        WoodlandMansionPieces.generateMansion(context.structureTemplateManager(), startPos, rotation, list, context.random());
        Objects.requireNonNull(builder);
        list.forEach(builder::addPiece);
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, PiecesContainer pieces) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int i = level.getMinY();
        BoundingBox boundingbox1 = pieces.calculateBoundingBox();
        int j = boundingbox1.minY();

        for (int k = chunkBB.minX(); k <= chunkBB.maxX(); ++k) {
            for (int l = chunkBB.minZ(); l <= chunkBB.maxZ(); ++l) {
                blockpos_mutableblockpos.set(k, j, l);
                if (!level.isEmptyBlock(blockpos_mutableblockpos) && boundingbox1.isInside(blockpos_mutableblockpos) && pieces.isInsidePiece(blockpos_mutableblockpos)) {
                    for (int i1 = j - 1; i1 > i; --i1) {
                        blockpos_mutableblockpos.setY(i1);
                        if (!level.isEmptyBlock(blockpos_mutableblockpos) && !level.getBlockState(blockpos_mutableblockpos).liquid()) {
                            break;
                        }

                        level.setBlock(blockpos_mutableblockpos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                    }
                }
            }
        }

    }

    @Override
    public StructureType<?> type() {
        return StructureType.WOODLAND_MANSION;
    }
}
