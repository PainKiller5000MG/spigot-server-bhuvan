package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class DarkOakFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<DarkOakFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).apply(instance, DarkOakFoliagePlacer::new);
    });

    public DarkOakFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.DARK_OAK_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        BlockPos blockpos = foliageAttachment.pos().above(offset);
        boolean flag = foliageAttachment.doubleTrunk();

        if (flag) {
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + 2, -1, flag);
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + 3, 0, flag);
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + 2, 1, flag);
            if (random.nextBoolean()) {
                this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius, 2, flag);
            }
        } else {
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + 2, -1, flag);
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, leafRadius + 1, 0, flag);
        }

    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 4;
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return y != 0 || !doubleTrunk || dx != -currentRadius && dx < currentRadius || dz != -currentRadius && dz < currentRadius ? super.shouldSkipLocationSigned(random, dx, y, dz, currentRadius, doubleTrunk) : true;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return y == -1 && !doubleTrunk ? dx == currentRadius && dz == currentRadius : (y == 1 ? dx + dz > currentRadius * 2 - 2 : false);
    }
}
