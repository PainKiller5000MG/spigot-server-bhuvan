package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).apply(instance, FancyTrunkPlacer::new);
    });
    private static final double TRUNK_HEIGHT_SCALE = 0.618D;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382D;
    private static final double BRANCH_SLOPE = 0.381D;
    private static final double BRANCH_LENGTH_MAGIC = 0.328D;

    public FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        int j = 5;
        int k = treeHeight + 2;
        int l = Mth.floor((double) k * 0.618D);

        setDirtAt(level, trunkSetter, random, origin.below(), config);
        double d0 = 1.0D;
        int i1 = Math.min(1, Mth.floor(1.382D + Math.pow(1.0D * (double) k / 13.0D, 2.0D)));
        int j1 = origin.getY() + l;
        int k1 = k - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();

        list.add(new FancyTrunkPlacer.FoliageCoords(origin.above(k1), j1));

        for (; k1 >= 0; --k1) {
            float f = treeShape(k, k1);

            if (f >= 0.0F) {
                for (int l1 = 0; l1 < i1; ++l1) {
                    double d1 = 1.0D;
                    double d2 = 1.0D * (double) f * ((double) random.nextFloat() + 0.328D);
                    double d3 = (double) (random.nextFloat() * 2.0F) * Math.PI;
                    double d4 = d2 * Math.sin(d3) + 0.5D;
                    double d5 = d2 * Math.cos(d3) + 0.5D;
                    BlockPos blockpos1 = origin.offset(Mth.floor(d4), k1 - 1, Mth.floor(d5));
                    BlockPos blockpos2 = blockpos1.above(5);

                    if (this.makeLimb(level, trunkSetter, random, blockpos1, blockpos2, false, config)) {
                        int i2 = origin.getX() - blockpos1.getX();
                        int j2 = origin.getZ() - blockpos1.getZ();
                        double d6 = (double) blockpos1.getY() - Math.sqrt((double) (i2 * i2 + j2 * j2)) * 0.381D;
                        int k2 = d6 > (double) j1 ? j1 : (int) d6;
                        BlockPos blockpos3 = new BlockPos(origin.getX(), k2, origin.getZ());

                        if (this.makeLimb(level, trunkSetter, random, blockpos3, blockpos1, false, config)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockpos1, blockpos3.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(level, trunkSetter, random, origin, origin.above(l), true, config);
        this.makeBranches(level, trunkSetter, random, k, origin, list, config);
        List<FoliagePlacer.FoliageAttachment> list1 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer_foliagecoords : list) {
            if (this.trimBranches(k, fancytrunkplacer_foliagecoords.getBranchBase() - origin.getY())) {
                list1.add(fancytrunkplacer_foliagecoords.attachment);
            }
        }

        return list1;
    }

    private boolean makeLimb(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, BlockPos startPos, BlockPos endPos, boolean doPlace, TreeConfiguration config) {
        if (!doPlace && Objects.equals(startPos, endPos)) {
            return true;
        } else {
            BlockPos blockpos2 = endPos.offset(-startPos.getX(), -startPos.getY(), -startPos.getZ());
            int i = this.getSteps(blockpos2);
            float f = (float) blockpos2.getX() / (float) i;
            float f1 = (float) blockpos2.getY() / (float) i;
            float f2 = (float) blockpos2.getZ() / (float) i;

            for (int j = 0; j <= i; ++j) {
                BlockPos blockpos3 = startPos.offset(Mth.floor(0.5F + (float) j * f), Mth.floor(0.5F + (float) j * f1), Mth.floor(0.5F + (float) j * f2));

                if (doPlace) {
                    this.placeLog(level, trunkSetter, random, blockpos3, config, (blockstate) -> {
                        return (BlockState) blockstate.trySetValue(RotatedPillarBlock.AXIS, this.getLogAxis(startPos, blockpos3));
                    });
                } else if (!this.isFree(level, blockpos3)) {
                    return false;
                }
            }

            return true;
        }
    }

    private int getSteps(BlockPos pos) {
        int i = Mth.abs(pos.getX());
        int j = Mth.abs(pos.getY());
        int k = Mth.abs(pos.getZ());

        return Math.max(i, Math.max(j, k));
    }

    private Direction.Axis getLogAxis(BlockPos startPos, BlockPos blockPos) {
        Direction.Axis direction_axis = Direction.Axis.Y;
        int i = Math.abs(blockPos.getX() - startPos.getX());
        int j = Math.abs(blockPos.getZ() - startPos.getZ());
        int k = Math.max(i, j);

        if (k > 0) {
            if (i == k) {
                direction_axis = Direction.Axis.X;
            } else {
                direction_axis = Direction.Axis.Z;
            }
        }

        return direction_axis;
    }

    private boolean trimBranches(int height, int localY) {
        return (double) localY >= (double) height * 0.2D;
    }

    private void makeBranches(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int height, BlockPos origin, List<FancyTrunkPlacer.FoliageCoords> foliageCoords, TreeConfiguration config) {
        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer_foliagecoords : foliageCoords) {
            int j = fancytrunkplacer_foliagecoords.getBranchBase();
            BlockPos blockpos1 = new BlockPos(origin.getX(), j, origin.getZ());

            if (!blockpos1.equals(fancytrunkplacer_foliagecoords.attachment.pos()) && this.trimBranches(height, j - origin.getY())) {
                this.makeLimb(level, trunkSetter, random, blockpos1, fancytrunkplacer_foliagecoords.attachment.pos(), true, config);
            }
        }

    }

    private static float treeShape(int height, int y) {
        if ((float) y < (float) height * 0.3F) {
            return -1.0F;
        } else {
            float f = (float) height / 2.0F;
            float f1 = f - (float) y;
            float f2 = Mth.sqrt(f * f - f1 * f1);

            if (f1 == 0.0F) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }

            return f2 * 0.5F;
        }
    }

    private static class FoliageCoords {

        private final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos pos, int branchBase) {
            this.attachment = new FoliagePlacer.FoliageAttachment(pos, 0, false);
            this.branchBase = branchBase;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
