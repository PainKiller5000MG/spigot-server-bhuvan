package net.minecraft.world.level;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface CollisionGetter extends BlockGetter {

    WorldBorder getWorldBorder();

    @Nullable
    BlockGetter getChunkForCollisions(int chunkX, int chunkZ);

    default boolean isUnobstructed(@Nullable Entity source, VoxelShape shape) {
        return true;
    }

    default boolean isUnobstructed(BlockState state, BlockPos pos, CollisionContext context) {
        VoxelShape voxelshape = state.getCollisionShape(this, pos, context);

        return voxelshape.isEmpty() || this.isUnobstructed((Entity) null, voxelshape.move((Vec3i) pos));
    }

    default boolean isUnobstructed(Entity ignore) {
        return this.isUnobstructed(ignore, Shapes.create(ignore.getBoundingBox()));
    }

    default boolean noCollision(AABB aabb) {
        return this.noCollision((Entity) null, aabb);
    }

    default boolean noCollision(Entity source) {
        return this.noCollision(source, source.getBoundingBox());
    }

    default boolean noCollision(@Nullable Entity entity, AABB aabb) {
        return this.noCollision(entity, aabb, false);
    }

    default boolean noCollision(@Nullable Entity entity, AABB aabb, boolean alwaysCollideWithFluids) {
        return this.noBlockCollision(entity, aabb, alwaysCollideWithFluids) && this.noEntityCollision(entity, aabb) && this.noBorderCollision(entity, aabb);
    }

    default boolean noBlockCollision(@Nullable Entity entity, AABB aabb) {
        return this.noBlockCollision(entity, aabb, false);
    }

    default boolean noBlockCollision(@Nullable Entity entity, AABB aabb, boolean alwaysCollideWithFluids) {
        for (VoxelShape voxelshape : alwaysCollideWithFluids ? this.getBlockAndLiquidCollisions(entity, aabb) : this.getBlockCollisions(entity, aabb)) {
            if (!voxelshape.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    default boolean noEntityCollision(@Nullable Entity entity, AABB aabb) {
        return this.getEntityCollisions(entity, aabb).isEmpty();
    }

    default boolean noBorderCollision(@Nullable Entity entity, AABB aabb) {
        if (entity == null) {
            return true;
        } else {
            VoxelShape voxelshape = this.borderCollision(entity, aabb);

            return voxelshape == null || !Shapes.joinIsNotEmpty(voxelshape, Shapes.create(aabb), BooleanOp.AND);
        }
    }

    List<VoxelShape> getEntityCollisions(@Nullable Entity source, AABB testArea);

    default Iterable<VoxelShape> getCollisions(@Nullable Entity source, AABB box) {
        List<VoxelShape> list = this.getEntityCollisions(source, box);
        Iterable<VoxelShape> iterable = this.getBlockCollisions(source, box);

        return list.isEmpty() ? iterable : Iterables.concat(list, iterable);
    }

    default Iterable<VoxelShape> getPreMoveCollisions(@Nullable Entity source, AABB box, Vec3 oldPos) {
        List<VoxelShape> list = this.getEntityCollisions(source, box);
        Iterable<VoxelShape> iterable = this.getBlockCollisionsFromContext(CollisionContext.withPosition(source, oldPos.y), box);

        return list.isEmpty() ? iterable : Iterables.concat(list, iterable);
    }

    default Iterable<VoxelShape> getBlockCollisions(@Nullable Entity source, AABB box) {
        return this.getBlockCollisionsFromContext(source == null ? CollisionContext.empty() : CollisionContext.of(source), box);
    }

    default Iterable<VoxelShape> getBlockAndLiquidCollisions(@Nullable Entity source, AABB box) {
        return this.getBlockCollisionsFromContext(source == null ? CollisionContext.emptyWithFluidCollisions() : CollisionContext.of(source, true), box);
    }

    private Iterable<VoxelShape> getBlockCollisionsFromContext(CollisionContext source, AABB box) {
        return () -> {
            return new BlockCollisions(this, source, box, false, (blockpos_mutableblockpos, voxelshape) -> {
                return voxelshape;
            });
        };
    }

    private @Nullable VoxelShape borderCollision(Entity source, AABB box) {
        WorldBorder worldborder = this.getWorldBorder();

        return worldborder.isInsideCloseToBorder(source, box) ? worldborder.getCollisionShape() : null;
    }

    default BlockHitResult clipIncludingBorder(ClipContext c) {
        BlockHitResult blockhitresult = this.clip(c);
        WorldBorder worldborder = this.getWorldBorder();

        if (worldborder.isWithinBounds(c.getFrom()) && !worldborder.isWithinBounds(blockhitresult.getLocation())) {
            Vec3 vec3 = blockhitresult.getLocation().subtract(c.getFrom());
            Direction direction = Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z);
            Vec3 vec31 = worldborder.clampVec3ToBound(blockhitresult.getLocation());

            return new BlockHitResult(vec31, direction, BlockPos.containing(vec31), false, true);
        } else {
            return blockhitresult;
        }
    }

    default boolean collidesWithSuffocatingBlock(@Nullable Entity source, AABB box) {
        BlockCollisions<VoxelShape> blockcollisions = new BlockCollisions<VoxelShape>(this, source, box, true, (blockpos_mutableblockpos, voxelshape) -> {
            return voxelshape;
        });

        while (blockcollisions.hasNext()) {
            if (!((VoxelShape) blockcollisions.next()).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    default Optional<BlockPos> findSupportingBlock(Entity source, AABB box) {
        BlockPos blockpos = null;
        double d0 = Double.MAX_VALUE;
        BlockCollisions<BlockPos> blockcollisions = new BlockCollisions<BlockPos>(this, source, box, false, (blockpos_mutableblockpos, voxelshape) -> {
            return blockpos_mutableblockpos;
        });

        while (blockcollisions.hasNext()) {
            BlockPos blockpos1 = (BlockPos) blockcollisions.next();
            double d1 = blockpos1.distToCenterSqr(source.position());

            if (d1 < d0 || d1 == d0 && (blockpos == null || blockpos.compareTo((Vec3i) blockpos1) < 0)) {
                blockpos = blockpos1.immutable();
                d0 = d1;
            }
        }

        return Optional.ofNullable(blockpos);
    }

    default Optional<Vec3> findFreePosition(@Nullable Entity source, VoxelShape allowedCenters, Vec3 preferredCenter, double sizeX, double sizeY, double sizeZ) {
        if (allowedCenters.isEmpty()) {
            return Optional.empty();
        } else {
            AABB aabb = allowedCenters.bounds().inflate(sizeX, sizeY, sizeZ);
            VoxelShape voxelshape1 = (VoxelShape) StreamSupport.stream(this.getBlockCollisions(source, aabb).spliterator(), false).filter((voxelshape2) -> {
                return this.getWorldBorder() == null || this.getWorldBorder().isWithinBounds(voxelshape2.bounds());
            }).flatMap((voxelshape2) -> {
                return voxelshape2.toAabbs().stream();
            }).map((aabb1) -> {
                return aabb1.inflate(sizeX / 2.0D, sizeY / 2.0D, sizeZ / 2.0D);
            }).map(Shapes::create).reduce(Shapes.empty(), Shapes::or);
            VoxelShape voxelshape2 = Shapes.join(allowedCenters, voxelshape1, BooleanOp.ONLY_FIRST);

            return voxelshape2.closestPointTo(preferredCenter);
        }
    }
}
