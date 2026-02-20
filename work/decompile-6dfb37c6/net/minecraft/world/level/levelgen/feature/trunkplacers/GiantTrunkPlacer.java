package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class GiantTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<GiantTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).apply(instance, GiantTrunkPlacer::new);
    });

    public GiantTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.GIANT_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        BlockPos blockpos1 = origin.below();

        setDirtAt(level, trunkSetter, random, blockpos1, config);
        setDirtAt(level, trunkSetter, random, blockpos1.east(), config);
        setDirtAt(level, trunkSetter, random, blockpos1.south(), config);
        setDirtAt(level, trunkSetter, random, blockpos1.south().east(), config);
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < treeHeight; ++j) {
            this.placeLogIfFreeWithOffset(level, trunkSetter, random, blockpos_mutableblockpos, config, origin, 0, j, 0);
            if (j < treeHeight - 1) {
                this.placeLogIfFreeWithOffset(level, trunkSetter, random, blockpos_mutableblockpos, config, origin, 1, j, 0);
                this.placeLogIfFreeWithOffset(level, trunkSetter, random, blockpos_mutableblockpos, config, origin, 1, j, 1);
                this.placeLogIfFreeWithOffset(level, trunkSetter, random, blockpos_mutableblockpos, config, origin, 0, j, 1);
            }
        }

        return ImmutableList.of(new FoliagePlacer.FoliageAttachment(origin.above(treeHeight), 0, true));
    }

    private void placeLogIfFreeWithOffset(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, BlockPos.MutableBlockPos trunkPos, TreeConfiguration config, BlockPos treePos, int x, int y, int z) {
        trunkPos.setWithOffset(treePos, x, y, z);
        this.placeLogIfFree(level, trunkSetter, random, trunkPos, config);
    }
}
