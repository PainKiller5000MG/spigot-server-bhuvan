package net.minecraft.world.level.levelgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class FlatLevelSource extends ChunkGenerator {

    public static final MapCodec<FlatLevelSource> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings)).apply(instance, instance.stable(FlatLevelSource::new));
    });
    private final FlatLevelGeneratorSettings settings;

    public FlatLevelSource(FlatLevelGeneratorSettings generatorSettings) {
        FixedBiomeSource fixedbiomesource = new FixedBiomeSource(generatorSettings.getBiome());

        Objects.requireNonNull(generatorSettings);
        super(fixedbiomesource, Util.memoize(generatorSettings::adjustGenerationSettings));
        this.settings = generatorSettings;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long levelSeed) {
        Stream<Holder<StructureSet>> stream = (Stream) this.settings.structureOverrides().map(HolderSet::stream).orElseGet(() -> {
            return structureSets.listElements().map((holder_reference) -> {
                return holder_reference;
            });
        });

        return ChunkGeneratorStructureState.createForFlat(randomState, levelSeed, this.biomeSource, stream);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return FlatLevelSource.CODEC;
    }

    public FlatLevelGeneratorSettings settings() {
        return this.settings;
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {}

    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return heightAccessor.getMinY() + Math.min(heightAccessor.getHeight(), this.settings.getLayers().size());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk) {
        List<BlockState> list = this.settings.getLayers();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        Heightmap heightmap = centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = centerChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int i = 0; i < Math.min(centerChunk.getHeight(), list.size()); ++i) {
            BlockState blockstate = (BlockState) list.get(i);

            if (blockstate != null) {
                int j = centerChunk.getMinY() + i;

                for (int k = 0; k < 16; ++k) {
                    for (int l = 0; l < 16; ++l) {
                        centerChunk.setBlockState(blockpos_mutableblockpos.set(k, j, l), blockstate);
                        heightmap.update(k, j, l, blockstate);
                        heightmap1.update(k, j, l, blockstate);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(centerChunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        List<BlockState> list = this.settings.getLayers();

        for (int k = Math.min(list.size() - 1, heightAccessor.getMaxY()); k >= 0; --k) {
            BlockState blockstate = (BlockState) list.get(k);

            if (blockstate != null && type.isOpaque().test(blockstate)) {
                return heightAccessor.getMinY() + k + 1;
            }
        }

        return heightAccessor.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return new NoiseColumn(heightAccessor.getMinY(), (BlockState[]) this.settings.getLayers().stream().limit((long) heightAccessor.getHeight()).map((blockstate) -> {
            return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
        }).toArray((k) -> {
            return new BlockState[k];
        }));
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {}

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
        return -63;
    }
}
