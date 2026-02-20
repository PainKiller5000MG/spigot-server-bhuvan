package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EntityAttachments {

    private final Map<EntityAttachment, List<Vec3>> attachments;

    private EntityAttachments(Map<EntityAttachment, List<Vec3>> attachments) {
        this.attachments = attachments;
    }

    public static EntityAttachments createDefault(float width, float height) {
        return builder().build(width, height);
    }

    public static EntityAttachments.Builder builder() {
        return new EntityAttachments.Builder();
    }

    public EntityAttachments scale(float x, float y, float z) {
        return new EntityAttachments(Util.makeEnumMap(EntityAttachment.class, (entityattachment) -> {
            List<Vec3> list = new ArrayList();

            for (Vec3 vec3 : (List) this.attachments.get(entityattachment)) {
                list.add(vec3.multiply((double) x, (double) y, (double) z));
            }

            return list;
        }));
    }

    public @Nullable Vec3 getNullable(EntityAttachment attachment, int index, float rotY) {
        List<Vec3> list = (List) this.attachments.get(attachment);

        return index >= 0 && index < list.size() ? transformPoint((Vec3) list.get(index), rotY) : null;
    }

    public Vec3 get(EntityAttachment attachment, int index, float rotY) {
        Vec3 vec3 = this.getNullable(attachment, index, rotY);

        if (vec3 == null) {
            String s = String.valueOf(attachment);

            throw new IllegalStateException("Had no attachment point of type: " + s + " for index: " + index);
        } else {
            return vec3;
        }
    }

    public Vec3 getAverage(EntityAttachment attachment) {
        List<Vec3> list = (List) this.attachments.get(attachment);

        if (list != null && !list.isEmpty()) {
            Vec3 vec3 = Vec3.ZERO;

            for (Vec3 vec31 : list) {
                vec3 = vec3.add(vec31);
            }

            return vec3.scale((double) (1.0F / (float) list.size()));
        } else {
            throw new IllegalStateException("No attachment points of type: PASSENGER");
        }
    }

    public Vec3 getClamped(EntityAttachment attachment, int index, float rotY) {
        List<Vec3> list = (List) this.attachments.get(attachment);

        if (list.isEmpty()) {
            throw new IllegalStateException("Had no attachment points of type: " + String.valueOf(attachment));
        } else {
            Vec3 vec3 = (Vec3) list.get(Mth.clamp(index, 0, list.size() - 1));

            return transformPoint(vec3, rotY);
        }
    }

    private static Vec3 transformPoint(Vec3 point, float rotY) {
        return point.yRot(-rotY * ((float) Math.PI / 180F));
    }

    public static class Builder {

        private final Map<EntityAttachment, List<Vec3>> attachments = new EnumMap(EntityAttachment.class);

        private Builder() {}

        public EntityAttachments.Builder attach(EntityAttachment attachment, float x, float y, float z) {
            return this.attach(attachment, new Vec3((double) x, (double) y, (double) z));
        }

        public EntityAttachments.Builder attach(EntityAttachment attachment, Vec3 point) {
            ((List) this.attachments.computeIfAbsent(attachment, (entityattachment1) -> {
                return new ArrayList(1);
            })).add(point);
            return this;
        }

        public EntityAttachments build(float width, float height) {
            Map<EntityAttachment, List<Vec3>> map = Util.<EntityAttachment, List<Vec3>>makeEnumMap(EntityAttachment.class, (entityattachment) -> {
                List<Vec3> list = (List) this.attachments.get(entityattachment);

                return list == null ? entityattachment.createFallbackPoints(width, height) : List.copyOf(list);
            });

            return new EntityAttachments(map);
        }
    }
}
