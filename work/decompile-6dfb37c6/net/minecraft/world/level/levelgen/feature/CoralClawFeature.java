package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CoralClawFeature extends CoralFeature {

    public CoralClawFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos origin, BlockState state) {
        if (!this.placeCoralBlock(level, random, origin, state)) {
            return false;
        } else {
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int i = random.nextInt(2) + 2;
            List<Direction> list = Util.toShuffledList(Stream.of(direction, direction.getClockWise(), direction.getCounterClockWise()), random);

            for (Direction direction1 : list.subList(0, i)) {
                BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();
                int j = random.nextInt(2) + 1;

                blockpos_mutableblockpos.move(direction1);
                int k;
                Direction direction2;

                if (direction1 == direction) {
                    direction2 = direction;
                    k = random.nextInt(3) + 2;
                } else {
                    blockpos_mutableblockpos.move(Direction.UP);
                    Direction[] adirection = new Direction[]{direction1, Direction.UP};

                    direction2 = (Direction) Util.getRandom(adirection, random);
                    k = random.nextInt(3) + 3;
                }

                for (int l = 0; l < j && this.placeCoralBlock(level, random, blockpos_mutableblockpos, state); ++l) {
                    blockpos_mutableblockpos.move(direction2);
                }

                blockpos_mutableblockpos.move(direction2.getOpposite());
                blockpos_mutableblockpos.move(Direction.UP);

                for (int i1 = 0; i1 < k; ++i1) {
                    blockpos_mutableblockpos.move(direction);
                    if (!this.placeCoralBlock(level, random, blockpos_mutableblockpos, state)) {
                        break;
                    }

                    if (random.nextFloat() < 0.25F) {
                        blockpos_mutableblockpos.move(Direction.UP);
                    }
                }
            }

            return true;
        }
    }
}
