package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class SpruceFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<SpruceFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).and(IntProvider.codec(0, 24).fieldOf("trunk_height").forGetter((sprucefoliageplacer) -> {
            return sprucefoliageplacer.trunkHeight;
        })).apply(instance, SpruceFoliagePlacer::new);
    });
    private final IntProvider trunkHeight;

    public SpruceFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider trunkHeight) {
        super(radius, offset);
        this.trunkHeight = trunkHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.SPRUCE_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        BlockPos blockpos = foliageAttachment.pos();
        int i1 = random.nextInt(2);
        int j1 = 1;
        int k1 = 0;

        for (int l1 = offset; l1 >= -foliageHeight; --l1) {
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, i1, l1, foliageAttachment.doubleTrunk());
            if (i1 >= j1) {
                i1 = k1;
                k1 = 1;
                j1 = Math.min(j1 + 1, leafRadius + foliageAttachment.radiusOffset());
            } else {
                ++i1;
            }
        }

    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return Math.max(4, treeHeight - this.trunkHeight.sample(random));
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx == currentRadius && dz == currentRadius && currentRadius > 0;
    }
}
