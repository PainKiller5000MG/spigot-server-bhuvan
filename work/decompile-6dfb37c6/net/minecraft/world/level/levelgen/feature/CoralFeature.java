package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public abstract class CoralFeature extends Feature<NoneFeatureConfiguration> {

    public CoralFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomsource = context.random();
        WorldGenLevel worldgenlevel = context.level();
        BlockPos blockpos = context.origin();
        Optional<Block> optional = BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.CORAL_BLOCKS, randomsource).map(Holder::value);

        return optional.isEmpty() ? false : this.placeFeature(worldgenlevel, randomsource, blockpos, ((Block) optional.get()).defaultBlockState());
    }

    protected abstract boolean placeFeature(LevelAccessor level, RandomSource random, BlockPos origin, BlockState state);

    protected boolean placeCoralBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate1 = level.getBlockState(pos);

        if ((blockstate1.is(Blocks.WATER) || blockstate1.is(BlockTags.CORALS)) && level.getBlockState(blockpos1).is(Blocks.WATER)) {
            level.setBlock(pos, state, 3);
            if (random.nextFloat() < 0.25F) {
                BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.CORALS, random).map(Holder::value).ifPresent((block) -> {
                    level.setBlock(blockpos1, block.defaultBlockState(), 2);
                });
            } else if (random.nextFloat() < 0.05F) {
                level.setBlock(blockpos1, (BlockState) Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1), 2);
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (random.nextFloat() < 0.2F) {
                    BlockPos blockpos2 = pos.relative(direction);

                    if (level.getBlockState(blockpos2).is(Blocks.WATER)) {
                        BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.WALL_CORALS, random).map(Holder::value).ifPresent((block) -> {
                            BlockState blockstate2 = block.defaultBlockState();

                            if (blockstate2.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                blockstate2 = (BlockState) blockstate2.setValue(BaseCoralWallFanBlock.FACING, direction);
                            }

                            level.setBlock(blockpos2, blockstate2, 2);
                        });
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
