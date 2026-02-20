package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LeaveVineDecorator extends TreeDecorator {

    public static final MapCodec<LeaveVineDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability").xmap(LeaveVineDecorator::new, (leavevinedecorator) -> {
        return leavevinedecorator.probability;
    });
    private final float probability;

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.LEAVE_VINE;
    }

    public LeaveVineDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource randomsource = context.random();

        context.leaves().forEach((blockpos) -> {
            if (randomsource.nextFloat() < this.probability) {
                BlockPos blockpos1 = blockpos.west();

                if (context.isAir(blockpos1)) {
                    addHangingVine(blockpos1, VineBlock.EAST, context);
                }
            }

            if (randomsource.nextFloat() < this.probability) {
                BlockPos blockpos2 = blockpos.east();

                if (context.isAir(blockpos2)) {
                    addHangingVine(blockpos2, VineBlock.WEST, context);
                }
            }

            if (randomsource.nextFloat() < this.probability) {
                BlockPos blockpos3 = blockpos.north();

                if (context.isAir(blockpos3)) {
                    addHangingVine(blockpos3, VineBlock.SOUTH, context);
                }
            }

            if (randomsource.nextFloat() < this.probability) {
                BlockPos blockpos4 = blockpos.south();

                if (context.isAir(blockpos4)) {
                    addHangingVine(blockpos4, VineBlock.NORTH, context);
                }
            }

        });
    }

    private static void addHangingVine(BlockPos pos, BooleanProperty direction, TreeDecorator.Context context) {
        context.placeVine(pos, direction);
        int i = 4;

        for (BlockPos blockpos1 = pos.below(); context.isAir(blockpos1) && i > 0; --i) {
            context.placeVine(blockpos1, direction);
            blockpos1 = blockpos1.below();
        }

    }
}
