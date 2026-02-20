package net.minecraft.world.level.redstone;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public class Orientation {

    public static final StreamCodec<ByteBuf, Orientation> STREAM_CODEC = ByteBufCodecs.idMapper(Orientation::fromIndex, Orientation::getIndex);
    private static final Orientation[] ORIENTATIONS = (Orientation[]) Util.make(() -> {
        Orientation[] aorientation = new Orientation[48];

        generateContext(new Orientation(Direction.UP, Direction.NORTH, Orientation.SideBias.LEFT), aorientation);
        return aorientation;
    });
    private final Direction up;
    private final Direction front;
    private final Direction side;
    private final Orientation.SideBias sideBias;
    private final int index;
    private final List<Direction> neighbors;
    private final List<Direction> horizontalNeighbors;
    private final List<Direction> verticalNeighbors;
    private final Map<Direction, Orientation> withFront = new EnumMap(Direction.class);
    private final Map<Direction, Orientation> withUp = new EnumMap(Direction.class);
    private final Map<Orientation.SideBias, Orientation> withSideBias = new EnumMap(Orientation.SideBias.class);

    private Orientation(Direction up, Direction front, Orientation.SideBias sideBias) {
        this.up = up;
        this.front = front;
        this.sideBias = sideBias;
        this.index = generateIndex(up, front, sideBias);
        Vec3i vec3i = front.getUnitVec3i().cross(up.getUnitVec3i());
        Direction direction2 = Direction.getNearest(vec3i, (Direction) null);

        Objects.requireNonNull(direction2);
        if (this.sideBias == Orientation.SideBias.RIGHT) {
            this.side = direction2;
        } else {
            this.side = direction2.getOpposite();
        }

        this.neighbors = List.of(this.front.getOpposite(), this.front, this.side, this.side.getOpposite(), this.up.getOpposite(), this.up);
        this.horizontalNeighbors = this.neighbors.stream().filter((direction3) -> {
            return direction3.getAxis() != this.up.getAxis();
        }).toList();
        this.verticalNeighbors = this.neighbors.stream().filter((direction3) -> {
            return direction3.getAxis() == this.up.getAxis();
        }).toList();
    }

    public static Orientation of(Direction up, Direction front, Orientation.SideBias sideBias) {
        return Orientation.ORIENTATIONS[generateIndex(up, front, sideBias)];
    }

    public Orientation withUp(Direction up) {
        return (Orientation) this.withUp.get(up);
    }

    public Orientation withFront(Direction front) {
        return (Orientation) this.withFront.get(front);
    }

    public Orientation withFrontPreserveUp(Direction front) {
        return front.getAxis() == this.up.getAxis() ? this : (Orientation) this.withFront.get(front);
    }

    public Orientation withFrontAdjustSideBias(Direction front) {
        Orientation orientation = this.withFront(front);

        return this.front == orientation.side ? orientation.withMirror() : orientation;
    }

    public Orientation withSideBias(Orientation.SideBias sideBias) {
        return (Orientation) this.withSideBias.get(sideBias);
    }

    public Orientation withMirror() {
        return this.withSideBias(this.sideBias.getOpposite());
    }

    public Direction getFront() {
        return this.front;
    }

    public Direction getUp() {
        return this.up;
    }

    public Direction getSide() {
        return this.side;
    }

    public Orientation.SideBias getSideBias() {
        return this.sideBias;
    }

    public List<Direction> getDirections() {
        return this.neighbors;
    }

    public List<Direction> getHorizontalDirections() {
        return this.horizontalNeighbors;
    }

    public List<Direction> getVerticalDirections() {
        return this.verticalNeighbors;
    }

    public String toString() {
        String s = String.valueOf(this.up);

        return "[up=" + s + ",front=" + String.valueOf(this.front) + ",sideBias=" + String.valueOf(this.sideBias) + "]";
    }

    public int getIndex() {
        return this.index;
    }

    public static Orientation fromIndex(int index) {
        return Orientation.ORIENTATIONS[index];
    }

    public static Orientation random(RandomSource rand) {
        return (Orientation) Util.getRandom(Orientation.ORIENTATIONS, rand);
    }

    private static Orientation generateContext(Orientation self, Orientation[] lookup) {
        if (lookup[self.getIndex()] != null) {
            return lookup[self.getIndex()];
        } else {
            lookup[self.getIndex()] = self;

            for (Orientation.SideBias orientation_sidebias : Orientation.SideBias.values()) {
                self.withSideBias.put(orientation_sidebias, generateContext(new Orientation(self.up, self.front, orientation_sidebias), lookup));
            }

            for (Direction direction : Direction.values()) {
                Direction direction1 = self.up;

                if (direction == self.up) {
                    direction1 = self.front.getOpposite();
                }

                if (direction == self.up.getOpposite()) {
                    direction1 = self.front;
                }

                self.withFront.put(direction, generateContext(new Orientation(direction1, direction, self.sideBias), lookup));
            }

            for (Direction direction2 : Direction.values()) {
                Direction direction3 = self.front;

                if (direction2 == self.front) {
                    direction3 = self.up.getOpposite();
                }

                if (direction2 == self.front.getOpposite()) {
                    direction3 = self.up;
                }

                self.withUp.put(direction2, generateContext(new Orientation(direction2, direction3, self.sideBias), lookup));
            }

            return self;
        }
    }

    @VisibleForTesting
    protected static int generateIndex(Direction up, Direction front, Orientation.SideBias sideBias) {
        if (up.getAxis() == front.getAxis()) {
            throw new IllegalStateException("Up-vector and front-vector can not be on the same axis");
        } else {
            int i;

            if (up.getAxis() == Direction.Axis.Y) {
                i = front.getAxis() == Direction.Axis.X ? 1 : 0;
            } else {
                i = front.getAxis() == Direction.Axis.Y ? 1 : 0;
            }

            int j = i << 1 | front.getAxisDirection().ordinal();

            return ((up.ordinal() << 2) + j << 1) + sideBias.ordinal();
        }
    }

    public static enum SideBias {

        LEFT("left"), RIGHT("right");

        private final String name;

        private SideBias(String name) {
            this.name = name;
        }

        public Orientation.SideBias getOpposite() {
            return this == Orientation.SideBias.LEFT ? Orientation.SideBias.RIGHT : Orientation.SideBias.LEFT;
        }

        public String toString() {
            return this.name;
        }
    }
}
