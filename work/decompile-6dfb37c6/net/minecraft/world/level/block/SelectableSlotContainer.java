package net.minecraft.world.level.block;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public interface SelectableSlotContainer {

    int getRows();

    int getColumns();

    default OptionalInt getHitSlot(BlockHitResult hitResult, Direction blockFacing) {
        return (OptionalInt) getRelativeHitCoordinatesForBlockFace(hitResult, blockFacing).map((vec2) -> {
            int i = getSection(1.0F - vec2.y, this.getRows());
            int j = getSection(vec2.x, this.getColumns());

            return OptionalInt.of(j + i * this.getColumns());
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hitResult, Direction blockFacing) {
        Direction direction1 = hitResult.getDirection();

        if (blockFacing != direction1) {
            return Optional.empty();
        } else {
            BlockPos blockpos = hitResult.getBlockPos().relative(direction1);
            Vec3 vec3 = hitResult.getLocation().subtract((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
            double d0 = vec3.x();
            double d1 = vec3.y();
            double d2 = vec3.z();
            Optional optional;

            switch (direction1) {
                case NORTH:
                    optional = Optional.of(new Vec2((float) (1.0D - d0), (float) d1));
                    break;
                case SOUTH:
                    optional = Optional.of(new Vec2((float) d0, (float) d1));
                    break;
                case WEST:
                    optional = Optional.of(new Vec2((float) d2, (float) d1));
                    break;
                case EAST:
                    optional = Optional.of(new Vec2((float) (1.0D - d2), (float) d1));
                    break;
                case DOWN:
                case UP:
                    optional = Optional.empty();
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return optional;
        }
    }

    static int getSection(float relativeCoordinate, int maxSections) {
        float f1 = relativeCoordinate * 16.0F;
        float f2 = 16.0F / (float) maxSections;

        return Mth.clamp(Mth.floor(f1 / f2), 0, maxSections - 1);
    }
}
