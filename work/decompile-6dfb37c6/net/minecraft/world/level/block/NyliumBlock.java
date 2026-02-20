package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.NetherFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.lighting.LightEngine;

public class NyliumBlock extends Block implements BonemealableBlock {

    public static final MapCodec<NyliumBlock> CODEC = simpleCodec(NyliumBlock::new);

    @Override
    public MapCodec<NyliumBlock> codec() {
        return NyliumBlock.CODEC;
    }

    protected NyliumBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    private static boolean canBeNylium(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos1 = pos.above();
        BlockState blockstate1 = level.getBlockState(blockpos1);
        int i = LightEngine.getLightBlockInto(state, blockstate1, Direction.UP, blockstate1.getLightBlock());

        return i < 15;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canBeNylium(state, level, pos)) {
            level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
        }

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
        BlockState blockstate1 = level.getBlockState(pos);
        BlockPos blockpos1 = pos.above();
        ChunkGenerator chunkgenerator = level.getChunkSource().getGenerator();
        Registry<ConfiguredFeature<?, ?>> registry = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);

        if (blockstate1.is(Blocks.CRIMSON_NYLIUM)) {
            this.place(registry, NetherFeatures.CRIMSON_FOREST_VEGETATION_BONEMEAL, level, chunkgenerator, random, blockpos1);
        } else if (blockstate1.is(Blocks.WARPED_NYLIUM)) {
            this.place(registry, NetherFeatures.WARPED_FOREST_VEGETATION_BONEMEAL, level, chunkgenerator, random, blockpos1);
            this.place(registry, NetherFeatures.NETHER_SPROUTS_BONEMEAL, level, chunkgenerator, random, blockpos1);
            if (random.nextInt(8) == 0) {
                this.place(registry, NetherFeatures.TWISTING_VINES_BONEMEAL, level, chunkgenerator, random, blockpos1);
            }
        }

    }

    private void place(Registry<ConfiguredFeature<?, ?>> configuredFeatures, ResourceKey<ConfiguredFeature<?, ?>> id, ServerLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        configuredFeatures.get(id).ifPresent((holder_reference) -> {
            ((ConfiguredFeature) holder_reference.value()).place(level, generator, random, pos);
        });
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
