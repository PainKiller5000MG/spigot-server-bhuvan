package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CanyonWorldCarver extends WorldCarver<CanyonCarverConfiguration> {

    public CanyonWorldCarver(Codec<CanyonCarverConfiguration> configurationFactory) {
        super(configurationFactory);
    }

    public boolean isStartChunk(CanyonCarverConfiguration configuration, RandomSource random) {
        return random.nextFloat() <= configuration.probability;
    }

    public boolean carve(CarvingContext context, CanyonCarverConfiguration configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos sourceChunkPos, CarvingMask mask) {
        int i = (this.getRange() * 2 - 1) * 16;
        double d0 = (double) sourceChunkPos.getBlockX(random.nextInt(16));
        int j = configuration.y.sample(random, context);
        double d1 = (double) sourceChunkPos.getBlockZ(random.nextInt(16));
        float f = random.nextFloat() * ((float) Math.PI * 2F);
        float f1 = configuration.verticalRotation.sample(random);
        double d2 = (double) configuration.yScale.sample(random);
        float f2 = configuration.shape.thickness.sample(random);
        int k = (int) ((float) i * configuration.shape.distanceFactor.sample(random));
        int l = 0;

        this.doCarve(context, configuration, chunk, biomeGetter, random.nextLong(), aquifer, d0, (double) j, d1, f2, f, f1, 0, k, d2, mask);
        return true;
    }

    private void doCarve(CarvingContext context, CanyonCarverConfiguration configuration, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, long tunnelSeed, Aquifer aquifer, double x, double y, double z, float thickness, float horizontalRotation, float verticalRotation, int step, int distance, double yScale, CarvingMask mask) {
        RandomSource randomsource = RandomSource.create(tunnelSeed);
        float[] afloat = this.initWidthFactors(context, configuration, randomsource);
        float f3 = 0.0F;
        float f4 = 0.0F;

        for (int l = step; l < distance; ++l) {
            double d4 = 1.5D + (double) (Mth.sin((double) ((float) l * (float) Math.PI / (float) distance)) * thickness);
            double d5 = d4 * yScale;

            d4 *= (double) configuration.shape.horizontalRadiusFactor.sample(randomsource);
            d5 = this.updateVerticalRadius(configuration, randomsource, d5, (float) distance, (float) l);
            float f5 = Mth.cos((double) verticalRotation);
            float f6 = Mth.sin((double) verticalRotation);

            x += (double) (Mth.cos((double) horizontalRotation) * f5);
            y += (double) f6;
            z += (double) (Mth.sin((double) horizontalRotation) * f5);
            verticalRotation *= 0.7F;
            verticalRotation += f4 * 0.05F;
            horizontalRotation += f3 * 0.05F;
            f4 *= 0.8F;
            f3 *= 0.5F;
            f4 += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 2.0F;
            f3 += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 4.0F;
            if (randomsource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, l, distance, thickness)) {
                    return;
                }

                this.carveEllipsoid(context, configuration, chunk, biomeGetter, aquifer, x, y, z, d4, d5, mask, (carvingcontext1, d6, d7, d8, i1) -> {
                    return this.shouldSkip(carvingcontext1, afloat, d6, d7, d8, i1);
                });
            }
        }

    }

    private float[] initWidthFactors(CarvingContext context, CanyonCarverConfiguration configuration, RandomSource random) {
        int i = context.getGenDepth();
        float[] afloat = new float[i];
        float f = 1.0F;

        for (int j = 0; j < i; ++j) {
            if (j == 0 || random.nextInt(configuration.shape.widthSmoothness) == 0) {
                f = 1.0F + random.nextFloat() * random.nextFloat();
            }

            afloat[j] = f * f;
        }

        return afloat;
    }

    private double updateVerticalRadius(CanyonCarverConfiguration configuration, RandomSource random, double verticalRadius, float distance, float currentStep) {
        float f2 = 1.0F - Mth.abs(0.5F - currentStep / distance) * 2.0F;
        float f3 = configuration.shape.verticalRadiusDefaultFactor + configuration.shape.verticalRadiusCenterFactor * f2;

        return (double) f3 * verticalRadius * (double) Mth.randomBetween(random, 0.75F, 1.0F);
    }

    private boolean shouldSkip(CarvingContext context, float[] widthFactorPerHeight, double xd, double yd, double zd, int y) {
        int j = y - context.getMinGenY();

        return (xd * xd + zd * zd) * (double) widthFactorPerHeight[j - 1] + yd * yd / 6.0D >= 1.0D;
    }
}
