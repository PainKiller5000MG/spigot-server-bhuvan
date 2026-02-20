package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class MangroveRootPlacer extends RootPlacer {

    public static final int ROOT_WIDTH_LIMIT = 8;
    public static final int ROOT_LENGTH_LIMIT = 15;
    public static final MapCodec<MangroveRootPlacer> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return rootPlacerParts(instance).and(MangroveRootPlacement.CODEC.fieldOf("mangrove_root_placement").forGetter((mangroverootplacer) -> {
            return mangroverootplacer.mangroveRootPlacement;
        })).apply(instance, MangroveRootPlacer::new);
    });
    private final MangroveRootPlacement mangroveRootPlacement;

    public MangroveRootPlacer(IntProvider trunkOffsetY, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement, MangroveRootPlacement mangroveRootPlacement) {
        super(trunkOffsetY, rootProvider, aboveRootPlacement);
        this.mangroveRootPlacement = mangroveRootPlacement;
    }

    @Override
    public boolean placeRoots(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> rootSetter, RandomSource random, BlockPos origin, BlockPos trunkOrigin, TreeConfiguration config) {
        List<BlockPos> list = Lists.newArrayList();
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();

        while (blockpos_mutableblockpos.getY() < trunkOrigin.getY()) {
            if (!this.canPlaceRoot(level, blockpos_mutableblockpos)) {
                return false;
            }

            blockpos_mutableblockpos.move(Direction.UP);
        }

        list.add(trunkOrigin.below());

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos2 = trunkOrigin.relative(direction);
            List<BlockPos> list1 = Lists.newArrayList();

            if (!this.simulateRoots(level, random, blockpos2, direction, trunkOrigin, list1, 0)) {
                return false;
            }

            list.addAll(list1);
            list.add(trunkOrigin.relative(direction));
        }

        for (BlockPos blockpos3 : list) {
            this.placeRoot(level, rootSetter, random, blockpos3, config);
        }

        return true;
    }

    private boolean simulateRoots(LevelSimulatedReader level, RandomSource random, BlockPos rootPos, Direction dir, BlockPos rootOrigin, List<BlockPos> rootPositions, int layer) {
        int j = this.mangroveRootPlacement.maxRootLength();

        if (layer != j && rootPositions.size() <= j) {
            for (BlockPos blockpos2 : this.potentialRootPositions(rootPos, dir, random, rootOrigin)) {
                if (this.canPlaceRoot(level, blockpos2)) {
                    rootPositions.add(blockpos2);
                    if (!this.simulateRoots(level, random, blockpos2, dir, rootOrigin, rootPositions, layer + 1)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    protected List<BlockPos> potentialRootPositions(BlockPos pos, Direction prevDir, RandomSource random, BlockPos rootOrigin) {
        BlockPos blockpos2 = pos.below();
        BlockPos blockpos3 = pos.relative(prevDir);
        int i = pos.distManhattan(rootOrigin);
        int j = this.mangroveRootPlacement.maxRootWidth();
        float f = this.mangroveRootPlacement.randomSkewChance();

        return i > j - 3 && i <= j ? (random.nextFloat() < f ? List.of(blockpos2, blockpos3.below()) : List.of(blockpos2)) : (i > j ? List.of(blockpos2) : (random.nextFloat() < f ? List.of(blockpos2) : (random.nextBoolean() ? List.of(blockpos3) : List.of(blockpos2))));
    }

    @Override
    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return super.canPlaceRoot(level, pos) || level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.is(this.mangroveRootPlacement.canGrowThrough());
        });
    }

    @Override
    protected void placeRoot(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> rootSetter, RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (level.isStateAtPosition(pos, (blockstate) -> {
            return blockstate.is(this.mangroveRootPlacement.muddyRootsIn());
        })) {
            BlockState blockstate = this.mangroveRootPlacement.muddyRootsProvider().getState(random, pos);

            rootSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, blockstate));
        } else {
            super.placeRoot(level, rootSetter, random, pos, config);
        }

    }

    @Override
    protected RootPlacerType<?> type() {
        return RootPlacerType.MANGROVE_ROOT_PLACER;
    }
}
