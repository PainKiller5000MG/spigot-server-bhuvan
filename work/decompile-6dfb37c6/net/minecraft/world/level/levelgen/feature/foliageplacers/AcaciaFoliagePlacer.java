package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class AcaciaFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<AcaciaFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).apply(instance, AcaciaFoliagePlacer::new);
    });

    public AcaciaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.ACACIA_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        boolean flag = foliageAttachment.doubleTrunk();
        BlockPos blockpos = foliageAttachment.pos().above(offset);

        this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + foliageAttachment.radiusOffset(), -1 - foliageHeight, flag);
        this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius - 1, -foliageHeight, flag);
        this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + foliageAttachment.radiusOffset() - 1, 0, flag);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return y == 0 ? (dx > 1 || dz > 1) && dx != 0 && dz != 0 : dx == currentRadius && dz == currentRadius && currentRadius > 0;
    }
}
