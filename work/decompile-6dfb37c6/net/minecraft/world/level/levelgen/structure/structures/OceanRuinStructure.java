package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class OceanRuinStructure extends Structure {

    public static final MapCodec<OceanRuinStructure> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(settingsCodec(instance), OceanRuinStructure.Type.CODEC.fieldOf("biome_temp").forGetter((oceanruinstructure) -> {
            return oceanruinstructure.biomeTemp;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("large_probability").forGetter((oceanruinstructure) -> {
            return oceanruinstructure.largeProbability;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("cluster_probability").forGetter((oceanruinstructure) -> {
            return oceanruinstructure.clusterProbability;
        })).apply(instance, OceanRuinStructure::new);
    });
    public final OceanRuinStructure.Type biomeTemp;
    public final float largeProbability;
    public final float clusterProbability;

    public OceanRuinStructure(Structure.StructureSettings settings, OceanRuinStructure.Type biomeTemp, float largeProbability, float clusterProbability) {
        super(settings);
        this.biomeTemp = biomeTemp;
        this.largeProbability = largeProbability;
        this.clusterProbability = clusterProbability;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.OCEAN_FLOOR_WG, (structurepiecesbuilder) -> {
            this.generatePieces(structurepiecesbuilder, context);
        });
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context) {
        BlockPos blockpos = new BlockPos(context.chunkPos().getMinBlockX(), 90, context.chunkPos().getMinBlockZ());
        Rotation rotation = Rotation.getRandom(context.random());

        OceanRuinPieces.addPieces(context.structureTemplateManager(), blockpos, rotation, builder, context.random(), this);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.OCEAN_RUIN;
    }

    public static enum Type implements StringRepresentable {

        WARM("warm"), COLD("cold");

        public static final Codec<OceanRuinStructure.Type> CODEC = StringRepresentable.<OceanRuinStructure.Type>fromEnum(OceanRuinStructure.Type::values);
        /** @deprecated */
        @Deprecated
        public static final Codec<OceanRuinStructure.Type> LEGACY_CODEC = ExtraCodecs.<OceanRuinStructure.Type>legacyEnum(OceanRuinStructure.Type::valueOf);
        private final String name;

        private Type(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
