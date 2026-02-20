package net.minecraft.world.phys;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class AABB {

    private static final double EPSILON = 1.0E-7D;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public AABB(BlockPos pos) {
        this((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1));
    }

    public AABB(Vec3 begin, Vec3 end) {
        this(begin.x, begin.y, begin.z, end.x, end.y, end.z);
    }

    public static AABB of(BoundingBox box) {
        return new AABB((double) box.minX(), (double) box.minY(), (double) box.minZ(), (double) (box.maxX() + 1), (double) (box.maxY() + 1), (double) (box.maxZ() + 1));
    }

    public static AABB unitCubeFromLowerCorner(Vec3 pos) {
        return new AABB(pos.x, pos.y, pos.z, pos.x + 1.0D, pos.y + 1.0D, pos.z + 1.0D);
    }

    public static AABB encapsulatingFullBlocks(BlockPos pos0, BlockPos pos1) {
        return new AABB((double) Math.min(pos0.getX(), pos1.getX()), (double) Math.min(pos0.getY(), pos1.getY()), (double) Math.min(pos0.getZ(), pos1.getZ()), (double) (Math.max(pos0.getX(), pos1.getX()) + 1), (double) (Math.max(pos0.getY(), pos1.getY()) + 1), (double) (Math.max(pos0.getZ(), pos1.getZ()) + 1));
    }

    public AABB setMinX(double minX) {
        return new AABB(minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double minY) {
        return new AABB(this.minX, minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double minZ) {
        return new AABB(this.minX, this.minY, minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double maxX) {
        return new AABB(this.minX, this.minY, this.minZ, maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double maxY) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, maxY, this.maxZ);
    }

    public AABB setMaxZ(double maxZ) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, maxZ);
    }

    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof AABB)) {
            return false;
        } else {
            AABB aabb = (AABB) o;

            return Double.compare(aabb.minX, this.minX) != 0 ? false : (Double.compare(aabb.minY, this.minY) != 0 ? false : (Double.compare(aabb.minZ, this.minZ) != 0 ? false : (Double.compare(aabb.maxX, this.maxX) != 0 ? false : (Double.compare(aabb.maxY, this.maxY) != 0 ? false : Double.compare(aabb.maxZ, this.maxZ) == 0))));
        }
    }

    public int hashCode() {
        long i = Double.doubleToLongBits(this.minX);
        int j = (int) (i ^ i >>> 32);

        i = Double.doubleToLongBits(this.minY);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.minZ);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxX);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxY);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.maxZ);
        j = 31 * j + (int) (i ^ i >>> 32);
        return j;
    }

    public AABB contract(double xa, double ya, double za) {
        double d3 = this.minX;
        double d4 = this.minY;
        double d5 = this.minZ;
        double d6 = this.maxX;
        double d7 = this.maxY;
        double d8 = this.maxZ;

        if (xa < 0.0D) {
            d3 -= xa;
        } else if (xa > 0.0D) {
            d6 -= xa;
        }

        if (ya < 0.0D) {
            d4 -= ya;
        } else if (ya > 0.0D) {
            d7 -= ya;
        }

        if (za < 0.0D) {
            d5 -= za;
        } else if (za > 0.0D) {
            d8 -= za;
        }

        return new AABB(d3, d4, d5, d6, d7, d8);
    }

    public AABB expandTowards(Vec3 delta) {
        return this.expandTowards(delta.x, delta.y, delta.z);
    }

    public AABB expandTowards(double xa, double ya, double za) {
        double d3 = this.minX;
        double d4 = this.minY;
        double d5 = this.minZ;
        double d6 = this.maxX;
        double d7 = this.maxY;
        double d8 = this.maxZ;

        if (xa < 0.0D) {
            d3 += xa;
        } else if (xa > 0.0D) {
            d6 += xa;
        }

        if (ya < 0.0D) {
            d4 += ya;
        } else if (ya > 0.0D) {
            d7 += ya;
        }

        if (za < 0.0D) {
            d5 += za;
        } else if (za > 0.0D) {
            d8 += za;
        }

        return new AABB(d3, d4, d5, d6, d7, d8);
    }

    public AABB inflate(double xAdd, double yAdd, double zAdd) {
        double d3 = this.minX - xAdd;
        double d4 = this.minY - yAdd;
        double d5 = this.minZ - zAdd;
        double d6 = this.maxX + xAdd;
        double d7 = this.maxY + yAdd;
        double d8 = this.maxZ + zAdd;

        return new AABB(d3, d4, d5, d6, d7, d8);
    }

    public AABB inflate(double amountToAddInAllDirections) {
        return this.inflate(amountToAddInAllDirections, amountToAddInAllDirections, amountToAddInAllDirections);
    }

    public AABB intersect(AABB other) {
        double d0 = Math.max(this.minX, other.minX);
        double d1 = Math.max(this.minY, other.minY);
        double d2 = Math.max(this.minZ, other.minZ);
        double d3 = Math.min(this.maxX, other.maxX);
        double d4 = Math.min(this.maxY, other.maxY);
        double d5 = Math.min(this.maxZ, other.maxZ);

        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    public AABB minmax(AABB other) {
        double d0 = Math.min(this.minX, other.minX);
        double d1 = Math.min(this.minY, other.minY);
        double d2 = Math.min(this.minZ, other.minZ);
        double d3 = Math.max(this.maxX, other.maxX);
        double d4 = Math.max(this.maxY, other.maxY);
        double d5 = Math.max(this.maxZ, other.maxZ);

        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    public AABB move(double xa, double ya, double za) {
        return new AABB(this.minX + xa, this.minY + ya, this.minZ + za, this.maxX + xa, this.maxY + ya, this.maxZ + za);
    }

    public AABB move(BlockPos pos) {
        return new AABB(this.minX + (double) pos.getX(), this.minY + (double) pos.getY(), this.minZ + (double) pos.getZ(), this.maxX + (double) pos.getX(), this.maxY + (double) pos.getY(), this.maxZ + (double) pos.getZ());
    }

    public AABB move(Vec3 pos) {
        return this.move(pos.x, pos.y, pos.z);
    }

    public AABB move(Vector3f pos) {
        return this.move((double) pos.x, (double) pos.y, (double) pos.z);
    }

    public boolean intersects(AABB aabb) {
        return this.intersects(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.minX < maxX && this.maxX > minX && this.minY < maxY && this.maxY > minY && this.minZ < maxZ && this.maxZ > minZ;
    }

    public boolean intersects(Vec3 min, Vec3 max) {
        return this.intersects(Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z), Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z));
    }

    public boolean intersects(BlockPos pos) {
        return this.intersects((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1));
    }

    public boolean contains(Vec3 vec) {
        return this.contains(vec.x, vec.y, vec.z);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() {
        double d0 = this.getXsize();
        double d1 = this.getYsize();
        double d2 = this.getZsize();

        return (d0 + d1 + d2) / 3.0D;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double xSubstract, double ySubtract, double zSubtract) {
        return this.inflate(-xSubstract, -ySubtract, -zSubtract);
    }

    public AABB deflate(double amount) {
        return this.inflate(-amount);
    }

    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        return clip(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, from, to);
    }

    public static Optional<Vec3> clip(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3 from, Vec3 to) {
        double[] adouble = new double[]{1.0D};
        double d6 = to.x - from.x;
        double d7 = to.y - from.y;
        double d8 = to.z - from.z;
        Direction direction = getDirection(minX, minY, minZ, maxX, maxY, maxZ, from, adouble, (Direction) null, d6, d7, d8);

        if (direction == null) {
            return Optional.empty();
        } else {
            double d9 = adouble[0];

            return Optional.of(from.add(d9 * d6, d9 * d7, d9 * d8));
        }
    }

    public static @Nullable BlockHitResult clip(Iterable<AABB> aabBs, Vec3 from, Vec3 to, BlockPos pos) {
        double[] adouble = new double[]{1.0D};
        Direction direction = null;
        double d0 = to.x - from.x;
        double d1 = to.y - from.y;
        double d2 = to.z - from.z;

        for (AABB aabb : aabBs) {
            direction = getDirection(aabb.move(pos), from, adouble, direction, d0, d1, d2);
        }

        if (direction == null) {
            return null;
        } else {
            double d3 = adouble[0];

            return new BlockHitResult(from.add(d3 * d0, d3 * d1, d3 * d2), direction, pos, false);
        }
    }

    private static @Nullable Direction getDirection(AABB aabb, Vec3 from, double[] scaleReference, @Nullable Direction direction, double dx, double dy, double dz) {
        return getDirection(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ, from, scaleReference, direction, dx, dy, dz);
    }

    private static @Nullable Direction getDirection(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3 from, double[] scaleReference, @Nullable Direction direction, double dx, double dy, double dz) {
        if (dx > 1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dx, dy, dz, minX, minY, maxY, minZ, maxZ, Direction.WEST, from.x, from.y, from.z);
        } else if (dx < -1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, Direction.EAST, from.x, from.y, from.z);
        }

        if (dy > 1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dy, dz, dx, minY, minZ, maxZ, minX, maxX, Direction.DOWN, from.y, from.z, from.x);
        } else if (dy < -1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, Direction.UP, from.y, from.z, from.x);
        }

        if (dz > 1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dz, dx, dy, minZ, minX, maxX, minY, maxY, Direction.NORTH, from.z, from.x, from.y);
        } else if (dz < -1.0E-7D) {
            direction = clipPoint(scaleReference, direction, dz, dx, dy, maxZ, minX, maxX, minY, maxY, Direction.SOUTH, from.z, from.x, from.y);
        }

        return direction;
    }

    private static @Nullable Direction clipPoint(double[] scaleReference, @Nullable Direction direction, double da, double db, double dc, double point, double minB, double maxB, double minC, double maxC, Direction newDirection, double fromA, double fromB, double fromC) {
        double d11 = (point - fromA) / da;
        double d12 = fromB + d11 * db;
        double d13 = fromC + d11 * dc;

        if (0.0D < d11 && d11 < scaleReference[0] && minB - 1.0E-7D < d12 && d12 < maxB + 1.0E-7D && minC - 1.0E-7D < d13 && d13 < maxC + 1.0E-7D) {
            scaleReference[0] = d11;
            return newDirection;
        } else {
            return direction;
        }
    }

    public boolean collidedAlongVector(Vec3 vector, List<AABB> aabbs) {
        Vec3 vec31 = this.getCenter();
        Vec3 vec32 = vec31.add(vector);

        for (AABB aabb : aabbs) {
            AABB aabb1 = aabb.inflate(this.getXsize() * 0.5D - 1.0E-7D, this.getYsize() * 0.5D - 1.0E-7D, this.getZsize() * 0.5D - 1.0E-7D);

            if (aabb1.contains(vec32) || aabb1.contains(vec31)) {
                return true;
            }

            if (aabb1.clip(vec31, vec32).isPresent()) {
                return true;
            }
        }

        return false;
    }

    public double distanceToSqr(Vec3 point) {
        double d0 = Math.max(Math.max(this.minX - point.x, point.x - this.maxX), 0.0D);
        double d1 = Math.max(Math.max(this.minY - point.y, point.y - this.maxY), 0.0D);
        double d2 = Math.max(Math.max(this.minZ - point.z, point.z - this.maxZ), 0.0D);

        return Mth.lengthSquared(d0, d1, d2);
    }

    public double distanceToSqr(AABB boundingBox) {
        double d0 = Math.max(Math.max(this.minX - boundingBox.maxX, boundingBox.minX - this.maxX), 0.0D);
        double d1 = Math.max(Math.max(this.minY - boundingBox.maxY, boundingBox.minY - this.maxY), 0.0D);
        double d2 = Math.max(Math.max(this.minZ - boundingBox.maxZ, boundingBox.minZ - this.maxZ), 0.0D);

        return Mth.lengthSquared(d0, d1, d2);
    }

    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5D, this.minX, this.maxX), Mth.lerp(0.5D, this.minY, this.maxY), Mth.lerp(0.5D, this.minZ, this.maxZ));
    }

    public Vec3 getBottomCenter() {
        return new Vec3(Mth.lerp(0.5D, this.minX, this.maxX), this.minY, Mth.lerp(0.5D, this.minZ, this.maxZ));
    }

    public Vec3 getMinPosition() {
        return new Vec3(this.minX, this.minY, this.minZ);
    }

    public Vec3 getMaxPosition() {
        return new Vec3(this.maxX, this.maxY, this.maxZ);
    }

    public static AABB ofSize(Vec3 center, double sizeX, double sizeY, double sizeZ) {
        return new AABB(center.x - sizeX / 2.0D, center.y - sizeY / 2.0D, center.z - sizeZ / 2.0D, center.x + sizeX / 2.0D, center.y + sizeY / 2.0D, center.z + sizeZ / 2.0D);
    }

    public static class Builder {

        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        public Builder() {}

        public void include(Vector3fc v) {
            this.minX = Math.min(this.minX, v.x());
            this.minY = Math.min(this.minY, v.y());
            this.minZ = Math.min(this.minZ, v.z());
            this.maxX = Math.max(this.maxX, v.x());
            this.maxY = Math.max(this.maxY, v.y());
            this.maxZ = Math.max(this.maxZ, v.z());
        }

        public AABB build() {
            return new AABB((double) this.minX, (double) this.minY, (double) this.minZ, (double) this.maxX, (double) this.maxY, (double) this.maxZ);
        }
    }
}
