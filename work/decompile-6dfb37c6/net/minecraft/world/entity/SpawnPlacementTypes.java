package net.minecraft.world.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import org.jspecify.annotations.Nullable;

public interface SpawnPlacementTypes {

    SpawnPlacementType NO_RESTRICTIONS = (levelreader, blockpos, entitytype) -> {
        return true;
    };
    SpawnPlacementType IN_WATER = (levelreader, blockpos, entitytype) -> {
        if (entitytype != null && levelreader.getWorldBorder().isWithinBounds(blockpos)) {
            BlockPos blockpos1 = blockpos.above();

            return levelreader.getFluidState(blockpos).is(FluidTags.WATER) && !levelreader.getBlockState(blockpos1).isRedstoneConductor(levelreader, blockpos1);
        } else {
            return false;
        }
    };
    SpawnPlacementType IN_LAVA = (levelreader, blockpos, entitytype) -> {
        return entitytype != null && levelreader.getWorldBorder().isWithinBounds(blockpos) ? levelreader.getFluidState(blockpos).is(FluidTags.LAVA) : false;
    };
    SpawnPlacementType ON_GROUND = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader level, BlockPos blockPos, @Nullable EntityType<?> type) {
            if (type != null && level.getWorldBorder().isWithinBounds(blockPos)) {
                BlockPos blockpos1 = blockPos.above();
                BlockPos blockpos2 = blockPos.below();
                BlockState blockstate = level.getBlockState(blockpos2);

                return !blockstate.isValidSpawn(level, blockpos2, type) ? false : this.isValidEmptySpawnBlock(level, blockPos, type) && this.isValidEmptySpawnBlock(level, blockpos1, type);
            } else {
                return false;
            }
        }

        private boolean isValidEmptySpawnBlock(LevelReader level, BlockPos blockPos, EntityType<?> type) {
            BlockState blockstate = level.getBlockState(blockPos);

            return NaturalSpawner.isValidEmptySpawnBlock(level, blockPos, blockstate, blockstate.getFluidState(), type);
        }

        @Override
        public BlockPos adjustSpawnPosition(LevelReader level, BlockPos candidate) {
            BlockPos blockpos1 = candidate.below();

            return level.getBlockState(blockpos1).isPathfindable(PathComputationType.LAND) ? blockpos1 : candidate;
        }
    };
}
