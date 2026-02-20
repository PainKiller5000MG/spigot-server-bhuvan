package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class BlobFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<BlobFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return blobParts(instance).apply(instance, BlobFoliagePlacer::new);
    });
    protected final int height;

    protected static <P extends BlobFoliagePlacer> Products.P3<RecordCodecBuilder.Mu<P>, IntProvider, IntProvider, Integer> blobParts(RecordCodecBuilder.Instance<P> instance) {
        return foliagePlacerParts(instance).and(Codec.intRange(0, 16).fieldOf("height").forGetter((blobfoliageplacer) -> {
            return blobfoliageplacer.height;
        }));
    }

    public BlobFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.BLOB_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        for (int i1 = offset; i1 >= offset - foliageHeight; --i1) {
            int j1 = Math.max(leafRadius + foliageAttachment.radiusOffset() - 1 - i1 / 2, 0);

            this.placeLeavesRow(level, foliageSetter, random, config, foliageAttachment.pos(), j1, i1, foliageAttachment.doubleTrunk());
        }

    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx == currentRadius && dz == currentRadius && (random.nextInt(2) == 0 || y == 0);
    }
}
