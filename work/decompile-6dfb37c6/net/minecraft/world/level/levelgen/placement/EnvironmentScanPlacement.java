package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public class EnvironmentScanPlacement extends PlacementModifier {

    private final Direction directionOfSearch;
    private final BlockPredicate targetCondition;
    private final BlockPredicate allowedSearchCondition;
    private final int maxSteps;
    public static final MapCodec<EnvironmentScanPlacement> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Direction.VERTICAL_CODEC.fieldOf("direction_of_search").forGetter((environmentscanplacement) -> {
            return environmentscanplacement.directionOfSearch;
        }), BlockPredicate.CODEC.fieldOf("target_condition").forGetter((environmentscanplacement) -> {
            return environmentscanplacement.targetCondition;
        }), BlockPredicate.CODEC.optionalFieldOf("allowed_search_condition", BlockPredicate.alwaysTrue()).forGetter((environmentscanplacement) -> {
            return environmentscanplacement.allowedSearchCondition;
        }), Codec.intRange(1, 32).fieldOf("max_steps").forGetter((environmentscanplacement) -> {
            return environmentscanplacement.maxSteps;
        })).apply(instance, EnvironmentScanPlacement::new);
    });

    private EnvironmentScanPlacement(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        this.directionOfSearch = directionOfSearch;
        this.targetCondition = targetCondition;
        this.allowedSearchCondition = allowedSearchCondition;
        this.maxSteps = maxSteps;
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, BlockPredicate allowedSearchCondition, int maxSteps) {
        return new EnvironmentScanPlacement(directionOfSearch, targetCondition, allowedSearchCondition, maxSteps);
    }

    public static EnvironmentScanPlacement scanningFor(Direction directionOfSearch, BlockPredicate targetCondition, int maxSteps) {
        return scanningFor(directionOfSearch, targetCondition, BlockPredicate.alwaysTrue(), maxSteps);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();
        WorldGenLevel worldgenlevel = context.getLevel();

        if (!this.allowedSearchCondition.test(worldgenlevel, blockpos_mutableblockpos)) {
            return Stream.of();
        } else {
            int i = 0;

            while (true) {
                if (i < this.maxSteps) {
                    if (this.targetCondition.test(worldgenlevel, blockpos_mutableblockpos)) {
                        return Stream.of(blockpos_mutableblockpos);
                    }

                    blockpos_mutableblockpos.move(this.directionOfSearch);
                    if (worldgenlevel.isOutsideBuildHeight(blockpos_mutableblockpos.getY())) {
                        return Stream.of();
                    }

                    if (this.allowedSearchCondition.test(worldgenlevel, blockpos_mutableblockpos)) {
                        ++i;
                        continue;
                    }
                }

                if (this.targetCondition.test(worldgenlevel, blockpos_mutableblockpos)) {
                    return Stream.of(blockpos_mutableblockpos);
                }

                return Stream.of();
            }
        }
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.ENVIRONMENT_SCAN;
    }
}
