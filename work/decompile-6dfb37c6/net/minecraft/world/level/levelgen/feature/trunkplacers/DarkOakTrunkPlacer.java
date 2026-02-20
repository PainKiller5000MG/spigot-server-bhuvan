package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class DarkOakTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<DarkOakTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).apply(instance, DarkOakTrunkPlacer::new);
    });

    public DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.DARK_OAK_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        BlockPos blockpos1 = origin.below();

        setDirtAt(level, trunkSetter, random, blockpos1, config);
        setDirtAt(level, trunkSetter, random, blockpos1.east(), config);
        setDirtAt(level, trunkSetter, random, blockpos1.south(), config);
        setDirtAt(level, trunkSetter, random, blockpos1.south().east(), config);
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int j = treeHeight - random.nextInt(4);
        int k = 2 - random.nextInt(3);
        int l = origin.getX();
        int i1 = origin.getY();
        int j1 = origin.getZ();
        int k1 = l;
        int l1 = j1;
        int i2 = i1 + treeHeight - 1;

        for (int j2 = 0; j2 < treeHeight; ++j2) {
            if (j2 >= j && k > 0) {
                k1 += direction.getStepX();
                l1 += direction.getStepZ();
                --k;
            }

            int k2 = i1 + j2;
            BlockPos blockpos2 = new BlockPos(k1, k2, l1);

            if (TreeFeature.isAirOrLeaves(level, blockpos2)) {
                this.placeLog(level, trunkSetter, random, blockpos2, config);
                this.placeLog(level, trunkSetter, random, blockpos2.east(), config);
                this.placeLog(level, trunkSetter, random, blockpos2.south(), config);
                this.placeLog(level, trunkSetter, random, blockpos2.east().south(), config);
            }
        }

        list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(k1, i2, l1), 0, true));

        for (int l2 = -1; l2 <= 2; ++l2) {
            for (int i3 = -1; i3 <= 2; ++i3) {
                if ((l2 < 0 || l2 > 1 || i3 < 0 || i3 > 1) && random.nextInt(3) <= 0) {
                    int j3 = random.nextInt(3) + 2;

                    for (int k3 = 0; k3 < j3; ++k3) {
                        this.placeLog(level, trunkSetter, random, new BlockPos(l + l2, i2 - k3 - 1, j1 + i3), config);
                    }

                    list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(l + l2, i2, j1 + i3), 0, false));
                }
            }
        }

        return list;
    }
}
