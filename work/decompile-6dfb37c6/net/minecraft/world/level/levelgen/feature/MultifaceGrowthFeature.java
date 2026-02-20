package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration;

public class MultifaceGrowthFeature extends Feature<MultifaceGrowthConfiguration> {

    public MultifaceGrowthFeature(Codec<MultifaceGrowthConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<MultifaceGrowthConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        MultifaceGrowthConfiguration multifacegrowthconfiguration = context.config();

        if (!isAirOrWater(worldgenlevel.getBlockState(blockpos))) {
            return false;
        } else {
            List<Direction> list = multifacegrowthconfiguration.getShuffledDirections(randomsource);

            if (placeGrowthIfPossible(worldgenlevel, blockpos, worldgenlevel.getBlockState(blockpos), multifacegrowthconfiguration, randomsource, list)) {
                return true;
            } else {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos.mutable();

                for (Direction direction : list) {
                    blockpos_mutableblockpos.set(blockpos);
                    List<Direction> list1 = multifacegrowthconfiguration.getShuffledDirectionsExcept(randomsource, direction.getOpposite());

                    for (int i = 0; i < multifacegrowthconfiguration.searchRange; ++i) {
                        blockpos_mutableblockpos.setWithOffset(blockpos, direction);
                        BlockState blockstate = worldgenlevel.getBlockState(blockpos_mutableblockpos);

                        if (!isAirOrWater(blockstate) && !blockstate.is(multifacegrowthconfiguration.placeBlock)) {
                            break;
                        }

                        if (placeGrowthIfPossible(worldgenlevel, blockpos_mutableblockpos, blockstate, multifacegrowthconfiguration, randomsource, list1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }
    }

    public static boolean placeGrowthIfPossible(WorldGenLevel level, BlockPos pos, BlockState oldState, MultifaceGrowthConfiguration config, RandomSource random, List<Direction> placementDirections) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        for (Direction direction : placementDirections) {
            BlockState blockstate1 = level.getBlockState(blockpos_mutableblockpos.setWithOffset(pos, direction));

            if (blockstate1.is(config.canBePlacedOn)) {
                BlockState blockstate2 = config.placeBlock.getStateForPlacement(oldState, level, pos, direction);

                if (blockstate2 == null) {
                    return false;
                }

                level.setBlock(pos, blockstate2, 3);
                level.getChunk(pos).markPosForPostprocessing(pos);
                if (random.nextFloat() < config.chanceOfSpreading) {
                    config.placeBlock.getSpreader().spreadFromFaceTowardRandomDirection(blockstate2, level, pos, direction, random, true);
                }

                return true;
            }
        }

        return false;
    }

    private static boolean isAirOrWater(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }
}
