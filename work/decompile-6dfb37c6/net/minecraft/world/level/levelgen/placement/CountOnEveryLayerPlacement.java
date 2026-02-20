package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** @deprecated */
@Deprecated
public class CountOnEveryLayerPlacement extends PlacementModifier {

    public static final MapCodec<CountOnEveryLayerPlacement> CODEC = IntProvider.codec(0, 256).fieldOf("count").xmap(CountOnEveryLayerPlacement::new, (countoneverylayerplacement) -> {
        return countoneverylayerplacement.count;
    });
    private final IntProvider count;

    private CountOnEveryLayerPlacement(IntProvider count) {
        this.count = count;
    }

    public static CountOnEveryLayerPlacement of(IntProvider count) {
        return new CountOnEveryLayerPlacement(count);
    }

    public static CountOnEveryLayerPlacement of(int count) {
        return of(ConstantInt.of(count));
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        Stream.Builder<BlockPos> stream_builder = Stream.builder();
        int i = 0;

        boolean flag;

        do {
            flag = false;

            for (int j = 0; j < this.count.sample(random); ++j) {
                int k = random.nextInt(16) + origin.getX();
                int l = random.nextInt(16) + origin.getZ();
                int i1 = context.getHeight(Heightmap.Types.MOTION_BLOCKING, k, l);
                int j1 = findOnGroundYPosition(context, k, i1, l, i);

                if (j1 != Integer.MAX_VALUE) {
                    stream_builder.add(new BlockPos(k, j1, l));
                    flag = true;
                }
            }

            ++i;
        } while (flag);

        return stream_builder.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.COUNT_ON_EVERY_LAYER;
    }

    private static int findOnGroundYPosition(PlacementContext context, int xStart, int yStart, int zStart, int layerToPlaceOn) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(xStart, yStart, zStart);
        int i1 = 0;
        BlockState blockstate = context.getBlockState(blockpos_mutableblockpos);

        for (int j1 = yStart; j1 >= context.getMinY() + 1; --j1) {
            blockpos_mutableblockpos.setY(j1 - 1);
            BlockState blockstate1 = context.getBlockState(blockpos_mutableblockpos);

            if (!isEmpty(blockstate1) && isEmpty(blockstate) && !blockstate1.is(Blocks.BEDROCK)) {
                if (i1 == layerToPlaceOn) {
                    return blockpos_mutableblockpos.getY() + 1;
                }

                ++i1;
            }

            blockstate = blockstate1;
        }

        return Integer.MAX_VALUE;
    }

    private static boolean isEmpty(BlockState blockState) {
        return blockState.isAir() || blockState.is(Blocks.WATER) || blockState.is(Blocks.LAVA);
    }
}
