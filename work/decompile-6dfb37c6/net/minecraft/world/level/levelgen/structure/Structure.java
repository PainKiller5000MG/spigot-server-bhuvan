package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public abstract class Structure {

    public static final Codec<Structure> DIRECT_CODEC = BuiltInRegistries.STRUCTURE_TYPE.byNameCodec().dispatch(Structure::type, StructureType::codec);
    public static final Codec<Holder<Structure>> CODEC = RegistryFileCodec.<Holder<Structure>>create(Registries.STRUCTURE, Structure.DIRECT_CODEC);
    protected final Structure.StructureSettings settings;

    public static <S extends Structure> RecordCodecBuilder<S, Structure.StructureSettings> settingsCodec(RecordCodecBuilder.Instance<S> i) {
        return Structure.StructureSettings.CODEC.forGetter((structure) -> {
            return structure.settings;
        });
    }

    public static <S extends Structure> MapCodec<S> simpleCodec(Function<Structure.StructureSettings, S> constructor) {
        return RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(settingsCodec(instance)).apply(instance, constructor);
        });
    }

    protected Structure(Structure.StructureSettings settings) {
        this.settings = settings;
    }

    public HolderSet<Biome> biomes() {
        return this.settings.biomes;
    }

    public Map<MobCategory, StructureSpawnOverride> spawnOverrides() {
        return this.settings.spawnOverrides;
    }

    public GenerationStep.Decoration step() {
        return this.settings.step;
    }

    public TerrainAdjustment terrainAdaptation() {
        return this.settings.terrainAdaptation;
    }

    public BoundingBox adjustBoundingBox(BoundingBox boundingBox) {
        return this.terrainAdaptation() != TerrainAdjustment.NONE ? boundingBox.inflatedBy(12) : boundingBox;
    }

    public StructureStart generate(Holder<Structure> selected, ResourceKey<Level> dimension, RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed, ChunkPos sourceChunkPos, int references, LevelHeightAccessor heightAccessor, Predicate<Holder<Biome>> validBiome) {
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onStructureGenerate(sourceChunkPos, dimension, selected);
        Structure.GenerationContext structure_generationcontext = new Structure.GenerationContext(registryAccess, chunkGenerator, biomeSource, randomState, structureTemplateManager, seed, sourceChunkPos, heightAccessor, validBiome);
        Optional<Structure.GenerationStub> optional = this.findValidGenerationPoint(structure_generationcontext);

        if (optional.isPresent()) {
            StructurePiecesBuilder structurepiecesbuilder = ((Structure.GenerationStub) optional.get()).getPiecesBuilder();
            StructureStart structurestart = new StructureStart(this, sourceChunkPos, references, structurepiecesbuilder.build());

            if (structurestart.isValid()) {
                if (profiledduration != null) {
                    profiledduration.finish(true);
                }

                return structurestart;
            }
        }

        if (profiledduration != null) {
            profiledduration.finish(false);
        }

        return StructureStart.INVALID_START;
    }

    protected static Optional<Structure.GenerationStub> onTopOfChunkCenter(Structure.GenerationContext context, Heightmap.Types heightmap, Consumer<StructurePiecesBuilder> generator) {
        ChunkPos chunkpos = context.chunkPos();
        int i = chunkpos.getMiddleBlockX();
        int j = chunkpos.getMiddleBlockZ();
        int k = context.chunkGenerator().getFirstOccupiedHeight(i, j, heightmap, context.heightAccessor(), context.randomState());

        return Optional.of(new Structure.GenerationStub(new BlockPos(i, k, j), generator));
    }

    private static boolean isValidBiome(Structure.GenerationStub stub, Structure.GenerationContext context) {
        BlockPos blockpos = stub.position();

        return context.validBiome.test(context.chunkGenerator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(blockpos.getX()), QuartPos.fromBlock(blockpos.getY()), QuartPos.fromBlock(blockpos.getZ()), context.randomState.sampler()));
    }

    public void afterPlace(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, PiecesContainer pieces) {}

    private static int[] getCornerHeights(Structure.GenerationContext context, int minX, int sizeX, int minZ, int sizeZ) {
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        LevelHeightAccessor levelheightaccessor = context.heightAccessor();
        RandomState randomstate = context.randomState();

        return new int[]{chunkgenerator.getFirstOccupiedHeight(minX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate), chunkgenerator.getFirstOccupiedHeight(minX, minZ + sizeZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate), chunkgenerator.getFirstOccupiedHeight(minX + sizeX, minZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate), chunkgenerator.getFirstOccupiedHeight(minX + sizeX, minZ + sizeZ, Heightmap.Types.WORLD_SURFACE_WG, levelheightaccessor, randomstate)};
    }

    public static int getMeanFirstOccupiedHeight(Structure.GenerationContext context, int minX, int sizeX, int minZ, int sizeZ) {
        int[] aint = getCornerHeights(context, minX, sizeX, minZ, sizeZ);

        return (aint[0] + aint[1] + aint[2] + aint[3]) / 4;
    }

    protected static int getLowestY(Structure.GenerationContext context, int sizeX, int sizeZ) {
        ChunkPos chunkpos = context.chunkPos();
        int k = chunkpos.getMinBlockX();
        int l = chunkpos.getMinBlockZ();

        return getLowestY(context, k, l, sizeX, sizeZ);
    }

    protected static int getLowestY(Structure.GenerationContext context, int minX, int minZ, int sizeX, int sizeZ) {
        int[] aint = getCornerHeights(context, minX, sizeX, minZ, sizeZ);

        return Math.min(Math.min(aint[0], aint[1]), Math.min(aint[2], aint[3]));
    }

    /** @deprecated */
    @Deprecated
    protected BlockPos getLowestYIn5by5BoxOffset7Blocks(Structure.GenerationContext context, Rotation rotation) {
        int i = 5;
        int j = 5;

        if (rotation == Rotation.CLOCKWISE_90) {
            i = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            i = -5;
            j = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            j = -5;
        }

        ChunkPos chunkpos = context.chunkPos();
        int k = chunkpos.getBlockX(7);
        int l = chunkpos.getBlockZ(7);

        return new BlockPos(k, getLowestY(context, k, l, i, j), l);
    }

    protected abstract Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context);

    public Optional<Structure.GenerationStub> findValidGenerationPoint(Structure.GenerationContext context) {
        return this.findGenerationPoint(context).filter((structure_generationstub) -> {
            return isValidBiome(structure_generationstub, context);
        });
    }

    public abstract StructureType<?> type();

    public static record StructureSettings(HolderSet<Biome> biomes, Map<MobCategory, StructureSpawnOverride> spawnOverrides, GenerationStep.Decoration step, TerrainAdjustment terrainAdaptation) {

        private static final Structure.StructureSettings DEFAULT = new Structure.StructureSettings(HolderSet.direct(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        public static final MapCodec<Structure.StructureSettings> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Structure.StructureSettings::biomes), Codec.simpleMap(MobCategory.CODEC, StructureSpawnOverride.CODEC, StringRepresentable.keys(MobCategory.values())).fieldOf("spawn_overrides").forGetter(Structure.StructureSettings::spawnOverrides), GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(Structure.StructureSettings::step), TerrainAdjustment.CODEC.optionalFieldOf("terrain_adaptation", Structure.StructureSettings.DEFAULT.terrainAdaptation).forGetter(Structure.StructureSettings::terrainAdaptation)).apply(instance, Structure.StructureSettings::new);
        });

        public StructureSettings(HolderSet<Biome> biomes) {
            this(biomes, Structure.StructureSettings.DEFAULT.spawnOverrides, Structure.StructureSettings.DEFAULT.step, Structure.StructureSettings.DEFAULT.terrainAdaptation);
        }

        public static class Builder {

            private final HolderSet<Biome> biomes;
            private Map<MobCategory, StructureSpawnOverride> spawnOverrides;
            private GenerationStep.Decoration step;
            private TerrainAdjustment terrainAdaption;

            public Builder(HolderSet<Biome> biomes) {
                this.spawnOverrides = Structure.StructureSettings.DEFAULT.spawnOverrides;
                this.step = Structure.StructureSettings.DEFAULT.step;
                this.terrainAdaption = Structure.StructureSettings.DEFAULT.terrainAdaptation;
                this.biomes = biomes;
            }

            public Structure.StructureSettings.Builder spawnOverrides(Map<MobCategory, StructureSpawnOverride> spawnOverrides) {
                this.spawnOverrides = spawnOverrides;
                return this;
            }

            public Structure.StructureSettings.Builder generationStep(GenerationStep.Decoration step) {
                this.step = step;
                return this;
            }

            public Structure.StructureSettings.Builder terrainAdapation(TerrainAdjustment terrainAdaption) {
                this.terrainAdaption = terrainAdaption;
                return this;
            }

            public Structure.StructureSettings build() {
                return new Structure.StructureSettings(this.biomes, this.spawnOverrides, this.step, this.terrainAdaption);
            }
        }
    }

    public static record GenerationContext(RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState, StructureTemplateManager structureTemplateManager, WorldgenRandom random, long seed, ChunkPos chunkPos, LevelHeightAccessor heightAccessor, Predicate<Holder<Biome>> validBiome) {

        public GenerationContext(RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed, ChunkPos chunkPos, LevelHeightAccessor heightAccessor, Predicate<Holder<Biome>> validBiome) {
            this(registryAccess, chunkGenerator, biomeSource, randomState, structureTemplateManager, makeRandom(seed, chunkPos), seed, chunkPos, heightAccessor, validBiome);
        }

        private static WorldgenRandom makeRandom(long seed, ChunkPos chunkPos) {
            WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));

            worldgenrandom.setLargeFeatureSeed(seed, chunkPos.x, chunkPos.z);
            return worldgenrandom;
        }
    }

    public static record GenerationStub(BlockPos position, Either<Consumer<StructurePiecesBuilder>, StructurePiecesBuilder> generator) {

        public GenerationStub(BlockPos position, Consumer<StructurePiecesBuilder> generator) {
            this(position, Either.left(generator));
        }

        public StructurePiecesBuilder getPiecesBuilder() {
            return (StructurePiecesBuilder) this.generator.map((consumer) -> {
                StructurePiecesBuilder structurepiecesbuilder = new StructurePiecesBuilder();

                consumer.accept(structurepiecesbuilder);
                return structurepiecesbuilder;
            }, (structurepiecesbuilder) -> {
                return structurepiecesbuilder;
            });
        }
    }
}
