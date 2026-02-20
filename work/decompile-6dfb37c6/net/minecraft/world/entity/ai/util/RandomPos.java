package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomPos {

    private static final int RANDOM_POS_ATTEMPTS = 10;

    public RandomPos() {}

    public static BlockPos generateRandomDirection(RandomSource random, int horizontalDist, int verticalDist) {
        int k = random.nextInt(2 * horizontalDist + 1) - horizontalDist;
        int l = random.nextInt(2 * verticalDist + 1) - verticalDist;
        int i1 = random.nextInt(2 * horizontalDist + 1) - horizontalDist;

        return new BlockPos(k, l, i1);
    }

    public static @Nullable BlockPos generateRandomDirectionWithinRadians(RandomSource random, double minHorizontalDist, double maxHorizontalDist, int verticalDist, int flyingHeight, double xDir, double zDir, double maxXzRadiansFromDir) {
        double d5 = Mth.atan2(zDir, xDir) - (double) ((float) Math.PI / 2F);
        double d6 = d5 + (double) (2.0F * random.nextFloat() - 1.0F) * maxXzRadiansFromDir;
        double d7 = Mth.lerp(Math.sqrt(random.nextDouble()), minHorizontalDist, maxHorizontalDist) * (double) Mth.SQRT_OF_TWO;
        double d8 = -d7 * Math.sin(d6);
        double d9 = d7 * Math.cos(d6);

        if (Math.abs(d8) <= maxHorizontalDist && Math.abs(d9) <= maxHorizontalDist) {
            int k = random.nextInt(2 * verticalDist + 1) - verticalDist + flyingHeight;

            return BlockPos.containing(d8, (double) k, d9);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pos, int maxY, Predicate<BlockPos> solidityTester) {
        if (!solidityTester.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable().move(Direction.UP);

            while (blockpos_mutableblockpos.getY() <= maxY && solidityTester.test(blockpos_mutableblockpos)) {
                blockpos_mutableblockpos.move(Direction.UP);
            }

            return blockpos_mutableblockpos.immutable();
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pos, int aboveSolidAmount, int maxY, Predicate<BlockPos> solidityTester) {
        if (aboveSolidAmount < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + aboveSolidAmount + ", expected >= 0");
        } else if (!solidityTester.test(pos)) {
            return pos;
        } else {
            BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable().move(Direction.UP);

            while (blockpos_mutableblockpos.getY() <= maxY && solidityTester.test(blockpos_mutableblockpos)) {
                blockpos_mutableblockpos.move(Direction.UP);
            }

            int k = blockpos_mutableblockpos.getY();

            while (blockpos_mutableblockpos.getY() <= maxY && blockpos_mutableblockpos.getY() - k < aboveSolidAmount) {
                blockpos_mutableblockpos.move(Direction.UP);
                if (solidityTester.test(blockpos_mutableblockpos)) {
                    blockpos_mutableblockpos.move(Direction.DOWN);
                    break;
                }
            }

            return blockpos_mutableblockpos.immutable();
        }
    }

    public static @Nullable Vec3 generateRandomPos(PathfinderMob mob, Supplier<@Nullable BlockPos> posSupplier) {
        Objects.requireNonNull(mob);
        return generateRandomPos(posSupplier, mob::getWalkTargetValue);
    }

    public static @Nullable Vec3 generateRandomPos(Supplier<@Nullable BlockPos> posSupplier, ToDoubleFunction<BlockPos> positionWeightFunction) {
        double d0 = Double.NEGATIVE_INFINITY;
        BlockPos blockpos = null;

        for (int i = 0; i < 10; ++i) {
            BlockPos blockpos1 = (BlockPos) posSupplier.get();

            if (blockpos1 != null) {
                double d1 = positionWeightFunction.applyAsDouble(blockpos1);

                if (d1 > d0) {
                    d0 = d1;
                    blockpos = blockpos1;
                }
            }
        }

        return blockpos != null ? Vec3.atBottomCenterOf(blockpos) : null;
    }

    public static BlockPos generateRandomPosTowardDirection(PathfinderMob mob, double xzDist, RandomSource random, BlockPos direction) {
        double d1 = (double) direction.getX();
        double d2 = (double) direction.getZ();

        if (mob.hasHome() && xzDist > 1.0D) {
            BlockPos blockpos1 = mob.getHomePosition();

            if (mob.getX() > (double) blockpos1.getX()) {
                d1 -= random.nextDouble() * xzDist / 2.0D;
            } else {
                d1 += random.nextDouble() * xzDist / 2.0D;
            }

            if (mob.getZ() > (double) blockpos1.getZ()) {
                d2 -= random.nextDouble() * xzDist / 2.0D;
            } else {
                d2 += random.nextDouble() * xzDist / 2.0D;
            }
        }

        return BlockPos.containing(d1 + mob.getX(), (double) direction.getY() + mob.getY(), d2 + mob.getZ());
    }
}
