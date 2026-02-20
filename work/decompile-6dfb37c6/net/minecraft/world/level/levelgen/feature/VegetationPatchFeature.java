package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class VegetationPatchFeature extends Feature<VegetationPatchConfiguration> {

    public VegetationPatchFeature(Codec<VegetationPatchConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VegetationPatchConfiguration> context) {
        WorldGenLevel worldgenlevel = context.level();
        VegetationPatchConfiguration vegetationpatchconfiguration = context.config();
        RandomSource randomsource = context.random();
        BlockPos blockpos = context.origin();
        Predicate<BlockState> predicate = (blockstate) -> {
            return blockstate.is(vegetationpatchconfiguration.replaceable);
        };
        int i = vegetationpatchconfiguration.xzRadius.sample(randomsource) + 1;
        int j = vegetationpatchconfiguration.xzRadius.sample(randomsource) + 1;
        Set<BlockPos> set = this.placeGroundPatch(worldgenlevel, vegetationpatchconfiguration, randomsource, blockpos, predicate, i, j);

        this.distributeVegetation(context, worldgenlevel, vegetationpatchconfiguration, randomsource, set, i, j);
        return !set.isEmpty();
    }

    protected Set<BlockPos> placeGroundPatch(WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, BlockPos origin, Predicate<BlockState> replaceable, int xRadius, int zRadius) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = origin.mutable();
        BlockPos.MutableBlockPos blockpos_mutableblockpos1 = blockpos_mutableblockpos.mutable();
        Direction direction = config.surface.getDirection();
        Direction direction1 = direction.getOpposite();
        Set<BlockPos> set = new HashSet();

        for (int k = -xRadius; k <= xRadius; ++k) {
            boolean flag = k == -xRadius || k == xRadius;

            for (int l = -zRadius; l <= zRadius; ++l) {
                boolean flag1 = l == -zRadius || l == zRadius;
                boolean flag2 = flag || flag1;
                boolean flag3 = flag && flag1;
                boolean flag4 = flag2 && !flag3;

                if (!flag3 && (!flag4 || config.extraEdgeColumnChance != 0.0F && random.nextFloat() <= config.extraEdgeColumnChance)) {
                    blockpos_mutableblockpos.setWithOffset(origin, k, 0, l);

                    for (int i1 = 0; level.isStateAtPosition(blockpos_mutableblockpos, BlockBehaviour.BlockStateBase::isAir) && i1 < config.verticalRange; ++i1) {
                        blockpos_mutableblockpos.move(direction);
                    }

                    for (int j1 = 0; level.isStateAtPosition(blockpos_mutableblockpos, (blockstate) -> {
                        return !blockstate.isAir();
                    }) && j1 < config.verticalRange; ++j1) {
                        blockpos_mutableblockpos.move(direction1);
                    }

                    blockpos_mutableblockpos1.setWithOffset(blockpos_mutableblockpos, config.surface.getDirection());
                    BlockState blockstate = level.getBlockState(blockpos_mutableblockpos1);

                    if (level.isEmptyBlock(blockpos_mutableblockpos) && blockstate.isFaceSturdy(level, blockpos_mutableblockpos1, config.surface.getDirection().getOpposite())) {
                        int k1 = config.depth.sample(random) + (config.extraBottomBlockChance > 0.0F && random.nextFloat() < config.extraBottomBlockChance ? 1 : 0);
                        BlockPos blockpos1 = blockpos_mutableblockpos1.immutable();
                        boolean flag5 = this.placeGround(level, config, replaceable, random, blockpos_mutableblockpos1, k1);

                        if (flag5) {
                            set.add(blockpos1);
                        }
                    }
                }
            }
        }

        return set;
    }

    protected void distributeVegetation(FeaturePlaceContext<VegetationPatchConfiguration> context, WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, Set<BlockPos> surface, int xRadius, int zRadius) {
        for (BlockPos blockpos : surface) {
            if (config.vegetationChance > 0.0F && random.nextFloat() < config.vegetationChance) {
                this.placeVegetation(level, config, context.chunkGenerator(), random, blockpos);
            }
        }

    }

    protected boolean placeVegetation(WorldGenLevel level, VegetationPatchConfiguration config, ChunkGenerator generator, RandomSource random, BlockPos vegetationPos) {
        return ((PlacedFeature) config.vegetationFeature.value()).place(level, generator, random, vegetationPos.relative(config.surface.getDirection().getOpposite()));
    }

    protected boolean placeGround(WorldGenLevel level, VegetationPatchConfiguration config, Predicate<BlockState> replaceable, RandomSource random, BlockPos.MutableBlockPos belowPos, int depth) {
        for (int j = 0; j < depth; ++j) {
            BlockState blockstate = config.groundState.getState(random, belowPos);
            BlockState blockstate1 = level.getBlockState(belowPos);

            if (!blockstate.is(blockstate1.getBlock())) {
                if (!replaceable.test(blockstate1)) {
                    return j != 0;
                }

                level.setBlock(belowPos, blockstate, 2);
                belowPos.move(config.surface.getDirection());
            }
        }

        return true;
    }
}
