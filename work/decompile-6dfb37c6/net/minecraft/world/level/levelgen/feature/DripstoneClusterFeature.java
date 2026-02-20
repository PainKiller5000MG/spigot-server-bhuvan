package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ClampedNormalFloat;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;

public class DripstoneClusterFeature extends Feature<DripstoneClusterConfiguration> {

    public DripstoneClusterFeature(Codec<DripstoneClusterConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DripstoneClusterConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        DripstoneClusterConfiguration dripstoneclusterconfiguration = context.config();
        RandomSource randomsource = context.random();

        if (!DripstoneUtils.isEmptyOrWater(worldgenlevel, blockpos)) {
            return false;
        } else {
            int i = dripstoneclusterconfiguration.height.sample(randomsource);
            float f = dripstoneclusterconfiguration.wetness.sample(randomsource);
            float f1 = dripstoneclusterconfiguration.density.sample(randomsource);
            int j = dripstoneclusterconfiguration.radius.sample(randomsource);
            int k = dripstoneclusterconfiguration.radius.sample(randomsource);

            for (int l = -j; l <= j; ++l) {
                for (int i1 = -k; i1 <= k; ++i1) {
                    double d0 = this.getChanceOfStalagmiteOrStalactite(j, k, l, i1, dripstoneclusterconfiguration);
                    BlockPos blockpos1 = blockpos.offset(l, 0, i1);

                    this.placeColumn(worldgenlevel, randomsource, blockpos1, l, i1, f, d0, i, f1, dripstoneclusterconfiguration);
                }
            }

            return true;
        }
    }

