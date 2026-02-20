package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface BlockGetter extends LevelHeightAccessor {

    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity blockentity = this.getBlockEntity(pos);

        return blockentity != null && blockentity.getType() == type ? Optional.of(blockentity) : Optional.empty();
    }

    BlockState getBlockState(BlockPos pos);

    FluidState getFluidState(BlockPos pos);

    default int getLightEmission(BlockPos pos) {
        return this.getBlockState(pos).getLightEmission();
    }

    default Stream<BlockState> getBlockStates(AABB box) {
        return BlockPos.betweenClosedStream(box).map(this::getBlockState);
    }

    default BlockHitResult isBlockInLine(ClipBlockStateContext c) {
        return (BlockHitResult) traverseBlocks(c.getFrom(), c.getTo(), c, (clipblockstatecontext1, blockpos) -> {
            BlockState blockstate = this.getBlockState(blockpos);
            Vec3 vec3 = clipblockstatecontext1.getFrom().subtract(clipblockstatecontext1.getTo());

            return clipblockstatecontext1.isTargetBlock().test(blockstate) ? new BlockHitResult(clipblockstatecontext1.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipblockstatecontext1.getTo()), false) : null;
        }, (clipblockstatecontext1) -> {
            Vec3 vec3 = clipblockstatecontext1.getFrom().subtract(clipblockstatecontext1.getTo());

            return BlockHitResult.miss(clipblockstatecontext1.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipblockstatecontext1.getTo()));
        });
    }

    default BlockHitResult clip(ClipContext c) {
        return (BlockHitResult) traverseBlocks(c.getFrom(), c.getTo(), c, (clipcontext1, blockpos) -> {
            BlockState blockstate = this.getBlockState(blockpos);
            FluidState fluidstate = this.getFluidState(blockpos);
            Vec3 vec3 = clipcontext1.getFrom();
            Vec3 vec31 = clipcontext1.getTo();
            VoxelShape voxelshape = clipcontext1.getBlockShape(blockstate, this, blockpos);
            BlockHitResult blockhitresult = this.clipWithInteractionOverride(vec3, vec31, blockpos, voxelshape, blockstate);
            VoxelShape voxelshape1 = clipcontext1.getFluidShape(fluidstate, this, blockpos);
            BlockHitResult blockhitresult1 = voxelshape1.clip(vec3, vec31, blockpos);
            double d0 = blockhitresult == null ? Double.MAX_VALUE : clipcontext1.getFrom().distanceToSqr(blockhitresult.getLocation());
            double d1 = blockhitresult1 == null ? Double.MAX_VALUE : clipcontext1.getFrom().distanceToSqr(blockhitresult1.getLocation());

            return d0 <= d1 ? blockhitresult : blockhitresult1;
        }, (clipcontext1) -> {
            Vec3 vec3 = clipcontext1.getFrom().subtract(clipcontext1.getTo());

            return BlockHitResult.miss(clipcontext1.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipcontext1.getTo()));
        });
    }

    default @Nullable BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape blockShape, BlockState blockState) {
        BlockHitResult blockhitresult = blockShape.clip(from, to, pos);

        if (blockhitresult != null) {
            BlockHitResult blockhitresult1 = blockState.getInteractionShape(this, pos).clip(from, to, pos);

            if (blockhitresult1 != null && blockhitresult1.getLocation().subtract(from).lengthSqr() < blockhitresult.getLocation().subtract(from).lengthSqr()) {
                return blockhitresult.withDirection(blockhitresult1.getDirection());
            }
        }

        return blockhitresult;
    }

    default double getBlockFloorHeight(VoxelShape blockShape, Supplier<VoxelShape> belowBlockShape) {
        if (!blockShape.isEmpty()) {
            return blockShape.max(Direction.Axis.Y);
        } else {
            double d0 = ((VoxelShape) belowBlockShape.get()).max(Direction.Axis.Y);

            return d0 >= 1.0D ? d0 - 1.0D : Double.NEGATIVE_INFINITY;
        }
    }

    default double getBlockFloorHeight(BlockPos pos) {
        return this.getBlockFloorHeight(this.getBlockState(pos).getCollisionShape(this, pos), () -> {
            BlockPos blockpos1 = pos.below();

            return this.getBlockState(blockpos1).getCollisionShape(this, blockpos1);
        });
    }

    static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, @Nullable T> consumer, Function<C, T> missFactory) {
        if (from.equals(to)) {
            return (T) missFactory.apply(context);
        } else {
            double d0 = Mth.lerp(-1.0E-7D, to.x, from.x);
            double d1 = Mth.lerp(-1.0E-7D, to.y, from.y);
            double d2 = Mth.lerp(-1.0E-7D, to.z, from.z);
            double d3 = Mth.lerp(-1.0E-7D, from.x, to.x);
            double d4 = Mth.lerp(-1.0E-7D, from.y, to.y);
            double d5 = Mth.lerp(-1.0E-7D, from.z, to.z);
            int i = Mth.floor(d3);
            int j = Mth.floor(d4);
            int k = Mth.floor(d5);
            BlockPos.MutableBlockPos blockpos_mutableblockpos = new BlockPos.MutableBlockPos(i, j, k);
            T t0 = (T) consumer.apply(context, blockpos_mutableblockpos);

            if (t0 != null) {
                return t0;
            } else {
                double d6 = d0 - d3;
                double d7 = d1 - d4;
                double d8 = d2 - d5;
                int l = Mth.sign(d6);
                int i1 = Mth.sign(d7);
                int j1 = Mth.sign(d8);
                double d9 = l == 0 ? Double.MAX_VALUE : (double) l / d6;
                double d10 = i1 == 0 ? Double.MAX_VALUE : (double) i1 / d7;
                double d11 = j1 == 0 ? Double.MAX_VALUE : (double) j1 / d8;
                double d12 = d9 * (l > 0 ? 1.0D - Mth.frac(d3) : Mth.frac(d3));
                double d13 = d10 * (i1 > 0 ? 1.0D - Mth.frac(d4) : Mth.frac(d4));
                double d14 = d11 * (j1 > 0 ? 1.0D - Mth.frac(d5) : Mth.frac(d5));

                while (d12 <= 1.0D || d13 <= 1.0D || d14 <= 1.0D) {
                    if (d12 < d13) {
                        if (d12 < d14) {
                            i += l;
                            d12 += d9;
                        } else {
                            k += j1;
                            d14 += d11;
                        }
                    } else if (d13 < d14) {
                        j += i1;
                        d13 += d10;
                    } else {
                        k += j1;
                        d14 += d11;
                    }

                    T t1 = (T) consumer.apply(context, blockpos_mutableblockpos.set(i, j, k));

                    if (t1 != null) {
                        return t1;
                    }
                }

                return (T) missFactory.apply(context);
            }
        }
    }

    static boolean forEachBlockIntersectedBetween(Vec3 from, Vec3 to, AABB aabbAtTarget, BlockGetter.BlockStepVisitor visitor) {
        Vec3 vec32 = to.subtract(from);

        if (vec32.lengthSqr() < (double) Mth.square(1.0E-5F)) {
            for (BlockPos blockpos : BlockPos.betweenClosed(aabbAtTarget)) {
                if (!visitor.visit(blockpos, 0)) {
                    return false;
                }
            }

            return true;
        } else {
            LongSet longset = new LongOpenHashSet();

            for (BlockPos blockpos1 : BlockPos.betweenCornersInDirection(aabbAtTarget.move(vec32.scale(-1.0D)), vec32)) {
                if (!visitor.visit(blockpos1, 0)) {
                    return false;
                }

                longset.add(blockpos1.asLong());
            }

            int i = addCollisionsAlongTravel(longset, vec32, aabbAtTarget, visitor);

            if (i < 0) {
                return false;
            } else {
                for (BlockPos blockpos2 : BlockPos.betweenCornersInDirection(aabbAtTarget, vec32)) {
                    if (longset.add(blockpos2.asLong()) && !visitor.visit(blockpos2, i + 1)) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    private static int addCollisionsAlongTravel(LongSet visitedBlocks, Vec3 deltaMove, AABB aabbAtTarget, BlockGetter.BlockStepVisitor visitor) {
        double d0 = aabbAtTarget.getXsize();
        double d1 = aabbAtTarget.getYsize();
        double d2 = aabbAtTarget.getZsize();
        Vec3i vec3i = getFurthestCorner(deltaMove);
        Vec3 vec31 = aabbAtTarget.getCenter();
        Vec3 vec32 = new Vec3(vec31.x() + d0 * 0.5D * (double) vec3i.getX(), vec31.y() + d1 * 0.5D * (double) vec3i.getY(), vec31.z() + d2 * 0.5D * (double) vec3i.getZ());
        Vec3 vec33 = vec32.subtract(deltaMove);
        int i = Mth.floor(vec33.x);
        int j = Mth.floor(vec33.y);
        int k = Mth.floor(vec33.z);
        int l = Mth.sign(deltaMove.x);
        int i1 = Mth.sign(deltaMove.y);
        int j1 = Mth.sign(deltaMove.z);
        double d3 = l == 0 ? Double.MAX_VALUE : (double) l / deltaMove.x;
        double d4 = i1 == 0 ? Double.MAX_VALUE : (double) i1 / deltaMove.y;
        double d5 = j1 == 0 ? Double.MAX_VALUE : (double) j1 / deltaMove.z;
        double d6 = d3 * (l > 0 ? 1.0D - Mth.frac(vec33.x) : Mth.frac(vec33.x));
        double d7 = d4 * (i1 > 0 ? 1.0D - Mth.frac(vec33.y) : Mth.frac(vec33.y));
        double d8 = d5 * (j1 > 0 ? 1.0D - Mth.frac(vec33.z) : Mth.frac(vec33.z));
        int k1 = 0;

        while (d6 <= 1.0D || d7 <= 1.0D || d8 <= 1.0D) {
            if (d6 < d7) {
                if (d6 < d8) {
                    i += l;
                    d6 += d3;
                } else {
                    k += j1;
                    d8 += d5;
                }
            } else if (d7 < d8) {
                j += i1;
                d7 += d4;
            } else {
                k += j1;
                d8 += d5;
            }

            Optional<Vec3> optional = AABB.clip((double) i, (double) j, (double) k, (double) (i + 1), (double) (j + 1), (double) (k + 1), vec33, vec32);

            if (!optional.isEmpty()) {
                ++k1;
                Vec3 vec34 = (Vec3) optional.get();
                double d9 = Mth.clamp(vec34.x, (double) i + (double) 1.0E-5F, (double) i + 1.0D - (double) 1.0E-5F);
                double d10 = Mth.clamp(vec34.y, (double) j + (double) 1.0E-5F, (double) j + 1.0D - (double) 1.0E-5F);
                double d11 = Mth.clamp(vec34.z, (double) k + (double) 1.0E-5F, (double) k + 1.0D - (double) 1.0E-5F);
                int l1 = Mth.floor(d9 - d0 * (double) vec3i.getX());
                int i2 = Mth.floor(d10 - d1 * (double) vec3i.getY());
                int j2 = Mth.floor(d11 - d2 * (double) vec3i.getZ());
                int k2 = k1;

                for (BlockPos blockpos : BlockPos.betweenCornersInDirection(i, j, k, l1, i2, j2, deltaMove)) {
                    if (visitedBlocks.add(blockpos.asLong()) && !visitor.visit(blockpos, k2)) {
                        return -1;
                    }
                }
            }
        }

        return k1;
    }

    private static Vec3i getFurthestCorner(Vec3 direction) {
        double d0 = Math.abs(Vec3.X_AXIS.dot(direction));
        double d1 = Math.abs(Vec3.Y_AXIS.dot(direction));
        double d2 = Math.abs(Vec3.Z_AXIS.dot(direction));
        int i = direction.x >= 0.0D ? 1 : -1;
        int j = direction.y >= 0.0D ? 1 : -1;
        int k = direction.z >= 0.0D ? 1 : -1;

        return d0 <= d1 && d0 <= d2 ? new Vec3i(-i, -k, j) : (d1 <= d2 ? new Vec3i(k, -j, -i) : new Vec3i(-j, i, -k));
    }

    @FunctionalInterface
    public interface BlockStepVisitor {

        boolean visit(BlockPos pos, int iteration);
    }
}
