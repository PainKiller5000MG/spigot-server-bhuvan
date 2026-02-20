package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import org.jspecify.annotations.Nullable;

public class BasaltColumnsFeature extends Feature<ColumnFeatureConfiguration> {

    private static final ImmutableList<Block> CANNOT_PLACE_ON = ImmutableList.of(Blocks.LAVA, Blocks.BEDROCK, Blocks.MAGMA_BLOCK, Blocks.SOUL_SAND, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_STAIRS, Blocks.NETHER_WART, Blocks.CHEST, Blocks.SPAWNER);
    private static final int CLUSTERED_REACH = 5;
    private static final int CLUSTERED_SIZE = 50;
    private static final int UNCLUSTERED_REACH = 8;
    private static final int UNCLUSTERED_SIZE = 15;

    public BasaltColumnsFeature(Codec<ColumnFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ColumnFeatureConfiguration> context) {
        int i = context.chunkGenerator().getSeaLevel();
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        ColumnFeatureConfiguration columnfeatureconfiguration = context.config();

        if (!canPlaceAt(worldgenlevel, i, blockpos.mutable())) {
            return false;
        } else {
            int j = columnfeatureconfiguration.height().sample(randomsource);
            boolean flag = randomsource.nextFloat() < 0.9F;
            int k = Math.min(j, flag ? 5 : 8);
            int l = flag ? 50 : 15;
            boolean flag1 = false;

            for (BlockPos blockpos1 : BlockPos.randomBetweenClosed(randomsource, l, blockpos.getX() - k, blockpos.getY(), blockpos.getZ() - k, blockpos.getX() + k, blockpos.getY(), blockpos.getZ() + k)) {
                int i1 = j - blockpos1.distManhattan(blockpos);

                if (i1 >= 0) {
                    flag1 |= this.placeColumn(worldgenlevel, i, blockpos1, i1, columnfeatureconfiguration.reach().sample(randomsource));
                }
            }

            return flag1;
        }
    }

    private boolean placeColumn(LevelAccessor level, int lavaSeaLevel, BlockPos origin, int columnHeight, int reach) {
        boolean flag = false;

        for (BlockPos blockpos1 : BlockPos.betweenClosed(origin.getX() - reach, origin.getY(), origin.getZ() - reach, origin.getX() + reach, origin.getY(), origin.getZ() + reach)) {
            int l = blockpos1.distManhattan(origin);
            BlockPos blockpos2 = isAirOrLavaOcean(level, lavaSeaLevel, blockpos1) ? findSurface(level, lavaSeaLevel, blockpos1.mutable(), l) : findAir(level, blockpos1.mutable(), l);

            if (blockpos2 != null) {
                int i1 = columnHeight - l / 2;

                for (BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos2.mutable(); i1 >= 0; --i1) {
                    if (isAirOrLavaOcean(level, lavaSeaLevel, blockpos_mutableblockpos)) {
                        this.setBlock(level, blockpos_mutableblockpos, Blocks.BASALT.defaultBlockState());
                        blockpos_mutableblockpos.move(Direction.UP);
                        flag = true;
                    } else {
                        if (!level.getBlockState(blockpos_mutableblockpos).is(Blocks.BASALT)) {
                            break;
                        }

                        blockpos_mutableblockpos.move(Direction.UP);
                    }
                }
            }
        }

        return flag;
    }

    private static @Nullable BlockPos findSurface(LevelAccessor level, int lavaSeaLevel, BlockPos.MutableBlockPos cursor, int limit) {
        while (cursor.getY() > level.getMinY() + 1 && limit > 0) {
            --limit;
            if (canPlaceAt(level, lavaSeaLevel, cursor)) {
                return cursor;
            }

            cursor.move(Direction.DOWN);
        }

        return null;
    }

    private static boolean canPlaceAt(LevelAccessor level, int lavaSeaLevel, BlockPos.MutableBlockPos cursor) {
        if (!isAirOrLavaOcean(level, lavaSeaLevel, cursor)) {
            return false;
        } else {
            BlockState blockstate = level.getBlockState(cursor.move(Direction.DOWN));

            cursor.move(Direction.UP);
            return !blockstate.isAir() && !BasaltColumnsFeature.CANNOT_PLACE_ON.contains(blockstate.getBlock());
        }
    }

    private static @Nullable BlockPos findAir(LevelAccessor level, BlockPos.MutableBlockPos cursor, int limit) {
        while (cursor.getY() <= level.getMaxY() && limit > 0) {
            --limit;
            BlockState blockstate = level.getBlockState(cursor);

            if (BasaltColumnsFeature.CANNOT_PLACE_ON.contains(blockstate.getBlock())) {
                return null;
            }

            if (blockstate.isAir()) {
                return cursor;
            }

            cursor.move(Direction.UP);
        }

        return null;
    }

    private static boolean isAirOrLavaOcean(LevelAccessor level, int lavaSeaLevel, BlockPos blockPos) {
        BlockState blockstate = level.getBlockState(blockPos);

        return blockstate.isAir() || blockstate.is(Blocks.LAVA) && blockPos.getY() <= lavaSeaLevel;
    }
}
