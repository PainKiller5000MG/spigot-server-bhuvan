package net.minecraft.world.level.levelgen.feature;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

public class DripstoneUtils {

    public DripstoneUtils() {}

    protected static double getDripstoneHeight(double xzDistanceFromCenter, double dripstoneRadius, double scale, double bluntness) {
        if (xzDistanceFromCenter < bluntness) {
            xzDistanceFromCenter = bluntness;
        }

        double d4 = 0.384D;
        double d5 = xzDistanceFromCenter / dripstoneRadius * 0.384D;
        double d6 = 0.75D * Math.pow(d5, 1.3333333333333333D);
        double d7 = Math.pow(d5, 0.6666666666666666D);
        double d8 = 0.3333333333333333D * Math.log(d5);
        double d9 = scale * (d6 - d7 - d8);

        d9 = Math.max(d9, 0.0D);
        return d9 / 0.384D * dripstoneRadius;
    }

    protected static boolean isCircleMostlyEmbeddedInStone(WorldGenLevel level, BlockPos center, int xzRadius) {
        if (isEmptyOrWaterOrLava(level, center)) {
            return false;
        } else {
            float f = 6.0F;
            float f1 = 6.0F / (float) xzRadius;

            for (float f2 = 0.0F; f2 < ((float) Math.PI * 2F); f2 += f1) {
                int j = (int) (Mth.cos((double) f2) * (float) xzRadius);
                int k = (int) (Mth.sin((double) f2) * (float) xzRadius);

                if (isEmptyOrWaterOrLava(level, center.offset(j, 0, k))) {
                    return false;
                }
            }

            return true;
        }
    }

    protected static boolean isEmptyOrWater(LevelAccessor level, BlockPos pos) {
        return level.isStateAtPosition(pos, DripstoneUtils::isEmptyOrWater);
    }

    protected static boolean isEmptyOrWaterOrLava(LevelAccessor level, BlockPos pos) {
        return level.isStateAtPosition(pos, DripstoneUtils::isEmptyOrWaterOrLava);
    }

    protected static void buildBaseToTipColumn(Direction direction, int totalLength, boolean mergedTip, Consumer<BlockState> consumer) {
        if (totalLength >= 3) {
            consumer.accept(createPointedDripstone(direction, DripstoneThickness.BASE));

            for (int j = 0; j < totalLength - 3; ++j) {
                consumer.accept(createPointedDripstone(direction, DripstoneThickness.MIDDLE));
            }
        }

        if (totalLength >= 2) {
            consumer.accept(createPointedDripstone(direction, DripstoneThickness.FRUSTUM));
        }

        if (totalLength >= 1) {
            consumer.accept(createPointedDripstone(direction, mergedTip ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP));
        }

    }

    protected static void growPointedDripstone(LevelAccessor level, BlockPos startPos, Direction tipDirection, int height, boolean mergedTip) {
        if (isDripstoneBase(level.getBlockState(startPos.relative(tipDirection.getOpposite())))) {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = startPos.mutable();

            buildBaseToTipColumn(tipDirection, height, mergedTip, (blockstate) -> {
                if (blockstate.is(Blocks.POINTED_DRIPSTONE)) {
                    blockstate = (BlockState) blockstate.setValue(PointedDripstoneBlock.WATERLOGGED, level.isWaterAt(blockpos_mutableblockpos));
                }

                level.setBlock(blockpos_mutableblockpos, blockstate, 2);
                blockpos_mutableblockpos.move(tipDirection);
            });
        }
    }

    protected static boolean placeDripstoneBlockIfPossible(LevelAccessor level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        if (blockstate.is(BlockTags.DRIPSTONE_REPLACEABLE)) {
            level.setBlock(pos, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 2);
            return true;
        } else {
            return false;
        }
    }

    private static BlockState createPointedDripstone(Direction direction, DripstoneThickness thickness) {
        return (BlockState) ((BlockState) Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, direction)).setValue(PointedDripstoneBlock.THICKNESS, thickness);
    }

    public static boolean isDripstoneBaseOrLava(BlockState state) {
        return isDripstoneBase(state) || state.is(Blocks.LAVA);
    }

    public static boolean isDripstoneBase(BlockState state) {
        return state.is(Blocks.DRIPSTONE_BLOCK) || state.is(BlockTags.DRIPSTONE_REPLACEABLE);
    }

    public static boolean isEmptyOrWater(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER);
    }

    public static boolean isNeitherEmptyNorWater(BlockState state) {
        return !state.isAir() && !state.is(Blocks.WATER);
    }

    public static boolean isEmptyOrWaterOrLava(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA);
    }
}
