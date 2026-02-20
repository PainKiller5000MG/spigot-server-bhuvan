package net.minecraft.world.entity;

import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum Relative {

    X(0), Y(1), Z(2), Y_ROT(3), X_ROT(4), DELTA_X(5), DELTA_Y(6), DELTA_Z(7), ROTATE_DELTA(8);

    public static final Set<Relative> ALL = Set.of(values());
    public static final Set<Relative> ROTATION = Set.of(Relative.X_ROT, Relative.Y_ROT);
    public static final Set<Relative> DELTA = Set.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z, Relative.ROTATE_DELTA);
    public static final StreamCodec<ByteBuf, Set<Relative>> SET_STREAM_CODEC = ByteBufCodecs.INT.map(Relative::unpack, Relative::pack);
    private final int bit;

    @SafeVarargs
    public static Set<Relative> union(Set<Relative>... sets) {
        HashSet<Relative> hashset = new HashSet();

        for (Set<Relative> set : sets) {
            hashset.addAll(set);
        }

        return hashset;
    }

    public static Set<Relative> rotation(boolean relativeYRot, boolean relativeXRot) {
        Set<Relative> set = EnumSet.noneOf(Relative.class);

        if (relativeYRot) {
            set.add(Relative.Y_ROT);
        }

        if (relativeXRot) {
            set.add(Relative.X_ROT);
        }

        return set;
    }

    public static Set<Relative> position(boolean relativeX, boolean relativeY, boolean relativeZ) {
        Set<Relative> set = EnumSet.noneOf(Relative.class);

        if (relativeX) {
            set.add(Relative.X);
        }

        if (relativeY) {
            set.add(Relative.Y);
        }

        if (relativeZ) {
            set.add(Relative.Z);
        }

        return set;
    }

    public static Set<Relative> direction(boolean relativeX, boolean relativeY, boolean relativeZ) {
        Set<Relative> set = EnumSet.noneOf(Relative.class);

        if (relativeX) {
            set.add(Relative.DELTA_X);
        }

        if (relativeY) {
            set.add(Relative.DELTA_Y);
        }

        if (relativeZ) {
            set.add(Relative.DELTA_Z);
        }

        return set;
    }

    private Relative(int bit) {
        this.bit = bit;
    }

    private int getMask() {
        return 1 << this.bit;
    }

    private boolean isSet(int value) {
        return (value & this.getMask()) == this.getMask();
    }

    public static Set<Relative> unpack(int value) {
        Set<Relative> set = EnumSet.noneOf(Relative.class);

        for (Relative relative : values()) {
            if (relative.isSet(value)) {
                set.add(relative);
            }
        }

        return set;
    }

    public static int pack(Set<Relative> set) {
        int i = 0;

        for (Relative relative : set) {
            i |= relative.getMask();
        }

        return i;
    }
}
