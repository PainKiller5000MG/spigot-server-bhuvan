package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class SurfaceSystem {

    private static final BlockState WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
    private static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private final BlockState defaultBlock;
    private final int seaLevel;
    private final BlockState[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState randomState, BlockState defaultBlock, int seaLevel, PositionalRandomFactory noiseRandom) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.noiseRandom = noiseRandom;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        this.clayBands = generateBands(noiseRandom.fromHashOf(Identifier.withDefaultNamespace("clay_bands")));
        this.surfaceNoise = randomState.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = randomState.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = randomState.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = randomState.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = randomState.getOrCreateNoise(Noises.ICEBERG_SURFACE);
    }

    public void buildSurface(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean useLegacyRandom, WorldGenerationContext generationContext, final ChunkAccess protoChunk, NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource) {
        final BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        final ChunkPos chunkpos = protoChunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        BlockColumn blockcolumn = new BlockColumn() {
            @Override
            public BlockState getBlock(int blockY) {
                return protoChunk.getBlockState(blockpos_mutableblockpos.setY(blockY));
            }

            @Override
            public void setBlock(int blockY, BlockState state) {
                LevelHeightAccessor levelheightaccessor = protoChunk.getHeightAccessorForGeneration();

                if (levelheightaccessor.isInsideBuildHeight(blockY)) {
                    protoChunk.setBlockState(blockpos_mutableblockpos.setY(blockY), state);
                    if (!state.getFluidState().isEmpty()) {
                        protoChunk.markPosForPostprocessing(blockpos_mutableblockpos);
                    }
                }

            }

            public String toString() {
                return "ChunkBlockColumn " + String.valueOf(chunkpos);
            }
        };

        Objects.requireNonNull(biomeManager);
        SurfaceRules.Context surfacerules_context = new SurfaceRules.Context(this, randomState, protoChunk, noiseChunk, biomeManager::getBiome, biomes, generationContext);
        SurfaceRules.SurfaceRule surfacerules_surfacerule = (SurfaceRules.SurfaceRule) ruleSource.apply(surfacerules_context);
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; ++k) {
            for (int l = 0; l < 16; ++l) {
                int i1 = i + k;
                int j1 = j + l;
                int k1 = protoChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;

                blockpos_mutableblockpos.setX(i1).setZ(j1);
                Holder<Biome> holder = biomeManager.getBiome(blockpos_mutableblockpos1.set(i1, useLegacyRandom ? 0 : k1, j1));

                if (holder.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockcolumn, i1, j1, k1, protoChunk);
                }

                int l1 = protoChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;

                surfacerules_context.updateXZ(i1, j1);
                int i2 = 0;
                int j2 = Integer.MIN_VALUE;
                int k2 = Integer.MAX_VALUE;
                int l2 = protoChunk.getMinY();

                for (int i3 = l1; i3 >= l2; --i3) {
                    BlockState blockstate = blockcolumn.getBlock(i3);

                    if (blockstate.isAir()) {
                        i2 = 0;
                        j2 = Integer.MIN_VALUE;
                    } else if (!blockstate.getFluidState().isEmpty()) {
                        if (j2 == Integer.MIN_VALUE) {
                            j2 = i3 + 1;
                        }
                    } else {
                        if (k2 >= i3) {
                            k2 = DimensionType.WAY_BELOW_MIN_Y;

                            for (int j3 = i3 - 1; j3 >= l2 - 1; --j3) {
                                BlockState blockstate1 = blockcolumn.getBlock(j3);

                                if (!this.isStone(blockstate1)) {
                                    k2 = j3 + 1;
                                    break;
                                }
                            }
                        }

                        ++i2;
                        int k3 = i3 - k2 + 1;

                        surfacerules_context.updateY(i2, k3, j2, i1, i3, j1);
                        if (blockstate == this.defaultBlock) {
                            BlockState blockstate2 = surfacerules_surfacerule.tryApply(i1, i3, j1);

                            if (blockstate2 != null) {
                                blockcolumn.setBlock(i3, blockstate2);
                            }
                        }
                    }
                }

                if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    this.frozenOceanExtension(surfacerules_context.getMinSurfaceLevel(), holder.value(), blockcolumn, blockpos_mutableblockpos1, i1, j1, k1);
                }
            }
        }

    }

    protected int getSurfaceDepth(int blockX, int blockZ) {
        double d0 = this.surfaceNoise.getValue((double) blockX, 0.0D, (double) blockZ);

        return (int) (d0 * 2.75D + 3.0D + this.noiseRandom.at(blockX, 0, blockZ).nextDouble() * 0.25D);
    }

    protected double getSurfaceSecondary(int blockX, int blockZ) {
        return this.surfaceSecondaryNoise.getValue((double) blockX, 0.0D, (double) blockZ);
    }

    private boolean isStone(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    /** @deprecated */
    @Deprecated
    public Optional<BlockState> topMaterial(SurfaceRules.RuleSource ruleSource, CarvingContext carvingContext, Function<BlockPos, Holder<Biome>> biomeGetter, ChunkAccess chunk, NoiseChunk noiseChunk, BlockPos pos, boolean underFluid) {
        SurfaceRules.Context surfacerules_context = new SurfaceRules.Context(this, carvingContext.randomState(), chunk, noiseChunk, biomeGetter, carvingContext.registryAccess().lookupOrThrow(Registries.BIOME), carvingContext);
        SurfaceRules.SurfaceRule surfacerules_surfacerule = (SurfaceRules.SurfaceRule) ruleSource.apply(surfacerules_context);
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        surfacerules_context.updateXZ(i, k);
        surfacerules_context.updateY(1, 1, underFluid ? j + 1 : Integer.MIN_VALUE, i, j, k);
        BlockState blockstate = surfacerules_surfacerule.tryApply(i, j, k);

        return Optional.ofNullable(blockstate);
    }

    private void erodedBadlandsExtension(BlockColumn column, int blockX, int blockZ, int height, LevelHeightAccessor protoChunk) {
        double d0 = 0.2D;
        double d1 = Math.min(Math.abs(this.badlandsSurfaceNoise.getValue((double) blockX, 0.0D, (double) blockZ) * 8.25D), this.badlandsPillarNoise.getValue((double) blockX * 0.2D, 0.0D, (double) blockZ * 0.2D) * 15.0D);

        if (d1 > 0.0D) {
            double d2 = 0.75D;
            double d3 = 1.5D;
            double d4 = Math.abs(this.badlandsPillarRoofNoise.getValue((double) blockX * 0.75D, 0.0D, (double) blockZ * 0.75D) * 1.5D);
            double d5 = 64.0D + Math.min(d1 * d1 * 2.5D, Math.ceil(d4 * 50.0D) + 24.0D);
            int l = Mth.floor(d5);

            if (height <= l) {
                for (int i1 = l; i1 >= protoChunk.getMinY(); --i1) {
                    BlockState blockstate = column.getBlock(i1);

                    if (blockstate.is(this.defaultBlock.getBlock())) {
                        break;
                    }

                    if (blockstate.is(Blocks.WATER)) {
                        return;
                    }
                }

                for (int j1 = l; j1 >= protoChunk.getMinY() && column.getBlock(j1).isAir(); --j1) {
                    column.setBlock(j1, this.defaultBlock);
                }

            }
        }
    }

    private void frozenOceanExtension(int minSurfaceLevel, Biome surfaceBiome, BlockColumn column, BlockPos.MutableBlockPos blockPos, int blockX, int blockZ, int height) {
        double d0 = 1.28D;
        double d1 = Math.min(Math.abs(this.icebergSurfaceNoise.getValue((double) blockX, 0.0D, (double) blockZ) * 8.25D), this.icebergPillarNoise.getValue((double) blockX * 1.28D, 0.0D, (double) blockZ * 1.28D) * 15.0D);

        if (d1 > 1.8D) {
            double d2 = 1.17D;
            double d3 = 1.5D;
            double d4 = Math.abs(this.icebergPillarRoofNoise.getValue((double) blockX * 1.17D, 0.0D, (double) blockZ * 1.17D) * 1.5D);
            double d5 = Math.min(d1 * d1 * 1.2D, Math.ceil(d4 * 40.0D) + 14.0D);

            if (surfaceBiome.shouldMeltFrozenOceanIcebergSlightly(blockPos.set(blockX, this.seaLevel, blockZ), this.seaLevel)) {
                d5 -= 2.0D;
            }

            double d6;

            if (d5 > 2.0D) {
                d6 = (double) this.seaLevel - d5 - 7.0D;
                d5 += (double) this.seaLevel;
            } else {
                d5 = 0.0D;
                d6 = 0.0D;
            }

            double d7 = d5;
            RandomSource randomsource = this.noiseRandom.at(blockX, 0, blockZ);
            int i1 = 2 + randomsource.nextInt(4);
            int j1 = this.seaLevel + 18 + randomsource.nextInt(10);
            int k1 = 0;

            for (int l1 = Math.max(height, (int) d5 + 1); l1 >= minSurfaceLevel; --l1) {
                if (column.getBlock(l1).isAir() && l1 < (int) d7 && randomsource.nextDouble() > 0.01D || column.getBlock(l1).is(Blocks.WATER) && l1 > (int) d6 && l1 < this.seaLevel && d6 != 0.0D && randomsource.nextDouble() > 0.15D) {
                    if (k1 <= i1 && l1 > j1) {
                        column.setBlock(l1, SurfaceSystem.SNOW_BLOCK);
                        ++k1;
                    } else {
                        column.setBlock(l1, SurfaceSystem.PACKED_ICE);
                    }
                }
            }

        }
    }

    private static BlockState[] generateBands(RandomSource random) {
        BlockState[] ablockstate = new BlockState[192];

        Arrays.fill(ablockstate, SurfaceSystem.TERRACOTTA);

        for (int i = 0; i < ablockstate.length; ++i) {
            i += random.nextInt(5) + 1;
            if (i < ablockstate.length) {
                ablockstate[i] = SurfaceSystem.ORANGE_TERRACOTTA;
            }
        }

        makeBands(random, ablockstate, 1, SurfaceSystem.YELLOW_TERRACOTTA);
        makeBands(random, ablockstate, 2, SurfaceSystem.BROWN_TERRACOTTA);
        makeBands(random, ablockstate, 1, SurfaceSystem.RED_TERRACOTTA);
        int j = random.nextIntBetweenInclusive(9, 15);
        int k = 0;

        for (int l = 0; k < j && l < ablockstate.length; l += random.nextInt(16) + 4) {
            ablockstate[l] = SurfaceSystem.WHITE_TERRACOTTA;
            if (l - 1 > 0 && random.nextBoolean()) {
                ablockstate[l - 1] = SurfaceSystem.LIGHT_GRAY_TERRACOTTA;
            }

            if (l + 1 < ablockstate.length && random.nextBoolean()) {
                ablockstate[l + 1] = SurfaceSystem.LIGHT_GRAY_TERRACOTTA;
            }

            ++k;
        }

        return ablockstate;
    }

    private static void makeBands(RandomSource random, BlockState[] clayBands, int baseWidth, BlockState state) {
        int j = random.nextIntBetweenInclusive(6, 15);

        for (int k = 0; k < j; ++k) {
            int l = baseWidth + random.nextInt(3);
            int i1 = random.nextInt(clayBands.length);

            for (int j1 = 0; i1 + j1 < clayBands.length && j1 < l; ++j1) {
                clayBands[i1 + j1] = state;
            }
        }

    }

    protected BlockState getBand(int worldX, int y, int worldZ) {
        int l = (int) Math.round(this.clayBandsOffsetNoise.getValue((double) worldX, 0.0D, (double) worldZ) * 4.0D);

        return this.clayBands[(y + l + this.clayBands.length) % this.clayBands.length];
    }
}
