package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class ShipwreckStructure extends Structure {

    public static final MapCodec<ShipwreckStructure> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(settingsCodec(instance), Codec.BOOL.fieldOf("is_beached").forGetter((shipwreckstructure) -> {
            return shipwreckstructure.isBeached;
        })).apply(instance, ShipwreckStructure::new);
    });
    public final boolean isBeached;

    public ShipwreckStructure(Structure.StructureSettings settings, boolean isBeached) {
        super(settings);
        this.isBeached = isBeached;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Heightmap.Types heightmap_types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;

        return onTopOfChunkCenter(context, heightmap_types, (structurepiecesbuilder) -> {
            this.generatePieces(structurepiecesbuilder, context);
        });
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockpos = new BlockPos(context.chunkPos().getMinBlockX(), 90, context.chunkPos().getMinBlockZ());
        ShipwreckPieces.ShipwreckPiece shipwreckpieces_shipwreckpiece = ShipwreckPieces.addRandomPiece(context.structureTemplateManager(), blockpos, rotation, builder, context.random(), this.isBeached);

        if (shipwreckpieces_shipwreckpiece.isTooBigToFitInWorldGenRegion()) {
            BoundingBox boundingbox = shipwreckpieces_shipwreckpiece.getBoundingBox();
            int i;

            if (this.isBeached) {
                int j = Structure.getLowestY(context, boundingbox.minX(), boundingbox.getXSpan(), boundingbox.minZ(), boundingbox.getZSpan());

                i = shipwreckpieces_shipwreckpiece.calculateBeachedPosition(j, context.random());
            } else {
                i = Structure.getMeanFirstOccupiedHeight(context, boundingbox.minX(), boundingbox.getXSpan(), boundingbox.minZ(), boundingbox.getZSpan());
            }

            shipwreckpieces_shipwreckpiece.adjustPositionHeight(i);
        }

    }

    @Override
    public StructureType<?> type() {
        return StructureType.SHIPWRECK;
    }
}
