package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.AABB;

public class SpikeFeature extends Feature<SpikeConfiguration> {

    public static final int NUMBER_OF_SPIKES = 10;
    private static final int SPIKE_DISTANCE = 42;
    private static final LoadingCache<Long, List<SpikeFeature.EndSpike>> SPIKE_CACHE = CacheBuilder.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build(new SpikeFeature.SpikeCacheLoader());

    public SpikeFeature(Codec<SpikeConfiguration> codec) {
        super(codec);
    }

    public static List<SpikeFeature.EndSpike> getSpikesForLevel(WorldGenLevel level) {
        RandomSource randomsource = RandomSource.create(level.getSeed());
        long i = randomsource.nextLong() & 65535L;

        return (List) SpikeFeature.SPIKE_CACHE.getUnchecked(i);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpikeConfiguration> context) {
        SpikeConfiguration spikeconfiguration = context.config();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        BlockPos blockpos = context.origin();
        List<SpikeFeature.EndSpike> list = spikeconfiguration.getSpikes();

        if (list.isEmpty()) {
            list = getSpikesForLevel(worldgenlevel);
        }

        for (SpikeFeature.EndSpike spikefeature_endspike : list) {
            if (spikefeature_endspike.isCenterWithinChunk(blockpos)) {
                this.placeSpike(worldgenlevel, randomsource, spikeconfiguration, spikefeature_endspike);
            }
        }

        return true;
    }

    private void placeSpike(ServerLevelAccessor level, RandomSource random, SpikeConfiguration config, SpikeFeature.EndSpike spike) {
        int i = spike.getRadius();

        for (BlockPos blockpos : BlockPos.betweenClosed(new BlockPos(spike.getCenterX() - i, level.getMinY(), spike.getCenterZ() - i), new BlockPos(spike.getCenterX() + i, spike.getHeight() + 10, spike.getCenterZ() + i))) {
            if (blockpos.distToLowCornerSqr((double) spike.getCenterX(), (double) blockpos.getY(), (double) spike.getCenterZ()) <= (double) (i * i + 1) && blockpos.getY() < spike.getHeight()) {
                this.setBlock(level, blockpos, Blocks.OBSIDIAN.defaultBlockState());
            } else if (blockpos.getY() > 65) {
                this.setBlock(level, blockpos, Blocks.AIR.defaultBlockState());
            }
        }

        if (spike.isGuarded()) {
            int j = -2;
            int k = 2;
            int l = 3;
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos();

            for (int i1 = -2; i1 <= 2; ++i1) {
                for (int j1 = -2; j1 <= 2; ++j1) {
                    for (int k1 = 0; k1 <= 3; ++k1) {
                        boolean flag = Mth.abs(i1) == 2;
                        boolean flag1 = Mth.abs(j1) == 2;
                        boolean flag2 = k1 == 3;

                        if (flag || flag1 || flag2) {
                            boolean flag3 = i1 == -2 || i1 == 2 || flag2;
                            boolean flag4 = j1 == -2 || j1 == 2 || flag2;
                            BlockState blockstate = (BlockState) ((BlockState) ((BlockState) ((BlockState) Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, flag3 && j1 != -2)).setValue(IronBarsBlock.SOUTH, flag3 && j1 != 2)).setValue(IronBarsBlock.WEST, flag4 && i1 != -2)).setValue(IronBarsBlock.EAST, flag4 && i1 != 2);

                            this.setBlock(level, blockpos_mutableblockpos.set(spike.getCenterX() + i1, spike.getHeight() + k1, spike.getCenterZ() + j1), blockstate);
                        }
                    }
                }
            }
        }

        EndCrystal endcrystal = EntityType.END_CRYSTAL.create(level.getLevel(), EntitySpawnReason.STRUCTURE);

        if (endcrystal != null) {
            endcrystal.setBeamTarget(config.getCrystalBeamTarget());
            endcrystal.setInvulnerable(config.isCrystalInvulnerable());
            endcrystal.snapTo((double) spike.getCenterX() + 0.5D, (double) (spike.getHeight() + 1), (double) spike.getCenterZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(endcrystal);
            BlockPos blockpos1 = endcrystal.blockPosition();

            this.setBlock(level, blockpos1.below(), Blocks.BEDROCK.defaultBlockState());
            this.setBlock(level, blockpos1, FireBlock.getState(level, blockpos1));
        }

    }

    public static class EndSpike {

        public static final Codec<SpikeFeature.EndSpike> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("centerX").orElse(0).forGetter((spikefeature_endspike) -> {
                return spikefeature_endspike.centerX;
            }), Codec.INT.fieldOf("centerZ").orElse(0).forGetter((spikefeature_endspike) -> {
                return spikefeature_endspike.centerZ;
            }), Codec.INT.fieldOf("radius").orElse(0).forGetter((spikefeature_endspike) -> {
                return spikefeature_endspike.radius;
            }), Codec.INT.fieldOf("height").orElse(0).forGetter((spikefeature_endspike) -> {
                return spikefeature_endspike.height;
            }), Codec.BOOL.fieldOf("guarded").orElse(false).forGetter((spikefeature_endspike) -> {
                return spikefeature_endspike.guarded;
            })).apply(instance, SpikeFeature.EndSpike::new);
        });
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final int height;
        private final boolean guarded;
        private final AABB topBoundingBox;

        public EndSpike(int centerX, int centerZ, int radius, int height, boolean guarded) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.height = height;
            this.guarded = guarded;
            this.topBoundingBox = new AABB((double) (centerX - radius), (double) DimensionType.MIN_Y, (double) (centerZ - radius), (double) (centerX + radius), (double) DimensionType.MAX_Y, (double) (centerZ + radius));
        }

        public boolean isCenterWithinChunk(BlockPos chunkOrigin) {
            return SectionPos.blockToSectionCoord(chunkOrigin.getX()) == SectionPos.blockToSectionCoord(this.centerX) && SectionPos.blockToSectionCoord(chunkOrigin.getZ()) == SectionPos.blockToSectionCoord(this.centerZ);
        }

        public int getCenterX() {
            return this.centerX;
        }

        public int getCenterZ() {
            return this.centerZ;
        }

        public int getRadius() {
            return this.radius;
        }

        public int getHeight() {
            return this.height;
        }

        public boolean isGuarded() {
            return this.guarded;
        }

        public AABB getTopBoundingBox() {
            return this.topBoundingBox;
        }
    }

    private static class SpikeCacheLoader extends CacheLoader<Long, List<SpikeFeature.EndSpike>> {

        private SpikeCacheLoader() {}

        public List<SpikeFeature.EndSpike> load(Long seed) {
            IntArrayList intarraylist = Util.toShuffledList(IntStream.range(0, 10), RandomSource.create(seed));
            List<SpikeFeature.EndSpike> list = Lists.newArrayList();

            for (int i = 0; i < 10; ++i) {
                int j = Mth.floor(42.0D * Math.cos(2.0D * (-Math.PI + (Math.PI / 10D) * (double) i)));
                int k = Mth.floor(42.0D * Math.sin(2.0D * (-Math.PI + (Math.PI / 10D) * (double) i)));
                int l = intarraylist.get(i);
                int i1 = 2 + l / 3;
                int j1 = 76 + l * 3;
                boolean flag = l == 1 || l == 2;

                list.add(new SpikeFeature.EndSpike(j, k, i1, j1, flag));
            }

            return list;
        }
    }
}
