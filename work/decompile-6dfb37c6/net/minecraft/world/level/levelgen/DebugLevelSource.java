package net.minecraft.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;

public class DebugLevelSource extends ChunkGenerator {

    public static final MapCodec<DebugLevelSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RegistryOps.retrieveElement(Biomes.PLAINS)).apply(instance, instance.stable(DebugLevelSource::new));
    });
    private static final int BLOCK_MARGIN = 2;
    private static final List<BlockState> ALL_BLOCKS = (List) StreamSupport.stream(BuiltInRegistries.BLOCK.spliterator(), false).flatMap((block) -> {
        return block.getStateDefinition().getPossibleStates().stream();
    }).collect(Collectors.toList());
    private static final int GRID_WIDTH = Mth.ceil(Mth.sqrt((float) DebugLevelSource.ALL_BLOCKS.size()));
    private static final int GRID_HEIGHT = Mth.ceil((float) DebugLevelSource.ALL_BLOCKS.size() / (float) DebugLevelSource.GRID_WIDTH);
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
    public static final int HEIGHT = 70;
    public static final int BARRIER_HEIGHT = 60;

    public DebugLevelSource(Holder.Reference<Biome> plains) {
        super(new FixedBiomeSource(plains));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return DebugLevelSource.CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {}

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.x;
        int j = chunkpos.z;

        for (int k = 0; k < 16; ++k) {
            for (int l = 0; l < 16; ++l) {
                int i1 = SectionPos.sectionToBlockCoord(i, k);
                int j1 = SectionPos.sectionToBlockCoord(j, l);

                level.setBlock(blockpos_mutableblockpos.set(i1, 60, j1), DebugLevelSource.BARRIER, 2);
                BlockState blockstate = getBlockStateFor(i1, j1);

                level.setBlock(blockpos_mutableblockpos.set(i1, 70, j1), blockstate, 2);
            }
        }

    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk) {
        return CompletableFuture.completedFuture(centerChunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return new NoiseColumn(0, new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {}

    public static BlockState getBlockStateFor(int worldX, int worldZ) {
        BlockState blockstate = DebugLevelSource.AIR;

        if (worldX > 0 && worldZ > 0 && worldX % 2 != 0 && worldZ % 2 != 0) {
            worldX /= 2;
            worldZ /= 2;
            if (worldX <= DebugLevelSource.GRID_WIDTH && worldZ <= DebugLevelSource.GRID_HEIGHT) {
                int k = Mth.abs(worldX * DebugLevelSource.GRID_WIDTH + worldZ);

                if (k < DebugLevelSource.ALL_BLOCKS.size()) {
                    blockstate = (BlockState) DebugLevelSource.ALL_BLOCKS.get(k);
                }
            }
        }

        return blockstate;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {}

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }
}
