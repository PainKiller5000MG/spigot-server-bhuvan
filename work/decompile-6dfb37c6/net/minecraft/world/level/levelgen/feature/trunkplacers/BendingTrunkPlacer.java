package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class BendingTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<BendingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).and(instance.group(ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_height_for_leaves", 1).forGetter((bendingtrunkplacer) -> {
            return bendingtrunkplacer.minHeightForLeaves;
        }), IntProvider.codec(1, 64).fieldOf("bend_length").forGetter((bendingtrunkplacer) -> {
            return bendingtrunkplacer.bendLength;
        }))).apply(instance, BendingTrunkPlacer::new);
    });
    private final int minHeightForLeaves;
    private final IntProvider bendLength;

    public BendingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int minHeightForLeaves, IntProvider bendLength) {
        super(baseHeight, heightRandA, heightRandB);
        this.minHeightForLeaves = minHeightForLeaves;
        this.bendLength = bendLength;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.BENDING_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int j = treeHeight - 1;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();
        BlockPos blockpos1 = blockpos_mutableblockpos.below();

        setDirtAt(level, trunkSetter, random, blockpos1, config);
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();

        for (int k = 0; k <= j; ++k) {
            if (k + 1 >= j + random.nextInt(2)) {
                blockpos_mutableblockpos.move(direction);
            }

            if (TreeFeature.validTreePos(level, blockpos_mutableblockpos)) {
                this.placeLog(level, trunkSetter, random, blockpos_mutableblockpos, config);
            }

            if (k >= this.minHeightForLeaves) {
                list.add(new FoliagePlacer.FoliageAttachment(blockpos_mutableblockpos.immutable(), 0, false));
            }

            blockpos_mutableblockpos.move(Direction.UP);
        }

        int l = this.bendLength.sample(random);

        for (int i1 = 0; i1 <= l; ++i1) {
            if (TreeFeature.validTreePos(level, blockpos_mutableblockpos)) {
                this.placeLog(level, trunkSetter, random, blockpos_mutableblockpos, config);
            }

            list.add(new FoliagePlacer.FoliageAttachment(blockpos_mutableblockpos.immutable(), 0, false));
            blockpos_mutableblockpos.move(direction);
        }

        return list;
    }
}
