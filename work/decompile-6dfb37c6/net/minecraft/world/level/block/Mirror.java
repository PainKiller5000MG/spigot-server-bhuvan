package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

public enum Mirror implements StringRepresentable {

    NONE("none", OctahedralGroup.IDENTITY), LEFT_RIGHT("left_right", OctahedralGroup.INVERT_Z), FRONT_BACK("front_back", OctahedralGroup.INVERT_X);

    public static final Codec<Mirror> CODEC = StringRepresentable.<Mirror>fromEnum(Mirror::values);
    /** @deprecated */
    @Deprecated
    public static final Codec<Mirror> LEGACY_CODEC = ExtraCodecs.<Mirror>legacyEnum(Mirror::valueOf);
    private final String id;
    private final Component symbol;
    private final OctahedralGroup rotation;

    private Mirror(String id, OctahedralGroup rotation) {
        this.id = id;
        this.symbol = Component.translatable("mirror." + id);
        this.rotation = rotation;
    }

    public int mirror(int rotation, int steps) {
        int k = steps / 2;
        int l = rotation > k ? rotation - steps : rotation;

        switch (this.ordinal()) {
            case 1:
                return (k - l + steps) % steps;
            case 2:
                return (steps - l) % steps;
            default:
                return rotation;
        }
    }

    public Rotation getRotation(Direction value) {
        Direction.Axis direction_axis = value.getAxis();

        return (this != Mirror.LEFT_RIGHT || direction_axis != Direction.Axis.Z) && (this != Mirror.FRONT_BACK || direction_axis != Direction.Axis.X) ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    public Direction mirror(Direction direction) {
        return this == Mirror.FRONT_BACK && direction.getAxis() == Direction.Axis.X ? direction.getOpposite() : (this == Mirror.LEFT_RIGHT && direction.getAxis() == Direction.Axis.Z ? direction.getOpposite() : direction);
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Component symbol() {
        return this.symbol;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
