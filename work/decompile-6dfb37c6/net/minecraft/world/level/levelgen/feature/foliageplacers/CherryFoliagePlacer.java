package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class CherryFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<CherryFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return foliagePlacerParts(instance).and(instance.group(IntProvider.codec(4, 16).fieldOf("height").forGetter((cherryfoliageplacer) -> {
            return cherryfoliageplacer.height;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("wide_bottom_layer_hole_chance").forGetter((cherryfoliageplacer) -> {
            return cherryfoliageplacer.wideBottomLayerHoleChance;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("corner_hole_chance").forGetter((cherryfoliageplacer) -> {
            return cherryfoliageplacer.wideBottomLayerHoleChance;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_chance").forGetter((cherryfoliageplacer) -> {
            return cherryfoliageplacer.hangingLeavesChance;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_extension_chance").forGetter((cherryfoliageplacer) -> {
            return cherryfoliageplacer.hangingLeavesExtensionChance;
        }))).apply(instance, CherryFoliagePlacer::new);
    });
    private final IntProvider height;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height, float wideBottomLayerHoleChance, float cornerHoleChance, float hangingLeavesChance, float hangingLeavesExtensionChance) {
        super(radius, offset);
        this.height = height;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.CHERRY_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset) {
        boolean flag = foliageAttachment.doubleTrunk();
        BlockPos blockpos = foliageAttachment.pos().above(offset);
        int i1 = leafRadius + foliageAttachment.radiusOffset() - 1;

        this.placeLeavesRow(level, foliageSetter, random, config, blockpos, i1 - 2, foliageHeight - 3, flag);
        this.placeLeavesRow(level, foliageSetter, random, config, blockpos, i1 - 1, foliageHeight - 4, flag);

        for (int j1 = foliageHeight - 5; j1 >= 0; --j1) {
            this.placeLeavesRow(level, foliageSetter, random, config, blockpos, i1, j1, flag);
        }

        this.placeLeavesRowWithHangingLeavesBelow(level, foliageSetter, random, config, blockpos, i1, -1, flag, this.hangingLeavesChance, this.hangingLeavesExtensionChance);
        this.placeLeavesRowWithHangingLeavesBelow(level, foliageSetter, random, config, blockpos, i1 - 1, -2, flag, this.hangingLeavesChance, this.hangingLeavesExtensionChance);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        if (y == -1 && (dx == currentRadius || dz == currentRadius) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        } else {
            boolean flag1 = dx == currentRadius && dz == currentRadius;
            boolean flag2 = currentRadius > 2;

            return flag2 ? flag1 || dx + dz > currentRadius * 2 - 2 && random.nextFloat() < this.cornerHoleChance : flag1 && random.nextFloat() < this.cornerHoleChance;
        }
    }
}
