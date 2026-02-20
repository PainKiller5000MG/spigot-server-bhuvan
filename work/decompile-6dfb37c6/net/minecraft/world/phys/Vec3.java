package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Vec3 implements Position {

    public static final Codec<Vec3> CODEC = Codec.DOUBLE.listOf().comapFlatMap((list) -> {
        return Util.fixedSize(list, 3).map((list1) -> {
            return new Vec3((Double) list1.get(0), (Double) list1.get(1), (Double) list1.get(2));
        });
    }, (vec3) -> {
        return List.of(vec3.x(), vec3.y(), vec3.z());
    });
    public static final StreamCodec<ByteBuf, Vec3> STREAM_CODEC = new StreamCodec<ByteBuf, Vec3>() {
        public Vec3 decode(ByteBuf input) {
            return FriendlyByteBuf.readVec3(input);
        }

        public void encode(ByteBuf output, Vec3 value) {
            FriendlyByteBuf.writeVec3(output, value);
        }
    };
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);
    public static final Vec3 X_AXIS = new Vec3(1.0D, 0.0D, 0.0D);
    public static final Vec3 Y_AXIS = new Vec3(0.0D, 1.0D, 0.0D);
    public static final Vec3 Z_AXIS = new Vec3(0.0D, 0.0D, 1.0D);
    public final double x;
    public final double y;
    public final double z;

    public static Vec3 atLowerCornerOf(Vec3i pos) {
        return new Vec3((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i pos, double x, double y, double z) {
        return new Vec3((double) pos.getX() + x, (double) pos.getY() + y, (double) pos.getZ() + z);
    }

    public static Vec3 atCenterOf(Vec3i pos) {
        return atLowerCornerWithOffset(pos, 0.5D, 0.5D, 0.5D);
    }

    public static Vec3 atBottomCenterOf(Vec3i pos) {
        return atLowerCornerWithOffset(pos, 0.5D, 0.0D, 0.5D);
    }

    public static Vec3 upFromBottomCenterOf(Vec3i pos, double yOffset) {
        return atLowerCornerWithOffset(pos, 0.5D, yOffset, 0.5D);
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3fc vec) {
        this((double) vec.x(), (double) vec.y(), (double) vec.z());
    }

    public Vec3(Vec3i vec) {
        this((double) vec.getX(), (double) vec.getY(), (double) vec.getZ());
    }

    public Vec3 vectorTo(Vec3 vec) {
        return new Vec3(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3 normalize() {
        double d0 = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);

        return d0 < (double) 1.0E-5F ? Vec3.ZERO : new Vec3(this.x / d0, this.y / d0, this.z / d0);
    }

    public double dot(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3 cross(Vec3 vec) {
        return new Vec3(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3 subtract(Vec3 vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3 subtract(double value) {
        return this.subtract(value, value, value);
    }

    public Vec3 subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3 add(double value) {
        return this.add(value, value, value);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    public boolean closerThan(Position pos, double distance) {
        return this.distanceToSqr(pos.x(), pos.y(), pos.z()) < distance * distance;
    }

    public double distanceTo(Vec3 vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;

        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    public double distanceToSqr(Vec3 vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double distanceToSqr(double x, double y, double z) {
        double d3 = x - this.x;
        double d4 = y - this.y;
        double d5 = z - this.z;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public boolean closerThan(Vec3 vec, double distanceXZ, double distanceY) {
        double d2 = vec.x() - this.x;
        double d3 = vec.y() - this.y;
        double d4 = vec.z() - this.z;

        return Mth.lengthSquared(d2, d4) < Mth.square(distanceXZ) && Math.abs(d3) < distanceY;
    }

    public Vec3 scale(double scale) {
        return this.multiply(scale, scale, scale);
    }

    public Vec3 reverse() {
        return this.scale(-1.0D);
    }

    public Vec3 multiply(Vec3 scale) {
        return this.multiply(scale.x, scale.y, scale.z);
    }

    public Vec3 multiply(double xScale, double yScale, double zScale) {
        return new Vec3(this.x * xScale, this.y * yScale, this.z * zScale);
    }

    public Vec3 horizontal() {
        return new Vec3(this.x, 0.0D, this.z);
    }

    public Vec3 offsetRandom(RandomSource random, float offset) {
        return this.add((double) ((random.nextFloat() - 0.5F) * offset), (double) ((random.nextFloat() - 0.5F) * offset), (double) ((random.nextFloat() - 0.5F) * offset));
    }

    public Vec3 offsetRandomXZ(RandomSource random, float offset) {
        return this.add((double) ((random.nextFloat() - 0.5F) * offset), 0.0D, (double) ((random.nextFloat() - 0.5F) * offset));
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Vec3)) {
            return false;
        } else {
            Vec3 vec3 = (Vec3) o;

            return Double.compare(vec3.x, this.x) != 0 ? false : (Double.compare(vec3.y, this.y) != 0 ? false : Double.compare(vec3.z, this.z) == 0);
        }
    }

    public int hashCode() {
        long i = Double.doubleToLongBits(this.x);
        int j = (int) (i ^ i >>> 32);

        i = Double.doubleToLongBits(this.y);
        j = 31 * j + (int) (i ^ i >>> 32);
        i = Double.doubleToLongBits(this.z);
        j = 31 * j + (int) (i ^ i >>> 32);
        return j;
    }

    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3 lerp(Vec3 vec, double a) {
        return new Vec3(Mth.lerp(a, this.x, vec.x), Mth.lerp(a, this.y, vec.y), Mth.lerp(a, this.z, vec.z));
    }

    public Vec3 xRot(float radians) {
        float f1 = Mth.cos((double) radians);
        float f2 = Mth.sin((double) radians);
        double d0 = this.x;
        double d1 = this.y * (double) f1 + this.z * (double) f2;
        double d2 = this.z * (double) f1 - this.y * (double) f2;

        return new Vec3(d0, d1, d2);
    }

    public Vec3 yRot(float radians) {
        float f1 = Mth.cos((double) radians);
        float f2 = Mth.sin((double) radians);
        double d0 = this.x * (double) f1 + this.z * (double) f2;
        double d1 = this.y;
        double d2 = this.z * (double) f1 - this.x * (double) f2;

        return new Vec3(d0, d1, d2);
    }

    public Vec3 zRot(float radians) {
        float f1 = Mth.cos((double) radians);
        float f2 = Mth.sin((double) radians);
        double d0 = this.x * (double) f1 + this.y * (double) f2;
        double d1 = this.y * (double) f1 - this.x * (double) f2;
        double d2 = this.z;

        return new Vec3(d0, d1, d2);
    }

    public Vec3 rotateClockwise90() {
        return new Vec3(-this.z, this.y, this.x);
    }

    public static Vec3 directionFromRotation(Vec2 rotation) {
        return directionFromRotation(rotation.x, rotation.y);
    }

    public static Vec3 directionFromRotation(float rotX, float rotY) {
        float f2 = Mth.cos((double) (-rotY * ((float) Math.PI / 180F) - (float) Math.PI));
        float f3 = Mth.sin((double) (-rotY * ((float) Math.PI / 180F) - (float) Math.PI));
        float f4 = -Mth.cos((double) (-rotX * ((float) Math.PI / 180F)));
        float f5 = Mth.sin((double) (-rotX * ((float) Math.PI / 180F)));

        return new Vec3((double) (f3 * f4), (double) f5, (double) (f2 * f4));
    }

    public Vec2 rotation() {
        float f = (float) Math.atan2(-this.x, this.z) * (180F / (float) Math.PI);
        float f1 = (float) Math.asin(-this.y / Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z)) * (180F / (float) Math.PI);

        return new Vec2(f1, f);
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d0 = axes.contains(Direction.Axis.X) ? (double) Mth.floor(this.x) : this.x;
        double d1 = axes.contains(Direction.Axis.Y) ? (double) Mth.floor(this.y) : this.y;
        double d2 = axes.contains(Direction.Axis.Z) ? (double) Mth.floor(this.z) : this.z;

        return new Vec3(d0, d1, d2);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double value) {
        double d1 = axis == Direction.Axis.X ? value : this.x;
        double d2 = axis == Direction.Axis.Y ? value : this.y;
        double d3 = axis == Direction.Axis.Z ? value : this.z;

        return new Vec3(d1, d2, d3);
    }

    public Vec3 relative(Direction direction, double distance) {
        Vec3i vec3i = direction.getUnitVec3i();

        return new Vec3(this.x + distance * (double) vec3i.getX(), this.y + distance * (double) vec3i.getY(), this.z + distance * (double) vec3i.getZ());
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float) this.x, (float) this.y, (float) this.z);
    }

    public Vec3 projectedOn(Vec3 onto) {
        return onto.lengthSqr() == 0.0D ? onto : onto.scale(this.dot(onto)).scale(1.0D / onto.lengthSqr());
    }

    public static Vec3 applyLocalCoordinatesToRotation(Vec2 rotation, Vec3 direction) {
        float f = Mth.cos((double) ((rotation.y + 90.0F) * ((float) Math.PI / 180F)));
        float f1 = Mth.sin((double) ((rotation.y + 90.0F) * ((float) Math.PI / 180F)));
        float f2 = Mth.cos((double) (-rotation.x * ((float) Math.PI / 180F)));
        float f3 = Mth.sin((double) (-rotation.x * ((float) Math.PI / 180F)));
        float f4 = Mth.cos((double) ((-rotation.x + 90.0F) * ((float) Math.PI / 180F)));
        float f5 = Mth.sin((double) ((-rotation.x + 90.0F) * ((float) Math.PI / 180F)));
        Vec3 vec31 = new Vec3((double) (f * f2), (double) f3, (double) (f1 * f2));
        Vec3 vec32 = new Vec3((double) (f * f4), (double) f5, (double) (f1 * f4));
        Vec3 vec33 = vec31.cross(vec32).scale(-1.0D);
        double d0 = vec31.x * direction.z + vec32.x * direction.y + vec33.x * direction.x;
        double d1 = vec31.y * direction.z + vec32.y * direction.y + vec33.y * direction.x;
        double d2 = vec31.z * direction.z + vec32.z * direction.y + vec33.z * direction.x;

        return new Vec3(d0, d1, d2);
    }

    public Vec3 addLocalCoordinates(Vec3 direction) {
        return applyLocalCoordinatesToRotation(this.rotation(), direction);
    }

    public boolean isFinite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
    }
}
