package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FallenTreeConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

public class FallenTreeFeature extends Feature<FallenTreeConfiguration> {

    private static final int STUMP_HEIGHT = 1;
    private static final int STUMP_HEIGHT_PLUS_EMPTY_SPACE = 2;
    private static final int FALLEN_LOG_MAX_FALL_HEIGHT_TO_GROUND = 5;
    private static final int FALLEN_LOG_MAX_GROUND_GAP = 2;
    private static final int FALLEN_LOG_MAX_SPACE_FROM_STUMP = 2;

    public FallenTreeFeature(Codec<FallenTreeConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FallenTreeConfiguration> context) {
        this.placeFallenTree(context.config(), context.origin(), context.level(), context.random());
        return true;
    }

    private void placeFallenTree(FallenTreeConfiguration config, BlockPos origin, WorldGenLevel level, RandomSource random) {
        this.placeStump(config, level, random, origin.mutable());
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = config.logLength.sample(random) - 2;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.relative(direction, 2 + random.nextInt(2)).mutable();

        this.setGroundHeightForFallenLogStartPos(level, blockpos_mutableblockpos);
        if (this.canPlaceEntireFallenLog(level, i, blockpos_mutableblockpos, direction)) {
            this.placeFallenLog(config, level, random, i, blockpos_mutableblockpos, direction);
        }

    }

    private void setGroundHeightForFallenLogStartPos(WorldGenLevel level, BlockPos.MutableBlockPos logStartPos) {
        logStartPos.move(Direction.UP, 1);

        for (int i = 0; i < 6; ++i) {
            if (this.mayPlaceOn(level, logStartPos)) {
                return;
            }

            logStartPos.move(Direction.DOWN);
        }

    }

    private void placeStump(FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos stumpPos) {
        BlockPos blockpos = this.placeLogBlock(config, level, random, stumpPos, Function.identity());

        this.decorateLogs(level, random, Set.of(blockpos), config.stumpDecorators);
    }

    private boolean canPlaceEntireFallenLog(WorldGenLevel level, int logLength, BlockPos.MutableBlockPos logStartPos, Direction direction) {
        int j = 0;

        for (int k = 0; k < logLength; ++k) {
            if (!TreeFeature.validTreePos(level, logStartPos)) {
                return false;
            }

            if (!this.isOverSolidGround(level, logStartPos)) {
                ++j;
                if (j > 2) {
                    return false;
                }
            } else {
                j = 0;
            }

            logStartPos.move(direction);
        }

        logStartPos.move(direction.getOpposite(), logLength);
        return true;
    }

    private void placeFallenLog(FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, int logLength, BlockPos.MutableBlockPos logStartPos, Direction direction) {
        Set<BlockPos> set = new HashSet();

        for (int j = 0; j < logLength; ++j) {
            set.add(this.placeLogBlock(config, level, random, logStartPos, getSidewaysStateModifier(direction)));
            logStartPos.move(direction);
        }

        this.decorateLogs(level, random, set, config.logDecorators);
    }

    private boolean mayPlaceOn(LevelAccessor level, BlockPos blockPos) {
        return TreeFeature.validTreePos(level, blockPos) && this.isOverSolidGround(level, blockPos);
    }

    private boolean isOverSolidGround(LevelAccessor level, BlockPos blockPos) {
        return level.getBlockState(blockPos.below()).isFaceSturdy(level, blockPos, Direction.UP);
    }

    private BlockPos placeLogBlock(FallenTreeConfiguration config, WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos blockPos, Function<BlockState, BlockState> sidewaysStateModifier) {
        level.setBlock(blockPos, (BlockState) sidewaysStateModifier.apply(config.trunkProvider.getState(random, blockPos)), 3);
        this.markAboveForPostProcessing(level, blockPos);
        return blockPos.immutable();
    }

    private void decorateLogs(WorldGenLevel level, RandomSource random, Set<BlockPos> logs, List<TreeDecorator> decorators) {
        if (!decorators.isEmpty()) {
            TreeDecorator.Context treedecorator_context = new TreeDecorator.Context(level, this.getDecorationSetter(level), random, logs, Set.of(), Set.of());

            decorators.forEach((treedecorator) -> {
                treedecorator.place(treedecorator_context);
            });
        }

    }

    private BiConsumer<BlockPos, BlockState> getDecorationSetter(WorldGenLevel level) {
        return (blockpos, blockstate) -> {
            level.setBlock(blockpos, blockstate, 19);
        };
    }

    private static Function<BlockState, BlockState> getSidewaysStateModifier(Direction direction) {
        return (blockstate) -> {
            return (BlockState) blockstate.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
        };
    }
}
