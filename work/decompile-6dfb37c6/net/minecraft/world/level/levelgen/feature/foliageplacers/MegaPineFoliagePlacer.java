package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaPineFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<MegaPineFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).and(IntProvider.codec(0, 24).fieldOf("crown_height").forGetter((megapinefoliageplacer) -> {
            return megapinefoliageplacer.crownHeight;
        })).apply(instance, MegaPineFoliagePlacer::new);
    });
    private final IntProvider crownHeight;

    public MegaPineFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider crownHeight) {
        super(radius, offset);
        this.crownHeight = crownHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_PINE_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        BlockPos blockpos = foliageAttachment.pos();
        int i1 = 0;

        for (int j1 = blockpos.getY() - foliageHeight + offset; j1 <= blockpos.getY() + offset; ++j1) {
            int k1 = blockpos.getY() - j1;
            int l1 = leafRadius + foliageAttachment.radiusOffset() + Mth.floor((float) k1 / (float) foliageHeight * 3.5F);
            int i2;

            if (k1 > 0 && l1 == i1 && (j1 & 1) == 0) {
                i2 = l1 + 1;
            } else {
                i2 = l1;
            }

            this.placeLeavesRow(level, foliageSetter, random, config, new BlockPos(blockpos.getX(), j1, blockpos.getZ()), i2, 0, foliageAttachment.doubleTrunk());
            i1 = l1;
        }

    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.crownHeight.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return dx + dz >= 7 ? true : dx * dx + dz * dz > currentRadius * currentRadius;
    }
}