    private void placeColumn(WorldGenLevel level, RandomSource random, BlockPos pos, int dx, int dz, float chanceOfWater, double chanceOfStalagmiteOrStalactite, int clusterHeight, float density, DripstoneClusterConfiguration config) {
        Optional<Column> optional = Column.scan(level, pos, config.floorToCeilingSearchRange, DripstoneUtils::isEmptyOrWater, DripstoneUtils::isNeitherEmptyNorWater);

        if (!optional.isEmpty()) {
            OptionalInt optionalint = ((Column) optional.get()).getCeiling();
            OptionalInt optionalint1 = ((Column) optional.get()).getFloor();

            if (!optionalint.isEmpty() || !optionalint1.isEmpty()) {
                boolean flag = random.nextFloat() < chanceOfWater;
                Column column;

                if (flag && optionalint1.isPresent() && this.canPlacePool(level, pos.atY(optionalint1.getAsInt()))) {
                    int l = optionalint1.getAsInt();

                    column = ((Column) optional.get()).withFloor(OptionalInt.of(l - 1));
                    level.setBlock(pos.atY(l), Blocks.WATER.defaultBlockState(), 2);
                } else {
                    column = (Column) optional.get();
                }

                OptionalInt optionalint2 = column.getFloor();
                boolean flag1 = random.nextDouble() < chanceOfStalagmiteOrStalactite;
                int i1;

                if (optionalint.isPresent() && flag1 && !this.isLava(level, pos.atY(optionalint.getAsInt()))) {
                    int j1 = config.dripstoneBlockLayerThickness.sample(random);

                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(optionalint.getAsInt()), j1, Direction.UP);
                    int k1;

                    if (optionalint2.isPresent()) {
                        k1 = Math.min(clusterHeight, optionalint.getAsInt() - optionalint2.getAsInt());
                    } else {
                        k1 = clusterHeight;
                    }

                    i1 = this.getDripstoneHeight(random, dx, dz, density, k1, config);
                } else {
                    i1 = 0;
                }

                boolean flag2 = random.nextDouble() < chanceOfStalagmiteOrStalactite;
                int l1;

                if (optionalint2.isPresent() && flag2 && !this.isLava(level, pos.atY(optionalint2.getAsInt()))) {
                    int i2 = config.dripstoneBlockLayerThickness.sample(random);

                    this.replaceBlocksWithDripstoneBlocks(level, pos.atY(optionalint2.getAsInt()), i2, Direction.DOWN);
                    if (optionalint.isPresent()) {
                        l1 = Math.max(0, i1 + Mth.randomBetweenInclusive(random, -config.maxStalagmiteStalactiteHeightDiff, config.maxStalagmiteStalactiteHeightDiff));
                    } else {
                        l1 = this.getDripstoneHeight(random, dx, dz, density, clusterHeight, config);
                    }
                } else {
                    l1 = 0;
                }

                int j2;
                int k2;

                if (optionalint.isPresent() && optionalint2.isPresent() && optionalint.getAsInt() - i1 <= optionalint2.getAsInt() + l1) {
                    int l2 = optionalint2.getAsInt();
                    int i3 = optionalint.getAsInt();
                    int j3 = Math.max(i3 - i1, l2 + 1);
                    int k3 = Math.min(l2 + l1, i3 - 1);
                    int l3 = Mth.randomBetweenInclusive(random, j3, k3 + 1);
                    int i4 = l3 - 1;

                    k2 = i3 - l3;
                    j2 = i4 - l2;
                } else {
                    k2 = i1;
                    j2 = l1;
                }

                boolean flag3 = random.nextBoolean() && k2 > 0 && j2 > 0 && column.getHeight().isPresent() && k2 + j2 == column.getHeight().getAsInt();

                if (optionalint.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(optionalint.getAsInt() - 1), Direction.DOWN, k2, flag3);
                }

                if (optionalint2.isPresent()) {
                    DripstoneUtils.growPointedDripstone(level, pos.atY(optionalint2.getAsInt() + 1), Direction.UP, j2, flag3);
                }

            }
        }
    }

    private boolean isLava(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA);
    }

    private int getDripstoneHeight(RandomSource random, int dx, int dz, float density, int maxHeight, DripstoneClusterConfiguration config) {
        if (random.nextFloat() > density) {
            return 0;
        } else {
            int l = Math.abs(dx) + Math.abs(dz);
            float f1 = (float) Mth.clampedMap((double) l, 0.0D, (double) config.maxDistanceFromCenterAffectingHeightBias, (double) maxHeight / 2.0D, 0.0D);

            return (int) randomBetweenBiased(random, 0.0F, (float) maxHeight, f1, (float) config.heightDeviation);
        }
    }

    private boolean canPlacePool(WorldGenLevel level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        if (!blockstate.is(Blocks.WATER) && !blockstate.is(Blocks.DRIPSTONE_BLOCK) && !blockstate.is(Blocks.POINTED_DRIPSTONE)) {
            if (level.getBlockState(pos.above()).getFluidState().is(FluidTags.WATER)) {
                return false;
            } else {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (!this.canBeAdjacentToWater(level, pos.relative(direction))) {
                        return false;
                    }
                }

                return this.canBeAdjacentToWater(level, pos.below());
            }
        } else {
            return false;
        }
    }

    private boolean canBeAdjacentToWater(LevelAccessor level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        return blockstate.is(BlockTags.BASE_STONE_OVERWORLD) || blockstate.getFluidState().is(FluidTags.WATER);
    }

    private void replaceBlocksWithDripstoneBlocks(WorldGenLevel level, BlockPos firstPos, int maxCount, Direction direction) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = firstPos.mutable();

        for (int j = 0; j < maxCount; ++j) {
            if (!DripstoneUtils.placeDripstoneBlockIfPossible(level, blockpos_mutableblockpos)) {
                return;
            }

            blockpos_mutableblockpos.move(direction);
        }

    }

    private double getChanceOfStalagmiteOrStalactite(int xRadius, int zRadius, int dx, int dz, DripstoneClusterConfiguration config) {
        int i1 = xRadius - Math.abs(dx);
        int j1 = zRadius - Math.abs(dz);
        int k1 = Math.min(i1, j1);

        return (double) Mth.clampedMap((float) k1, 0.0F, (float) config.maxDistanceFromEdgeAffectingChanceOfDripstoneColumn, config.chanceOfDripstoneColumnAtMaxDistanceFromCenter, 1.0F);
    }

    private static float randomBetweenBiased(RandomSource random, float min, float maxExclusive, float mean, float deviation) {
        return ClampedNormalFloat.sample(random, mean, deviation, min, maxExclusive);
    }
}
