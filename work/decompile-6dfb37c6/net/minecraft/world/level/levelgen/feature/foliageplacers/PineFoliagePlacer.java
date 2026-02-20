package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class PineFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<PineFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).and(IntProvider.codec(0, 24).fieldOf("height").forGetter((pinefoliageplacer) -> {
            return pinefoliageplacer.height;
        })).apply(instance, PineFoliagePlacer::new);
    });
    private final IntProvider height;

    public PineFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.PINE_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        int i1 = 0;

        for (int j1 = offset; j1 >= offset - foliageHeight; --j1) {
            this.placeLeavesRow(level, foliageSetter, random, config, foliageAttachment.pos(), i1, j1, foliageAttachment.doubleTrunk());
            if (i1 >= 1 && j1 == offset - foliageHeight + 1) {
                --i1;
            } else if (i1 < leafRadius + foliageAttachment.radiusOffset()) {
                ++i1;
            }
        }

    }

    @Override
    public int foliageRadius(RandomSource random, int trunkHeight) {
        return super.foliageRadius(random, trunkHeight) + random.nextInt(Math.max(trunkHeight + 1, 1));
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx == currentRadius && dz == currentRadius && currentRadius > 0;
    }
}
