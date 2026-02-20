package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class NetherFossilStructure extends Structure {

    public static final MapCodec<NetherFossilStructure> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(settingsCodec(instance), HeightProvider.CODEC.fieldOf("height").forGetter((netherfossilstructure) -> {
            return netherfossilstructure.height;
        })).apply(instance, NetherFossilStructure::new);
    });
    public final HeightProvider height;

    public NetherFossilStructure(Structure.StructureSettings settings, HeightProvider height) {
        super(settings);
        this.height = height;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        WorldgenRandom worldgenrandom = context.random();
        int i = context.chunkPos().getMinBlockX() + worldgenrandom.nextInt(16);
        int j = context.chunkPos().getMinBlockZ() + worldgenrandom.nextInt(16);
        int k = context.chunkGenerator().getSeaLevel();
        WorldGenerationContext worldgenerationcontext = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());
        int l = this.height.sample(worldgenrandom, worldgenerationcontext);
        NoiseColumn noisecolumn = context.chunkGenerator().getBaseColumn(i, j, context.heightAccessor(), context.randomState());
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(i, l, j);

        while (l > k) {
            BlockState blockstate = noisecolumn.getBlock(l);

            --l;
            BlockState blockstate1 = noisecolumn.getBlock(l);

            if (blockstate.isAir() && (blockstate1.is(Blocks.SOUL_SAND) || blockstate1.isFaceSturdy(EmptyBlockGetter.INSTANCE, blockpos_mutableblockpos.setY(l), Direction.UP))) {
                break;
            }
        }

        if (l <= k) {
            return Optional.empty();
        } else {
            BlockPos blockpos = new BlockPos(i, l, j);

            return Optional.of(new Structure.GenerationStub(blockpos, (structurepiecesbuilder) -> {
                NetherFossilPieces.addPieces(context.structureTemplateManager(), structurepiecesbuilder, worldgenrandom, blockpos);
            }));
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.NETHER_FOSSIL;
    }
}
