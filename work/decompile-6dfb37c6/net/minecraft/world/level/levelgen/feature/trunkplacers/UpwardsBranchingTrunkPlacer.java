package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class UpwardsBranchingTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<UpwardsBranchingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).and(instance.group(IntProvider.POSITIVE_CODEC.fieldOf("extra_branch_steps").forGetter((upwardsbranchingtrunkplacer) -> {
            return upwardsbranchingtrunkplacer.extraBranchSteps;
        }), Codec.floatRange(0.0F, 1.0F).fieldOf("place_branch_per_log_probability").forGetter((upwardsbranchingtrunkplacer) -> {
            return upwardsbranchingtrunkplacer.placeBranchPerLogProbability;
        }), IntProvider.NON_NEGATIVE_CODEC.fieldOf("extra_branch_length").forGetter((upwardsbranchingtrunkplacer) -> {
            return upwardsbranchingtrunkplacer.extraBranchLength;
        }), RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("can_grow_through").forGetter((upwardsbranchingtrunkplacer) -> {
            return upwardsbranchingtrunkplacer.canGrowThrough;
        }))).apply(instance, UpwardsBranchingTrunkPlacer::new);
    });
    private final IntProvider extraBranchSteps;
    private final float placeBranchPerLogProbability;
    private final IntProvider extraBranchLength;
    private final HolderSet<Block> canGrowThrough;

    public UpwardsBranchingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, IntProvider extraBranchSteps, float placeBranchPerLogProbability, IntProvider extraBranchLength, HolderSet<Block> canGrowThrough) {
        super(baseHeight, heightRandA, heightRandB);
        this.extraBranchSteps = extraBranchSteps;
        this.placeBranchPerLogProbability = placeBranchPerLogProbability;
        this.extraBranchLength = extraBranchLength;
        this.canGrowThrough = canGrowThrough;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.UPWARDS_BRANCHING_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < treeHeight; ++j) {
            int k = origin.getY() + j;

            if (this.placeLog(level, trunkSetter, random, blockpos_mutableblockpos.set(origin.getX(), k, origin.getZ()), config) && j < treeHeight - 1 && random.nextFloat() < this.placeBranchPerLogProbability) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int l = this.extraBranchLength.sample(random);
                int i1 = Math.max(0, l - this.extraBranchLength.sample(random) - 1);
                int j1 = this.extraBranchSteps.sample(random);

                this.placeBranch(level, trunkSetter, random, treeHeight, config, list, blockpos_mutableblockpos, k, direction, i1, j1);
            }

            if (j == treeHeight - 1) {
                list.add(new FoliagePlacer.FoliageAttachment(blockpos_mutableblockpos.set(origin.getX(), k + 1, origin.getZ()), 0, false));
            }
        }

        return list;
    }

    private void placeBranch(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, TreeConfiguration config, List<FoliagePlacer.FoliageAttachment> attachments, BlockPos.MutableBlockPos logPos, int currentHeight, Direction branchDir, int branchPos, int branchSteps) {
        int i1 = currentHeight + branchPos;
        int j1 = logPos.getX();
        int k1 = logPos.getZ();

        for (int l1 = branchPos; l1 < treeHeight && branchSteps > 0; --branchSteps) {
            if (l1 >= 1) {
                int i2 = currentHeight + l1;

                j1 += branchDir.getStepX();
                k1 += branchDir.getStepZ();
                i1 = i2;
                if (this.placeLog(level, trunkSetter, random, logPos.set(j1, i2, k1), config)) {
                    i1 = i2 + 1;
                }

                attachments.add(new FoliagePlacer.FoliageAttachment(logPos.immutable(), 0, false));
            }

            ++l1;
        }

        if (i1 - currentHeight > 1) {
            BlockPos blockpos = new BlockPos(j1, i1, k1);

            attachments.add(new FoliagePlacer.FoliageAttachment(blockpos, 0, false));
            attachments.add(new FoliagePlacer.FoliageAttachment(blockpos.below(2), 0, false));
        }

    }

    @Override
    protected boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return super.validTreePos(level, pos) || level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.is(this.canGrowThrough);
        });
    }
}
