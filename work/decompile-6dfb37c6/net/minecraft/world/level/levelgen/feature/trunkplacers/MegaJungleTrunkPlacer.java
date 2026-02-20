package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class MegaJungleTrunkPlacer extends GiantTrunkPlacer {

    public static final MapCodec<MegaJungleTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).apply(instance, MegaJungleTrunkPlacer::new);
    });

    public MegaJungleTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.MEGA_JUNGLE_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();

        list.addAll(super.placeTrunk(level, trunkSetter, random, treeHeight, origin, config));

        for (int j = treeHeight - 2 - random.nextInt(4); j > treeHeight / 2; j -= 2 + random.nextInt(4)) {
            float f = random.nextFloat() * ((float) Math.PI * 2F);
            int k = 0;
            int l = 0;

            for (int i1 = 0; i1 < 5; ++i1) {
                k = (int) (1.5F + Mth.cos((double) f) * (float) i1);
                l = (int) (1.5F + Mth.sin((double) f) * (float) i1);
                BlockPos blockpos1 = origin.offset(k, j - 3 + i1 / 2, l);

                this.placeLog(level, trunkSetter, random, blockpos1, config);
            }

            list.add(new FoliagePlacer.FoliageAttachment(origin.offset(k, j, l), -2, false));
        }

        return list;
    }
}
