package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.RootSystemConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RootSystemFeature extends Feature<RootSystemConfiguration> {

    public RootSystemFeature(Codec<RootSystemConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RootSystemConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();

        if (!worldgenlevel.getBlockState(blockpos).isAir()) {
            return false;
        } else {
            RandomSource randomsource = context.random();
            BlockPos blockpos1 = context.origin();
            RootSystemConfiguration rootsystemconfiguration = context.config();
            BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos1.mutable();

            if (placeDirtAndTree(worldgenlevel, context.chunkGenerator(), rootsystemconfiguration, randomsource, blockpos_mutableblockpos, blockpos1)) {
                placeRoots(worldgenlevel, rootsystemconfiguration, randomsource, blockpos1, blockpos_mutableblockpos);
            }

            return true;
        }
    }

    private static boolean spaceForTree(WorldGenLevel level, RootSystemConfiguration config, BlockPos pos) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();

        for (int i = 1; i <= config.requiredVerticalSpaceForTree; ++i) {
            blockpos_mutableblockpos.move(Direction.UP);
            BlockState blockstate = level.getBlockState(blockpos_mutableblockpos);

            if (!isAllowedTreeSpace(blockstate, i, config.allowedVerticalWaterForTree)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAllowedTreeSpace(BlockState state, int blocksAboveOrigin, int allowedVerticalWaterHeight) {
        if (state.isAir()) {
            return true;
        } else {
            int k = blocksAboveOrigin + 1;

            return k <= allowedVerticalWaterHeight && state.getFluidState().is(FluidTags.WATER);
        }
    }

    private static boolean placeDirtAndTree(WorldGenLevel level, ChunkGenerator generator, RootSystemConfiguration config, RandomSource random, BlockPos.MutableBlockPos workingPos, BlockPos pos) {
        for (int i = 0; i < config.rootColumnMaxHeight; ++i) {
            workingPos.move(Direction.UP);
            if (config.allowedTreePosition.test(level, workingPos) && spaceForTree(level, config, workingPos)) {
                BlockPos blockpos1 = workingPos.below();

                if (level.getFluidState(blockpos1).is(FluidTags.LAVA) || !level.getBlockState(blockpos1).isSolid()) {
                    return false;
                }

                if (((PlacedFeature) config.treeFeature.value()).place(level, generator, random, workingPos)) {
                    placeDirt(pos, pos.getY() + i, level, config, random);
                    return true;
                }
            }
        }

        return false;
    }

    private static void placeDirt(BlockPos origin, int targetHeight, WorldGenLevel level, RootSystemConfiguration config, RandomSource random) {
        int j = origin.getX();
        int k = origin.getZ();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();

        for (int l = origin.getY(); l < targetHeight; ++l) {
            placeRootedDirt(level, config, random, j, k, blockpos_mutableblockpos.set(j, l, k));
        }

    }

    private static void placeRootedDirt(WorldGenLevel level, RootSystemConfiguration config, RandomSource random, int originX, int originZ, BlockPos.MutableBlockPos workingPos) {
        int k = config.rootRadius;
        Predicate<BlockState> predicate = (blockstate) -> {
            return blockstate.is(config.rootReplaceable);
        };

        for (int l = 0; l < config.rootPlacementAttempts; ++l) {
            workingPos.setWithOffset(workingPos, random.nextInt(k) - random.nextInt(k), 0, random.nextInt(k) - random.nextInt(k));
            if (predicate.test(level.getBlockState(workingPos))) {
                level.setBlock(workingPos, config.rootStateProvider.getState(random, workingPos), 2);
            }

            workingPos.setX(originX);
            workingPos.setZ(originZ);
        }

    }

    private static void placeRoots(WorldGenLevel level, RootSystemConfiguration config, RandomSource random, BlockPos pos, BlockPos.MutableBlockPos workingPos) {
        int i = config.hangingRootRadius;
        int j = config.hangingRootsVerticalSpan;

        for (int k = 0; k < config.hangingRootPlacementAttempts; ++k) {
            workingPos.setWithOffset(pos, random.nextInt(i) - random.nextInt(i), random.nextInt(j) - random.nextInt(j), random.nextInt(i) - random.nextInt(i));
            if (level.isEmptyBlock(workingPos)) {
                BlockState blockstate = config.hangingRootStateProvider.getState(random, workingPos);

                if (blockstate.canSurvive(level, workingPos) && level.getBlockState(workingPos.above()).isFaceSturdy(level, workingPos, Direction.DOWN)) {
                    level.setBlock(workingPos, blockstate, 2);
                }
            }
        }

    }
}
