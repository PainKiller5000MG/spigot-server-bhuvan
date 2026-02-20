package net.minecraft.world.entity;

import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public record PositionMoveRotation(Vec3 position, Vec3 deltaMovement, float yRot, float xRot) {

    public static final StreamCodec<FriendlyByteBuf, PositionMoveRotation> STREAM_CODEC = StreamCodec.composite(Vec3.STREAM_CODEC, PositionMoveRotation::position, Vec3.STREAM_CODEC, PositionMoveRotation::deltaMovement, ByteBufCodecs.FLOAT, PositionMoveRotation::yRot, ByteBufCodecs.FLOAT, PositionMoveRotation::xRot, PositionMoveRotation::new);

    public static PositionMoveRotation of(Entity entity) {
        return entity.isInterpolating() ? new PositionMoveRotation(entity.getInterpolation().position(), entity.getKnownMovement(), entity.getInterpolation().yRot(), entity.getInterpolation().xRot()) : new PositionMoveRotation(entity.position(), entity.getKnownMovement(), entity.getYRot(), entity.getXRot());
    }

    public PositionMoveRotation withRotation(float yRot, float xRot) {
        return new PositionMoveRotation(this.position(), this.deltaMovement(), yRot, xRot);
    }

    public static PositionMoveRotation of(TeleportTransition transition) {
        return new PositionMoveRotation(transition.position(), transition.deltaMovement(), transition.yRot(), transition.xRot());
    }

    public static PositionMoveRotation calculateAbsolute(PositionMoveRotation source, PositionMoveRotation change, Set<Relative> relatives) {
        double d0 = relatives.contains(Relative.X) ? source.position.x : 0.0D;
        double d1 = relatives.contains(Relative.Y) ? source.position.y : 0.0D;
        double d2 = relatives.contains(Relative.Z) ? source.position.z : 0.0D;
        float f = relatives.contains(Relative.Y_ROT) ? source.yRot : 0.0F;
        float f1 = relatives.contains(Relative.X_ROT) ? source.xRot : 0.0F;
        Vec3 vec3 = new Vec3(d0 + change.position.x, d1 + change.position.y, d2 + change.position.z);
        float f2 = f + change.yRot;
        float f3 = Mth.clamp(f1 + change.xRot, -90.0F, 90.0F);
        Vec3 vec31 = source.deltaMovement;

        if (relatives.contains(Relative.ROTATE_DELTA)) {
            float f4 = source.yRot - f2;
            float f5 = source.xRot - f3;

            vec31 = vec31.xRot((float) Math.toRadians((double) f5));
            vec31 = vec31.yRot((float) Math.toRadians((double) f4));
        }

        Vec3 vec32 = new Vec3(calculateDelta(vec31.x, change.deltaMovement.x, relatives, Relative.DELTA_X), calculateDelta(vec31.y, change.deltaMovement.y, relatives, Relative.DELTA_Y), calculateDelta(vec31.z, change.deltaMovement.z, relatives, Relative.DELTA_Z));

        return new PositionMoveRotation(vec3, vec32, f2, f3);
    }

    private static double calculateDelta(double currentDelta, double deltaChange, Set<Relative> relatives, Relative relative) {
        return relatives.contains(relative) ? currentDelta + deltaChange : deltaChange;
    }
}
