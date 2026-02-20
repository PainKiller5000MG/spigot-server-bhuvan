package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.Fluids;

public abstract class FoliagePlacer {

    public static final Codec<FoliagePlacer> CODEC = BuiltInRegistries.FOLIAGE_PLACER_TYPE.byNameCodec().dispatch(FoliagePlacer::type, FoliagePlacerType::codec);
    protected final IntProvider radius;
    protected final IntProvider offset;

    protected static <P extends FoliagePlacer> Products.P2<RecordCodecBuilder.Mu<P>, IntProvider, IntProvider> foliagePlacerParts(RecordCodecBuilder.Instance<P> instance) {
        return instance.group(IntProvider.codec(0, 16).fieldOf("radius").forGetter((foliageplacer) -> {
            return foliageplacer.radius;
        }), IntProvider.codec(0, 16).fieldOf("offset").forGetter((foliageplacer) -> {
            return foliageplacer.offset;
        }));
    }

    public FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    protected abstract FoliagePlacerType<?> type();

    public void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius) {
        this.createFoliage(level, foliageSetter, random, config, treeHeight, foliageAttachment, foliageHeight, leafRadius, this.offset(random));
    }

    protected abstract void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment foliageAttachment, int foliageHeight, int leafRadius, int offset);

    public abstract int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config);

    public int foliageRadius(RandomSource random, int trunkHeight) {
        return this.radius.sample(random);
    }

    private int offset(RandomSource random) {
        return this.offset.sample(random);
    }

    protected abstract boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk);

    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        int i1;
        int j1;

        if (doubleTrunk) {
            i1 = Math.min(Math.abs(dx), Math.abs(dx - 1));
            j1 = Math.min(Math.abs(dz), Math.abs(dz - 1));
        } else {
            i1 = Math.abs(dx);
            j1 = Math.abs(dz);
        }

        return this.shouldSkipLocation(random, i1, y, j1, currentRadius, doubleTrunk);
    }

    protected void placeLeavesRow(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, BlockPos origin, int currentRadius, int y, boolean doubleTrunk) {
        int k = doubleTrunk ? 1 : 0;
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int l = -currentRadius; l <= currentRadius + k; ++l) {
            for (int i1 = -currentRadius; i1 <= currentRadius + k; ++i1) {
                if (!this.shouldSkipLocationSigned(random, l, y, i1, currentRadius, doubleTrunk)) {
                    blockpos_mutableblockpos.setWithOffset(origin, l, y, i1);
                    tryPlaceLeaf(level, foliageSetter, random, config, blockpos_mutableblockpos);
                }
            }
        }

    }

    protected final void placeLeavesRowWithHangingLeavesBelow(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, BlockPos origin, int currentRadius, int y, boolean doubleTrunk, float hangingLeavesChance, float hangingLeavesExtensionChance) {
        this.placeLeavesRow(level, foliageSetter, random, config, origin, currentRadius, y, doubleTrunk);
        int k = doubleTrunk ? 1 : 0;
        BlockPos blockpos1 = origin.below();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction direction1 = direction.getClockWise();
            int l = direction1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? currentRadius + k : currentRadius;

            blockpos_mutableblockpos.setWithOffset(origin, 0, y - 1, 0).move(direction1, l).move(direction, -currentRadius);
            int i1 = -currentRadius;

            while (i1 < currentRadius + k) {
                boolean flag1 = foliageSetter.isSet(blockpos_mutableblockpos.move(Direction.UP));

                blockpos_mutableblockpos.move(Direction.DOWN);
                if (flag1 && tryPlaceExtension(level, foliageSetter, random, config, hangingLeavesChance, blockpos1, blockpos_mutableblockpos)) {
                    blockpos_mutableblockpos.move(Direction.DOWN);
                    tryPlaceExtension(level, foliageSetter, random, config, hangingLeavesExtensionChance, blockpos1, blockpos_mutableblockpos);
                    blockpos_mutableblockpos.move(Direction.UP);
                }

                ++i1;
                blockpos_mutableblockpos.move(direction);
            }
        }

    }

    private static boolean tryPlaceExtension(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, float chance, BlockPos logPos, BlockPos.MutableBlockPos pos) {
        return pos.distManhattan(logPos) >= 7 ? false : (random.nextFloat() > chance ? false : tryPlaceLeaf(level, foliageSetter, random, config, pos));
    }

    protected static boolean tryPlaceLeaf(LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration config, BlockPos pos) {
        boolean flag = level.isStateAtPosition(pos, (blockstate) -> {
            return (Boolean) blockstate.getValueOrElse(BlockStateProperties.PERSISTENT, false);
        });

        if (!flag && TreeFeature.validTreePos(level, pos)) {
            BlockState blockstate = config.foliageProvider.getState(random, pos);

            if (blockstate.hasProperty(BlockStateProperties.WATERLOGGED)) {
                blockstate = (BlockState) blockstate.setValue(BlockStateProperties.WATERLOGGED, level.isFluidAtPosition(pos, (fluidstate) -> {
                    return fluidstate.isSourceOfType(Fluids.WATER);
                }));
            }

            foliageSetter.set(pos, blockstate);
            return true;
        } else {
            return false;
        }
    }

    public static final class FoliageAttachment {

        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {
            this.pos = pos;
            this.radiusOffset = radiusOffset;
            this.doubleTrunk = doubleTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }

    public interface FoliageSetter {

        void set(BlockPos pos, BlockState state);

        boolean isSet(BlockPos pos);
    }
}
