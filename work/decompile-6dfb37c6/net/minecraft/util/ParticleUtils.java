package net.minecraft.util;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {

    public ParticleUtils() {}

    public static void spawnParticlesOnBlockFaces(Level level, BlockPos pos, ParticleOptions particle, IntProvider particlesPerFaceRange) {
        for (Direction direction : Direction.values()) {
            spawnParticlesOnBlockFace(level, pos, particle, particlesPerFaceRange, direction, () -> {
                return getRandomSpeedRanges(level.random);
            }, 0.55D);
        }

    }

    public static void spawnParticlesOnBlockFace(Level level, BlockPos pos, ParticleOptions particle, IntProvider particlesPerFaceRange, Direction face, Supplier<Vec3> speedSupplier, double stepFactor) {
        int i = particlesPerFaceRange.sample(level.random);

        for (int j = 0; j < i; ++j) {
            spawnParticleOnFace(level, pos, face, particle, (Vec3) speedSupplier.get(), stepFactor);
        }

    }

    private static Vec3 getRandomSpeedRanges(RandomSource random) {
        return new Vec3(Mth.nextDouble(random, -0.5D, 0.5D), Mth.nextDouble(random, -0.5D, 0.5D), Mth.nextDouble(random, -0.5D, 0.5D));
    }

    public static void spawnParticlesAlongAxis(Direction.Axis attachedAxis, Level level, BlockPos pos, double radius, ParticleOptions particle, UniformInt sparkCount) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        boolean flag = attachedAxis == Direction.Axis.X;
        boolean flag1 = attachedAxis == Direction.Axis.Y;
        boolean flag2 = attachedAxis == Direction.Axis.Z;
        int i = sparkCount.sample(level.random);

        for (int j = 0; j < i; ++j) {
            double d1 = vec3.x + Mth.nextDouble(level.random, -1.0D, 1.0D) * (flag ? 0.5D : radius);
            double d2 = vec3.y + Mth.nextDouble(level.random, -1.0D, 1.0D) * (flag1 ? 0.5D : radius);
            double d3 = vec3.z + Mth.nextDouble(level.random, -1.0D, 1.0D) * (flag2 ? 0.5D : radius);
            double d4 = flag ? Mth.nextDouble(level.random, -1.0D, 1.0D) : 0.0D;
            double d5 = flag1 ? Mth.nextDouble(level.random, -1.0D, 1.0D) : 0.0D;
            double d6 = flag2 ? Mth.nextDouble(level.random, -1.0D, 1.0D) : 0.0D;

            level.addParticle(particle, d1, d2, d3, d4, d5, d6);
        }

    }

    public static void spawnParticleOnFace(Level level, BlockPos pos, Direction face, ParticleOptions particle, Vec3 speed, double stepFactor) {
        Vec3 vec31 = Vec3.atCenterOf(pos);
        int i = face.getStepX();
        int j = face.getStepY();
        int k = face.getStepZ();
        double d1 = vec31.x + (i == 0 ? Mth.nextDouble(level.random, -0.5D, 0.5D) : (double) i * stepFactor);
        double d2 = vec31.y + (j == 0 ? Mth.nextDouble(level.random, -0.5D, 0.5D) : (double) j * stepFactor);
        double d3 = vec31.z + (k == 0 ? Mth.nextDouble(level.random, -0.5D, 0.5D) : (double) k * stepFactor);
        double d4 = i == 0 ? speed.x() : 0.0D;
        double d5 = j == 0 ? speed.y() : 0.0D;
        double d6 = k == 0 ? speed.z() : 0.0D;

        level.addParticle(particle, d1, d2, d3, d4, d5, d6);
    }

    public static void spawnParticleBelow(Level level, BlockPos pos, RandomSource random, ParticleOptions particle) {
        double d0 = (double) pos.getX() + random.nextDouble();
        double d1 = (double) pos.getY() - 0.05D;
        double d2 = (double) pos.getZ() + random.nextDouble();

        level.addParticle(particle, d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    public static void spawnParticleInBlock(LevelAccessor level, BlockPos pos, int count, ParticleOptions particle) {
        double d0 = 0.5D;
        BlockState blockstate = level.getBlockState(pos);
        double d1 = blockstate.isAir() ? 1.0D : blockstate.getShape(level, pos).max(Direction.Axis.Y);

        spawnParticles(level, pos, count, 0.5D, d1, true, particle);
    }

    public static void spawnParticles(LevelAccessor level, BlockPos pos, int count, double spreadWidth, double spreadHeight, boolean allowFloatingParticles, ParticleOptions particle) {
        RandomSource randomsource = level.getRandom();

        for (int j = 0; j < count; ++j) {
            double d2 = randomsource.nextGaussian() * 0.02D;
            double d3 = randomsource.nextGaussian() * 0.02D;
            double d4 = randomsource.nextGaussian() * 0.02D;
            double d5 = 0.5D - spreadWidth;
            double d6 = (double) pos.getX() + d5 + randomsource.nextDouble() * spreadWidth * 2.0D;
            double d7 = (double) pos.getY() + randomsource.nextDouble() * spreadHeight;
            double d8 = (double) pos.getZ() + d5 + randomsource.nextDouble() * spreadWidth * 2.0D;

            if (allowFloatingParticles || !level.getBlockState(BlockPos.containing(d6, d7, d8).below()).isAir()) {
                level.addParticle(particle, d6, d7, d8, d2, d3, d4);
            }
        }

    }

    public static void spawnSmashAttackParticles(LevelAccessor level, BlockPos pos, int count) {
        Vec3 vec3 = pos.getCenter().add(0.0D, 0.5D, 0.0D);
        BlockParticleOption blockparticleoption = new BlockParticleOption(ParticleTypes.DUST_PILLAR, level.getBlockState(pos));

        for (int j = 0; (float) j < (float) count / 3.0F; ++j) {
            double d0 = vec3.x + level.getRandom().nextGaussian() / 2.0D;
            double d1 = vec3.y;
            double d2 = vec3.z + level.getRandom().nextGaussian() / 2.0D;
            double d3 = level.getRandom().nextGaussian() * (double) 0.2F;
            double d4 = level.getRandom().nextGaussian() * (double) 0.2F;
            double d5 = level.getRandom().nextGaussian() * (double) 0.2F;

            level.addParticle(blockparticleoption, d0, d1, d2, d3, d4, d5);
        }

        for (int k = 0; (float) k < (float) count / 1.5F; ++k) {
            double d6 = vec3.x + 3.5D * Math.cos((double) k) + level.getRandom().nextGaussian() / 2.0D;
            double d7 = vec3.y;
            double d8 = vec3.z + 3.5D * Math.sin((double) k) + level.getRandom().nextGaussian() / 2.0D;
            double d9 = level.getRandom().nextGaussian() * (double) 0.05F;
            double d10 = level.getRandom().nextGaussian() * (double) 0.05F;
            double d11 = level.getRandom().nextGaussian() * (double) 0.05F;

            level.addParticle(blockparticleoption, d6, d7, d8, d9, d10, d11);
        }

    }
}
