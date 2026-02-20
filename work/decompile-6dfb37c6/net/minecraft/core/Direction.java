package net.minecraft.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public enum Direction implements StringRepresentable {

    DOWN(0, 1, -1, "down", Direction.AxisDirection.NEGATIVE, Direction.Axis.Y, new Vec3i(0, -1, 0)), UP(1, 0, -1, "up", Direction.AxisDirection.POSITIVE, Direction.Axis.Y, new Vec3i(0, 1, 0)), NORTH(2, 3, 2, "north", Direction.AxisDirection.NEGATIVE, Direction.Axis.Z, new Vec3i(0, 0, -1)), SOUTH(3, 2, 0, "south", Direction.AxisDirection.POSITIVE, Direction.Axis.Z, new Vec3i(0, 0, 1)), WEST(4, 5, 1, "west", Direction.AxisDirection.NEGATIVE, Direction.Axis.X, new Vec3i(-1, 0, 0)), EAST(5, 4, 3, "east", Direction.AxisDirection.POSITIVE, Direction.Axis.X, new Vec3i(1, 0, 0));

    public static final StringRepresentable.EnumCodec<Direction> CODEC = StringRepresentable.<Direction>fromEnum(Direction::values);
    public static final Codec<Direction> VERTICAL_CODEC = Direction.CODEC.validate(Direction::verifyVertical);
    public static final IntFunction<Direction> BY_ID = ByIdMap.<Direction>continuous(Direction::get3DDataValue, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final StreamCodec<ByteBuf, Direction> STREAM_CODEC = ByteBufCodecs.idMapper(Direction.BY_ID, Direction::get3DDataValue);
    /** @deprecated */
    @Deprecated
    public static final Codec<Direction> LEGACY_ID_CODEC = Codec.BYTE.xmap(Direction::from3DDataValue, (direction) -> {
        return (byte) direction.get3DDataValue();
    });
    /** @deprecated */
    @Deprecated
    public static final Codec<Direction> LEGACY_ID_CODEC_2D = Codec.BYTE.xmap(Direction::from2DDataValue, (direction) -> {
        return (byte) direction.get2DDataValue();
    });
    private static final ImmutableList<Direction.Axis> YXZ_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);
    private static final ImmutableList<Direction.Axis> YZX_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X);
    private final int data3d;
    private final int oppositeIndex;
    private final int data2d;
    private final String name;
    private final Direction.Axis axis;
    private final Direction.AxisDirection axisDirection;
    private final Vec3i normal;
    private final Vec3 normalVec3;
    private final Vector3fc normalVec3f;
    private static final Direction[] VALUES = values();
    private static final Direction[] BY_3D_DATA = (Direction[]) Arrays.stream(Direction.VALUES).sorted(Comparator.comparingInt((direction) -> {
        return direction.data3d;
    })).toArray((i) -> {
        return new Direction[i];
    });
    private static final Direction[] BY_2D_DATA = (Direction[]) Arrays.stream(Direction.VALUES).filter((direction) -> {
        return direction.getAxis().isHorizontal();
    }).sorted(Comparator.comparingInt((direction) -> {
        return direction.data2d;
    })).toArray((i) -> {
        return new Direction[i];
    });

    private Direction(int data3d, int oppositeIndex, int data2d, String name, Direction.AxisDirection axisDirection, Direction.Axis axis, Vec3i normal) {
        this.data3d = data3d;
        this.data2d = data2d;
        this.oppositeIndex = oppositeIndex;
        this.name = name;
        this.axis = axis;
        this.axisDirection = axisDirection;
        this.normal = normal;
        this.normalVec3 = Vec3.atLowerCornerOf(normal);
        this.normalVec3f = new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
    }

    public static Direction[] orderedByNearest(Entity entity) {
        float f = entity.getViewXRot(1.0F) * ((float) Math.PI / 180F);
        float f1 = -entity.getViewYRot(1.0F) * ((float) Math.PI / 180F);
        float f2 = Mth.sin((double) f);
        float f3 = Mth.cos((double) f);
        float f4 = Mth.sin((double) f1);
        float f5 = Mth.cos((double) f1);
        boolean flag = f4 > 0.0F;
        boolean flag1 = f2 < 0.0F;
        boolean flag2 = f5 > 0.0F;
        float f6 = flag ? f4 : -f4;
        float f7 = flag1 ? -f2 : f2;
        float f8 = flag2 ? f5 : -f5;
        float f9 = f6 * f3;
        float f10 = f8 * f3;
        Direction direction = flag ? Direction.EAST : Direction.WEST;
        Direction direction1 = flag1 ? Direction.UP : Direction.DOWN;
        Direction direction2 = flag2 ? Direction.SOUTH : Direction.NORTH;

        return f6 > f8 ? (f7 > f9 ? makeDirectionArray(direction1, direction, direction2) : (f10 > f7 ? makeDirectionArray(direction, direction2, direction1) : makeDirectionArray(direction, direction1, direction2))) : (f7 > f10 ? makeDirectionArray(direction1, direction2, direction) : (f9 > f7 ? makeDirectionArray(direction2, direction, direction1) : makeDirectionArray(direction2, direction1, direction)));
    }

    private static Direction[] makeDirectionArray(Direction axis1, Direction axis2, Direction axis3) {
        return new Direction[]{axis1, axis2, axis3, axis3.getOpposite(), axis2.getOpposite(), axis1.getOpposite()};
    }

    public static Direction rotate(Matrix4fc matrix, Direction facing) {
        Vector3f vector3f = matrix.transformDirection(facing.normalVec3f, new Vector3f());

        return getApproximateNearest(vector3f.x(), vector3f.y(), vector3f.z());
    }

    public static Collection<Direction> allShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    public static Stream<Direction> stream() {
        return Stream.of(Direction.VALUES);
    }

    public static float getYRot(Direction direction) {
        float f;

        switch (direction.ordinal()) {
            case 2:
                f = 180.0F;
                break;
            case 3:
                f = 0.0F;
                break;
            case 4:
                f = 90.0F;
                break;
            case 5:
                f = -90.0F;
                break;
            default:
                throw new IllegalStateException("No y-Rot for vertical axis: " + String.valueOf(direction));
        }

        return f;
    }

    public Quaternionf getRotation() {
        Quaternionf quaternionf;

        switch (this.ordinal()) {
            case 0:
                quaternionf = (new Quaternionf()).rotationX((float) Math.PI);
                break;
            case 1:
                quaternionf = new Quaternionf();
                break;
            case 2:
                quaternionf = (new Quaternionf()).rotationXYZ(((float) Math.PI / 2F), 0.0F, (float) Math.PI);
                break;
            case 3:
                quaternionf = (new Quaternionf()).rotationX(((float) Math.PI / 2F));
                break;
            case 4:
                quaternionf = (new Quaternionf()).rotationXYZ(((float) Math.PI / 2F), 0.0F, ((float) Math.PI / 2F));
                break;
            case 5:
                quaternionf = (new Quaternionf()).rotationXYZ(((float) Math.PI / 2F), 0.0F, (-(float) Math.PI / 2F));
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return quaternionf;
    }

    public int get3DDataValue() {
        return this.data3d;
    }

    public int get2DDataValue() {
        return this.data2d;
    }

    public Direction.AxisDirection getAxisDirection() {
        return this.axisDirection;
    }

    public static Direction getFacingAxis(Entity entity, Direction.Axis axis) {
        Direction direction;

        switch (axis.ordinal()) {
            case 0:
                direction = Direction.EAST.isFacingAngle(entity.getViewYRot(1.0F)) ? Direction.EAST : Direction.WEST;
                break;
            case 1:
                direction = entity.getViewXRot(1.0F) < 0.0F ? Direction.UP : Direction.DOWN;
                break;
            case 2:
                direction = Direction.SOUTH.isFacingAngle(entity.getViewYRot(1.0F)) ? Direction.SOUTH : Direction.NORTH;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return direction;
    }

    public Direction getOpposite() {
        return from3DDataValue(this.oppositeIndex);
    }

    public Direction getClockWise(Direction.Axis axis) {
        Direction direction;

        switch (axis.ordinal()) {
            case 0:
                direction = this != Direction.WEST && this != Direction.EAST ? this.getClockWiseX() : this;
                break;
            case 1:
                direction = this != Direction.UP && this != Direction.DOWN ? this.getClockWise() : this;
                break;
            case 2:
                direction = this != Direction.NORTH && this != Direction.SOUTH ? this.getClockWiseZ() : this;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return direction;
    }

    public Direction getCounterClockWise(Direction.Axis axis) {
        Direction direction;

        switch (axis.ordinal()) {
            case 0:
                direction = this != Direction.WEST && this != Direction.EAST ? this.getCounterClockWiseX() : this;
                break;
            case 1:
                direction = this != Direction.UP && this != Direction.DOWN ? this.getCounterClockWise() : this;
                break;
            case 2:
                direction = this != Direction.NORTH && this != Direction.SOUTH ? this.getCounterClockWiseZ() : this;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return direction;
    }

    public Direction getClockWise() {
        Direction direction;

        switch (this.ordinal()) {
            case 2:
                direction = Direction.EAST;
                break;
            case 3:
                direction = Direction.WEST;
                break;
            case 4:
                direction = Direction.NORTH;
                break;
            case 5:
                direction = Direction.SOUTH;
                break;
            default:
                throw new IllegalStateException("Unable to get Y-rotated facing of " + String.valueOf(this));
        }

        return direction;
    }

    private Direction getClockWiseX() {
        Direction direction;

        switch (this.ordinal()) {
            case 0:
                direction = Direction.SOUTH;
                break;
            case 1:
                direction = Direction.NORTH;
                break;
            case 2:
                direction = Direction.DOWN;
                break;
            case 3:
                direction = Direction.UP;
                break;
            default:
                throw new IllegalStateException("Unable to get X-rotated facing of " + String.valueOf(this));
        }

        return direction;
    }

    private Direction getCounterClockWiseX() {
        Direction direction;

        switch (this.ordinal()) {
            case 0:
                direction = Direction.NORTH;
                break;
            case 1:
                direction = Direction.SOUTH;
                break;
            case 2:
                direction = Direction.UP;
                break;
            case 3:
                direction = Direction.DOWN;
                break;
            default:
                throw new IllegalStateException("Unable to get X-rotated facing of " + String.valueOf(this));
        }

        return direction;
    }

    private Direction getClockWiseZ() {
        Direction direction;

        switch (this.ordinal()) {
            case 0:
                direction = Direction.WEST;
                break;
            case 1:
                direction = Direction.EAST;
                break;
            case 2:
            case 3:
            default:
                throw new IllegalStateException("Unable to get Z-rotated facing of " + String.valueOf(this));
            case 4:
                direction = Direction.UP;
                break;
            case 5:
                direction = Direction.DOWN;
        }

        return direction;
    }

    private Direction getCounterClockWiseZ() {
        Direction direction;

        switch (this.ordinal()) {
            case 0:
                direction = Direction.EAST;
                break;
            case 1:
                direction = Direction.WEST;
                break;
            case 2:
            case 3:
            default:
                throw new IllegalStateException("Unable to get Z-rotated facing of " + String.valueOf(this));
            case 4:
                direction = Direction.DOWN;
                break;
            case 5:
                direction = Direction.UP;
        }

        return direction;
    }

    public Direction getCounterClockWise() {
        Direction direction;

        switch (this.ordinal()) {
            case 2:
                direction = Direction.WEST;
                break;
            case 3:
                direction = Direction.EAST;
                break;
            case 4:
                direction = Direction.SOUTH;
                break;
            case 5:
                direction = Direction.NORTH;
                break;
            default:
                throw new IllegalStateException("Unable to get CCW facing of " + String.valueOf(this));
        }

        return direction;
    }

    public int getStepX() {
        return this.normal.getX();
    }

    public int getStepY() {
        return this.normal.getY();
    }

    public int getStepZ() {
        return this.normal.getZ();
    }

    public Vector3f step() {
        return new Vector3f(this.normalVec3f);
    }

    public String getName() {
        return this.name;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    public static @Nullable Direction byName(String name) {
        return Direction.CODEC.byName(name);
    }

    public static Direction from3DDataValue(int data) {
        return Direction.BY_3D_DATA[Mth.abs(data % Direction.BY_3D_DATA.length)];
    }

    public static Direction from2DDataValue(int data) {
        return Direction.BY_2D_DATA[Mth.abs(data % Direction.BY_2D_DATA.length)];
    }

    public static Direction fromYRot(double yRot) {
        return from2DDataValue(Mth.floor(yRot / 90.0D + 0.5D) & 3);
    }

    public static Direction fromAxisAndDirection(Direction.Axis axis, Direction.AxisDirection direction) {
        Direction direction1;

        switch (axis.ordinal()) {
            case 0:
                direction1 = direction == Direction.AxisDirection.POSITIVE ? Direction.EAST : Direction.WEST;
                break;
            case 1:
                direction1 = direction == Direction.AxisDirection.POSITIVE ? Direction.UP : Direction.DOWN;
                break;
            case 2:
                direction1 = direction == Direction.AxisDirection.POSITIVE ? Direction.SOUTH : Direction.NORTH;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return direction1;
    }

    public float toYRot() {
        return (float) ((this.data2d & 3) * 90);
    }

    public static Direction getRandom(RandomSource random) {
        return (Direction) Util.getRandom(Direction.VALUES, random);
    }

    public static Direction getApproximateNearest(double dx, double dy, double dz) {
        return getApproximateNearest((float) dx, (float) dy, (float) dz);
    }

    public static Direction getApproximateNearest(float dx, float dy, float dz) {
        Direction direction = Direction.NORTH;
        float f3 = Float.MIN_VALUE;

        for (Direction direction1 : Direction.VALUES) {
            float f4 = dx * (float) direction1.normal.getX() + dy * (float) direction1.normal.getY() + dz * (float) direction1.normal.getZ();

            if (f4 > f3) {
                f3 = f4;
                direction = direction1;
            }
        }

        return direction;
    }

    public static Direction getApproximateNearest(Vec3 vec) {
        return getApproximateNearest(vec.x, vec.y, vec.z);
    }

    @Contract("_,_,_,!null->!null;_,_,_,_->_")
    public static @Nullable Direction getNearest(int x, int y, int z, @Nullable Direction orElse) {
        int l = Math.abs(x);
        int i1 = Math.abs(y);
        int j1 = Math.abs(z);

        return l > j1 && l > i1 ? (x < 0 ? Direction.WEST : Direction.EAST) : (j1 > l && j1 > i1 ? (z < 0 ? Direction.NORTH : Direction.SOUTH) : (i1 > l && i1 > j1 ? (y < 0 ? Direction.DOWN : Direction.UP) : orElse));
    }

    @Contract("_,!null->!null;_,_->_")
    public static @Nullable Direction getNearest(Vec3i vec, @Nullable Direction orElse) {
        return getNearest(vec.getX(), vec.getY(), vec.getZ(), orElse);
    }

    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    private static DataResult<Direction> verifyVertical(Direction v) {
        return v.getAxis().isVertical() ? DataResult.success(v) : DataResult.error(() -> {
            return "Expected a vertical direction";
        });
    }

    public static Direction get(Direction.AxisDirection axisDirection, Direction.Axis axis) {
        for (Direction direction : Direction.VALUES) {
            if (direction.getAxisDirection() == axisDirection && direction.getAxis() == axis) {
                return direction;
            }
        }

        String s = String.valueOf(axisDirection);

        throw new IllegalArgumentException("No such direction: " + s + " " + String.valueOf(axis));
    }

    public static ImmutableList<Direction.Axis> axisStepOrder(Vec3 movement) {
        return Math.abs(movement.x) < Math.abs(movement.z) ? Direction.YZX_AXIS_ORDER : Direction.YXZ_AXIS_ORDER;
    }

    public Vec3i getUnitVec3i() {
        return this.normal;
    }

    public Vec3 getUnitVec3() {
        return this.normalVec3;
    }

    public Vector3fc getUnitVec3f() {
        return this.normalVec3f;
    }

    public boolean isFacingAngle(float yAngle) {
        float f1 = yAngle * ((float) Math.PI / 180F);
        float f2 = -Mth.sin((double) f1);
        float f3 = Mth.cos((double) f1);

        return (float) this.normal.getX() * f2 + (float) this.normal.getZ() * f3 > 0.0F;
    }

    public static enum Axis implements Predicate<Direction>, StringRepresentable {

        X("x") {
            @Override
            public int choose(int x, int y, int z) {
                return x;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return x;
            }

            @Override
            public double choose(double x, double y, double z) {
                return x;
            }

            @Override
            public Direction getPositive() {
                return Direction.EAST;
            }

            @Override
            public Direction getNegative() {
                return Direction.WEST;
            }
        },
        Y("y") {
            @Override
            public int choose(int x, int y, int z) {
                return y;
            }

            @Override
            public double choose(double x, double y, double z) {
                return y;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return y;
            }

            @Override
            public Direction getPositive() {
                return Direction.UP;
            }

            @Override
            public Direction getNegative() {
                return Direction.DOWN;
            }
        },
        Z("z") {
            @Override
            public int choose(int x, int y, int z) {
                return z;
            }

            @Override
            public double choose(double x, double y, double z) {
                return z;
            }

            @Override
            public boolean choose(boolean x, boolean y, boolean z) {
                return z;
            }

            @Override
            public Direction getPositive() {
                return Direction.SOUTH;
            }

            @Override
            public Direction getNegative() {
                return Direction.NORTH;
            }
        };

        public static final Direction.Axis[] VALUES = values();
        public static final StringRepresentable.EnumCodec<Direction.Axis> CODEC = StringRepresentable.<Direction.Axis>fromEnum(Direction.Axis::values);
        private final String name;

        private Axis(String name) {
            this.name = name;
        }

        public static Direction.@Nullable Axis byName(String name) {
            return Direction.Axis.CODEC.byName(name);
        }

        public String getName() {
            return this.name;
        }

        public boolean isVertical() {
            return this == Direction.Axis.Y;
        }

        public boolean isHorizontal() {
            return this == Direction.Axis.X || this == Direction.Axis.Z;
        }

        public abstract Direction getPositive();

        public abstract Direction getNegative();

        public Direction[] getDirections() {
            return new Direction[]{this.getPositive(), this.getNegative()};
        }

        public String toString() {
            return this.name;
        }

        public static Direction.Axis getRandom(RandomSource random) {
            return (Direction.Axis) Util.getRandom(Direction.Axis.VALUES, random);
        }

        public boolean test(@Nullable Direction input) {
            return input != null && input.getAxis() == this;
        }

        public Direction.Plane getPlane() {
            Direction.Plane direction_plane;

            switch (this.ordinal()) {
                case 0:
                case 2:
                    direction_plane = Direction.Plane.HORIZONTAL;
                    break;
                case 1:
                    direction_plane = Direction.Plane.VERTICAL;
                    break;
                default:
                    throw new MatchException((String) null, (Throwable) null);
            }

            return direction_plane;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public abstract int choose(int x, int y, int z);

        public abstract double choose(double x, double y, double z);

        public abstract boolean choose(boolean x, boolean y, boolean z);
    }

    public static enum AxisDirection {

        POSITIVE(1, "Towards positive"), NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        private AxisDirection(int step, String name) {
            this.step = step;
            this.name = name;
        }

        public int getStep() {
            return this.step;
        }

        public String getName() {
            return this.name;
        }

        public String toString() {
            return this.name;
        }

        public Direction.AxisDirection opposite() {
            return this == Direction.AxisDirection.POSITIVE ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE;
        }
    }

    public static enum Plane implements Predicate<Direction>, Iterable<Direction> {

        HORIZONTAL(new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}), VERTICAL(new Direction[]{Direction.UP, Direction.DOWN}, new Direction.Axis[]{Direction.Axis.Y});

        private final Direction[] faces;
        private final Direction.Axis[] axis;

        private Plane(Direction[] faces, Direction.Axis[] axis) {
            this.faces = faces;
            this.axis = axis;
        }

        public Direction getRandomDirection(RandomSource random) {
            return (Direction) Util.getRandom(this.faces, random);
        }

        public Direction.Axis getRandomAxis(RandomSource random) {
            return (Direction.Axis) Util.getRandom(this.axis, random);
        }

        public boolean test(@Nullable Direction input) {
            return input != null && input.getAxis().getPlane() == this;
        }

        public Iterator<Direction> iterator() {
            return Iterators.forArray(this.faces);
        }

        public Stream<Direction> stream() {
            return Arrays.stream(this.faces);
        }

        public List<Direction> shuffledCopy(RandomSource random) {
            return Util.shuffledCopy(this.faces, random);
        }

        public int length() {
            return this.faces.length;
        }
    }
}
