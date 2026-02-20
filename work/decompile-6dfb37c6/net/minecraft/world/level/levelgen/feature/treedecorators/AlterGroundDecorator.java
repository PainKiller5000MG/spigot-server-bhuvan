package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AlterGroundDecorator extends TreeDecorator {

    public static final MapCodec<AlterGroundDecorator> CODEC = BlockStateProvider.CODEC.fieldOf("provider").xmap(AlterGroundDecorator::new, (altergrounddecorator) -> {
        return altergrounddecorator.provider;
    });
    private final BlockStateProvider provider;

    public AlterGroundDecorator(BlockStateProvider provider) {
        this.provider = provider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ALTER_GROUND;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> list = TreeFeature.getLowestTrunkOrRootOfTree(context);

        if (!list.isEmpty()) {
            int i = ((BlockPos) list.get(0)).getY();

            list.stream().filter((blockpos) -> {
                return blockpos.getY() == i;
            }).forEach((blockpos) -> {
                this.placeCircle(context, blockpos.west().north());
                this.placeCircle(context, blockpos.east(2).north());
                this.placeCircle(context, blockpos.west().south(2));
                this.placeCircle(context, blockpos.east(2).south(2));

                for (int j = 0; j < 5; ++j) {
                    int k = context.random().nextInt(64);
                    int l = k % 8;
                    int i1 = k / 8;

                    if (l == 0 || l == 7 || i1 == 0 || i1 == 7) {
                        this.placeCircle(context, blockpos.offset(-3 + l, 0, -3 + i1));
                    }
                }

            });
        }
    }

    private void placeCircle(TreeDecorator.Context context, BlockPos pos) {
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                if (Math.abs(i) != 2 || Math.abs(j) != 2) {
                    this.placeBlockAt(context, pos.offset(i, 0, j));
                }
            }
        }

    }

    private void placeBlockAt(TreeDecorator.Context context, BlockPos pos) {
        for (int i = 2; i >= -3; --i) {
            BlockPos blockpos1 = pos.above(i);

            if (Feature.isGrassOrDirt(context.level(), blockpos1)) {
                context.setBlock(blockpos1, this.provider.getState(context.random(), pos));
                break;
            }

            if (!context.isAir(blockpos1) && i < 0) {
                break;
            }
        }

    }
}
