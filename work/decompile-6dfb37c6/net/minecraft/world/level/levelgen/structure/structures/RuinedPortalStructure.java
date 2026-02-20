package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class RuinedPortalStructure extends Structure {

    private static final String[] STRUCTURE_LOCATION_PORTALS = new String[]{"ruined_portal/portal_1", "ruined_portal/portal_2", "ruined_portal/portal_3", "ruined_portal/portal_4", "ruined_portal/portal_5", "ruined_portal/portal_6", "ruined_portal/portal_7", "ruined_portal/portal_8", "ruined_portal/portal_9", "ruined_portal/portal_10"};
    private static final String[] STRUCTURE_LOCATION_GIANT_PORTALS = new String[]{"ruined_portal/giant_portal_1", "ruined_portal/giant_portal_2", "ruined_portal/giant_portal_3"};
    private static final float PROBABILITY_OF_GIANT_PORTAL = 0.05F;
    private static final int MIN_Y_INDEX = 15;
    private final List<RuinedPortalStructure.Setup> setups;
    public static final MapCodec<RuinedPortalStructure> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(settingsCodec(instance), ExtraCodecs.nonEmptyList(RuinedPortalStructure.Setup.CODEC.listOf()).fieldOf("setups").forGetter((ruinedportalstructure) -> {
            return ruinedportalstructure.setups;
        })).apply(instance, RuinedPortalStructure::new);
    });

    public RuinedPortalStructure(Structure.StructureSettings settings, List<RuinedPortalStructure.Setup> setups) {
        super(settings);
        this.setups = setups;
    }

    public RuinedPortalStructure(Structure.StructureSettings settings, RuinedPortalStructure.Setup setup) {
        this(settings, List.of(setup));
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        RuinedPortalPiece.Properties ruinedportalpiece_properties = new RuinedPortalPiece.Properties();
        WorldgenRandom worldgenrandom = context.random();
        RuinedPortalStructure.Setup ruinedportalstructure_setup = null;

        if (this.setups.size() > 1) {
            float f = 0.0F;

            for (RuinedPortalStructure.Setup ruinedportalstructure_setup1 : this.setups) {
                f += ruinedportalstructure_setup1.weight();
            }

            float f1 = worldgenrandom.nextFloat();

            for (RuinedPortalStructure.Setup ruinedportalstructure_setup2 : this.setups) {
                f1 -= ruinedportalstructure_setup2.weight() / f;
                if (f1 < 0.0F) {
                    ruinedportalstructure_setup = ruinedportalstructure_setup2;
                    break;
                }
            }
        } else {
            ruinedportalstructure_setup = (RuinedPortalStructure.Setup) this.setups.get(0);
        }

        if (ruinedportalstructure_setup == null) {
            throw new IllegalStateException();
        } else {
            ruinedportalpiece_properties.airPocket = sample(worldgenrandom, ruinedportalstructure_setup.airPocketProbability());
            ruinedportalpiece_properties.mossiness = ruinedportalstructure_setup.mossiness();
            ruinedportalpiece_properties.overgrown = ruinedportalstructure_setup.overgrown();
            ruinedportalpiece_properties.vines = ruinedportalstructure_setup.vines();
            ruinedportalpiece_properties.replaceWithBlackstone = ruinedportalstructure_setup.replaceWithBlackstone();
            Identifier identifier;

            if (worldgenrandom.nextFloat() < 0.05F) {
                identifier = Identifier.withDefaultNamespace(RuinedPortalStructure.STRUCTURE_LOCATION_GIANT_PORTALS[worldgenrandom.nextInt(RuinedPortalStructure.STRUCTURE_LOCATION_GIANT_PORTALS.length)]);
            } else {
                identifier = Identifier.withDefaultNamespace(RuinedPortalStructure.STRUCTURE_LOCATION_PORTALS[worldgenrandom.nextInt(RuinedPortalStructure.STRUCTURE_LOCATION_PORTALS.length)]);
            }

            StructureTemplate structuretemplate = context.structureTemplateManager().getOrCreate(identifier);
            Rotation rotation = (Rotation) Util.getRandom(Rotation.values(), worldgenrandom);
            Mirror mirror = worldgenrandom.nextFloat() < 0.5F ? Mirror.NONE : Mirror.FRONT_BACK;
            BlockPos blockpos = new BlockPos(structuretemplate.getSize().getX() / 2, 0, structuretemplate.getSize().getZ() / 2);
            ChunkGenerator chunkgenerator = context.chunkGenerator();
            LevelHeightAccessor levelheightaccessor = context.heightAccessor();
            RandomState randomstate = context.randomState();
            BlockPos blockpos1 = context.chunkPos().getWorldPosition();
            BoundingBox boundingbox = structuretemplate.getBoundingBox(blockpos1, rotation, blockpos, mirror);
            BlockPos blockpos2 = boundingbox.getCenter();
            int i = chunkgenerator.getBaseHeight(blockpos2.getX(), blockpos2.getZ(), RuinedPortalPiece.getHeightMapType(ruinedportalstructure_setup.placement()), levelheightaccessor, randomstate) - 1;
            int j = findSuitableY(worldgenrandom, chunkgenerator, ruinedportalstructure_setup.placement(), ruinedportalpiece_properties.airPocket, i, boundingbox.getYSpan(), boundingbox, levelheightaccessor, randomstate);
            BlockPos blockpos3 = new BlockPos(blockpos1.getX(), j, blockpos1.getZ());

            return Optional.of(new Structure.GenerationStub(blockpos3, (structurepiecesbuilder) -> {
                if (ruinedportalstructure_setup.canBeCold()) {
                    ruinedportalpiece_properties.cold = isCold(blockpos3, context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(blockpos3.getX()), QuartPos.fromBlock(blockpos3.getY()), QuartPos.fromBlock(blockpos3.getZ()), randomstate.sampler()), chunkgenerator.getSeaLevel());
                }

                structurepiecesbuilder.addPiece(new RuinedPortalPiece(context.structureTemplateManager(), blockpos3, ruinedportalstructure_setup.placement(), ruinedportalpiece_properties, identifier, structuretemplate, rotation, mirror, blockpos));
            }));
        }
    }

    private static boolean sample(WorldgenRandom random, float limit) {
        return limit == 0.0F ? false : (limit == 1.0F ? true : random.nextFloat() < limit);
    }

    private static boolean isCold(BlockPos pos, Holder<Biome> biome, int seaLevel) {
        return ((Biome) biome.value()).coldEnoughToSnow(pos, seaLevel);
    }

    private static int findSuitableY(RandomSource random, ChunkGenerator generator, RuinedPortalPiece.VerticalPlacement verticalPlacement, boolean airPocket, int surfaceYAtCenter, int ySpan, BoundingBox boundingBox, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int k = heightAccessor.getMinY() + 15;
        int l;

        if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER) {
            if (airPocket) {
                l = Mth.randomBetweenInclusive(random, 32, 100);
            } else if (random.nextFloat() < 0.5F) {
                l = Mth.randomBetweenInclusive(random, 27, 29);
            } else {
                l = Mth.randomBetweenInclusive(random, 29, 100);
            }
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_MOUNTAIN) {
            int i1 = surfaceYAtCenter - ySpan;

            l = getRandomWithinInterval(random, 70, i1);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.UNDERGROUND) {
            int j1 = surfaceYAtCenter - ySpan;

            l = getRandomWithinInterval(random, k, j1);
        } else if (verticalPlacement == RuinedPortalPiece.VerticalPlacement.PARTLY_BURIED) {
            l = surfaceYAtCenter - ySpan + Mth.randomBetweenInclusive(random, 2, 8);
        } else {
            l = surfaceYAtCenter;
        }

        List<BlockPos> list = ImmutableList.of(new BlockPos(boundingBox.minX(), 0, boundingBox.minZ()), new BlockPos(boundingBox.maxX(), 0, boundingBox.minZ()), new BlockPos(boundingBox.minX(), 0, boundingBox.maxZ()), new BlockPos(boundingBox.maxX(), 0, boundingBox.maxZ()));
        List<NoiseColumn> list1 = (List) list.stream().map((blockpos) -> {
            return generator.getBaseColumn(blockpos.getX(), blockpos.getZ(), heightAccessor, randomState);
        }).collect(Collectors.toList());
        Heightmap.Types heightmap_types = verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;

        int k1;

        for (k1 = l; k1 > k; --k1) {
            int l1 = 0;

            for (NoiseColumn noisecolumn : list1) {
                BlockState blockstate = noisecolumn.getBlock(k1);

                if (heightmap_types.isOpaque().test(blockstate)) {
                    ++l1;
                    if (l1 == 3) {
                        return k1;
                    }
                }
            }
        }

        return k1;
    }

    private static int getRandomWithinInterval(RandomSource random, int minPreferred, int max) {
        return minPreferred < max ? Mth.randomBetweenInclusive(random, minPreferred, max) : max;
    }

    @Override
    public StructureType<?> type() {
        return StructureType.RUINED_PORTAL;
    }

    public static record Setup(RuinedPortalPiece.VerticalPlacement placement, float airPocketProbability, float mossiness, boolean overgrown, boolean vines, boolean canBeCold, boolean replaceWithBlackstone, float weight) {

        public static final Codec<RuinedPortalStructure.Setup> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(RuinedPortalPiece.VerticalPlacement.CODEC.fieldOf("placement").forGetter(RuinedPortalStructure.Setup::placement), Codec.floatRange(0.0F, 1.0F).fieldOf("air_pocket_probability").forGetter(RuinedPortalStructure.Setup::airPocketProbability), Codec.floatRange(0.0F, 1.0F).fieldOf("mossiness").forGetter(RuinedPortalStructure.Setup::mossiness), Codec.BOOL.fieldOf("overgrown").forGetter(RuinedPortalStructure.Setup::overgrown), Codec.BOOL.fieldOf("vines").forGetter(RuinedPortalStructure.Setup::vines), Codec.BOOL.fieldOf("can_be_cold").forGetter(RuinedPortalStructure.Setup::canBeCold), Codec.BOOL.fieldOf("replace_with_blackstone").forGetter(RuinedPortalStructure.Setup::replaceWithBlackstone), ExtraCodecs.POSITIVE_FLOAT.fieldOf("weight").forGetter(RuinedPortalStructure.Setup::weight)).apply(instance, RuinedPortalStructure.Setup::new);
        });
    }
}
