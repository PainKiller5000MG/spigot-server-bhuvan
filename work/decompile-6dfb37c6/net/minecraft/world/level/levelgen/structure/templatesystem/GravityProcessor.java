package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class GravityProcessor extends StructureProcessor {

    public static final MapCodec<GravityProcessor> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Heightmap.Types.CODEC.fieldOf("heightmap").orElse(Heightmap.Types.WORLD_SURFACE_WG).forGetter((gravityprocessor) -> {
            return gravityprocessor.heightmap;
        }), Codec.INT.fieldOf("offset").orElse(0).forGetter((gravityprocessor) -> {
            return gravityprocessor.offset;
        })).apply(instance, GravityProcessor::new);
    });
    private final Heightmap.Types heightmap;
    private final int offset;

    public GravityProcessor(Heightmap.Types heightmap, int offset) {
        this.heightmap = heightmap;
        this.offset = offset;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos, StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
        Heightmap.Types heightmap_types;

        if (level instanceof ServerLevel) {
            if (this.heightmap == Heightmap.Types.WORLD_SURFACE_WG) {
                heightmap_types = Heightmap.Types.WORLD_SURFACE;
            } else if (this.heightmap == Heightmap.Types.OCEAN_FLOOR_WG) {
                heightmap_types = Heightmap.Types.OCEAN_FLOOR;
            } else {
                heightmap_types = this.heightmap;
            }
        } else {
            heightmap_types = this.heightmap;
        }

        BlockPos blockpos2 = processedBlockInfo.pos();
        int i = level.getHeight(heightmap_types, blockpos2.getX(), blockpos2.getZ()) + this.offset;
        int j = originalBlockInfo.pos().getY();

        return new StructureTemplate.StructureBlockInfo(new BlockPos(blockpos2.getX(), i + j, blockpos2.getZ()), processedBlockInfo.state(), processedBlockInfo.nbt());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.GRAVITY;
    }
}
