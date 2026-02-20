package net.minecraft.core;

import com.google.common.base.MoreObjects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.stream.IntStream;
import javax.annotation.concurrent.Immutable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector3i;

@Immutable
public class Vec3i implements Comparable<Vec3i> {

    public static final Codec<Vec3i> CODEC = Codec.INT_STREAM.comapFlatMap((intstream) -> {
        return Util.fixedSize(intstream, 3).map((aint) -> {
            return new Vec3i(aint[0], aint[1], aint[2]);
        });
    }, (vec3i) -> {
        return IntStream.of(new int[]{vec3i.getX(), vec3i.getY(), vec3i.getZ()});
    });
    public static final StreamCodec<ByteBuf, Vec3i> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Vec3i::getX, ByteBufCodecs.VAR_INT, Vec3i::getY, ByteBufCodecs.VAR_INT, Vec3i::getZ, Vec3i::new);
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    private int x;
    private int y;
    private int z;

    public static Codec<Vec3i> offsetCodec(int maxOffsetPerAxis) {
        return Vec3i.CODEC.validate((vec3i) -> {
            return Math.abs(vec3i.getX()) < maxOffsetPerAxis && Math.abs(vec3i.getY()) < maxOffsetPerAxis && Math.abs(vec3i.getZ()) < maxOffsetPerAxis ? DataResult.success(vec3i) : DataResult.error(() -> {
                return "Position out of range, expected at most " + maxOffsetPerAxis + ": " + String.valueOf(vec3i);
            });
        });
    }

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Vec3i)) {
            return false;
        } else {
            Vec3i vec3i = (Vec3i) o;

            return this.getX() == vec3i.getX() && this.getY() == vec3i.getY() && this.getZ() == vec3i.getZ();
        }
    }

    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    public int compareTo(Vec3i pos) {
        return this.getY() == pos.getY() ? (this.getZ() == pos.getZ() ? this.getX() - pos.getX() : this.getZ() - pos.getZ()) : this.getY() - pos.getY();
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    protected Vec3i setX(int x) {
        this.x = x;
        return this;
    }

    protected Vec3i setY(int y) {
        this.y = y;
        return this;
    }

    protected Vec3i setZ(int z) {
        this.z = z;
        return this;
    }

    public Vec3i offset(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new Vec3i(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3i offset(Vec3i vec) {
        return this.offset(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vec3i subtract(Vec3i vec) {
        return this.offset(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    public Vec3i multiply(int scale) {
        return scale == 1 ? this : (scale == 0 ? Vec3i.ZERO : new Vec3i(this.getX() * scale, this.getY() * scale, this.getZ() * scale));
    }

    public Vec3i multiply(int xScale, int yScale, int zScale) {
        return new Vec3i(this.getX() * xScale, this.getY() * yScale, this.getZ() * zScale);
    }

    public Vec3i above() {
        return this.above(1);
    }

    public Vec3i above(int steps) {
        return this.relative(Direction.UP, steps);
    }

    public Vec3i below() {
        return this.below(1);
    }

    public Vec3i below(int steps) {
        return this.relative(Direction.DOWN, steps);
    }

    public Vec3i north() {
        return this.north(1);
    }

    public Vec3i north(int steps) {
        return this.relative(Direction.NORTH, steps);
    }

    public Vec3i south() {
        return this.south(1);
    }

    public Vec3i south(int steps) {
        return this.relative(Direction.SOUTH, steps);
    }

    public Vec3i west() {
        return this.west(1);
    }

    public Vec3i west(int steps) {
        return this.relative(Direction.WEST, steps);
    }

    public Vec3i east() {
        return this.east(1);
    }

    public Vec3i east(int steps) {
        return this.relative(Direction.EAST, steps);
    }

    public Vec3i relative(Direction direction) {
        return this.relative(direction, 1);
    }

    public Vec3i relative(Direction direction, int steps) {
        return steps == 0 ? this : new Vec3i(this.getX() + direction.getStepX() * steps, this.getY() + direction.getStepY() * steps, this.getZ() + direction.getStepZ() * steps);
    }

    public Vec3i relative(Direction.Axis axis, int steps) {
        if (steps == 0) {
            return this;
        } else {
            int j = axis == Direction.Axis.X ? steps : 0;
            int k = axis == Direction.Axis.Y ? steps : 0;
            int l = axis == Direction.Axis.Z ? steps : 0;

            return new Vec3i(this.getX() + j, this.getY() + k, this.getZ() + l);
        }
    }

    public Vec3i cross(Vec3i upVector) {
        return new Vec3i(this.getY() * upVector.getZ() - this.getZ() * upVector.getY(), this.getZ() * upVector.getX() - this.getX() * upVector.getZ(), this.getX() * upVector.getY() - this.getY() * upVector.getX());
    }

    public boolean closerThan(Vec3i pos, double distance) {
        return this.distSqr(pos) < Mth.square(distance);
    }

    public boolean closerToCenterThan(Position pos, double distance) {
        return this.distToCenterSqr(pos) < Mth.square(distance);
    }

    public double distSqr(Vec3i pos) {
        return this.distToLowCornerSqr((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
    }

    public double distToCenterSqr(Position pos) {
        return this.distToCenterSqr(pos.x(), pos.y(), pos.z());
    }

    public double distToCenterSqr(double x, double y, double z) {
        double d3 = (double) this.getX() + 0.5D - x;
        double d4 = (double) this.getY() + 0.5D - y;
        double d5 = (double) this.getZ() + 0.5D - z;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distToLowCornerSqr(double x, double y, double z) {
        double d3 = (double) this.getX() - x;
        double d4 = (double) this.getY() - y;
        double d5 = (double) this.getZ() - z;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public int distManhattan(Vec3i pos) {
        float f = (float) Math.abs(pos.getX() - this.getX());
        float f1 = (float) Math.abs(pos.getY() - this.getY());
        float f2 = (float) Math.abs(pos.getZ() - this.getZ());

        return (int) (f + f1 + f2);
    }

    public int distChessboard(Vec3i pos) {
        int i = Math.abs(this.getX() - pos.getX());
        int j = Math.abs(this.getY() - pos.getY());
        int k = Math.abs(this.getZ() - pos.getZ());

        return Math.max(Math.max(i, j), k);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vector3i toMutable() {
        return new Vector3i(this.x, this.y, this.z);
    }

    public String toString() {
        return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }

    public String toShortString() {
        int i = this.getX();

        return i + ", " + this.getY() + ", " + this.getZ();
    }
}
