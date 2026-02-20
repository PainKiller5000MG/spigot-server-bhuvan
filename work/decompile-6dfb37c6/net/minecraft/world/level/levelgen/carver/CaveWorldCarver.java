package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {

    public CaveWorldCarver(Codec<CaveCarverConfiguration> configurationFactory) {
        super(configurationFactory);
    }

    public boolean isStartChunk(CaveCarverConfiguration configuration, RandomSource random) {
        return random.nextFloat() <= configuration.probability;
    }

    public boolean carve(CarvingContext context, CaveCarverConfiguration configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos sourceChunkPos, CarvingMask mask) {
        int i = SectionPos.sectionToBlockCoord(this.getRange() * 2 - 1);
        int j = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (int k = 0; k < j; ++k) {
            double d0 = (double) sourceChunkPos.getBlockX(random.nextInt(16));
            double d1 = (double) configuration.y.sample(random, context);
            double d2 = (double) sourceChunkPos.getBlockZ(random.nextInt(16));
            double d3 = (double) configuration.horizontalRadiusMultiplier.sample(random);
            double d4 = (double) configuration.verticalRadiusMultiplier.sample(random);
            double d5 = (double) configuration.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker worldcarver_carveskipchecker = (carvingcontext1, d6, d7, d8, l) -> {
                return shouldSkip(d6, d7, d8, d5);
            };
            int l = 1;

            if (random.nextInt(4) == 0) {
                double d6 = (double) configuration.yScale.sample(random);
                float f = 1.0F + random.nextFloat() * 6.0F;

                this.createRoom(context, configuration, chunk, biomeGetter, aquifer, d0, d1, d2, f, d6, mask, worldcarver_carveskipchecker);
                l += random.nextInt(4);
            }

            for (int i1 = 0; i1 < l; ++i1) {
                float f1 = random.nextFloat() * ((float) Math.PI * 2F);
                float f2 = (random.nextFloat() - 0.5F) / 4.0F;
                float f3 = this.getThickness(random);
                int j1 = i - random.nextInt(i / 4);
                int k1 = 0;

                this.createTunnel(context, configuration, chunk, biomeGetter, random.nextLong(), aquifer, d0, d1, d2, d3, d4, f3, f1, f2, 0, j1, this.getYScale(), mask, worldcarver_carveskipchecker);
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();

        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return 1.0D;
    }

    protected void createRoom(CarvingContext context, CaveCarverConfiguration configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, Aquifer aquifer, double x, double y, double z, float thickness, double yScale, CarvingMask mask, WorldCarver.CarveSkipChecker skipChecker) {
        double d4 = 1.5D + (double) (Mth.sin((double) ((float) Math.PI / 2F)) * thickness);
        double d5 = d4 * yScale;

        this.carveEllipsoid(context, configuration, chunk, biomeGetter, aquifer, x + 1.0D, y, z, d4, d5, mask, skipChecker);
    }

    protected void createTunnel(CarvingContext context, CaveCarverConfiguration configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, long tunnelSeed, Aquifer aquifer, double x, double y, double z, double horizontalRadiusMultiplier, double verticalRadiusMultiplier, float thickness, float horizontalRotation, float verticalRotation, int step, int dist, double yScale, CarvingMask mask, WorldCarver.CarveSkipChecker skipChecker) {
        RandomSource randomsource = RandomSource.create(tunnelSeed);
        int l = randomsource.nextInt(dist / 2) + dist / 4;
        boolean flag = randomsource.nextInt(6) == 0;
        float f3 = 0.0F;
        float f4 = 0.0F;

        for (int i1 = step; i1 < dist; ++i1) {
            double d6 = 1.5D + (double) (Mth.sin((double) ((float) Math.PI * (float) i1 / (float) dist)) * thickness);
            double d7 = d6 * yScale;
            float f5 = Mth.cos((double) verticalRotation);

            x += (double) (Mth.cos((double) horizontalRotation) * f5);
            y += (double) Mth.sin((double) verticalRotation);
            z += (double) (Mth.sin((double) horizontalRotation) * f5);
            verticalRotation *= flag ? 0.92F : 0.7F;
            verticalRotation += f4 * 0.1F;
            horizontalRotation += f3 * 0.1F;
            f4 *= 0.9F;
            f3 *= 0.75F;
            f4 += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 2.0F;
            f3 += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 4.0F;
            if (i1 == l && thickness > 1.0F) {
                this.createTunnel(context, configuration, chunk, biomeGetter, randomsource.nextLong(), aquifer, x, y, z, horizontalRadiusMultiplier, verticalRadiusMultiplier, randomsource.nextFloat() * 0.5F + 0.5F, horizontalRotation - ((float) Math.PI / 2F), verticalRotation / 3.0F, i1, dist, 1.0D, mask, skipChecker);
                this.createTunnel(context, configuration, chunk, biomeGetter, randomsource.nextLong(), aquifer, x, y, z, horizontalRadiusMultiplier, verticalRadiusMultiplier, randomsource.nextFloat() * 0.5F + 0.5F, horizontalRotation + ((float) Math.PI / 2F), verticalRotation / 3.0F, i1, dist, 1.0D, mask, skipChecker);
                return;
            }

            if (randomsource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, i1, dist, thickness)) {
                    return;
                }

                this.carveEllipsoid(context, configuration, chunk, biomeGetter, aquifer, x, y, z, d6 * horizontalRadiusMultiplier, d7 * verticalRadiusMultiplier, mask, skipChecker);
            }
        }

    }

    private static boolean shouldSkip(double xd, double yd, double zd, double floorLevel) {
        return yd <= floorLevel ? true : xd * xd + yd * yd + zd * zd >= 1.0D;
    }
}
