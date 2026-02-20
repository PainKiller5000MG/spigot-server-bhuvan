package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;

public enum Rotation implements StringRepresentable {

    NONE(0, "none", OctahedralGroup.IDENTITY), CLOCKWISE_90(1, "clockwise_90", OctahedralGroup.ROT_90_Y_NEG), CLOCKWISE_180(2, "180", OctahedralGroup.ROT_180_FACE_XZ), COUNTERCLOCKWISE_90(3, "counterclockwise_90", OctahedralGroup.ROT_90_Y_POS);

    public static final IntFunction<Rotation> BY_ID = ByIdMap.<Rotation>continuous(Rotation::getIndex, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final Codec<Rotation> CODEC = StringRepresentable.<Rotation>fromEnum(Rotation::values);
    public static final StreamCodec<ByteBuf, Rotation> STREAM_CODEC = ByteBufCodecs.idMapper(Rotation.BY_ID, Rotation::getIndex);
    /** @deprecated */
    @Deprecated
    public static final Codec<Rotation> LEGACY_CODEC = ExtraCodecs.<Rotation>legacyEnum(Rotation::valueOf);
    private final int index;
    private final String id;
    private final OctahedralGroup rotation;

    private Rotation(int index, String id, OctahedralGroup rotation) {
        this.index = index;
        this.id = id;
        this.rotation = rotation;
    }

    public Rotation getRotated(Rotation rot) {
        Rotation rotation1;

        switch (rot.ordinal()) {
            case 1:
                switch (this.ordinal()) {
                    case 0:
                        rotation1 = Rotation.CLOCKWISE_90;
                        return rotation1;
                    case 1:
                        rotation1 = Rotation.CLOCKWISE_180;
                        return rotation1;
                    case 2:
                        rotation1 = Rotation.COUNTERCLOCKWISE_90;
                        return rotation1;
                    case 3:
                        rotation1 = Rotation.NONE;
                        return rotation1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            case 2:
                switch (this.ordinal()) {
                    case 0:
                        rotation1 = Rotation.CLOCKWISE_180;
                        return rotation1;
                    case 1:
                        rotation1 = Rotation.COUNTERCLOCKWISE_90;
                        return rotation1;
                    case 2:
                        rotation1 = Rotation.NONE;
                        return rotation1;
                    case 3:
                        rotation1 = Rotation.CLOCKWISE_90;
                        return rotation1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            case 3:
                switch (this.ordinal()) {
                    case 0:
                        rotation1 = Rotation.COUNTERCLOCKWISE_90;
                        return rotation1;
                    case 1:
                        rotation1 = Rotation.NONE;
                        return rotation1;
                    case 2:
                        rotation1 = Rotation.CLOCKWISE_90;
                        return rotation1;
                    case 3:
                        rotation1 = Rotation.CLOCKWISE_180;
                        return rotation1;
                    default:
                        throw new MatchException((String) null, (Throwable) null);
                }
            default:
                rotation1 = this;
                return rotation1;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Direction rotate(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y) {
            return direction;
        } else {
            Direction direction1;

            switch (this.ordinal()) {
                case 1:
                    direction1 = direction.getClockWise();
                    break;
                case 2:
                    direction1 = direction.getOpposite();
                    break;
                case 3:
                    direction1 = direction.getCounterClockWise();
                    break;
                default:
                    direction1 = direction;
            }

            return direction1;
        }
    }

    public int rotate(int rotation, int steps) {
        int k;

        switch (this.ordinal()) {
            case 1:
                k = (rotation + steps / 4) % steps;
                break;
            case 2:
                k = (rotation + steps / 2) % steps;
                break;
            case 3:
                k = (rotation + steps * 3 / 4) % steps;
                break;
            default:
                k = rotation;
        }

        return k;
    }

    public static Rotation getRandom(RandomSource random) {
        return (Rotation) Util.getRandom(values(), random);
    }

    public static List<Rotation> getShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    private int getIndex() {
        return this.index;
    }
}
