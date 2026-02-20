package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.configurations.PointedDripstoneConfiguration;

public class PointedDripstoneFeature extends Feature<PointedDripstoneConfiguration> {

    public PointedDripstoneFeature(Codec<PointedDripstoneConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PointedDripstoneConfiguration> context) {
        LevelAccessor levelaccessor = context.level();
        BlockPos blockpos = context.origin();
        RandomSource randomsource = context.random();
        PointedDripstoneConfiguration pointeddripstoneconfiguration = context.config();
        Optional<Direction> optional = getTipDirection(levelaccessor, blockpos, randomsource);

        if (optional.isEmpty()) {
            return false;
        } else {
            BlockPos blockpos1 = blockpos.relative(((Direction) optional.get()).getOpposite());

            createPatchOfDripstoneBlocks(levelaccessor, randomsource, blockpos1, pointeddripstoneconfiguration);
            int i = randomsource.nextFloat() < pointeddripstoneconfiguration.chanceOfTallerDripstone && DripstoneUtils.isEmptyOrWater(levelaccessor.getBlockState(blockpos.relative((Direction) optional.get()))) ? 2 : 1;

            DripstoneUtils.growPointedDripstone(levelaccessor, blockpos, (Direction) optional.get(), i, false);
            return true;
        }
    }

    private static Optional<Direction> getTipDirection(LevelAccessor level, BlockPos pos, RandomSource random) {
        boolean flag = DripstoneUtils.isDripstoneBase(level.getBlockState(pos.above()));
        boolean flag1 = DripstoneUtils.isDripstoneBase(level.getBlockState(pos.below()));

        return flag && flag1 ? Optional.of(random.nextBoolean() ? Direction.DOWN : Direction.UP) : (flag ? Optional.of(Direction.DOWN) : (flag1 ? Optional.of(Direction.UP) : Optional.empty()));
    }

    private static void createPatchOfDripstoneBlocks(LevelAccessor level, RandomSource random, BlockPos pos, PointedDripstoneConfiguration config) {
        DripstoneUtils.placeDripstoneBlockIfPossible(level, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextFloat() <= config.chanceOfDirectionalSpread) {
                BlockPos blockpos1 = pos.relative(direction);

                DripstoneUtils.placeDripstoneBlockIfPossible(level, blockpos1);
                if (random.nextFloat() <= config.chanceOfSpreadRadius2) {
                    BlockPos blockpos2 = blockpos1.relative(Direction.getRandom(random));

                    DripstoneUtils.placeDripstoneBlockIfPossible(level, blockpos2);
                    if (random.nextFloat() <= config.chanceOfSpreadRadius3) {
                        BlockPos blockpos3 = blockpos2.relative(Direction.getRandom(random));

                        DripstoneUtils.placeDripstoneBlockIfPossible(level, blockpos3);
                    }
                }
            }
        }

    }
}
