package net.minecraft.world.entity;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public enum EntityAttachment {

    PASSENGER(EntityAttachment.Fallback.AT_HEIGHT), VEHICLE(EntityAttachment.Fallback.AT_FEET), NAME_TAG(EntityAttachment.Fallback.AT_HEIGHT), WARDEN_CHEST(EntityAttachment.Fallback.AT_CENTER);

    private final EntityAttachment.Fallback fallback;

    private EntityAttachment(EntityAttachment.Fallback fallback) {
        this.fallback = fallback;
    }

    public List<Vec3> createFallbackPoints(float width, float height) {
        return this.fallback.create(width, height);
    }

    public interface Fallback {

        List<Vec3> ZERO = List.of(Vec3.ZERO);
        EntityAttachment.Fallback AT_FEET = (f, f1) -> {
            return EntityAttachment.Fallback.ZERO;
        };
        EntityAttachment.Fallback AT_HEIGHT = (f, f1) -> {
            return List.of(new Vec3(0.0D, (double) f1, 0.0D));
        };
        EntityAttachment.Fallback AT_CENTER = (f, f1) -> {
            return List.of(new Vec3(0.0D, (double) f1 / 2.0D, 0.0D));
        };

        List<Vec3> create(float width, float height);
    }
}
