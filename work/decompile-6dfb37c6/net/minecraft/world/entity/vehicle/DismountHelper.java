package net.minecraft.world.entity.vehicle;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class DismountHelper {

    public DismountHelper() {}

    public static int[][] offsetsForDirection(Direction forward) {
        Direction direction1 = forward.getClockWise();
        Direction direction2 = direction1.getOpposite();
        Direction direction3 = forward.getOpposite();

        return new int[][]{{direction1.getStepX(), direction1.getStepZ()}, {direction2.getStepX(), direction2.getStepZ()}, {direction3.getStepX() + direction1.getStepX(), direction3.getStepZ() + direction1.getStepZ()}, {direction3.getStepX() + direction2.getStepX(), direction3.getStepZ() + direction2.getStepZ()}, {forward.getStepX() + direction1.getStepX(), forward.getStepZ() + direction1.getStepZ()}, {forward.getStepX() + direction2.getStepX(), forward.getStepZ() + direction2.getStepZ()}, {direction3.getStepX(), direction3.getStepZ()}, {forward.getStepX(), forward.getStepZ()}};
    }

    public static boolean isBlockFloorValid(double blockFloorHeight) {
        return !Double.isInfinite(blockFloorHeight) && blockFloorHeight < 1.0D;
    }

    public static boolean canDismountTo(CollisionGetter level, LivingEntity passenger, AABB box) {
        for (VoxelShape voxelshape : level.getBlockCollisions(passenger, box)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        if (!level.getWorldBorder().isWithinBounds(box)) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean canDismountTo(CollisionGetter level, Vec3 location, LivingEntity passenger, Pose dismountPose) {
        return canDismountTo(level, passenger, passenger.getLocalBoundsForPose(dismountPose).move(location));
    }

    public static VoxelShape nonClimbableShape(BlockGetter level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos);

        return !blockstate.is(BlockTags.CLIMBABLE) && (!(blockstate.getBlock() instanceof TrapDoorBlock) || !(Boolean) blockstate.getValue(TrapDoorBlock.OPEN)) ? blockstate.getCollisionShape(level, pos) : Shapes.empty();
    }

    public static double findCeilingFrom(BlockPos pos, int blocks, Function<BlockPos, VoxelShape> shapeGetter) {
        BlockPos.MutableBlockPos blockpos_mutableblockpos = pos.mutable();
        int j = 0;

        while (j < blocks) {
            VoxelShape voxelshape = (VoxelShape) shapeGetter.apply(blockpos_mutableblockpos);

            if (!voxelshape.isEmpty()) {
                return (double) (pos.getY() + j) + voxelshape.min(Direction.Axis.Y);
            }

            ++j;
            blockpos_mutableblockpos.move(Direction.UP);
        }

        return Double.POSITIVE_INFINITY;
    }

    public static @Nullable Vec3 findSafeDismountLocation(EntityType<?> type, CollisionGetter level, BlockPos blockPos, boolean checkDangerous) {
        if (checkDangerous && type.isBlockDangerous(level.getBlockState(blockPos))) {
            return null;
        } else {
            double d0 = level.getBlockFloorHeight(nonClimbableShape(level, blockPos), () -> {
                return nonClimbableShape(level, blockPos.below());
            });

            if (!isBlockFloorValid(d0)) {
                return null;
            } else if (checkDangerous && d0 <= 0.0D && type.isBlockDangerous(level.getBlockState(blockPos.below()))) {
                return null;
            } else {
                Vec3 vec3 = Vec3.upFromBottomCenterOf(blockPos, d0);
                AABB aabb = type.getDimensions().makeBoundingBox(vec3);

                for (VoxelShape voxelshape : level.getBlockCollisions((Entity) null, aabb)) {
                    if (!voxelshape.isEmpty()) {
                        return null;
                    }
                }

                return type != EntityType.PLAYER || !level.getBlockState(blockPos).is(BlockTags.INVALID_SPAWN_INSIDE) && !level.getBlockState(blockPos.above()).is(BlockTags.INVALID_SPAWN_INSIDE) ? (!level.getWorldBorder().isWithinBounds(aabb) ? null : vec3) : null;
            }
        }
    }
}
