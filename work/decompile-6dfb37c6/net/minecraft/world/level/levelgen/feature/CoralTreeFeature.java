package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CoralTreeFeature extends CoralFeature {

    public CoralTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    protected boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos origin, BlockState state) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();
        int i = random.nextInt(3) + 1;

        for (int j = 0; j < i; ++j) {
            if (!this.placeCoralBlock(level, random, blockpos_mutableblockpos, state)) {
                return true;
            }

            blockpos_mutableblockpos.move(Direction.UP);
        }

        BlockPos blockpos1 = blockpos_mutableblockpos.immutable();
        int k = random.nextInt(3) + 2;
        List<Direction> list = Direction.Plane.HORIZONTAL.shuffledCopy(random);

        for (Direction direction : list.subList(0, k)) {
            blockpos_mutableblockpos.set(blockpos1);
            blockpos_mutableblockpos.move(direction);
            int l = random.nextInt(5) + 2;
            int i1 = 0;

            for (int j1 = 0; j1 < l && this.placeCoralBlock(level, random, blockpos_mutableblockpos, state); ++j1) {
                ++i1;
                blockpos_mutableblockpos.move(Direction.UP);
                if (j1 == 0 || i1 >= 2 && random.nextFloat() < 0.25F) {
                    blockpos_mutableblockpos.move(direction);
                    i1 = 0;
                }
            }
        }

        return true;
    }
}
