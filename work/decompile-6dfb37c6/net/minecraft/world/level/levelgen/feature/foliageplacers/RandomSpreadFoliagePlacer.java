package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class RandomSpreadFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<RandomSpreadFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).and(instance.group(IntProvider.codec(1, 512).fieldOf("foliage_height").forGetter((randomspreadfoliageplacer) -> {
            return randomspreadfoliageplacer.foliageHeight;
        }), Codec.intRange(0, 256).fieldOf("leaf_placement_attempts").forGetter((randomspreadfoliageplacer) -> {
            return randomspreadfoliageplacer.leafPlacementAttempts;
        }))).apply(instance, RandomSpreadFoliagePlacer::new);
    });
    private final IntProvider foliageHeight;
    private final int leafPlacementAttempts;

    public RandomSpreadFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider foliageHeight, int leafPlacementAttempts) {
        super(radius, offset);
        this.foliageHeight = foliageHeight;
        this.leafPlacementAttempts = leafPlacementAttempts;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.RANDOM_SPREAD_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        BlockPos blockpos = foliageAttachment.pos();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = blockpos.mutable();

        for (int i1 = 0; i1 < this.leafPlacementAttempts; ++i1) {
            blockpos_mutableblockpos.setWithOffset(blockpos, random.nextInt(leafRadius) - random.nextInt(leafRadius), random.nextInt(foliageHeight) - random.nextInt(foliageHeight), random.nextInt(leafRadius) - random.nextInt(leafRadius));
            tryPlaceLeaf(level, foliageSetter, random, config, blockpos_mutableblockpos);
        }

    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.foliageHeight.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return false;
    }
}
