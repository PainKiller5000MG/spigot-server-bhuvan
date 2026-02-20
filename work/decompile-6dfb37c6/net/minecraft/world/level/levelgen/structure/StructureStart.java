package net.minecraft.world.level.levelgen.structure;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class StructureStart {

    public static final String INVALID_START_ID = "INVALID";
    public static final StructureStart INVALID_START = new StructureStart((Structure) null, new ChunkPos(0, 0), 0, new PiecesContainer(List.of()));
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Structure structure;
    private final PiecesContainer pieceContainer;
    private final ChunkPos chunkPos;
    private int references;
    private volatile @Nullable BoundingBox cachedBoundingBox;

    public StructureStart(Structure structure, ChunkPos chunkPos, int references, PiecesContainer pieceContainer) {
        this.structure = structure;
        this.chunkPos = chunkPos;
        this.references = references;
        this.pieceContainer = pieceContainer;
    }

    public static @Nullable StructureStart loadStaticStart(StructurePieceSerializationContext context, CompoundTag tag, long seed) {
        String s = tag.getStringOr("id", "");

        if ("INVALID".equals(s)) {
            return StructureStart.INVALID_START;
        } else {
            Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = (Structure) registry.getValue(Identifier.parse(s));

            if (structure == null) {
                StructureStart.LOGGER.error("Unknown stucture id: {}", s);
                return null;
            } else {
                ChunkPos chunkpos = new ChunkPos(tag.getIntOr("ChunkX", 0), tag.getIntOr("ChunkZ", 0));
                int j = tag.getIntOr("references", 0);
                ListTag listtag = tag.getListOrEmpty("Children");

                try {
                    PiecesContainer piecescontainer = PiecesContainer.load(listtag, context);

                    if (structure instanceof OceanMonumentStructure) {
                        piecescontainer = OceanMonumentStructure.regeneratePiecesAfterLoad(chunkpos, seed, piecescontainer);
                    }

                    return new StructureStart(structure, chunkpos, j, piecescontainer);
                } catch (Exception exception) {
                    StructureStart.LOGGER.error("Failed Start with id {}", s, exception);
                    return null;
                }
            }
        }
    }

    public BoundingBox getBoundingBox() {
        BoundingBox boundingbox = this.cachedBoundingBox;

        if (boundingbox == null) {
            boundingbox = this.structure.adjustBoundingBox(this.pieceContainer.calculateBoundingBox());
            this.cachedBoundingBox = boundingbox;
        }

        return boundingbox;
    }

    public void placeInChunk(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos) {
        List<StructurePiece> list = this.pieceContainer.pieces();

        if (!list.isEmpty()) {
            BoundingBox boundingbox1 = ((StructurePiece) list.get(0)).boundingBox;
            BlockPos blockpos = boundingbox1.getCenter();
            BlockPos blockpos1 = new BlockPos(blockpos.getX(), boundingbox1.minY(), blockpos.getZ());

            for (StructurePiece structurepiece : list) {
                if (structurepiece.getBoundingBox().intersects(chunkBB)) {
                    structurepiece.postProcess(level, structureManager, generator, random, chunkBB, chunkPos, blockpos1);
                }
            }

            this.structure.afterPlace(level, structureManager, generator, random, chunkBB, chunkPos, this.pieceContainer);
        }
    }

    public CompoundTag createTag(StructurePieceSerializationContext context, ChunkPos chunkPos) {
        CompoundTag compoundtag = new CompoundTag();

        if (this.isValid()) {
            compoundtag.putString("id", context.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(this.structure).toString());
            compoundtag.putInt("ChunkX", chunkPos.x);
            compoundtag.putInt("ChunkZ", chunkPos.z);
            compoundtag.putInt("references", this.references);
            compoundtag.put("Children", this.pieceContainer.save(context));
            return compoundtag;
        } else {
            compoundtag.putString("id", "INVALID");
            return compoundtag;
        }
    }

    public boolean isValid() {
        return !this.pieceContainer.isEmpty();
    }

    public ChunkPos getChunkPos() {
        return this.chunkPos;
    }

    public boolean canBeReferenced() {
        return this.references < this.getMaxReferences();
    }

    public void addReference() {
        ++this.references;
    }

    public int getReferences() {
        return this.references;
    }

    protected int getMaxReferences() {
        return 1;
    }

    public Structure getStructure() {
        return this.structure;
    }

    public List<StructurePiece> getPieces() {
        return this.pieceContainer.pieces();
    }
}
