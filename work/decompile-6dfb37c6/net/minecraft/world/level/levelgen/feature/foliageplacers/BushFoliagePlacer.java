package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class BushFoliagePlacer extends BlobFoliagePlacer {

    public static final MapCodec<BushFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return blobParts(instance).apply(instance, BushFoliagePlacer::new);
    });

    public BushFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset, height);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.BUSH_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        for (int i1 = offset; i1 >= offset - foliageHeight; --i1) {
            int j1 = leafRadius + foliageAttachment.radiusOffset() - 1 - i1;

            this.placeLeavesRow(level, foliageSetter, random, config, foliageAttachment.pos(), j1, i1, foliageAttachment.doubleTrunk());
        }

    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx == currentRadius && dz == currentRadius && random.nextInt(2) == 0;
    }
}
