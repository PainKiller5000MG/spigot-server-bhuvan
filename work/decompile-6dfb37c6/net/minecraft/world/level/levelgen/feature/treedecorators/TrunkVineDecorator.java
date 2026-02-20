package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;

public class TrunkVineDecorator extends TreeDecorator {

    public static final MapCodec<TrunkVineDecorator> CODEC = MapCodec.unit(() -> {
        return TrunkVineDecorator.INSTANCE;
    });
    public static final TrunkVineDecorator INSTANCE = new TrunkVineDecorator();

    public TrunkVineDecorator() {}

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.TRUNK_VINE;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomsource = context.random();

        context.logs().forEach((blockpos) -> {
            if (randomsource.nextInt(3) > 0) {
                BlockPos blockpos1 = blockpos.west();

                if (context.isAir(blockpos1)) {
                    context.placeVine(blockpos1, VineBlock.EAST);
                }
            }

            if (randomsource.nextInt(3) > 0) {
                BlockPos blockpos2 = blockpos.east();

                if (context.isAir(blockpos2)) {
                    context.placeVine(blockpos2, VineBlock.WEST);
                }
            }

            if (randomsource.nextInt(3) > 0) {
                BlockPos blockpos3 = blockpos.north();

                if (context.isAir(blockpos3)) {
                    context.placeVine(blockpos3, VineBlock.SOUTH);
                }
            }

            if (randomsource.nextInt(3) > 0) {
                BlockPos blockpos4 = blockpos.south();

                if (context.isAir(blockpos4)) {
                    context.placeVine(blockpos4, VineBlock.NORTH);
                }
            }

        });
    }
}
