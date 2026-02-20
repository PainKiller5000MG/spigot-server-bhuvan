package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class ForkingTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<ForkingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).apply(instance, ForkingTrunkPlacer::new);
    });

    public ForkingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FORKING_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        setDirtAt(level, trunkSetter, random, origin.below(), config);
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int j = treeHeight - random.nextInt(4) - 1;
        int k = 3 - random.nextInt(3);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        int l = origin.getX();
        int i1 = origin.getZ();
        OptionalInt optionalint = OptionalInt.empty();

        for (int j1 = 0; j1 < treeHeight; ++j1) {
            int k1 = origin.getY() + j1;

            if (j1 >= j && k > 0) {
                l += direction.getStepX();
                i1 += direction.getStepZ();
                --k;
            }

            if (this.placeLog(level, trunkSetter, random, blockpos_mutableblockpos.set(l, k1, i1), config)) {
                optionalint = OptionalInt.of(k1 + 1);
            }
        }

        if (optionalint.isPresent()) {
            list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(l, optionalint.getAsInt(), i1), 1, false));
        }

        l = origin.getX();
        i1 = origin.getZ();
        Direction direction1 = Direction.Plane.HORIZONTAL.getRandomDirection(random);

        if (direction1 != direction) {
            int l1 = j - random.nextInt(2) - 1;
            int i2 = 1 + random.nextInt(3);

            optionalint = OptionalInt.empty();

            for (int j2 = l1; j2 < treeHeight && i2 > 0; --i2) {
                if (j2 >= 1) {
                    int k2 = origin.getY() + j2;

                    l += direction1.getStepX();
                    i1 += direction1.getStepZ();
                    if (this.placeLog(level, trunkSetter, random, blockpos_mutableblockpos.set(l, k2, i1), config)) {
                        optionalint = OptionalInt.of(k2 + 1);
                    }
                }

                ++j2;
            }

            if (optionalint.isPresent()) {
                list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(l, optionalint.getAsInt(), i1), 0, false));
            }
        }

        return list;
    }
}
