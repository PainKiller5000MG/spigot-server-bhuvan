package net.minecraft.world.level.levelgen.carver;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

public abstract class WorldCarver<C extends CarverConfiguration> {

    public static final WorldCarver<CaveCarverConfiguration> CAVE = register("cave", new CaveWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CaveCarverConfiguration> NETHER_CAVE = register("nether_cave", new NetherWorldCarver(CaveCarverConfiguration.CODEC));
    public static final WorldCarver<CanyonCarverConfiguration> CANYON = register("canyon", new CanyonWorldCarver(CanyonCarverConfiguration.CODEC));
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected static final FluidState WATER = Fluids.WATER.defaultFluidState();
    protected static final FluidState LAVA = Fluids.LAVA.defaultFluidState();
    protected Set<Fluid> liquids;
    private final MapCodec<ConfiguredWorldCarver<C>> configuredCodec;

    private static <C extends CarverConfiguration, F extends WorldCarver<C>> F register(String name, F carver) {
        return (F) (Registry.register(BuiltInRegistries.CARVER, name, carver));
    }

    public WorldCarver(Codec<C> codec) {
        this.liquids = ImmutableSet.of(Fluids.WATER);
        this.configuredCodec = codec.fieldOf("config").xmap(this::configured, ConfiguredWorldCarver::config);
    }

    public ConfiguredWorldCarver<C> configured(C configuration) {
        return new ConfiguredWorldCarver<C>(this, configuration);
    }

    public MapCodec<ConfiguredWorldCarver<C>> configuredCodec() {
        return this.configuredCodec;
    }

    public int getRange() {
        return 4;
    }

    protected boolean carveEllipsoid(CarvingContext context, C configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, Aquifer aquifer, double x, double y, double z, double horizontalRadius, double verticalRadius, CarvingMask mask, WorldCarver.CarveSkipChecker skipChecker) {
        ChunkPos chunkpos = chunk.getPos();
        double d5 = (double) chunkpos.getMiddleBlockX();
        double d6 = (double) chunkpos.getMiddleBlockZ();
        double d7 = 16.0D + horizontalRadius * 2.0D;

        if (Math.abs(x - d5) <= d7 && Math.abs(z - d6) <= d7) {
            int i = chunkpos.getMinBlockX();
            int j = chunkpos.getMinBlockZ();
            int k = Math.max(Mth.floor(x - horizontalRadius) - i - 1, 0);
            int l = Math.min(Mth.floor(x + horizontalRadius) - i, 15);
            int i1 = Math.max(Mth.floor(y - verticalRadius) - 1, context.getMinGenY() + 1);
            int j1 = chunk.isUpgrading() ? 0 : 7;
            int k1 = Math.min(Mth.floor(y + verticalRadius) + 1, context.getMinGenY() + context.getGenDepth() - 1 - j1);
            int l1 = Math.max(Mth.floor(z - horizontalRadius) - j - 1, 0);
            int i2 = Math.min(Mth.floor(z + horizontalRadius) - j, 15);
            boolean flag = false;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos blockpos_mutableblockpos1 = new BlockPos.MutableBlockPos();

            for (int j2 = k; j2 <= l; ++j2) {
                int k2 = chunkpos.getBlockX(j2);
                double d8 = ((double) k2 + 0.5D - x) / horizontalRadius;

                for (int l2 = l1; l2 <= i2; ++l2) {
                    int i3 = chunkpos.getBlockZ(l2);
                    double d9 = ((double) i3 + 0.5D - z) / horizontalRadius;

                    if (d8 * d8 + d9 * d9 < 1.0D) {
                        MutableBoolean mutableboolean = new MutableBoolean(false);

                        for (int j3 = k1; j3 > i1; --j3) {
                            double d10 = ((double) j3 - 0.5D - y) / verticalRadius;

                            if (!skipChecker.shouldSkip(context, d8, d10, d9, j3) && (!mask.get(j2, j3, l2) || isDebugEnabled(configuration))) {
                                mask.set(j2, j3, l2);
                                blockpos_mutableblockpos.set(k2, j3, i3);
                                flag |= this.carveBlock(context, configuration, chunk, biomeGetter, mask, blockpos_mutableblockpos, blockpos_mutableblockpos1, aquifer, mutableboolean);
                            }
                        }
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    protected boolean carveBlock(CarvingContext context, C configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, CarvingMask mask, BlockPos.MutableBlockPos blockPos, BlockPos.MutableBlockPos helperPos, Aquifer aquifer, MutableBoolean hasGrass) {
        BlockState blockstate = chunk.getBlockState(blockPos);

        if (blockstate.is(Blocks.GRASS_BLOCK) || blockstate.is(Blocks.MYCELIUM)) {
            hasGrass.setTrue();
        }

        if (!this.canReplaceBlock(configuration, blockstate) && !isDebugEnabled(configuration)) {
            return false;
        } else {
            BlockState blockstate1 = this.getCarveState(context, configuration, blockPos, aquifer);

            if (blockstate1 == null) {
                return false;
            } else {
                chunk.setBlockState(blockPos, blockstate1);
                if (aquifer.shouldScheduleFluidUpdate() && !blockstate1.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(blockPos);
                }

                if (hasGrass.isTrue()) {
                    helperPos.setWithOffset(blockPos, Direction.DOWN);
                    if (chunk.getBlockState(helperPos).is(Blocks.DIRT)) {
                        context.topMaterial(biomeGetter, chunk, helperPos, !blockstate1.getFluidState().isEmpty()).ifPresent((blockstate2) -> {
                            chunk.setBlockState(helperPos, blockstate2);
                            if (!blockstate2.getFluidState().isEmpty()) {
                                chunk.markPosForPostprocessing(helperPos);
                            }

                        });
                    }
                }

                return true;
            }
        }
    }

    private @Nullable BlockState getCarveState(CarvingContext context, C configuration, BlockPos blockPos, Aquifer aquifer) {
        if (blockPos.getY() <= configuration.lavaLevel.resolveY(context)) {
            return WorldCarver.LAVA.createLegacyBlock();
        } else {
            BlockState blockstate = aquifer.computeSubstance(new DensityFunction.SinglePointContext(blockPos.getX(), blockPos.getY(), blockPos.getZ()), 0.0D);

            return blockstate == null ? (isDebugEnabled(configuration) ? configuration.debugSettings.getBarrierState() : null) : (isDebugEnabled(configuration) ? getDebugState(configuration, blockstate) : blockstate);
        }
    }

    private static BlockState getDebugState(CarverConfiguration configuration, BlockState state) {
        if (state.is(Blocks.AIR)) {
            return configuration.debugSettings.getAirState();
        } else if (state.is(Blocks.WATER)) {
            BlockState blockstate1 = configuration.debugSettings.getWaterState();

            return blockstate1.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState) blockstate1.setValue(BlockStateProperties.WATERLOGGED, true) : blockstate1;
        } else {
            return state.is(Blocks.LAVA) ? configuration.debugSettings.getLavaState() : state;
        }
    }

    public abstract boolean carve(CarvingContext context, C configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos sourceChunkPos, CarvingMask mask);

    public abstract boolean isStartChunk(C configuration, RandomSource random);

    protected boolean canReplaceBlock(C configuration, BlockState state) {
        return state.is(configuration.replaceable);
    }

    protected static boolean canReach(ChunkPos chunkPos, double x, double z, int currentStep, int totalSteps, float thickness) {
        double d2 = (double) chunkPos.getMiddleBlockX();
        double d3 = (double) chunkPos.getMiddleBlockZ();
        double d4 = x - d2;
        double d5 = z - d3;
        double d6 = (double) (totalSteps - currentStep);
        double d7 = (double) (thickness + 2.0F + 16.0F);

        return d4 * d4 + d5 * d5 - d6 * d6 <= d7 * d7;
    }

    private static boolean isDebugEnabled(CarverConfiguration configuration) {
        return SharedConstants.DEBUG_CARVERS || configuration.debugSettings.isDebugMode();
    }

    public interface CarveSkipChecker {

        boolean shouldSkip(CarvingContext context, double xd, double yd, double zd, int y);
    }
}
