package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;

public class WaterloggedVegetationPatchFeature extends VegetationPatchFeature {

    public WaterloggedVegetationPatchFeature(Codec<VegetationPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    protected Set<BlockPos> placeGroundPatch(WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, BlockPos origin, Predicate<BlockState> replaceable, int xRadius, int zRadius) {
        Set<BlockPos> set = super.placeGroundPatch(level, config, random, origin, replaceable, xRadius, zRadius);
        Set<BlockPos> set1 = new HashSet();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (BlockPos blockpos1 : set) {
            if (!isExposed(level, set, blockpos1, blockpos_mutableblockpos)) {
                set1.add(blockpos1);
            }
        }

        for (BlockPos blockpos2 : set1) {
            level.setBlock(blockpos2, Blocks.WATER.defaultBlockState(), 2);
        }

        return set1;
    }

    private static boolean isExposed(WorldGenLevel level, Set<BlockPos> surface, BlockPos pos, BlockPos.MutableBlockPos testPos) {
        return isExposedDirection(level, pos, testPos, Direction.NORTH) || isExposedDirection(level, pos, testPos, Direction.EAST) || isExposedDirection(level, pos, testPos, Direction.SOUTH) || isExposedDirection(level, pos, testPos, Direction.WEST) || isExposedDirection(level, pos, testPos, Direction.DOWN);
    }

    private static boolean isExposedDirection(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos testPos, Direction direction) {
        testPos.setWithOffset(pos, direction);
        return !level.getBlockState(testPos).isFaceSturdy(level, testPos, direction.getOpposite());
    }

    @Override
    protected boolean placeVegetation(WorldGenLevel level, VegetationPatchConfiguration config, ChunkGenerator generator, RandomSource random, BlockPos placementPos) {
        if (super.placeVegetation(level, config, generator, random, placementPos.below())) {
            BlockState blockstate = level.getBlockState(placementPos);

            if (blockstate.hasProperty(BlockStateProperties.WATERLOGGED) && !(Boolean) blockstate.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(placementPos, (BlockState) blockstate.setValue(BlockStateProperties.WATERLOGGED, true), 2);
            }

            return true;
        } else {
            return false;
        }
    }
}
