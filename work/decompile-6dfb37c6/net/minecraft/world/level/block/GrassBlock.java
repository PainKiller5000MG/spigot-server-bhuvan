package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class GrassBlock extends SpreadingSnowyDirtBlock implements BonemealableBlock {

    public static final MapCodec<GrassBlock> CODEC = simpleCodec(GrassBlock::new);

    @Override
    public MapCodec<GrassBlock> codec() {
        return GrassBlock.CODEC;
    }

    public GrassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate1 = Blocks.SHORT_GRASS.defaultBlockState();
        Optional<Holder.Reference<PlacedFeature>> optional = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).get(VegetationPlacements.GRASS_BONEMEAL);

        label51:
        for (int i = 0; i < 128; ++i) {
            BlockPos blockpos2 = blockpos1;

            for (int j = 0; j < i / 16; ++j) {
                blockpos2 = blockpos2.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (!level.getBlockState(blockpos2.below()).is(this) || level.getBlockState(blockpos2).isCollisionShapeFullBlock(level, blockpos2)) {
                    continue label51;
                }
            }

            BlockState blockstate2 = level.getBlockState(blockpos2);

            if (blockstate2.is(blockstate1.getBlock()) && random.nextInt(10) == 0) {
                BonemealableBlock bonemealableblock = (BonemealableBlock) blockstate1.getBlock();

                if (bonemealableblock.isValidBonemealTarget(level, blockpos2, blockstate2)) {
                    bonemealableblock.performBonemeal(level, random, blockpos2, blockstate2);
                }
            }

            if (blockstate2.isAir()) {
                Holder<PlacedFeature> holder;

                if (random.nextInt(8) == 0) {
                    List<ConfiguredFeature<?, ?>> list = ((Biome) level.getBiome(blockpos2).value()).getGenerationSettings().getFlowerFeatures();

                    if (list.isEmpty()) {
                        continue;
                    }

                    int k = random.nextInt(list.size());

                    holder = ((RandomPatchConfiguration) ((ConfiguredFeature) list.get(k)).config()).feature();
                } else {
                    if (!optional.isPresent()) {
                        continue;
                    }

                    holder = (Holder) optional.get();
                }

                ((PlacedFeature) holder.value()).place(level, level.getChunkSource().getGenerator(), random, blockpos2);
            }
        }

    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
