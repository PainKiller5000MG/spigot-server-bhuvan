package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class CherryTrunkPlacer extends TrunkPlacer {

    private static final Codec<UniformInt> BRANCH_START_CODEC = UniformInt.CODEC.codec().validate((uniformint) -> {
        return uniformint.getMaxValue() - uniformint.getMinValue() < 1 ? DataResult.error(() -> {
            return "Need at least 2 blocks variation for the branch starts to fit both branches";
        }) : DataResult.success(uniformint);
    });
    public static final MapCodec<CherryTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return trunkPlacerParts(instance).and(instance.group(IntProvider.codec(1, 3).fieldOf("branch_count").forGetter((cherrytrunkplacer) -> {
            return cherrytrunkplacer.branchCount;
        }), IntProvider.codec(2, 16).fieldOf("branch_horizontal_length").forGetter((cherrytrunkplacer) -> {
            return cherrytrunkplacer.branchHorizontalLength;
        }), IntProvider.validateCodec(-16, 0, CherryTrunkPlacer.BRANCH_START_CODEC).fieldOf("branch_start_offset_from_top").forGetter((cherrytrunkplacer) -> {
            return cherrytrunkplacer.branchStartOffsetFromTop;
        }), IntProvider.codec(-16, 16).fieldOf("branch_end_offset_from_top").forGetter((cherrytrunkplacer) -> {
            return cherrytrunkplacer.branchEndOffsetFromTop;
        }))).apply(instance, CherryTrunkPlacer::new);
    });
    private final IntProvider branchCount;
    private final IntProvider branchHorizontalLength;
    private final UniformInt branchStartOffsetFromTop;
    private final UniformInt secondBranchStartOffsetFromTop;
    private final IntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, IntProvider branchCount, IntProvider branchHorizontalLength, UniformInt branchStartOffsetFromTop, IntProvider branchEndOffsetFromTop) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop = UniformInt.of(branchStartOffsetFromTop.getMinValue(), branchStartOffsetFromTop.getMaxValue() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.CHERRY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        setDirtAt(level, trunkSetter, random, origin.below(), config);
        int j = Math.max(0, treeHeight - 1 + this.branchStartOffsetFromTop.sample(random));
        int k = Math.max(0, treeHeight - 1 + this.secondBranchStartOffsetFromTop.sample(random));

        if (k >= j) {
            ++k;
        }

        int l = this.branchCount.sample(random);
        boolean flag = l == 3;
        boolean flag1 = l >= 2;
        int i1;

        if (flag) {
            i1 = treeHeight;
        } else if (flag1) {
            i1 = Math.max(j, k) + 1;
        } else {
            i1 = j + 1;
        }

        for (int j1 = 0; j1 < i1; ++j1) {
            this.placeLog(level, trunkSetter, random, origin.above(j1), config);
        }

        List<FoliagePlacer.FoliageAttachment> list = new ArrayList();

        if (flag) {
            list.add(new FoliagePlacer.FoliageAttachment(origin.above(i1), 0, false));
        }

        BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Function<BlockState, BlockState> function = (blockstate) -> {
            return (BlockState) blockstate.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
        };

        list.add(this.generateBranch(level, trunkSetter, random, treeHeight, origin, config, function, direction, j, j < i1 - 1, blockpos_mutableblockpos));
        if (flag1) {
            list.add(this.generateBranch(level, trunkSetter, random, treeHeight, origin, config, function, direction.getOpposite(), k, k < i1 - 1, blockpos_mutableblockpos));
        }

        return list;
    }

    private FoliagePlacer.FoliageAttachment generateBranch(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config, Function<BlockState, BlockState> sidewaysStateModifier, Direction branchDirection, int offsetFromOrigin, boolean middleContinuesUpwards, BlockPos.MutableBlockPos logPos) {
        logPos.set(origin).move(Direction.UP, offsetFromOrigin);
        int k = treeHeight - 1 + this.branchEndOffsetFromTop.sample(random);
        boolean flag1 = middleContinuesUpwards || k < offsetFromOrigin;
        int l = this.branchHorizontalLength.sample(random) + (flag1 ? 1 : 0);
        BlockPos blockpos1 = origin.relative(branchDirection, l).above(k);
        int i1 = flag1 ? 2 : 1;

        for (int j1 = 0; j1 < i1; ++j1) {
            this.placeLog(level, trunkSetter, random, logPos.move(branchDirection), config, sidewaysStateModifier);
        }

        Direction direction1 = blockpos1.getY() > logPos.getY() ? Direction.UP : Direction.DOWN;

        while (true) {
            int k1 = logPos.distManhattan(blockpos1);

            if (k1 == 0) {
                return new FoliagePlacer.FoliageAttachment(blockpos1.above(), 0, false);
            }

            float f = (float) Math.abs(blockpos1.getY() - logPos.getY()) / (float) k1;
            boolean flag2 = random.nextFloat() < f;

            logPos.move(flag2 ? direction1 : branchDirection);
            this.placeLog(level, trunkSetter, random, logPos, config, flag2 ? Function.identity() : sidewaysStateModifier);
        }
    }
}
